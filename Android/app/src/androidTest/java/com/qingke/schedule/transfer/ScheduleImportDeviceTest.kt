package com.qingke.schedule.transfer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qingke.schedule.domain.Course
import com.qingke.schedule.domain.CourseSchedule
import com.qingke.schedule.domain.Period
import com.qingke.schedule.domain.RepeatRule
import com.qingke.schedule.domain.ScheduleData
import com.qingke.schedule.domain.Semester
import com.qingke.schedule.persistence.RoomScheduleRepository
import com.qingke.schedule.persistence.ScheduleDatabase
import com.qingke.schedule.preferences.AcademicCalendarPreferences
import com.qingke.schedule.preferences.AppearanceMode
import com.qingke.schedule.preferences.DataStoreSchedulePreferencesRepository
import com.qingke.schedule.preferences.LunchBreakSettings
import com.qingke.schedule.preferences.MakeupTeachingDay
import com.qingke.schedule.preferences.ReminderPreferences
import com.qingke.schedule.preferences.SchedulePreferences
import com.qingke.schedule.state.ScheduleAppState
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P4／A10 device contract: the real Room aggregate and the real DataStore preference file behind one import.
 * Duplicate business ids, reversed period numbers and the single destructive transaction must survive a real
 * close/reopen, while an injected failure or a cancellation keeps the original rows and preferences intact.
 */
@RunWith(AndroidJUnit4::class)
class ScheduleImportDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun aConfirmedImportReplacesTheWholeAggregateAndSurvivesReopen(): Unit = runBlocking {
        val databaseFile = databaseFile("replace")
        val preferencesFile = preferencesFile("replace")
        val repository = repository(databaseFile)
        val preferences = DataStoreSchedulePreferencesRepository.create(preferencesFile)
        val seeded = seededSchedule()
        repository.replace(seeded)
        val expectedPreferences = fullPreferences()
        preferences.save(expectedPreferences)
        val appState = ScheduleAppState(repository, preferences)
        appState.load()

        val preview = ScheduleImportPreview.of(ScheduleDataDecoder.decode(DUPLICATE_AND_REVERSED_JSON.encodeToByteArray()))
        assertEquals("重复与反序学期", preview.semesterName)
        assertEquals(2, preview.courseCount)
        assertEquals(seeded, repository.load())

        assertTrue(appState.replace(preview.data))

        val committed = appState.state.value.data
        assertEquals(listOf("duplicate", "duplicate"), committed.courses.map { it.id })
        assertEquals(listOf("shared", "shared"), committed.courses.map { it.schedules.single().id })
        assertEquals(listOf(2, 1), committed.semester?.periods?.map { it.number })
        assertEquals("2026-09-02T12:00:00.000Z", committed.updatedAt)
        assertEquals(listOf("B202", ""), committed.courses.map { it.schedules.single().classroom })
        assertEquals(listOf(1, 7), committed.courses.map { it.schedules.single().dayOfWeek })

        repository.database.close()
        val reopened = repository(databaseFile)
        assertEquals(committed, reopened.load())
        reopened.database.close()

        preferences.close()
        val reopenedPreferences = DataStoreSchedulePreferencesRepository.create(preferencesFile)
        assertEquals(expectedPreferences, reopenedPreferences.load())
        assertEquals(15, reopenedPreferences.load().reminder.reminderLeadMinutes)
        assertEquals(AppearanceMode.DARK, reopenedPreferences.load().appearanceMode)
        reopenedPreferences.close()

