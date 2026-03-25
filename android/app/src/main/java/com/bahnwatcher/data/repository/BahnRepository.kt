package com.bahnwatcher.data.repository

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.bahnwatcher.data.api.NtfyClient
import com.bahnwatcher.data.api.TransportApiClient
import com.bahnwatcher.data.db.AppDatabase
import com.bahnwatcher.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.*

val Context.dataStore by preferencesDataStore(name = "settings")

object PrefKeys {
    val NTFY_CHANNEL = stringPreferencesKey("ntfy_channel")
    val MONITORING = booleanPreferencesKey("monitoring")
    val DELAY_THRESHOLD = intPreferencesKey("delay_threshold")
    val NOTIFY_NORM = booleanPreferencesKey("notify_norm")
    val CONSENT_GIVEN = booleanPreferencesKey("consent_given")
    val CONSENT_GPS = booleanPreferencesKey("consent_gps")
    val CONSENT_NTFY = booleanPreferencesKey("consent_ntfy")
}

data class AppSettings(
    val ntfyChannel: String = "",
    val monitoring: Boolean = false,
    val delayThreshold: Int = 5,
    val notifyNorm: Boolean = true,
    val consentGiven: Boolean = false,
    val consentGps: Boolean = false,
    val consentNtfy: Boolean = false
)

class BahnRepository(private val context: Context) {
    private val api = TransportApiClient.service
    private val dao = AppDatabase.getInstance(context).favoriteDao()

    // ---- Favorites ----

    val favorites: Flow<List<Favorite>> = dao.getAllFlow()

    suspend fun getFavorites(): List<Favorite> = dao.getAll()

    suspend fun addFavorite(fav: Favorite) = dao.insert(fav)

    suspend fun updateFavorite(fav: Favorite) = dao.update(fav)

    suspend fun deleteFavorite(id: String) = dao.deleteById(id)

