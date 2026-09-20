package com.qingke.schedule.reminder

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * A08: the internal alarm registry, stored in its own DataStore file ("schedule_reminders") so it is fully
 * separate from the user preference file that whole-file rewrites share. A corrupt registry degrades to
 * "nothing is registered", which the next reconcile repairs by re-planning.
 */
class DataStoreReminderRegistry internal constructor(
    internal val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope? = null,
    private val beforeRead: suspend () -> Unit = {},
    private val beforeWrite: suspend () -> Unit = {},
) : ReminderRegistry {
    override suspend fun load(): ReminderRegistryState {
        beforeRead()
        return decode(dataStore.data.first()[KEY])
    }

    override suspend fun save(state: ReminderRegistryState): ReminderRegistryState {
        val stored = state.normalized()
        dataStore.edit { preferences ->
            beforeWrite()
            preferences[KEY] = FORMAT.encodeToString(StoredRegistry.from(stored))
        }
        return stored
    }

    suspend fun close() {
        scope?.cancel()
        scope?.coroutineContext?.job?.join()
    }

    internal companion object {
        val KEY = stringPreferencesKey("reminder_registry")
        const val VERSION = 1
        const val FILE_NAME = "schedule_reminders.preferences_pb"
        val FORMAT = Json { ignoreUnknownKeys = true }

        fun create(context: Context): DataStoreReminderRegistry =
            create(context.filesDir.resolve("datastore").resolve(FILE_NAME))

        internal fun create(file: File): DataStoreReminderRegistry {
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val dataStore = PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                scope = scope,
                produceFile = { file },
            )
            return DataStoreReminderRegistry(dataStore, scope)
        }

        internal fun decode(stored: String?): ReminderRegistryState {
            if (stored == null) return ReminderRegistryState()
            return runCatching { FORMAT.decodeFromString<StoredRegistry>(stored).toState() }
                .getOrElse { ReminderRegistryState() }
        }
    }
}

private fun ReminderRegistryState.normalized(): ReminderRegistryState =
    copy(alarms = alarms.distinctBy { it.uri }.sortedBy { it.fireAt })

@Serializable
private data class StoredRegistry(
    val version: Int = DataStoreReminderRegistry.VERSION,
    val generation: Long = 0,
    val alarms: List<StoredAlarm> = emptyList(),
) {
    fun toState(): ReminderRegistryState = ReminderRegistryState(
        generation = generation,
        alarms = alarms.mapNotNull { it.toAlarm() },
    )

    companion object {
        fun from(state: ReminderRegistryState) = StoredRegistry(
            version = DataStoreReminderRegistry.VERSION,
            generation = state.generation,
            alarms = state.alarms.map(StoredAlarm::from),
        )
    }
}

@Serializable
private data class StoredAlarm(
    val courseIndex: Int,
    val scheduleIndex: Int,
    val courseId: String,
    val scheduleId: String,
    val week: Int,
    val date: String,
    val isMakeup: Boolean,
    val fireAt: Long,
    val startAt: Long,
    val title: String,
    val body: String,
    val exact: Boolean,
) {
    fun toAlarm(): ReminderAlarm? {
        val localDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
        return ReminderAlarm(
            identity = CourseReminderIdentity(courseIndex, scheduleIndex, courseId, scheduleId, week, localDate, isMakeup),
            fireAt = Instant.ofEpochMilli(fireAt),
            startAt = Instant.ofEpochMilli(startAt),
            title = title,
            body = body,
            exact = exact,
        )
    }

    companion object {
        fun from(alarm: ReminderAlarm) = StoredAlarm(
            courseIndex = alarm.identity.courseIndex,
            scheduleIndex = alarm.identity.scheduleIndex,
            courseId = alarm.identity.courseId,
            scheduleId = alarm.identity.scheduleId,
            week = alarm.identity.week,
            date = alarm.identity.date.toString(),
            isMakeup = alarm.identity.isMakeup,
            fireAt = alarm.fireAt.toEpochMilli(),
            startAt = alarm.startAt.toEpochMilli(),
            title = alarm.title,
            body = alarm.body,
            exact = alarm.exact,
        )
    }
}
