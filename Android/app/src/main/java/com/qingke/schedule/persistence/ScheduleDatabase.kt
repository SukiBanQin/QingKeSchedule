package com.qingke.schedule.persistence

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "schedule_metadata")
data class MetadataEntity(@PrimaryKey val slot: Int = 1, val schemaVersion: Int, val updatedAt: String)

@Entity(tableName = "schedule_semester")
data class SemesterEntity(@PrimaryKey val slot: Int = 1, val id: String, val name: String, val startDate: String, val totalWeeks: Int)

@Entity(tableName = "schedule_periods")
data class PeriodEntity(@PrimaryKey(autoGenerate = true) val rowId: Long = 0, val sortIndex: Int, val number: Int, val startTime: String, val endTime: String)

@Entity(tableName = "schedule_courses")
data class CourseEntity(@PrimaryKey(autoGenerate = true) val rowId: Long = 0, val sortIndex: Int, val businessId: String, val name: String, val teacher: String, val color: String)

@Entity(
    tableName = "schedule_course_schedules",
    foreignKeys = [ForeignKey(entity = CourseEntity::class, parentColumns = ["rowId"], childColumns = ["courseRowId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("courseRowId")],
)
data class CourseScheduleEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val courseRowId: Long,
    val sortIndex: Int,
    val businessId: String,
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val startWeek: Int,
    val endWeek: Int,
    val repeatRule: String,
    val classroom: String,
)

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedule_metadata") suspend fun metadata(): List<MetadataEntity>
    @Query("SELECT * FROM schedule_semester") suspend fun semesters(): List<SemesterEntity>
    @Query("SELECT * FROM schedule_periods ORDER BY sortIndex") suspend fun periods(): List<PeriodEntity>
    @Query("SELECT * FROM schedule_courses ORDER BY sortIndex") suspend fun courses(): List<CourseEntity>
    @Query("SELECT * FROM schedule_course_schedules WHERE courseRowId = :courseRowId ORDER BY sortIndex") suspend fun schedules(courseRowId: Long): List<CourseScheduleEntity>
    @Query("DELETE FROM schedule_metadata") suspend fun clearMetadata()
    @Query("DELETE FROM schedule_semester") suspend fun clearSemester()
    @Query("DELETE FROM schedule_periods") suspend fun clearPeriods()
    @Query("DELETE FROM schedule_courses") suspend fun clearCourses()
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertMetadata(value: MetadataEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSemester(value: SemesterEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPeriods(values: List<PeriodEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCourse(value: CourseEntity): Long
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSchedules(values: List<CourseScheduleEntity>)
}

@Database(entities = [MetadataEntity::class, SemesterEntity::class, PeriodEntity::class, CourseEntity::class, CourseScheduleEntity::class], version = 1, exportSchema = true)
abstract class ScheduleDatabase : RoomDatabase() { abstract fun scheduleDao(): ScheduleDao }