    // ---- Settings ----

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            ntfyChannel = prefs[PrefKeys.NTFY_CHANNEL] ?: "",
            monitoring = prefs[PrefKeys.MONITORING] ?: false,
            delayThreshold = prefs[PrefKeys.DELAY_THRESHOLD] ?: 5,
            notifyNorm = prefs[PrefKeys.NOTIFY_NORM] ?: true,
            consentGiven = prefs[PrefKeys.CONSENT_GIVEN] ?: false,
            consentGps = prefs[PrefKeys.CONSENT_GPS] ?: false,
            consentNtfy = prefs[PrefKeys.CONSENT_NTFY] ?: false
        )
    }

    suspend fun saveSettings(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[PrefKeys.NTFY_CHANNEL] = settings.ntfyChannel
            prefs[PrefKeys.MONITORING] = settings.monitoring
            prefs[PrefKeys.DELAY_THRESHOLD] = settings.delayThreshold
            prefs[PrefKeys.NOTIFY_NORM] = settings.notifyNorm
            prefs[PrefKeys.CONSENT_GIVEN] = settings.consentGiven
            prefs[PrefKeys.CONSENT_GPS] = settings.consentGps
            prefs[PrefKeys.CONSENT_NTFY] = settings.consentNtfy
        }
    }

    suspend fun updateConsent(given: Boolean, gps: Boolean, ntfy: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PrefKeys.CONSENT_GIVEN] = given
            prefs[PrefKeys.CONSENT_GPS] = gps
            prefs[PrefKeys.CONSENT_NTFY] = ntfy
        }
    }

    // ---- API ----

    suspend fun searchStations(query: String): List<StopLocation> =
        runCatching { api.searchLocations(query) }.getOrDefault(emptyList())

    suspend fun searchJourneys(
        fromId: String,
        toId: String,
        isoDateTime: String,
        isDeparture: Boolean,
        results: Int = 5,
        includeLongDistance: Boolean = true
    ): List<JourneyUi> {
        // transport.rest products filter: exclude nationalExpress (ICE) and national (IC/EC)
        val products = if (!includeLongDistance) {
            mapOf(
                "products[nationalExpress]" to "false",
                "products[national]" to "false"
            )
        } else emptyMap()

        val response = if (isDeparture) {
            api.getJourneys(from = fromId, to = toId, departure = isoDateTime,
                results = results, products = products)
        } else {
            api.getJourneys(from = fromId, to = toId, arrival = isoDateTime,
                results = results, products = products)
        }
        return response.journeys?.mapNotNull { journey ->
            val legs = journey.legs ?: return@mapNotNull null
            val firstLeg = legs.firstOrNull() ?: return@mapNotNull null
            val lastLeg = legs.lastOrNull() ?: return@mapNotNull null
            val depTime = firstLeg.departure ?: firstLeg.plannedDeparture ?: return@mapNotNull null
            val arrTime = lastLeg.arrival ?: lastLeg.plannedArrival ?: return@mapNotNull null
            val plannedDep = firstLeg.plannedDeparture ?: depTime
            val plannedArr = lastLeg.plannedArrival ?: arrTime

            val depInstant = Instant.parse(depTime)
            val arrInstant = Instant.parse(arrTime)
            val durationMin = ChronoUnit.MINUTES.between(depInstant, arrInstant).toInt()
            val transfers = legs.count { !(it.walking ?: false) } - 1
            val hasWalking = legs.any { it.walking == true }

            JourneyUi(
                departure = formatTime(depTime),
                plannedDeparture = formatTime(plannedDep),
                arrival = formatTime(arrTime),
                plannedArrival = formatTime(plannedArr),
                durationMin = durationMin,
                transfers = maxOf(0, transfers),
                hasWalking = hasWalking,
                cancelled = legs.any { it.cancelled == true },
                departureDelay = (firstLeg.departureDelay ?: 0) / 60,
                arrivalDelay = (lastLeg.arrivalDelay ?: 0) / 60,
                platform = firstLeg.platform ?: firstLeg.plannedPlatform ?: "",
                legs = legs,
                from = firstLeg.origin?.name ?: "",
                to = lastLeg.destination?.name ?: "",
                fromId = firstLeg.origin?.id ?: fromId,
                toId = lastLeg.destination?.id ?: toId
            )
        } ?: emptyList()
    }

    suspend fun getDepartures(stopId: String, whenIso: String? = null): List<Departure> {
        return runCatching {
            api.getDepartures(stopId, `when` = whenIso).departures ?: emptyList()
        }.getOrDefault(emptyList())
    }

    suspend fun getNearbyStops(lat: Double, lon: Double): List<NearbyStop> {
        return runCatching {
            api.getNearbyStops(lat, lon)
        }.getOrDefault(emptyList())
    }

    suspend fun checkFavoriteStatus(fav: Favorite): FavoriteStatus {
        val departures = getDepartures(fav.fromId)
        val now = Instant.now()

        // Find next departure towards destination within a 2-hour window
        val dep = departures.firstOrNull { d ->
            val whenStr = d.`when` ?: d.plannedWhen ?: return@firstOrNull false
            runCatching {
                val instant = Instant.parse(whenStr)
                instant.isAfter(now) && instant.isBefore(now.plus(2, ChronoUnit.HOURS))
            }.getOrDefault(false)
        }

        if (dep == null) return FavoriteStatus(fav.id, "unknown", 0, "", "")

        val status = when {
            dep.cancelled == true -> "cancelled"
            (dep.delay ?: 0) > 0 -> "delay"
            else -> "ok"
        }
        val delayMin = (dep.delay ?: 0) / 60
        val platform = dep.platform ?: dep.plannedPlatform ?: ""
        val nextDep = dep.`when` ?: dep.plannedWhen ?: ""

        return FavoriteStatus(fav.id, status, delayMin, platform, formatTime(nextDep))
    }

    fun sendPushNotification(channel: String, title: String, message: String) {
        NtfyClient.send(channel, title, message)
    }

    // ---- Helpers ----

    private fun formatTime(iso: String): String = runCatching {
        val instant = Instant.parse(iso)
        val zdt = instant.atZone(ZoneId.systemDefault())
        DateTimeFormatter.ofPattern("HH:mm").format(zdt)
    }.getOrDefault(iso)

    fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    fun walkingMinutes(distanceMeters: Double): Int = (distanceMeters / 80).toInt() // ~80m/min
}