        databaseFile.delete()
        preferencesFile.delete()
    }

    @Test
    fun anInjectedCommitFailureKeepsTheDiskAndTheStateOnTheOriginalSchedule(): Unit = runBlocking {
        val databaseFile = databaseFile("injected")
        val preferencesFile = preferencesFile("injected")
        val preferences = DataStoreSchedulePreferencesRepository.create(preferencesFile)
        val expectedPreferences = fullPreferences()
        preferences.save(expectedPreferences)
        val seeded = seededSchedule()
        repository(databaseFile).also { it.replace(seeded); it.database.close() }

        val failingRepository = RoomScheduleRepository(
            database = database(context, databaseFile),
            clock = Clock.fixed(Instant.parse("2026-01-03T00:00:00Z"), ZoneOffset.UTC),
            beforeCommit = { error("injected write failure") },
        )
        val importState = ScheduleAppState(failingRepository, preferences)
        importState.load()
        assertEquals(seeded, importState.state.value.data)

        val imported = ScheduleDataDecoder.decode(DUPLICATE_AND_REVERSED_JSON.encodeToByteArray())
        assertFalse(importState.replace(imported))

        assertEquals(seeded, importState.state.value.data)
        assertTrue(importState.state.value.error.orEmpty().isNotBlank())
        failingRepository.database.close()
        val reopened = repository(databaseFile)
        assertEquals(seeded, reopened.load())
        reopened.database.close()
        assertEquals(expectedPreferences, preferences.load())

        preferences.close()
        databaseFile.delete()
        preferencesFile.delete()
    }

    @Test
    fun aCancelledImportTransactionKeepsTheOriginalRowsAndPreferences(): Unit = runBlocking {
        val databaseFile = databaseFile("cancel")
        val preferencesFile = preferencesFile("cancel")
        val preferences = DataStoreSchedulePreferencesRepository.create(preferencesFile)
        val expectedPreferences = fullPreferences()
        preferences.save(expectedPreferences)
        val seeded = seededSchedule()
        repository(databaseFile).also { it.replace(seeded); it.database.close() }

        val cancelling = RoomScheduleRepository(
            database = database(context, databaseFile),
            clock = Clock.fixed(Instant.parse("2026-01-03T00:00:00Z"), ZoneOffset.UTC),
            beforeCommit = { throw CancellationException("cancelled import") },
        )
        val appState = ScheduleAppState(cancelling, preferences)
        appState.load()

        val imported = ScheduleDataDecoder.decode(DUPLICATE_AND_REVERSED_JSON.encodeToByteArray())
        var cancelled = false
        try {
            appState.replace(imported)
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertNull(appState.state.value.error)
        assertEquals(seeded, appState.state.value.data)
        cancelling.database.close()

        val reopened = repository(databaseFile)
        assertEquals(seeded, reopened.load())
        reopened.database.close()
        assertEquals(expectedPreferences, preferences.load())
        preferences.close()
        databaseFile.delete()
        preferencesFile.delete()
    }

    @Test
    fun anEmptySemesterImportClearsTheWholeAggregateAndStaysReopenable(): Unit = runBlocking {
        val databaseFile = databaseFile("empty")
        val preferencesFile = preferencesFile("empty")
        val preferences = DataStoreSchedulePreferencesRepository.create(preferencesFile)
        val expectedPreferences = fullPreferences()
        preferences.save(expectedPreferences)
        val repository = repository(databaseFile)
        repository.replace(seededSchedule())
        val appState = ScheduleAppState(repository, preferences)
        appState.load()

        val preview = ScheduleImportPreview.of(ScheduleDataDecoder.decode(EMPTY_SEMESTER_JSON.encodeToByteArray()))
        assertEquals("未设置学期", preview.semesterName)
        assertEquals(0, preview.courseCount)

        assertTrue(appState.replace(preview.data))
        assertTrue(appState.state.value.needsOnboarding)
        assertNull(appState.state.value.data.semester)
        assertTrue(appState.state.value.data.courses.isEmpty())

        repository.database.close()
        val reopened = repository(databaseFile)
        assertNull(reopened.load().semester)
        assertTrue(reopened.load().courses.isEmpty())
        reopened.database.close()

        preferences.close()
        val reopenedPreferences = DataStoreSchedulePreferencesRepository.create(preferencesFile)
        assertEquals(expectedPreferences, reopenedPreferences.load())
        reopenedPreferences.close()
        databaseFile.delete()
        preferencesFile.delete()
    }

    @Test
    fun aParseFailureAndASystemCancelLeaveTheRealStoreUntouched(): Unit = runBlocking {
        val databaseFile = databaseFile("noop")
        val preferencesFile = preferencesFile("noop")
        val preferences = DataStoreSchedulePreferencesRepository.create(preferencesFile)
        val expectedPreferences = fullPreferences()
        preferences.save(expectedPreferences)
        val repository = repository(databaseFile)
        val seeded = seededSchedule()
        repository.replace(seeded)
        val appState = ScheduleAppState(repository, preferences)
        appState.load()

        listOf(
            "not json at all",
            """{"schemaVersion":2,"semester":null,"courses":[],"updatedAt":"2026-09-02T12:00:00.000Z"}""",
            """{"schemaVersion":1,"semester":null,"courses":[],"updatedAt":"2026-09-02T12:00:00.000Z","extra":true}""",
        ).forEach { payload ->
            var failure: String? = null
            try {
                ScheduleDataDecoder.decode(payload.encodeToByteArray())
            } catch (error: ScheduleDataException) {
                failure = error.message
            }
            assertTrue("$payload must be rejected", failure != null)
            assertTrue(failure.orEmpty().isNotBlank())
        }
        assertEquals(seeded, appState.state.value.data)

        repository.database.close()
        val reopened = repository(databaseFile)
        assertEquals(seeded, reopened.load())
        reopened.database.close()
        assertEquals(expectedPreferences, preferences.load())
        preferences.close()
        databaseFile.delete()
        preferencesFile.delete()
    }

    private fun databaseFile(label: String) = File(context.cacheDir, "a10-$label-${System.nanoTime()}.db")

    private fun preferencesFile(label: String) = File(context.cacheDir, "a10-$label-${System.nanoTime()}.preferences_pb")

    private fun database(context: Context, file: File) = Room
        .databaseBuilder(context, ScheduleDatabase::class.java, file.absolutePath)
        .allowMainThreadQueries()
        .build()

    private fun repository(file: File) = RoomScheduleRepository(
        database = database(context, file),
        clock = Clock.fixed(Instant.parse("2026-01-03T00:00:00Z"), ZoneOffset.UTC),
    )

    private fun seededSchedule() = ScheduleData(
        1,
        Semester(
            "semester-existing",
            "原有学期",
            "2026-09-01",
            18,
            listOf(Period(1, "08:00", "08:45"), Period(2, "08:55", "09:40")),
        ),
        listOf(
            Course(
                "existing",
                "已有课程",
                "教师",
                "#287B74",
                listOf(CourseSchedule("existing-slot", 2, 1, 2, 1, 18, RepeatRule.EVERY, "A101")),
            ),
        ),
        "2026-09-01T00:00:00.000Z",
    )

    private fun fullPreferences() = SchedulePreferences(
        appearanceMode = AppearanceMode.DARK,
        reminder = ReminderPreferences(remindersEnabled = true, reminderLeadMinutes = 15, usesCustomLeadTime = true),
        academicCalendar = AcademicCalendarPreferences(
            weekendsAreNonTeachingDays = true,
            nonTeachingDates = listOf("2026-10-01", "2026-10-02"),
            makeupTeachingDays = listOf(MakeupTeachingDay("2026-10-10", 3)),
            lunchBreak = LunchBreakSettings(isEnabled = false, title = "午休", startTime = "12:00", endTime = "13:30"),
        ),
    )

    private companion object {
        const val EMPTY_SEMESTER_JSON = """{"schemaVersion":1,"semester":null,"courses":[],"updatedAt":"1970-01-01T00:00:00.000Z"}"""

        /** Version 1 with duplicate business ids, duplicate schedule ids and a reversed period number order. */
        const val DUPLICATE_AND_REVERSED_JSON = """{"schemaVersion":1,"semester":{"id":"semester-duplicate","name":"重复与反序学期","startDate":"2026-09-02","totalWeeks":18,"periods":[{"number":2,"startTime":"08:00","endTime":"08:45"},{"number":1,"startTime":"08:55","endTime":"09:40"}]},"courses":[{"id":"duplicate","name":"第一门","teacher":"","color":"#287B74","schedules":[{"id":"shared","dayOfWeek":1,"startPeriod":2,"endPeriod":2,"startWeek":1,"endWeek":18,"repeat":"odd","classroom":"B202"}]},{"id":"duplicate","name":"第二门","teacher":"教师","color":"#E65A4F","schedules":[{"id":"shared","dayOfWeek":7,"startPeriod":1,"endPeriod":1,"startWeek":2,"endWeek":4,"repeat":"even","classroom":""}]}],"updatedAt":"2026-09-02T12:00:00.000Z"}"""
    }
}
