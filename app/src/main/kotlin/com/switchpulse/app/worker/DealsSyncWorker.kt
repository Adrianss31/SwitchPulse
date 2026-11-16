package com.switchpulse.app.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.switchpulse.app.BuildConfig
import com.switchpulse.app.MainActivity
import com.switchpulse.app.R
import com.switchpulse.app.SwitchPulseApp
import com.switchpulse.app.data.DealFilterEngine
import com.switchpulse.app.data.model.BackgroundRerankPolicy
import com.switchpulse.app.data.model.Deal
import com.switchpulse.app.data.model.LikedGame
import com.switchpulse.app.update.UpdateCheckResult

class DealsSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = SwitchPulseApp.containerFrom(applicationContext)
        val repository = container.repository
        val settings = repository.preferences.getSettings()
        val allowBackgroundAi = when (settings.backgroundRerankPolicy) {
            BackgroundRerankPolicy.OFF -> false
            BackgroundRerankPolicy.ALWAYS -> true
            BackgroundRerankPolicy.WIFI_ONLY -> isOnWifiOrUnmetered()
        }
        val refreshResult = repository.refreshDeals(allowBackgroundAi = allowBackgroundAi)

        refreshResult.getOrNull()?.let { freshDeals ->
            val alerts = repository.findWishlistPriceAlerts(freshDeals, settings)
            if (alerts.isNotEmpty()) {
                showPriceAlertNotifications(alerts)
            }
        }

        if (settings.autoCheckUpdates && settings.githubRepo.isNotBlank()) {
            val updateResult = container.updater.checkLatestRelease(
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

    private fun isOnWifiOrUnmetered(): Boolean {
        val cm = applicationContext.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
    }

    private fun showPriceAlertNotifications(alerts: List<Pair<LikedGame, Deal>>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_PRICE_ALERTS,
            "Avvisi Prezzo Wishlist",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)

        alerts.take(5).forEach { (game, deal) ->
            val targetStr = game.targetPriceEur?.let { DealFilterEngine.formatEur(it) }.orEmpty()
            val priceStr = DealFilterEngine.formatEur(deal.discountedPriceEur)
            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                data = Uri.parse("switchpulse://deal/${deal.id}")
                putExtra(MainActivity.EXTRA_OPEN_DEAL_ID, deal.id)
            }
            val requestCode = NOTIF_PRICE_ALERT_BASE + (deal.id.hashCode() and 0x0FFF)
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_PRICE_ALERTS)
                .setSmallIcon(R.drawable.ic_stat_switchpulse)
                .setContentTitle("${deal.title} a $priceStr (-${deal.discountPercent}%)")
                .setContentText(
                    if (targetStr.isNotEmpty()) "Sotto la tua soglia di $targetStr su Nintendo eShop."
                    else "Titolo in wishlist in offerta su Nintendo eShop."
                )
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
            NotificationManagerCompat.from(applicationContext).notify(requestCode, notification)
        }
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
            .setSmallIcon(R.drawable.ic_stat_switchpulse)
            .setContentTitle("Nuova versione disponibile ($tagName)")
            .setContentText("Tocca per scaricare e installare l'aggiornamento da GitHub.")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(NOTIF_UPDATE_ID, notification)
    }

    companion object {
        private const val CHANNEL_UPDATES = "switchpulse_updates"
        private const val CHANNEL_PRICE_ALERTS = "switchpulse_price_alerts"
        private const val NOTIF_UPDATE_ID = 4201
        private const val NOTIF_PRICE_ALERT_BASE = 5000
        const val EXTRA_OPEN_UPDATES = "open_updates"
    }
}
