package com.bahnwatcher.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.bahnwatcher.data.model.Favorite
import com.bahnwatcher.data.repository.BahnRepository
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

        // WorkManager minimum is 15 minutes. We add a 5-minute flex window so Android
        // can batch this with other apps' work → better battery efficiency.
        private const val INTERVAL_MINUTES = 15L
        private const val FLEX_MINUTES = 5L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MonitoringWorker>(
                INTERVAL_MINUTES, TimeUnit.MINUTES,
                FLEX_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                // Exponential backoff on failure (network hiccup etc.)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            // UPDATE replaces any existing schedule so changes take effect immediately.
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
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
                enableVibration(true)
            }
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(channel)
        }

        fun sendNotification(context: Context, title: String, message: String) {
            createNotificationChannel(context)
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            mgr.notify(System.currentTimeMillis().toInt(), notification)
        }
    }

    override suspend fun doWork(): Result {
        val repo = BahnRepository(applicationContext)
        val settings = repo.settings.first()

        if (!settings.monitoring) return Result.success()

        val favorites = repo.getFavorites()
        if (favorites.isEmpty()) return Result.success()

        val now = LocalTime.now()
        val todayDay = LocalDate.now().dayOfWeek.value % 7 // 0=Sun, 1=Mon, ...

        // Early exit: if no favorite has an active window right now, skip all API calls.
        val activeFavorites = favorites.filter { isInWindow(it, now, todayDay) }
        if (activeFavorites.isEmpty()) return Result.success()

        activeFavorites.forEach { fav ->
            val newStatus = runCatching { repo.checkFavoriteStatus(fav) }.getOrNull()
                ?: return@forEach

            val message: String? = when {
                newStatus.status == "cancelled" && fav.lastStatus != "cancelled" ->
                    "Zug ausgefallen!"
                newStatus.status == "delay" && fav.lastStatus != "delay" ->
                    "Verspätung: +${newStatus.delay} min"
                newStatus.delay > settings.delayThreshold && fav.lastDelay <= settings.delayThreshold ->
                    "Verspätung über Schwelle: +${newStatus.delay} min"
                newStatus.status == "ok" && fav.lastStatus == "delay" && settings.notifyNorm ->
                    "Zug wieder pünktlich!"
                newStatus.status == "ok" && fav.lastStatus == "cancelled" && settings.notifyNorm ->
                    "Zug fährt wieder!"
                else -> null
            }

            message?.let { sendNotification(applicationContext, fav.name, it) }

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

        val windowStart = from.minusMinutes(30)
        val windowEnd = to.plusMinutes(30)
        return now.isAfter(windowStart) && now.isBefore(windowEnd)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // Only reschedule if monitoring was active before reboot.
        // We read the DataStore synchronously via runBlocking in the receiver
        // (acceptable here since BroadcastReceiver must return quickly but
        //  this is a very fast DataStore read).
        kotlinx.coroutines.runBlocking {
            val repo = BahnRepository(context)
            val settings = repo.settings.first()
            if (settings.monitoring) {
                MonitoringWorker.schedule(context)
            }
        }
    }
}
