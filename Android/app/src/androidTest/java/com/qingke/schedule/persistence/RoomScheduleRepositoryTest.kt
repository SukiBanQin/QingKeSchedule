package com.qingke.schedule.persistence

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.sqlite.db.SupportSQLiteDatabase
import com.qingke.schedule.domain.Course
import com.qingke.schedule.domain.CourseSchedule
import com.qingke.schedule.domain.Period
import com.qingke.schedule.domain.RepeatRule
import com.qingke.schedule.domain.ScheduleData
import com.qingke.schedule.domain.Semester
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomScheduleRepositoryTest {
    @Test fun emptyRoundTripOrderDuplicatesAndReopen() {
        runBlocking {
            val file = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "schedule-test-${System.nanoTime()}.db")
            val repository = repository(file)
            assertEquals(ScheduleData(1, null, emptyList(), RoomScheduleRepository.EMPTY_UPDATED_AT), repository.load())
            val fixture = data()
            assertEquals(fixture, repository.replace(fixture))
            repository.database.close()
            val reopened = repository(file)
            assertEquals(fixture, reopened.load())
            reopened.database.close(); file.delete()
        }
    }

    @Test fun invalidWriteAndInjectedFailureRollBack() {
        runBlocking {
            val file = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "schedule-rollback-${System.nanoTime()}.db")
            val stable = data(); val repository = repository(file)
            repository.replace(stable)
            assertThrows(ScheduleRepositoryException::class.java) { runBlocking { repository.replace(stable.copy(semester = null)) } }
            assertEquals(stable, repository.load())
            val failing = repository(file, beforeCommit = { error("injected") })
            assertThrows(IllegalStateException::class.java) { runBlocking { failing.replace(data().copy(updatedAt = "2026-01-02T00:00:00Z")) } }
            repository.database.close(); failing.database.close()
            val reopened = repository(file)
            assertEquals(stable, reopened.load()); reopened.database.close(); file.delete()
        }
    }

    @Test fun deleteFirstDuplicateAndKeepsClockContract() = runBlocking {
        val repository = repository(null)
        repository.replace(data())
        val result = repository.deleteCourse("duplicate")
        assertEquals(listOf("duplicate"), result.courses.map { it.id })
        assertEquals("2026-01-03T00:00:00Z", result.updatedAt)
    }

    @Test fun saveSemesterPreservesCoursesAndUsesFixedClock() = runBlocking {
        val repository = repository(null)
        val original = data()
        repository.replace(original)
        val result = repository.saveSemester(original.semester!!.copy(name = "新学期"))
        assertEquals(original.courses, result.courses)
        assertEquals("新学期", result.semester?.name)
        assertEquals("2026-01-03T00:00:00Z", result.updatedAt)
    }

    @Test fun saveCourseUpdatesFirstDuplicateKeepsPositionAndAppendsNewCourse() = runBlocking {
        val repository = repository(null)
        repository.replace(data())
        val updated = repository.saveCourse(course("duplicate", "updated", schedules = schedules("updated-a", "updated-b")))
        assertEquals(listOf("updated", "two"), updated.courses.map { it.name })
        val appended = repository.saveCourse(course("new", "new"))
        assertEquals(listOf("updated", "two", "new"), appended.courses.map { it.name })
    }

    @Test fun deletingMissingCoursePreservesDataAndUpdatedAt() = runBlocking {
        val repository = repository(null)
        val original = data()
        repository.replace(original)
        assertEquals(original, repository.deleteCourse("missing"))
    }

    @Test fun foreignKeyCascadeRemovesSchedulesWhenCourseRowIsDeleted() = runBlocking {
        val repository = repository(null)
        repository.replace(data())
        val database = repository.database
        val rowId = database.scheduleDao().courses().first().rowId
        assertEquals(2, countSchedules(database.openHelper.readableDatabase, rowId))
        database.openHelper.writableDatabase.execSQL("DELETE FROM schedule_courses WHERE rowId = ?", arrayOf(rowId))
        assertEquals(0, countSchedules(database.openHelper.readableDatabase, rowId))
    }

    @Test fun invalidSemesterChangeAndMissingSemesterCourseKeepOldData() = runBlocking {
        val repository = repository(null)
        val original = data()
        repository.replace(original)
        val invalidSemester = original.semester!!.copy(periods = listOf(Period(9, "09:00", "09:45")))
        assertThrows(ScheduleRepositoryException.InvalidData::class.java) { runBlocking { repository.saveSemester(invalidSemester) } }
        assertEquals(original, repository.load())

        val emptyRepository = repository(null)
        assertThrows(ScheduleRepositoryException.InvalidData::class.java) { runBlocking { emptyRepository.saveCourse(course("no-semester", "invalid")) } }
        assertEquals(ScheduleData(1, null, emptyList(), RoomScheduleRepository.EMPTY_UPDATED_AT), emptyRepository.load())
    }

    @Test fun schemaVersionForeignKeyAndBusinessIdSchemaAreStable() = runBlocking {
        val repository = repository(null)
        val database = repository.database
        assertEquals(1, database.openHelper.readableDatabase.version)
        val foreignKey = database.openHelper.readableDatabase.query("PRAGMA foreign_key_list('schedule_course_schedules')")
        assertTrue(foreignKey.moveToFirst())
        assertEquals("CASCADE", foreignKey.getString(foreignKey.getColumnIndexOrThrow("on_delete")))
        foreignKey.close()
        val index = database.openHelper.readableDatabase.query("PRAGMA index_list('schedule_course_schedules')")
        assertTrue(index.moveToFirst())
        assertEquals("index_schedule_course_schedules_courseRowId", index.getString(index.getColumnIndexOrThrow("name")))
        index.close()
    }

    @Test fun invalidStoredAggregatesAreInconsistentAndNotCleared() = runBlocking {
        assertCorruptLoad("missing-metadata") { database ->
            database.scheduleDao().insertSemester(SemesterEntity(id = "term", name = "学期", startDate = "2026-02-23", totalWeeks = 16))
            database.scheduleDao().insertPeriods(listOf(PeriodEntity(sortIndex = 0, number = 1, startTime = "09:00", endTime = "09:45")))
        }
        assertCorruptLoad("unsupported-version") { database ->
            database.scheduleDao().insertMetadata(MetadataEntity(schemaVersion = 99, updatedAt = "2026-01-01T00:00:00Z"))
        }
        assertCorruptLoad("invalid-repeat") { database ->
            database.scheduleDao().insertMetadata(MetadataEntity(schemaVersion = 1, updatedAt = "2026-01-01T00:00:00Z"))
            database.scheduleDao().insertSemester(SemesterEntity(id = "term", name = "学期", startDate = "2026-02-23", totalWeeks = 16))
            database.scheduleDao().insertPeriods(listOf(PeriodEntity(sortIndex = 0, number = 1, startTime = "09:00", endTime = "09:45")))
            val rowId = database.scheduleDao().insertCourse(CourseEntity(sortIndex = 0, businessId = "course", name = "课程", teacher = "教师", color = "#112233"))
            database.scheduleDao().insertSchedules(listOf(scheduleEntity(rowId, 0, "slot", repeatRule = "invalid")))
        }
        assertCorruptLoad("invalid-domain") { database ->
            database.scheduleDao().insertMetadata(MetadataEntity(schemaVersion = 1, updatedAt = "2026-01-01T00:00:00Z"))
            database.scheduleDao().insertSemester(SemesterEntity(id = "term", name = "学期", startDate = "2026-02-23", totalWeeks = 16))
            database.scheduleDao().insertPeriods(listOf(PeriodEntity(sortIndex = 0, number = 1, startTime = "09:00", endTime = "09:45")))
            database.scheduleDao().insertCourse(CourseEntity(sortIndex = 0, businessId = "course", name = "课程", teacher = "教师", color = "invalid"))
        }
    }

    @Test fun cancellationPropagatesFromReadAndRollsBackWrite() {
        runBlocking {
            val file = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "schedule-cancellation-${System.nanoTime()}.db")
            val stable = data()
            val repository = repository(file)
            repository.replace(stable)

            val readCancelled = repository(file, beforeRead = { throw CancellationException("read cancelled") })
            assertThrows(CancellationException::class.java) { runBlocking { readCancelled.load() } }

            val writeCancelled = repository(file, beforeCommit = { throw CancellationException("write cancelled") })
            assertThrows(CancellationException::class.java) { runBlocking { writeCancelled.replace(stable.copy(updatedAt = "2026-01-02T00:00:00Z")) } }
            repository.database.close(); readCancelled.database.close(); writeCancelled.database.close()
            val reopened = repository(file)
            assertEquals(stable, reopened.load())
            reopened.database.close(); file.delete()
        }
    }

    private fun repository(
        file: File?,
        beforeCommit: suspend () -> Unit = {},
        beforeRead: suspend () -> Unit = {},
    ): RoomScheduleRepository {
        val builder = if (file == null) Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ScheduleDatabase::class.java) else Room.databaseBuilder(ApplicationProvider.getApplicationContext(), ScheduleDatabase::class.java, file.absolutePath)
        val database = builder.allowMainThreadQueries().build()
        return RoomScheduleRepository(
            database = database,
            clock = Clock.fixed(Instant.parse("2026-01-03T00:00:00Z"), ZoneOffset.UTC),
            beforeCommit = beforeCommit,
            beforeRead = beforeRead,
        )
    }

    private fun assertCorruptLoad(label: String, seed: suspend (ScheduleDatabase) -> Unit) {
        runBlocking {
            val repository = repository(null)
            seed(repository.database)
            val before = tableCounts(repository.database.openHelper.readableDatabase)
            val error = assertThrowsReturning(ScheduleRepositoryException.InconsistentStore::class.java) { runBlocking { repository.load() } }
            assertTrue("$label: ${error.message}", error.message?.isNotBlank() == true)
            assertEquals(before, tableCounts(repository.database.openHelper.readableDatabase))
        }
    }
}

