package com.switchpulse.app

import android.app.Application
import android.content.Context
import com.switchpulse.app.data.DealsRepository
import com.switchpulse.app.data.local.PreferencesStore
import com.switchpulse.app.data.local.PulseDatabase
import com.switchpulse.app.update.GitHubUpdater
import com.switchpulse.app.worker.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * Shared application-scoped dependency container providing a single OkHttpClient, SQLite database,
 * PreferencesStore, DealsRepository, and GitHubUpdater across ViewModel, WorkManager, and Widget.
 */
class AppContainer(appContext: Context) {
    val httpClient: OkHttpClient = DealsRepository.defaultSharedHttpClient()
    val database: PulseDatabase = PulseDatabase(appContext)
    val preferences: PreferencesStore = PreferencesStore(appContext, database)
    val repository: DealsRepository = DealsRepository(
        context = appContext,
        sharedHttpClient = httpClient,
        preferences = preferences
    )
    val updater: GitHubUpdater = GitHubUpdater(httpClient = httpClient)
}

class SwitchPulseApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val container: AppContainer by lazy { AppContainer(applicationContext) }

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            val settings = container.preferences.getSettings()
            SyncScheduler.schedulePeriodic(this@SwitchPulseApp, settings.syncIntervalHours)
        }
    }

    companion object {
        fun containerFrom(context: Context): AppContainer =
            (context.applicationContext as SwitchPulseApp).container
    }
}
