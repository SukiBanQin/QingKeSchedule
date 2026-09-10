package com.qingke.schedule

import android.app.Application

class QingKeScheduleApplication : Application() {
    private val dependenciesDelegate = lazy { DefaultScheduleAppDependencies.create(this) }

    val dependencies: ScheduleAppDependencies by dependenciesDelegate

    internal val dependenciesInitialized: Boolean
        get() = dependenciesDelegate.isInitialized()
}
