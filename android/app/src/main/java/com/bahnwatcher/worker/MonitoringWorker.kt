package com.bahnwatcher.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.bahnwatcher.MainActivity
import com.bahnwatcher.R
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

        // Intent extras for deep-linking from notification
        const val EXTRA_FAVORITE_ID = "extra_favorite_id"
        const val EXTRA_OPEN_SEARCH = "extra_open_search"
        const val EXTRA_FROM_ID = "extra_from_id"
        const val EXTRA_FROM_NAME = "extra_from_name"
        const val EXTRA_TO_ID = "extra_to_id"
        const val EXTRA_TO_NAME = "extra_to_name"

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
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

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

        fun sendNotification(
            context: Context,
            title: String,
            message: String,
            favoriteId: String,
            fromId: String,
            fromName: String,
            toId: String,
            toName: String
        ) {
            createNotificationChannel(context)
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Tap opens MainActivity → Favoriten-Tab → Detail-Dialog für diesen Favoriten
            val mainIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_FAVORITE_ID, favoriteId)
            }
            val mainPendingIntent = PendingIntent.getActivity(
                context,
                favoriteId.hashCode(),
                mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Action-Button öffnet Suche mit vorausgefüllter Von/Nach-Strecke
            val searchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_SEARCH, true)
                putExtra(EXTRA_FROM_ID, fromId)
                putExtra(EXTRA_FROM_NAME, fromName)
                putExtra(EXTRA_TO_ID, toId)
                putExtra(EXTRA_TO_NAME, toName)
            }
            val searchPendingIntent = PendingIntent.getActivity(
                context,
                (favoriteId + "_search").hashCode(),
                searchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(mainPendingIntent)
                .addAction(R.drawable.ic_notification, "Verbindungen suchen", searchPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            mgr.notify(favoriteId.hashCode(), notification)
        }
    }

    override suspend fun doWork(): Result {
        val repo = BahnRepository(applicationContext)
        val settings = repo.settings.first()

        if (!settings.monitoring) return Result.success()

        val favorites = repo.getFavorites()
        if (favorites.isEmpty()) return Result.success()

        val now = LocalTime.now()
        val todayDay = LocalDate.now().dayOfWeek.value % 7

        val activeFavorites = favorites.filter { isInWindow(it, now, todayDay) }
        if (activeFavorites.isEmpty()) return Result.success()

        activeFavorites.forEach { fav ->
            val newStatus = runCatching { repo.checkFavoriteStatus(fav) }.getOrNull()
                ?: return@forEach

            val baseMessage: String? = when {
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

            if (baseMessage != null) {
                // Bei Ausfall oder Verspätung: nächste Alternative suchen
                val alternativeText = if (newStatus.status in listOf("cancelled", "delay")) {
                    runCatching {
                        val nowIso = java.time.Instant.now().toString()
                        val alternatives = repo.searchJourneys(
                            fromId = fav.fromId,
                            toId = fav.toId,
                            isoDateTime = nowIso,
                            isDeparture = true,
                            results = 5
                        )
                        val next = alternatives.firstOrNull { !it.cancelled }
                        if (next != null) "\nAlternative: ${next.departure} → ${next.arrival} (${next.durationMin / 60}h ${next.durationMin % 60}min)"
                        else ""
                    }.getOrDefault("")
                } else ""

                val fullMessage = baseMessage + alternativeText

                sendNotification(
                    context = applicationContext,
                    title = fav.name,
                    message = fullMessage,
                    favoriteId = fav.id,
                    fromId = fav.fromId,
                    fromName = fav.fromName,
                    toId = fav.toId,
                    toName = fav.toName
                )
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

        val windowStart = from.minusMinutes(30)
        val windowEnd = to.plusMinutes(30)
        return now.isAfter(windowStart) && now.isBefore(windowEnd)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        kotlinx.coroutines.runBlocking {
            val repo = BahnRepository(context)
            val settings = repo.settings.first()
            if (settings.monitoring) {
                MonitoringWorker.schedule(context)
            }
        }
    }
}
