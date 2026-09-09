package com.qingke.schedule.persistence

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
}

private fun <T : Throwable> assertThrows(type: Class<T>, block: () -> Unit) {
    try { block() } catch (error: Throwable) { if (type.isInstance(error)) return; throw error }
    throw AssertionError("Expected ${type.name}")
}

private fun data() = ScheduleData(1, Semester("term", "学期", "2026-02-23", 16, listOf(Period(2, "09:00", "09:45"), Period(1, "10:00", "10:45"))), listOf(course("duplicate", "one"), course("duplicate", "two")), "2026-01-01T00:00:00Z")
private fun course(id: String, label: String) = Course(id, label, "教师", "#112233", listOf(CourseSchedule("$label-slot", 1, 2, 2, 1, 16, RepeatRule.EVERY, "A101")))
