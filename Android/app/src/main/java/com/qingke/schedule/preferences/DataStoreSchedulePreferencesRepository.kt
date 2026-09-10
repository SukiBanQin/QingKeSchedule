package com.qingke.schedule.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job

class DataStoreSchedulePreferencesRepository internal constructor(
    internal val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope? = null,
    private val beforeRead: suspend () -> Unit = {},
    private val beforeWrite: suspend () -> Unit = {},
) : SchedulePreferencesRepository {
    override suspend fun load(): SchedulePreferences {
        beforeRead()
        return dataStore.data.first().toSchedulePreferences()
    }

    override suspend fun save(preferences: SchedulePreferences): SchedulePreferences =
        update { preferences }

    override suspend fun update(transform: (SchedulePreferences) -> SchedulePreferences): SchedulePreferences {
        val updated = dataStore.updateData { current ->
            beforeWrite()
            transform(current.toSchedulePreferences()).toPreferences()
        }
        return updated.toSchedulePreferences()
    }

    suspend fun close() {
        scope?.cancel()
        scope?.coroutineContext?.job?.join()
    }

    companion object {
        const val FILE_NAME = "schedule_preferences.preferences_pb"

        fun create(context: Context): DataStoreSchedulePreferencesRepository = create(context.filesDir.resolve("datastore").resolve(FILE_NAME))

        internal fun create(
            file: java.io.File,
            beforeRead: suspend () -> Unit = {},
            beforeWrite: suspend () -> Unit = {},
        ): DataStoreSchedulePreferencesRepository {
            file.parentFile?.mkdirs()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val dataStore = PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                scope = scope,
                produceFile = { file },
            )
            return DataStoreSchedulePreferencesRepository(dataStore, scope, beforeRead, beforeWrite)
        }
    }
}
