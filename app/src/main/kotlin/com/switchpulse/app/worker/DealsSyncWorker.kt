package com.switchpulse.app.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.switchpulse.app.BuildConfig
import com.switchpulse.app.MainActivity
import com.switchpulse.app.R
import com.switchpulse.app.data.DealsRepository
import com.switchpulse.app.update.GitHubUpdater
import com.switchpulse.app.update.UpdateCheckResult

class DealsSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repository = DealsRepository(applicationContext)
        val refreshResult = repository.refreshDeals()

        val settings = repository.preferences.getSettings()
        if (settings.autoCheckUpdates && settings.githubRepo.isNotBlank()) {
            val updater = GitHubUpdater()
            val updateResult = updater.checkLatestRelease(
                repoSlug = settings.githubRepo,
                currentVersionName = BuildConfig.VERSION_NAME,
                currentVersionCode = BuildConfig.VERSION_CODE
            )
            if (updateResult is UpdateCheckResult.UpdateAvailable) {
                showUpdateNotification(updateResult.release.tagName)
            }
        }

        return if (refreshResult.isSuccess) Result.success() else Result.retry()
    }

    private fun showUpdateNotification(tagName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_UPDATES,
            "Aggiornamenti SwitchPulse",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_UPDATES, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_UPDATES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Nuova versione disponibile ($tagName)")
            .setContentText("Tocca per scaricare e installare l'aggiornamento da GitHub.")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(NOTIF_UPDATE_ID, notification)
    }

    companion object {
        private const val CHANNEL_UPDATES = "switchpulse_updates"
        private const val NOTIF_UPDATE_ID = 4201
        const val EXTRA_OPEN_UPDATES = "open_updates"
    }
}
