package com.qingke.schedule.reminder

import java.time.Instant
import java.time.LocalDate

/**
 * A08: identity of one planned reminder. Version 1 may legally contain duplicate course or schedule ids, so
 * the occurrence position and the actual date are part of the identity. [uri] is deterministic, unique per
 * occurrence and is used as the alarm PendingIntent data, so alarms can be cancelled without relying on a
 * string hash request code.
 */
data class CourseReminderIdentity(
    val courseIndex: Int,
    val scheduleIndex: Int,
    val courseId: String,
    val scheduleId: String,
    val week: Int,
    val date: LocalDate,
    val isMakeup: Boolean,
) {
    val uri: String
        get() = buildString {
            append(SCHEME)
            append("://reminder/")
            append(courseIndex)
            append('/')
            append(encode(courseId))
            append('/')
            append(scheduleIndex)
            append('/')
            append(encode(scheduleId))
            append('/')
            append(week)
            append('/')
            append(date)
            if (isMakeup) append("/makeup")
        }

    companion object {
        const val SCHEME = "qingke"

        private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

        /** RFC 3986 percent encoding over UTF-8 bytes; deterministic across JVM and Android. */
        fun encode(value: String): String = buildString {
            value.toByteArray(Charsets.UTF_8).forEach { byte ->
                val character = byte.toInt().toChar()
                if (character in UNRESERVED) {
                    append(character)
                } else {
                    append('%')
                    append(HEX[(byte.toInt() shr 4) and 0xF])
                    append(HEX[byte.toInt() and 0xF])
                }
            }
        }

        private val HEX = "0123456789ABCDEF".toCharArray()
    }
}

/** One reminder the planner wants to deliver for a concrete class date. */
data class CourseReminder(
    val identity: CourseReminderIdentity,
    val fireAt: Instant,
    val startAt: Instant,
    val title: String,
    val body: String,
) {
    /** Semantic key used to re-validate a delivered payload against the currently committed data. */
    fun sameOccurrenceAs(other: CourseReminder): Boolean =
        courseKey() == other.courseKey() && startAt == other.startAt && identity.date == other.identity.date

    private fun courseKey(): Triple<String, String, Int> =
        Triple(identity.courseId, identity.scheduleId, identity.scheduleIndex)
}
