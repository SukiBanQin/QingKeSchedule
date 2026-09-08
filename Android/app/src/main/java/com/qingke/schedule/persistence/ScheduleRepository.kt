package com.qingke.schedule.persistence

import com.qingke.schedule.domain.Course
import com.qingke.schedule.domain.ScheduleData
import com.qingke.schedule.domain.Semester

interface ScheduleRepository {
    suspend fun load(): ScheduleData
    suspend fun replace(data: ScheduleData): ScheduleData
    suspend fun saveSemester(semester: Semester): ScheduleData
    suspend fun saveCourse(course: Course): ScheduleData
    suspend fun deleteCourse(id: String): ScheduleData
}

sealed class ScheduleRepositoryException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidData(val details: String) : ScheduleRepositoryException("课表数据无效：$details")
    class InconsistentStore(details: String, cause: Throwable? = null) :
        ScheduleRepositoryException("课表存储损坏：$details", cause)
}
