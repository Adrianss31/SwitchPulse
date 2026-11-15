package com.switchpulse.app

import android.app.Application
import com.switchpulse.app.data.local.PreferencesStore
import com.switchpulse.app.worker.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SwitchPulseApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            val settings = PreferencesStore(this@SwitchPulseApp).getSettings()
            SyncScheduler.schedulePeriodic(this@SwitchPulseApp, settings.syncIntervalHours)
        }
    }
}
