package com.qingke.schedule

import android.content.Context
import androidx.room.Room
import com.qingke.schedule.persistence.RoomScheduleRepository
import com.qingke.schedule.persistence.ScheduleDatabase
import com.qingke.schedule.persistence.ScheduleRepository
import com.qingke.schedule.preferences.DataStoreSchedulePreferencesRepository
import com.qingke.schedule.preferences.SchedulePreferencesRepository
import com.qingke.schedule.reminder.AndroidAlarmScheduler
import com.qingke.schedule.reminder.AndroidNotificationPresenter
import com.qingke.schedule.reminder.CourseReminderCoordinator
import com.qingke.schedule.reminder.DataStoreReminderRegistry
import com.qingke.schedule.reminder.ReminderRegistry
import java.io.File

/**
 * A08: the application-level wiring later batches call into (settings page, ViewModel). The reminder
 * coordinator is exposed here so the receivers and future UI share one single writer.
 */
interface ScheduleAppDependencies {
    val scheduleRepository: ScheduleRepository
    val preferencesRepository: SchedulePreferencesRepository
    val reminderRegistry: ReminderRegistry
    val reminderCoordinator: CourseReminderCoordinator
}

internal class DefaultScheduleAppDependencies private constructor(
    internal val database: ScheduleDatabase,
    override val scheduleRepository: RoomScheduleRepository,
    override val preferencesRepository: DataStoreSchedulePreferencesRepository,
    override val reminderRegistry: DataStoreReminderRegistry,
    override val reminderCoordinator: CourseReminderCoordinator,
) : ScheduleAppDependencies {
    suspend fun close() {
        reminderRegistry.close()
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
            reminderRegistryFile = context.applicationContext.filesDir
                .resolve("datastore")
                .resolve(DataStoreReminderRegistry.FILE_NAME),
        )

        internal fun create(
            context: Context,
            databaseName: String,
            preferencesFile: File,
            reminderRegistryFile: File = preferencesFile.resolveSibling(DataStoreReminderRegistry.FILE_NAME),
        ): DefaultScheduleAppDependencies {
            val applicationContext = context.applicationContext
            val database = Room.databaseBuilder(
                applicationContext,
                ScheduleDatabase::class.java,
                databaseName,
            ).build()
            return try {
                val scheduleRepository = RoomScheduleRepository(database)
                val preferencesRepository = DataStoreSchedulePreferencesRepository.create(preferencesFile)
                val reminderRegistry = DataStoreReminderRegistry.create(reminderRegistryFile)
                val coordinator = CourseReminderCoordinator(
                    dataSource = { scheduleRepository.load() },
                    preferencesSource = { preferencesRepository.load() },
                    scheduler = AndroidAlarmScheduler(applicationContext),
                    presenter = AndroidNotificationPresenter(applicationContext),
                    registry = reminderRegistry,
                )
                DefaultScheduleAppDependencies(
                    database = database,
                    scheduleRepository = scheduleRepository,
                    preferencesRepository = preferencesRepository,
                    reminderRegistry = reminderRegistry,
                    reminderCoordinator = coordinator,
                )
            } catch (error: Throwable) {
                database.close()
                throw error
            }
        }
    }
}
