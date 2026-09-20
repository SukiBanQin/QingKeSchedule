package com.qingke.schedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.qingke.schedule.ui.QingKeApp
import com.qingke.schedule.viewmodel.ScheduleViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: ScheduleViewModel by viewModels {
        ScheduleViewModel.Factory((application as QingKeScheduleApplication).dependencies)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A08: entering the app re-submits the expected reminder alarms (no permission prompt, no new UI).
        (application as? QingKeScheduleApplication)?.requestReminderSync()
        setContent { QingKeApp(viewModel) }
    }
}
