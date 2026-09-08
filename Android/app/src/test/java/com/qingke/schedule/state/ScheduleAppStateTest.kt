package com.qingke.schedule.state

import com.qingke.schedule.domain.Course
import com.qingke.schedule.domain.CourseSchedule
import com.qingke.schedule.domain.Period
import com.qingke.schedule.domain.RepeatRule
import com.qingke.schedule.domain.ScheduleData
import com.qingke.schedule.domain.Semester
import com.qingke.schedule.persistence.ScheduleRepository
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
        val state = ScheduleAppState(FakeRepository(data = emptyData()))
        assertEquals(LoadStatus.NOT_LOADED, state.state.value.loadStatus)
        assertFalse(state.state.value.needsOnboarding)
    }

    @Test fun loadEmptyStoreMakesOnboardingNecessary() = runTest {
        val state = ScheduleAppState(FakeRepository(data = emptyData()))
        state.load()
        assertEquals(LoadStatus.READY, state.state.value.loadStatus)
        assertTrue(state.state.value.needsOnboarding)
    }

    @Test fun failedLoadKeepsSnapshotAndCanRetry() = runTest {
        val repository = FakeRepository(data = fixture()).also { it.failLoad = true }
        val state = ScheduleAppState(repository)
        state.load()
        assertEquals(LoadStatus.FAILED, state.state.value.loadStatus)
        assertFalse(state.state.value.needsOnboarding)
        repository.failLoad = false
        state.load()
        assertEquals(fixture(), state.state.value.data)
        assertNull(state.state.value.error)
    }

    @Test fun cancelledLoadPropagatesAndRestoresPreviousState() = runTest {
        val state = ScheduleAppState(FakeRepository(data = fixture()).also { it.cancelLoad = true })
        assertThrowsCancellation { state.load() }
        assertEquals(LoadStatus.NOT_LOADED, state.state.value.loadStatus)
        assertEquals(emptyData(), state.state.value.data)
        assertNull(state.state.value.error)
    }

    @Test fun successfulWritePublishesReturnedSnapshotWithoutSecondLoad() = runTest {
        val returned = fixture().copy(updatedAt = "2026-01-02T00:00:00Z")
        val repository = FakeRepository(data = fixture()).also { it.replaceResult = returned }
        val state = ScheduleAppState(repository)
        state.load()
        state.replace(fixture())
        assertEquals(returned, state.state.value.data)
        assertEquals(1, repository.loadCalls.get())
    }

    @Test fun failedWriteKeepsSnapshotAndClearsSaving() = runTest {
        val original = fixture()
        val repository = FakeRepository(data = original).also { it.failWrites = true }
        val state = ScheduleAppState(repository)
        state.load(); state.replace(emptyData())
        assertEquals(original, state.state.value.data)
        assertFalse(state.state.value.isSaving)
        assertTrue(state.state.value.error!!.isNotBlank())
        state.clearError(); assertNull(state.state.value.error)
    }

    @Test fun cancelledWritePropagatesWithoutPublishingAnError() = runTest {
        val original = fixture()
        val state = ScheduleAppState(FakeRepository(data = original).also { it.cancelWrites = true })
        state.load()
        assertThrowsCancellation { state.replace(emptyData()) }
        assertEquals(original, state.state.value.data)
        assertEquals(LoadStatus.READY, state.state.value.loadStatus)
        assertFalse(state.state.value.isSaving)
        assertNull(state.state.value.error)
    }

    @Test fun concurrentWritesAreSerialized() = runTest {
        val repository = FakeRepository(data = fixture()).also { it.delayWrites = true }
        val state = ScheduleAppState(repository); state.load()
        val first = async { state.saveCourse(course("second")) }
        val second = async { state.saveCourse(course("third")) }
        first.await(); second.await()
        assertEquals(listOf("first", "second", "third"), state.state.value.data.courses.map { it.id })
        assertEquals(1, repository.maxConcurrentWrites.get())
    }

    private class FakeRepository(var data: ScheduleData) : ScheduleRepository {
        var failLoad = false
        var failWrites = false
        var cancelLoad = false
        var cancelWrites = false
        var delayWrites = false
        var replaceResult: ScheduleData? = null
        val loadCalls = AtomicInteger()
        val maxConcurrentWrites = AtomicInteger()
        private val concurrentWrites = AtomicInteger()
        override suspend fun load(): ScheduleData { loadCalls.incrementAndGet(); if (cancelLoad) throw CancellationException("load cancelled"); if (failLoad) error("load failed"); return data }
        override suspend fun replace(data: ScheduleData): ScheduleData = write { replaceResult ?: data }
        override suspend fun saveSemester(semester: Semester): ScheduleData = write { data.copy(semester = semester) }
        override suspend fun saveCourse(course: Course): ScheduleData = write { data.copy(courses = data.courses + course) }
        override suspend fun deleteCourse(id: String): ScheduleData = write { data.copy(courses = data.courses.filterNot { it.id == id }) }
        private suspend fun write(block: () -> ScheduleData): ScheduleData {
            if (cancelWrites) throw CancellationException("write cancelled")
            if (failWrites) error("write failed")
            val running = concurrentWrites.incrementAndGet(); maxConcurrentWrites.updateAndGet { maxOf(it, running) }
            if (delayWrites) delay(10)
            return block().also { data = it; concurrentWrites.decrementAndGet() }
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
