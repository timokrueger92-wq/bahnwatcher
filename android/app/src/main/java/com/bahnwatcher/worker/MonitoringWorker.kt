package com.bahnwatcher.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.bahnwatcher.data.model.Favorite
import com.bahnwatcher.data.repository.AppSettings
import com.bahnwatcher.data.repository.BahnRepository
import com.bahnwatcher.data.repository.PrefKeys
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class MonitoringWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "bahnwatcher_monitoring"
        const val NOTIFICATION_CHANNEL_ID = "bahnwatcher_alerts"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MonitoringWorker>(2, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        fun createNotificationChannel(context: Context) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "BahnWatcher Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Echtzeit-Zugbenachrichtigungen"
            }
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(channel)
        }
    }

    override suspend fun doWork(): Result {
        val repo = BahnRepository(applicationContext)
        val settings = repo.settings.first()

        if (!settings.monitoring) return Result.success()

        val favorites = repo.getFavorites()
        val now = LocalTime.now()
        val todayDayOfWeek = LocalDate.now().dayOfWeek.value % 7 // 0=Sun, 1=Mon, ...

        favorites.forEach { fav ->
            if (!isInWindow(fav, now, todayDayOfWeek)) return@forEach

            val newStatus = runCatching { repo.checkFavoriteStatus(fav) }.getOrNull() ?: return@forEach
            val oldStatus = fav.lastStatus

            // Detect state changes and notify
            val message: String? = when {
                newStatus.status == "cancelled" && oldStatus != "cancelled" ->
                    "Zug ausgefallen!"
                newStatus.status == "delay" && oldStatus != "delay" ->
                    "Verspätung: +${newStatus.delay} min"
                newStatus.delay > settings.delayThreshold && (fav.lastDelay) <= settings.delayThreshold ->
                    "Verspätung über Schwelle: +${newStatus.delay} min"
                newStatus.status == "ok" && oldStatus == "delay" && settings.notifyNorm ->
                    "Zug wieder pünktlich!"
                newStatus.status == "ok" && oldStatus == "cancelled" && settings.notifyNorm ->
                    "Zug fährt wieder!"
                else -> null
            }

            message?.let {
                sendLocalNotification(fav.name, it)
                if (settings.ntfyChannel.isNotEmpty() && settings.consentNtfy) {
                    repo.sendPushNotification(settings.ntfyChannel, fav.name, it)
                }
            }

            repo.updateFavorite(
                fav.copy(
                    lastStatus = newStatus.status,
                    lastDelay = newStatus.delay,
                    lastPlatform = newStatus.platform,
                    lastChecked = System.currentTimeMillis()
                )
            )
        }
        return Result.success()
    }

    private fun isInWindow(fav: Favorite, now: LocalTime, todayDay: Int): Boolean {
        val days = fav.days.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (todayDay !in days) return false

        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        val from = runCatching { LocalTime.parse(fav.timeFrom, fmt) }.getOrNull() ?: return false
        val to = runCatching { LocalTime.parse(fav.timeTo, fmt) }.getOrNull() ?: return false

        // Add 30-min buffer before and after
        val windowStart = from.minusMinutes(30)
        val windowEnd = to.plusMinutes(30)
        return now.isAfter(windowStart) && now.isBefore(windowEnd)
    }

    private fun sendLocalNotification(title: String, message: String) {
        val mgr = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel(applicationContext)

        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        mgr.notify(System.currentTimeMillis().toInt(), notification)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Re-schedule monitoring after reboot if it was active
            MonitoringWorker.schedule(context)
        }
    }
}
