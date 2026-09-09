package com.qingke.schedule.persistence

import androidx.room.withTransaction
import com.qingke.schedule.domain.Course
import com.qingke.schedule.domain.CourseSchedule
import com.qingke.schedule.domain.Period
import com.qingke.schedule.domain.RepeatRule
import com.qingke.schedule.domain.SUPPORTED_SCHEMA_VERSION
import com.qingke.schedule.domain.ScheduleData
import com.qingke.schedule.domain.ScheduleValidator
import com.qingke.schedule.domain.Semester
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RoomScheduleRepository(
    internal val database: ScheduleDatabase,
    private val clock: Clock = Clock.systemUTC(),
    private val beforeCommit: suspend () -> Unit = {},
    private val beforeRead: suspend () -> Unit = {},
) : ScheduleRepository {
    private val mutex = Mutex()
    private val dao get() = database.scheduleDao()

    override suspend fun load(): ScheduleData = mutex.withLock { read() }

    override suspend fun replace(data: ScheduleData): ScheduleData = mutex.withLock {
        validate(data)
        database.withTransaction { write(data); beforeCommit(); data }
    }

    override suspend fun saveSemester(semester: Semester): ScheduleData = mutate { current ->
        current.copy(semester = semester, updatedAt = now())
    }

    override suspend fun saveCourse(course: Course): ScheduleData = mutate { current ->
        if (current.semester == null) throw ScheduleRepositoryException.InvalidData("请先设置学期")
        val index = current.courses.indexOfFirst { it.id == course.id }
        val courses = if (index < 0) current.courses + course else current.courses.toMutableList().also { it[index] = course }
        current.copy(courses = courses, updatedAt = now())
    }

    override suspend fun deleteCourse(id: String): ScheduleData = mutex.withLock {
        val current = read()
        val index = current.courses.indexOfFirst { it.id == id }
        if (index < 0) return@withLock current
        val next = current.copy(courses = current.courses.toMutableList().also { it.removeAt(index) }, updatedAt = now())
        validate(next)
        database.withTransaction { write(next); beforeCommit(); next }
    }

    private suspend fun mutate(transform: (ScheduleData) -> ScheduleData): ScheduleData = mutex.withLock {
        val next = transform(read())
        validate(next)
        database.withTransaction { write(next); beforeCommit(); next }
    }

    private fun now(): String = Instant.now(clock).toString()
    private fun empty() = ScheduleData(SUPPORTED_SCHEMA_VERSION, null, emptyList(), EMPTY_UPDATED_AT)
    private fun validate(data: ScheduleData) {
        ScheduleValidator.validate(data).firstOrNull()?.let { throw ScheduleRepositoryException.InvalidData("${it.path}：${it.message}") }
    }

    private fun validateStored(data: ScheduleData) {
        ScheduleValidator.validate(data).firstOrNull()?.let {
            throw ScheduleRepositoryException.InconsistentStore("存储数据无效：${it.path}：${it.message}")
        }
    }

    private suspend fun read(): ScheduleData {
        try {
            beforeRead()
            val metadata = dao.metadata(); val semesters = dao.semesters(); val periods = dao.periods(); val courses = dao.courses()
            if (metadata.isEmpty() && semesters.isEmpty() && periods.isEmpty() && courses.isEmpty()) return empty()
            if (metadata.size != 1 || semesters.size > 1 || metadata.firstOrNull()?.slot != 1) inconsistent("元数据缺失或重复")
            val meta = metadata.single()
            if (meta.schemaVersion != SUPPORTED_SCHEMA_VERSION) inconsistent("数据版本不受支持")
            if (semesters.isEmpty() && (periods.isNotEmpty() || courses.isNotEmpty())) inconsistent("缺失当前学期")
            val semester = semesters.singleOrNull()?.let { record -> Semester(record.id, record.name, record.startDate, record.totalWeeks, periods.map { Period(it.number, it.startTime, it.endTime) }) }
            val mappedCourses = courses.map { course ->
                val schedules = dao.schedules(course.rowId).map { item -> CourseSchedule(item.businessId, item.dayOfWeek, item.startPeriod, item.endPeriod, item.startWeek, item.endWeek, RepeatRule.entries.firstOrNull { it.name == item.repeatRule } ?: inconsistent("课程重复规则无效"), item.classroom) }
                Course(course.businessId, course.name, course.teacher, course.color, schedules)
            }
            val data = ScheduleData(meta.schemaVersion, semester, mappedCourses, meta.updatedAt)
            validateStored(data); return data
        } catch (error: CancellationException) { throw error
        } catch (error: ScheduleRepositoryException) { throw error
        } catch (error: Throwable) { throw ScheduleRepositoryException.InconsistentStore("读取失败", error) }
    }

    private suspend fun write(data: ScheduleData) {
        dao.clearMetadata(); dao.clearSemester(); dao.clearPeriods(); dao.clearCourses()
        dao.insertMetadata(MetadataEntity(schemaVersion = data.schemaVersion, updatedAt = data.updatedAt))
        data.semester?.let { semester ->
            dao.insertSemester(SemesterEntity(id = semester.id, name = semester.name, startDate = semester.startDate, totalWeeks = semester.totalWeeks))
            dao.insertPeriods(semester.periods.mapIndexed { index, it -> PeriodEntity(sortIndex = index, number = it.number, startTime = it.startTime, endTime = it.endTime) })
        }
        data.courses.forEachIndexed { courseIndex, course ->
            val courseRowId = dao.insertCourse(CourseEntity(sortIndex = courseIndex, businessId = course.id, name = course.name, teacher = course.teacher, color = course.color))
            dao.insertSchedules(course.schedules.mapIndexed { scheduleIndex, item -> CourseScheduleEntity(courseRowId = courseRowId, sortIndex = scheduleIndex, businessId = item.id, dayOfWeek = item.dayOfWeek, startPeriod = item.startPeriod, endPeriod = item.endPeriod, startWeek = item.startWeek, endWeek = item.endWeek, repeatRule = item.repeatRule.name, classroom = item.classroom) })
        }
    }

    private fun inconsistent(detail: String): Nothing = throw ScheduleRepositoryException.InconsistentStore(detail)
    companion object { const val EMPTY_UPDATED_AT = "1970-01-01T00:00:00.000Z" }
}
