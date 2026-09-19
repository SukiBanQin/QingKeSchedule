package com.qingke.schedule.persistence

import com.qingke.schedule.domain.Course
import com.qingke.schedule.domain.ScheduleData
import com.qingke.schedule.domain.Semester

interface ScheduleRepository {
    suspend fun load(): ScheduleData
    suspend fun replace(data: ScheduleData): ScheduleData
    suspend fun saveSemester(semester: Semester): ScheduleData

    /**
     * P3-06-R7: writes the semester and the cascaded course list in one atomic operation, so a period
     * deletion can never leave the new period table next to course references of the old one.
     */
    suspend fun saveSemesterWithCourses(semester: Semester, courses: List<Course>): ScheduleData =
        throw ScheduleRepositoryException.InconsistentStore("不支持学期与课程的原子保存")

    suspend fun saveCourse(course: Course): ScheduleData
    suspend fun deleteCourse(id: String): ScheduleData

    /**
     * P3-04 edits use an occurrence, not a business id: version-1 imports may legally contain
     * duplicate ids.  Implementations must reject a changed target rather than selecting another
     * matching id.
     */
    suspend fun saveCourseAt(index: Int, expected: Course, course: Course): ScheduleData =
        throw ScheduleRepositoryException.InconsistentStore("不支持按课程来源位置保存")

    suspend fun deleteCourseAt(index: Int, expected: Course): ScheduleData =
        throw ScheduleRepositoryException.InconsistentStore("不支持按课程来源位置删除")
}

sealed class ScheduleRepositoryException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidData(val details: String) : ScheduleRepositoryException("课表数据无效：$details")
    class InconsistentStore(details: String, cause: Throwable? = null) :
        ScheduleRepositoryException("课表存储损坏：$details", cause)
}
