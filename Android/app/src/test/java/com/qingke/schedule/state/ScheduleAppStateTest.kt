package com.qingke.schedule.state

import com.qingke.schedule.domain.Course
import com.qingke.schedule.domain.CourseSchedule
import com.qingke.schedule.domain.Period
import com.qingke.schedule.domain.RepeatRule
import com.qingke.schedule.domain.ScheduleData
import com.qingke.schedule.domain.Semester
import com.qingke.schedule.persistence.ScheduleRepository
import com.qingke.schedule.preferences.AppearanceMode
import com.qingke.schedule.preferences.ReminderPreferences
import com.qingke.schedule.preferences.SchedulePreferences
import com.qingke.schedule.preferences.SchedulePreferencesRepository
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleAppStateTest {
    @Test fun initialStateIsNotLoadedAndDoesNotNeedOnboarding() = runTest {
        val state = appState(FakeRepository(data = emptyData()))
        assertEquals(LoadStatus.NOT_LOADED, state.state.value.loadStatus)
        assertEquals(SchedulePreferences.defaults, state.state.value.preferences)
        assertFalse(state.state.value.needsOnboarding)
    }

    @Test fun loadEmptyStoreMakesOnboardingNecessary() = runTest {
        val state = appState(FakeRepository(data = emptyData()))
        state.load()
        assertEquals(LoadStatus.READY, state.state.value.loadStatus)
        assertTrue(state.state.value.needsOnboarding)
    }

    @Test fun successfulLoadPublishesScheduleAndPreferencesTogether() = runTest {
        val preferences = SchedulePreferences(
            appearanceMode = AppearanceMode.DARK,
            reminder = ReminderPreferences(remindersEnabled = true),
        )
        val state = appState(FakeRepository(fixture()), FakePreferencesRepository(preferences))
        state.load()
        assertEquals(fixture(), state.state.value.data)
        assertEquals(preferences, state.state.value.preferences)
        assertEquals(LoadStatus.READY, state.state.value.loadStatus)
    }

    @Test fun failedLoadKeepsSnapshotAndCanRetry() = runTest {
        val repository = FakeRepository(data = fixture()).also { it.failLoad = true }
        val preferences = SchedulePreferences(appearanceMode = AppearanceMode.DARK)
        val preferencesRepository = FakePreferencesRepository(preferences)
        val state = appState(repository, preferencesRepository)
        state.load()
        assertEquals(LoadStatus.FAILED, state.state.value.loadStatus)
        assertEquals(emptyData(), state.state.value.data)
        assertEquals(SchedulePreferences.defaults, state.state.value.preferences)
        assertEquals(0, preferencesRepository.loadCalls.get())
        assertFalse(state.state.value.needsOnboarding)
        assertTrue(state.state.value.error!!.startsWith("读取课表失败："))
        repository.failLoad = false
        state.load()
        assertEquals(fixture(), state.state.value.data)
        assertEquals(preferences, state.state.value.preferences)
        assertNull(state.state.value.error)
    }

    @Test fun preferencesLoadFailureDoesNotPublishLoadedScheduleAndCanRetry() = runTest {
        val preferences = SchedulePreferences(appearanceMode = AppearanceMode.DARK)
        val repository = FakePreferencesRepository(preferences).also { it.failLoad = true }
        val state = appState(FakeRepository(fixture()), repository)
        state.load()
        assertEquals(emptyData(), state.state.value.data)
        assertEquals(SchedulePreferences.defaults, state.state.value.preferences)
        assertEquals(LoadStatus.FAILED, state.state.value.loadStatus)
        assertTrue(state.state.value.error!!.startsWith("读取设置失败："))
        repository.failLoad = false
        state.load()
        assertEquals(fixture(), state.state.value.data)
        assertEquals(preferences, state.state.value.preferences)
        assertEquals(LoadStatus.READY, state.state.value.loadStatus)
    }

    @Test fun cancelledLoadPropagatesAndRestoresPreviousState() = runTest {
        val state = appState(FakeRepository(data = fixture()).also { it.cancelLoad = true })
        assertThrowsCancellation { state.load() }
        assertEquals(LoadStatus.NOT_LOADED, state.state.value.loadStatus)
        assertEquals(emptyData(), state.state.value.data)
        assertNull(state.state.value.error)
    }

    @Test fun cancelledPreferencesLoadPropagatesWithoutPublishingSchedule() = runTest {
        val state = appState(
            FakeRepository(data = fixture()),
            FakePreferencesRepository(SchedulePreferences(appearanceMode = AppearanceMode.DARK)).also {
                it.cancelLoad = true
            },
        )
        assertThrowsCancellation { state.load() }
        assertEquals(LoadStatus.NOT_LOADED, state.state.value.loadStatus)
        assertEquals(emptyData(), state.state.value.data)
        assertEquals(SchedulePreferences.defaults, state.state.value.preferences)
        assertFalse(state.state.value.isSaving)
        assertNull(state.state.value.error)
    }

    @Test fun successfulWritePublishesReturnedSnapshotWithoutSecondLoad() = runTest {
        val returned = fixture().copy(updatedAt = "2026-01-02T00:00:00Z")
        val repository = FakeRepository(data = fixture()).also { it.replaceResult = returned }
        val state = appState(repository)
        state.load()
        state.replace(fixture())
        assertEquals(returned, state.state.value.data)
        assertEquals(1, repository.loadCalls.get())
    }

    @Test fun successfulSaveSemesterPublishesReturnedSnapshotWithoutSecondLoad() = runTest {
        val returned = fixture().copy(semester = semester().copy(name = "returned"))
        val repository = FakeRepository(data = fixture()).also { it.semesterResult = returned }
        val state = appState(repository)
        state.load(); state.saveSemester(semester().copy(name = "requested"))
        assertEquals(returned, state.state.value.data)
        assertEquals(1, repository.loadCalls.get())
    }

    @Test fun successfulSaveCoursePublishesReturnedSnapshotWithoutSecondLoad() = runTest {
        val returned = fixture().copy(courses = listOf(course("returned")))
        val repository = FakeRepository(data = fixture()).also { it.courseResult = returned }
        val state = appState(repository)
        state.load(); state.saveCourse(course("requested"))
        assertEquals(returned, state.state.value.data)
        assertEquals(1, repository.loadCalls.get())
    }

    @Test fun successfulDeleteCoursePublishesReturnedSnapshotWithoutSecondLoad() = runTest {
        val returned = fixture().copy(courses = emptyList())
        val repository = FakeRepository(data = fixture()).also { it.deleteResult = returned }
        val state = appState(repository)
        state.load(); state.deleteCourse("first")
        assertEquals(returned, state.state.value.data)
        assertEquals(1, repository.loadCalls.get())
    }

    @Test fun failedWriteKeepsSnapshotAndClearsSaving() = runTest {
        val original = fixture()
        val repository = FakeRepository(data = original).also { it.failWrites = true }
        val state = appState(repository)
        state.load(); state.replace(emptyData())
        assertEquals(original, state.state.value.data)
        assertFalse(state.state.value.isSaving)
        assertTrue(state.state.value.error!!.isNotBlank())
        state.clearError(); assertNull(state.state.value.error)
    }

    @Test fun failedSaveSemesterKeepsSnapshotAndClearsSaving() = runTest {
        assertFailedWrite { saveSemester(semester()) }
    }

    @Test fun failedSaveCourseKeepsSnapshotAndClearsSaving() = runTest {
        assertFailedWrite { saveCourse(course("new")) }
    }

    @Test fun failedDeleteCourseKeepsSnapshotAndClearsSaving() = runTest {
        assertFailedWrite { deleteCourse("first") }
    }

    @Test fun cancelledWritePropagatesWithoutPublishingAnError() = runTest {
        val original = fixture()
        val state = appState(FakeRepository(data = original).also { it.cancelWrites = true })
        state.load()
        assertThrowsCancellation { state.replace(emptyData()) }
        assertEquals(original, state.state.value.data)
        assertEquals(LoadStatus.READY, state.state.value.loadStatus)
        assertFalse(state.state.value.isSaving)
        assertNull(state.state.value.error)
    }

    @Test fun preferenceSaveAndUpdatePublishReturnedSnapshotsWithoutChangingSchedule() = runTest {
        val loaded = SchedulePreferences(appearanceMode = AppearanceMode.LIGHT)
        val saved = SchedulePreferences(appearanceMode = AppearanceMode.DARK)
        val updated = saved.copy(reminder = ReminderPreferences(remindersEnabled = true))
        val repository = FakePreferencesRepository(loaded).also { it.saveResult = saved }
        val state = appState(FakeRepository(fixture()), repository)
        state.load()

        state.savePreferences(SchedulePreferences.defaults)
        assertEquals(fixture(), state.state.value.data)
        assertEquals(saved, state.state.value.preferences)
        assertEquals(1, repository.loadCalls.get())

        repository.updateResult = updated
        state.updatePreferences { it.copy(appearanceMode = AppearanceMode.SYSTEM) }
        assertEquals(fixture(), state.state.value.data)
        assertEquals(updated, state.state.value.preferences)
        assertEquals(1, repository.loadCalls.get())
    }

    @Test fun failedPreferenceWriteKeepsCompleteSnapshotAndCanRetry() = runTest {
        val loaded = SchedulePreferences(appearanceMode = AppearanceMode.DARK)
        val repository = FakePreferencesRepository(loaded).also { it.failWrites = true }
        val state = appState(FakeRepository(fixture()), repository)
        state.load()
        state.updatePreferences { SchedulePreferences.defaults }
        assertEquals(fixture(), state.state.value.data)
        assertEquals(loaded, state.state.value.preferences)
        assertFalse(state.state.value.isSaving)
        assertTrue(state.state.value.error!!.isNotBlank())

        repository.failWrites = false
        state.updatePreferences { SchedulePreferences.defaults }
        assertEquals(SchedulePreferences.defaults, state.state.value.preferences)
        assertNull(state.state.value.error)
    }

    @Test fun cancelledPreferenceWriteRestoresCompleteSnapshotAndPropagates() = runTest {
        val loaded = SchedulePreferences(appearanceMode = AppearanceMode.DARK)
        val repository = FakePreferencesRepository(loaded).also { it.cancelWrites = true }
        val state = appState(FakeRepository(fixture()), repository)
        state.load()
        assertThrowsCancellation { state.savePreferences(SchedulePreferences.defaults) }
        assertEquals(fixture(), state.state.value.data)
        assertEquals(loaded, state.state.value.preferences)
        assertEquals(LoadStatus.READY, state.state.value.loadStatus)
        assertFalse(state.state.value.isSaving)
        assertNull(state.state.value.error)
    }

    @Test fun concurrentWritesAreSerialized() = runTest {
        val repository = FakeRepository(data = fixture()).also { it.delayWrites = true }
        val state = appState(repository); state.load()
        val first = async { state.saveCourse(course("second")) }
        val second = async { state.saveCourse(course("third")) }
        first.await(); second.await()
        assertEquals(listOf("first", "second", "third"), state.state.value.data.courses.map { it.id })
        assertEquals(1, repository.maxConcurrentWrites.get())
    }

    @Test fun scheduleAndPreferenceWritesAreSerializedWithoutLosingEitherResult() = runTest {
        val tracker = OperationTracker()
        val scheduleRepository = FakeRepository(data = fixture(), tracker = tracker).also { it.delayWrites = true }
        val preferencesRepository = FakePreferencesRepository(tracker = tracker).also { it.delayWrites = true }
        val state = appState(scheduleRepository, preferencesRepository)
        state.load()
        val scheduleWrite = async { state.saveCourse(course("second")) }
        val preferencesWrite = async {
            state.updatePreferences { it.copy(appearanceMode = AppearanceMode.DARK) }
        }
        scheduleWrite.await()
        preferencesWrite.await()
        assertEquals(listOf("first", "second"), state.state.value.data.courses.map { it.id })
        assertEquals(AppearanceMode.DARK, state.state.value.preferences.appearanceMode)
        assertEquals(1, tracker.maxConcurrent.get())
    }

    private suspend fun assertFailedWrite(operation: suspend ScheduleAppState.() -> Unit) {
        val original = fixture()
        val state = appState(FakeRepository(data = original).also { it.failWrites = true })
        state.load()
        state.operation()
        assertEquals(original, state.state.value.data)
        assertFalse(state.state.value.isSaving)
        assertTrue(state.state.value.error!!.isNotBlank())
    }

    private fun appState(
        repository: FakeRepository,
        preferencesRepository: FakePreferencesRepository = FakePreferencesRepository(),
    ) = ScheduleAppState(repository, preferencesRepository)

    private class FakeRepository(
        var data: ScheduleData,
        private val tracker: OperationTracker? = null,
    ) : ScheduleRepository {
        var failLoad = false
        var failWrites = false
        var cancelLoad = false
        var cancelWrites = false
        var delayWrites = false
        var replaceResult: ScheduleData? = null
        var semesterResult: ScheduleData? = null
        var courseResult: ScheduleData? = null
        var deleteResult: ScheduleData? = null
        val loadCalls = AtomicInteger()
        val maxConcurrentWrites = AtomicInteger()
        private val concurrentWrites = AtomicInteger()
        override suspend fun load(): ScheduleData { loadCalls.incrementAndGet(); if (cancelLoad) throw CancellationException("load cancelled"); if (failLoad) error("load failed"); return data }
        override suspend fun replace(data: ScheduleData): ScheduleData = write { replaceResult ?: data }
        override suspend fun saveSemester(semester: Semester): ScheduleData = write { semesterResult ?: data.copy(semester = semester) }
        override suspend fun saveCourse(course: Course): ScheduleData = write { courseResult ?: data.copy(courses = data.courses + course) }
        override suspend fun deleteCourse(id: String): ScheduleData = write { deleteResult ?: data.copy(courses = data.courses.filterNot { it.id == id }) }
        private suspend fun write(block: () -> ScheduleData): ScheduleData {
            if (cancelWrites) throw CancellationException("write cancelled")
            if (failWrites) error("write failed")
            val running = concurrentWrites.incrementAndGet(); maxConcurrentWrites.updateAndGet { maxOf(it, running) }
            tracker?.enter()
            return try {
                if (delayWrites) delay(10)
                block().also { data = it }
            } finally {
                tracker?.exit()
                concurrentWrites.decrementAndGet()
            }
        }
    }

    private class FakePreferencesRepository(
        var data: SchedulePreferences = SchedulePreferences.defaults,
        private val tracker: OperationTracker? = null,
    ) : SchedulePreferencesRepository {
        var failLoad = false
        var cancelLoad = false
        var failWrites = false
        var cancelWrites = false
        var delayWrites = false
        var saveResult: SchedulePreferences? = null
        var updateResult: SchedulePreferences? = null
        val loadCalls = AtomicInteger()

        override suspend fun load(): SchedulePreferences {
            loadCalls.incrementAndGet()
            if (cancelLoad) throw CancellationException("preferences load cancelled")
            if (failLoad) error("preferences load failed")
            return data
        }

        override suspend fun save(preferences: SchedulePreferences): SchedulePreferences = write {
            saveResult ?: preferences
        }

        override suspend fun update(
            transform: (SchedulePreferences) -> SchedulePreferences,
        ): SchedulePreferences = write {
            updateResult ?: transform(data)
        }

        private suspend fun write(block: () -> SchedulePreferences): SchedulePreferences {
            if (cancelWrites) throw CancellationException("preferences write cancelled")
            if (failWrites) error("preferences write failed")
            tracker?.enter()
            return try {
                if (delayWrites) delay(10)
                block().also { data = it }
            } finally {
                tracker?.exit()
            }
        }
    }

    private class OperationTracker {
        private val concurrent = AtomicInteger()
        val maxConcurrent = AtomicInteger()

        fun enter() {
            val running = concurrent.incrementAndGet()
            maxConcurrent.updateAndGet { maxOf(it, running) }
        }

        fun exit() {
            concurrent.decrementAndGet()
        }
    }
}

private inline fun assertThrowsCancellation(block: () -> Unit) {
    try {
        block()
    } catch (_: CancellationException) {
        return
    }
    throw AssertionError("Expected CancellationException")
}

private fun emptyData() = ScheduleData(1, null, emptyList(), "1970-01-01T00:00:00.000Z")
private fun fixture() = ScheduleData(1, semester(), listOf(course("first")), "2026-01-01T00:00:00Z")
private fun semester() = Semester("term", "2026 春季", "2026-02-23", 16, listOf(Period(2, "09:00", "09:45"), Period(1, "10:00", "10:45")))
private fun course(id: String) = Course(id, id, "教师", "#112233", listOf(CourseSchedule("$id-slot", 1, 2, 2, 1, 16, RepeatRule.EVERY, "A101")))
