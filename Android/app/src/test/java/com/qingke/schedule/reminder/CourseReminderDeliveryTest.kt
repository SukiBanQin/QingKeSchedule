package com.qingke.schedule.reminder

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A08: a fired payload may only deliver when the committed data still contains that occurrence. */
class CourseReminderDeliveryTest {
    private val fireAt = Instant.parse("2026-03-01T23:50:00Z")
    private val startAt = Instant.parse("2026-03-02T00:00:00Z")
    private val date = LocalDate.parse("2026-03-02")

    private fun reminder(
        courseIndex: Int = 0,
        fire: Instant = fireAt,
        start: Instant = startAt,
        movedDate: LocalDate = date,
        title: String = "高等数学",
    ) = CourseReminder(
        identity = CourseReminderIdentity(courseIndex, 0, "c1", "s1", 1, movedDate, false),
        fireAt = fire,
        startAt = start,
        title = title,
        body = "08:00–08:45",
    )

    private fun payload(courseIndex: Int = 0, fire: Instant = fireAt, start: Instant = startAt, exact: Boolean = true) =
        ReminderAlarm(
            identity = CourseReminderIdentity(courseIndex, 0, "c1", "s1", 1, date, false),
            fireAt = fire,
            startAt = start,
            title = "高等数学",
            body = "08:00–08:45",
            exact = exact,
        )

    @Test fun matchingOccurrenceDeliversTheFreshPlanContent() {
        val decision = CourseReminderDelivery.decide(payload(), listOf(reminder(title = "高等数学（当前）")), fireAt)

        assertTrue(decision is CourseReminderDelivery.Decision.Deliver)
        assertEquals("高等数学（当前）", (decision as CourseReminderDelivery.Decision.Deliver).reminder.title)
    }

    @Test fun movedFireTimeIsSuppressed() {
        val decision = CourseReminderDelivery.decide(
            payload(fire = fireAt.minusSeconds(600)),
            listOf(reminder()),
            fireAt,
        )

        assertEquals(
            "fire time changed",
            (decision as CourseReminderDelivery.Decision.Suppress).reason,
        )
    }

    @Test fun removedOccurrenceIsSuppressed() {
        // The course moved to another date, so this payload no longer describes any planned occurrence.
        val moved = reminder(movedDate = date.plusDays(7), start = startAt.plusSeconds(7 * 86_400))

        val decision = CourseReminderDelivery.decide(payload(), listOf(moved), fireAt)

        assertEquals(
            "occurrence no longer planned",
            (decision as CourseReminderDelivery.Decision.Suppress).reason,
        )
    }

    @Test fun shiftedCourseIndexStillDeliversTheSameOccurrence() {
        val shifted = reminder(courseIndex = 1)

        val decision = CourseReminderDelivery.decide(payload(courseIndex = 0), listOf(shifted), fireAt)

        assertTrue(decision is CourseReminderDelivery.Decision.Deliver)
        assertEquals(shifted.startAt, (decision as CourseReminderDelivery.Decision.Deliver).reminder.startAt)
    }

    @Test fun veryLateDeliveryIsSuppressedButInexactLatenessIsAllowed() {
        val late = startAt.plus(CourseReminderDelivery.LATE_TOLERANCE).plusSeconds(61)

        val suppressed = CourseReminderDelivery.decide(payload(exact = false), listOf(reminder()), late)
        assertEquals(
            "class already started",
            (suppressed as CourseReminderDelivery.Decision.Suppress).reason,
        )

        val allowed = CourseReminderDelivery.decide(payload(exact = false), listOf(reminder()), startAt.plusSeconds(120))
        assertTrue(allowed is CourseReminderDelivery.Decision.Deliver)
    }
}
