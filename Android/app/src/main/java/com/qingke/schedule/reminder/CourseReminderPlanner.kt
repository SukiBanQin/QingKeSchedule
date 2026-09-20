package com.qingke.schedule.reminder

import com.qingke.schedule.domain.AcademicCalendarResolver
import com.qingke.schedule.domain.AcademicDayResolution
import com.qingke.schedule.domain.ScheduleData
import com.qingke.schedule.domain.ScheduleRules
import com.qingke.schedule.preferences.AcademicCalendarPreferences
import com.qingke.schedule.preferences.ReminderPreferences
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * A08: pure planner that turns the committed schedule into the future reminders for one rolling window.
 *
 * Rules mirror the iOS baseline: weekly/odd/even weeks inside startWeek-endWeek, stop dates, weekend closure
 * and makeup days resolved through the shared academic calendar resolver (a makeup date only matches schedules
 * whose weekday it follows and is filtered by the teaching week of its own date). Lunch break never produces a
 * reminder. Title is the course name; the body is "HH:mm-HH:mm" plus a " · classroom" suffix when set.
 *
 * Window choice: iOS caps pending requests at its platform limit (60). AlarmManager has no such limit, so this
 * batch plans a bounded rolling window ([DEFAULT_WINDOW] days, at most [DEFAULT_LIMIT] alarms) instead: it keeps
 * the alarm registry, the boot rebuild and the battery cost proportional to the near future, and the window
 * keeps advancing because every fired alarm reconciles the next window and the app start does too. The residual
 * limitation (a gap longer than the window with no alarm firing and no app launch) is recorded in the task
 * document and is a candidate for the next A08 batch.
 */
object CourseReminderPlanner {
    val DEFAULT_WINDOW: Duration = Duration.ofDays(14)
    const val DEFAULT_LIMIT = 120

    fun plan(
        data: ScheduleData,
        academicCalendar: AcademicCalendarPreferences = AcademicCalendarPreferences.defaults,
        leadMinutes: Int = ReminderPreferences.DEFAULT_LEAD_MINUTES,
        now: Instant,
        zone: ZoneId,
        window: Duration = DEFAULT_WINDOW,
        limit: Int = DEFAULT_LIMIT,
    ): List<CourseReminder> {
        val semester = data.semester ?: return emptyList()
        if (limit <= 0) return emptyList()
        val lead = leadMinutes.coerceIn(0, ReminderPreferences.VALID_LEAD_MINUTES.last)
        val horizon = now.plus(window)
        val reminders = mutableListOf<CourseReminder>()

        data.courses.forEachIndexed { courseIndex, course ->
            course.schedules.forEachIndexed { scheduleIndex, schedule ->
                val startPeriod = semester.periods.firstOrNull { it.number == schedule.startPeriod } ?: return@forEachIndexed
                val endPeriod = semester.periods.firstOrNull { it.number == schedule.endPeriod } ?: return@forEachIndexed
                val startMinutes = ScheduleRules.minutes(startPeriod.startTime) ?: return@forEachIndexed
                val body = listOf("${startPeriod.startTime}–${endPeriod.endTime}", schedule.classroom.trim())
                    .filter { it.isNotEmpty() }
                    .joinToString(" · ")
                val firstWeek = maxOf(1, schedule.startWeek)
                val lastWeek = minOf(schedule.endWeek, semester.totalWeeks)
                if (firstWeek > lastWeek) return@forEachIndexed

                for (week in firstWeek..lastWeek) {
                    if (!ScheduleRules.scheduleApplies(schedule, week)) continue
                    for (displayedDayOfWeek in 1..7) {
                        val date = ScheduleRules.dateForTeachingWeek(week, displayedDayOfWeek, semester) ?: continue
                        val resolution = AcademicCalendarResolver.resolve(date, academicCalendar)
                        val teaching = resolution as? AcademicDayResolution.TeachingDay ?: continue
                        if (teaching.sourceDayOfWeek != schedule.dayOfWeek) continue
                        val startAt = date.atTime(startMinutes / 60, startMinutes % 60).atZone(zone).toInstant()
                        val fireAt = startAt.minusSeconds(lead * 60L)
                        if (!fireAt.isAfter(now)) continue
                        if (fireAt.isAfter(horizon)) continue
                        reminders += CourseReminder(
                            identity = CourseReminderIdentity(
                                courseIndex = courseIndex,
                                scheduleIndex = scheduleIndex,
                                courseId = course.id,
                                scheduleId = schedule.id,
                                week = week,
                                date = date,
                                isMakeup = teaching.isMakeup,
                            ),
                            fireAt = fireAt,
                            startAt = startAt,
                            title = course.name,
                            body = body,
                        )
                    }
                }
            }
        }

        return reminders
            .sortedWith(compareBy({ it.fireAt }, { it.identity.uri }))
            .take(limit)
    }
}
