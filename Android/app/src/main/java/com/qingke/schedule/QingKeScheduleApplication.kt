package com.qingke.schedule

import android.app.Application
import com.qingke.schedule.reminder.ReminderReconcileReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class QingKeScheduleApplication : Application() {
    private val dependenciesDelegate = lazy { DefaultScheduleAppDependencies.create(this) }

    val dependencies: ScheduleAppDependencies by dependenciesDelegate

    internal val dependenciesInitialized: Boolean
        get() = dependenciesDelegate.isInitialized()

    private val reminderSyncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * A08: rebuild entry for "the user entered the app again". It creates no UI and asks for no permission; it
     * only re-submits the expected alarms for the current rolling window, which repairs a platform that lost
     * its alarms (reboot, package replace, process death) and advances the window when needed.
     */
    fun requestReminderSync(reason: ReminderReconcileReason = ReminderReconcileReason.APP_START) {
        reminderSyncScope.launch {
            runCatching { dependencies.reminderCoordinator.reconcile(reason) }
        }
    }

    override fun onTerminate() {
        reminderSyncScope.cancel()
        super.onTerminate()
    }
}
