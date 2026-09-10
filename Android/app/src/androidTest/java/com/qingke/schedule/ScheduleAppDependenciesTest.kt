package com.qingke.schedule

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qingke.schedule.domain.Course
import com.qingke.schedule.domain.CourseSchedule
import com.qingke.schedule.domain.Period
import com.qingke.schedule.domain.RepeatRule
import com.qingke.schedule.domain.Semester
import com.qingke.schedule.persistence.RoomScheduleRepository
import com.qingke.schedule.preferences.AcademicCalendarPreferences
import com.qingke.schedule.preferences.AppearanceMode
import com.qingke.schedule.preferences.DataStoreSchedulePreferencesRepository
import com.qingke.schedule.preferences.LunchBreakSettings
import com.qingke.schedule.preferences.MakeupTeachingDay
import com.qingke.schedule.preferences.ReminderPreferences
import com.qingke.schedule.preferences.SchedulePreferences
import com.qingke.schedule.state.LoadStatus
import com.qingke.schedule.state.ScheduleAppState
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleAppDependenciesTest {
    @Test
    fun manifestApplicationLazilyProvidesOneProductionDependencyContainer() {
        val application = ApplicationProvider.getApplicationContext<QingKeScheduleApplication>()
        assertFalse(application.dependenciesInitialized)

        val first = application.dependencies
        val second = application.dependencies

        assertTrue(application.dependenciesInitialized)
        assertSame(first, second)
        assertTrue(first.scheduleRepository is RoomScheduleRepository)
        assertTrue(first.preferencesRepository is DataStoreSchedulePreferencesRepository)
    }

    @Test
    fun realRoomAndDataStoreCompositionRestoresJointStateAfterReopen() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val suffix = System.nanoTime()
            val databaseName = "p2-04-composition-$suffix.db"
            val preferencesFile = File(
                context.cacheDir,
                "p2-04-composition-$suffix.preferences_pb",
            )
            context.deleteDatabase(databaseName)
            preferencesFile.delete()

            var dependencies: DefaultScheduleAppDependencies? = DefaultScheduleAppDependencies.create(
                context = context,
                databaseName = databaseName,
                preferencesFile = preferencesFile,
            )
            try {
                val initialState = ScheduleAppState(
                    dependencies!!.scheduleRepository,
                    dependencies.preferencesRepository,
                )
                initialState.load()
                assertEquals(LoadStatus.READY, initialState.state.value.loadStatus)
                assertTrue(initialState.state.value.needsOnboarding)
                assertEquals(SchedulePreferences.defaults, initialState.state.value.preferences)

                val semester = Semester(
                    id = "semester-1",
                    name = "2026 秋季学期",
                    startDate = "2026-09-07",
                    totalWeeks = 18,
                    periods = listOf(
                        Period(1, "08:00", "08:45"),
                        Period(2, "08:55", "09:40"),
                    ),
                )
                val course = Course(
                    id = "course-1",
                    name = "高等数学",
                    teacher = "张老师",
                    color = "#287B74",
                    schedules = listOf(
                        CourseSchedule(
                            id = "schedule-1",
                            dayOfWeek = 1,
                            startPeriod = 1,
                            endPeriod = 2,
                            startWeek = 1,
                            endWeek = 18,
                            repeatRule = RepeatRule.EVERY,
                            classroom = "A101",
                        ),
                    ),
                )
                val preferences = SchedulePreferences(
                    appearanceMode = AppearanceMode.DARK,
                    reminder = ReminderPreferences(
                        remindersEnabled = true,
                        reminderLeadMinutes = 15,
                        usesCustomLeadTime = true,
                    ),
                    academicCalendar = AcademicCalendarPreferences(
                        weekendsAreNonTeachingDays = true,
                        nonTeachingDates = listOf("2026-10-02", "2026-10-01", "2026-10-01"),
                        makeupTeachingDays = listOf(
                            MakeupTeachingDay("2026-10-01", 1),
                            MakeupTeachingDay("2026-10-10", 3),
                        ),
                        lunchBreak = LunchBreakSettings(
                            isEnabled = true,
                            title = "  午间休息  ",
                            startTime = "12:00",
                            endTime = "13:30",
                        ),
                    ),
                )

                initialState.saveSemester(semester)
                initialState.saveCourse(course)
                initialState.savePreferences(preferences)
                val expectedData = initialState.state.value.data
                val expectedPreferences = initialState.state.value.preferences
                assertEquals(preferences.normalized(), expectedPreferences)

                dependencies.close()
                dependencies = null
                dependencies = DefaultScheduleAppDependencies.create(
                    context = context,
                    databaseName = databaseName,
                    preferencesFile = preferencesFile,
                )
                val reopenedState = ScheduleAppState(
                    dependencies!!.scheduleRepository,
                    dependencies.preferencesRepository,
                )
                reopenedState.load()

                assertEquals(LoadStatus.READY, reopenedState.state.value.loadStatus)
                assertFalse(reopenedState.state.value.needsOnboarding)
                assertEquals(expectedData, reopenedState.state.value.data)
                assertEquals(expectedPreferences, reopenedState.state.value.preferences)
            } finally {
                dependencies?.close()
                context.deleteDatabase(databaseName)
                preferencesFile.delete()
            }
        }
    }
}
