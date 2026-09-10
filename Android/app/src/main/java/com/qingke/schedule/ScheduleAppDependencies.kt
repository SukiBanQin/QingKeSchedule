package com.qingke.schedule

import android.content.Context
import androidx.room.Room
import com.qingke.schedule.persistence.RoomScheduleRepository
import com.qingke.schedule.persistence.ScheduleDatabase
import com.qingke.schedule.persistence.ScheduleRepository
import com.qingke.schedule.preferences.DataStoreSchedulePreferencesRepository
import com.qingke.schedule.preferences.SchedulePreferencesRepository
import java.io.File

interface ScheduleAppDependencies {
    val scheduleRepository: ScheduleRepository
    val preferencesRepository: SchedulePreferencesRepository
}

internal class DefaultScheduleAppDependencies private constructor(
    internal val database: ScheduleDatabase,
    override val scheduleRepository: RoomScheduleRepository,
    override val preferencesRepository: DataStoreSchedulePreferencesRepository,
) : ScheduleAppDependencies {
    suspend fun close() {
        preferencesRepository.close()
        database.close()
    }

    companion object {
        const val DATABASE_NAME = "schedule.db"

        fun create(context: Context): DefaultScheduleAppDependencies = create(
            context = context,
            databaseName = DATABASE_NAME,
            preferencesFile = context.applicationContext.filesDir
                .resolve("datastore")
                .resolve(DataStoreSchedulePreferencesRepository.FILE_NAME),
        )

        internal fun create(
            context: Context,
            databaseName: String,
            preferencesFile: File,
        ): DefaultScheduleAppDependencies {
            val applicationContext = context.applicationContext
            val database = Room.databaseBuilder(
                applicationContext,
                ScheduleDatabase::class.java,
                databaseName,
            ).build()
            return try {
                val preferencesRepository = DataStoreSchedulePreferencesRepository.create(preferencesFile)
                DefaultScheduleAppDependencies(
                    database = database,
                    scheduleRepository = RoomScheduleRepository(database),
                    preferencesRepository = preferencesRepository,
                )
            } catch (error: Throwable) {
                database.close()
                throw error
            }
        }
    }
}