private fun <T : Throwable> assertThrows(type: Class<T>, block: () -> Unit) {
    try { block() } catch (error: Throwable) { if (type.isInstance(error)) return; throw error }
    throw AssertionError("Expected ${type.name}")
}

private fun <T : Throwable> assertThrowsReturning(type: Class<T>, block: () -> Unit): T {
    try { block() } catch (error: Throwable) { if (type.isInstance(error)) return error as T; throw error }
    throw AssertionError("Expected ${type.name}")
}

private fun countSchedules(database: SupportSQLiteDatabase, courseRowId: Long): Int = database.query("SELECT COUNT(*) FROM schedule_course_schedules WHERE courseRowId = $courseRowId").use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }

private fun tableCounts(database: SupportSQLiteDatabase): List<Int> = listOf(
    "schedule_metadata", "schedule_semester", "schedule_periods", "schedule_courses", "schedule_course_schedules",
).map { table -> database.query("SELECT COUNT(*) FROM $table").use { cursor -> cursor.moveToFirst(); cursor.getInt(0) } }

private fun data() = ScheduleData(1, Semester("term", "学期", "2026-02-23", 16, listOf(Period(2, "09:00", "09:45"), Period(1, "10:00", "10:45"))), listOf(course("duplicate", "one", schedules("shared", "shared")), course("duplicate", "two", schedules("shared", "shared"))), "2026-01-01T00:00:00Z")
private fun course(id: String, label: String, schedules: List<CourseSchedule> = schedules("$label-slot")) = Course(id, label, "教师", "#112233", schedules)
private fun schedules(first: String, second: String? = null): List<CourseSchedule> = listOfNotNull(
    CourseSchedule(first, 1, 2, 2, 1, 16, RepeatRule.EVERY, "A101"),
    second?.let { CourseSchedule(it, 2, 1, 1, 1, 16, RepeatRule.ODD, "B202") },
)
private fun scheduleEntity(courseRowId: Long, sortIndex: Int, id: String, repeatRule: String) = CourseScheduleEntity(courseRowId = courseRowId, sortIndex = sortIndex, businessId = id, dayOfWeek = 1, startPeriod = 1, endPeriod = 1, startWeek = 1, endWeek = 16, repeatRule = repeatRule, classroom = "A101")
