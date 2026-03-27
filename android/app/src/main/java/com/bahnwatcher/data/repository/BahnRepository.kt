package com.bahnwatcher.data.repository

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.bahnwatcher.data.api.TransportApiClient
import com.bahnwatcher.data.db.AppDatabase
import com.bahnwatcher.data.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.*

val Context.dataStore by preferencesDataStore(name = "settings")

object PrefKeys {
    val MONITORING = booleanPreferencesKey("monitoring")
    val DELAY_THRESHOLD = intPreferencesKey("delay_threshold")
    val NOTIFY_NORM = booleanPreferencesKey("notify_norm")
    val CONSENT_GIVEN = booleanPreferencesKey("consent_given")
    val CONSENT_GPS = booleanPreferencesKey("consent_gps")
}

data class AppSettings(
    val monitoring: Boolean = false,
    val delayThreshold: Int = 5,
    val notifyNorm: Boolean = true,
    val consentGiven: Boolean = false,
    val consentGps: Boolean = false
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
            monitoring = prefs[PrefKeys.MONITORING] ?: false,
            delayThreshold = prefs[PrefKeys.DELAY_THRESHOLD] ?: 5,
            notifyNorm = prefs[PrefKeys.NOTIFY_NORM] ?: true,
            consentGiven = prefs[PrefKeys.CONSENT_GIVEN] ?: false,
            consentGps = prefs[PrefKeys.CONSENT_GPS] ?: false
        )
    }

    suspend fun saveSettings(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[PrefKeys.MONITORING] = settings.monitoring
            prefs[PrefKeys.DELAY_THRESHOLD] = settings.delayThreshold
            prefs[PrefKeys.NOTIFY_NORM] = settings.notifyNorm
            prefs[PrefKeys.CONSENT_GIVEN] = settings.consentGiven
            prefs[PrefKeys.CONSENT_GPS] = settings.consentGps
        }
    }

    suspend fun updateConsent(given: Boolean, gps: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PrefKeys.CONSENT_GIVEN] = given
            prefs[PrefKeys.CONSENT_GPS] = gps
        }
    }

    // ---- API ----

    suspend fun searchStations(query: String): List<StopLocation> {
        repeat(2) { attempt ->
            val result = runCatching { api.searchLocations(query) }
            if (result.isSuccess) return result.getOrThrow()
            if (attempt == 0) delay(500)
        }
        return emptyList()
    }

    suspend fun searchJourneys(
        fromId: String,
        toId: String,
        isoDateTime: String,
        isDeparture: Boolean,
        results: Int = 5,
        includeLongDistance: Boolean = true
    ): List<JourneyUi> {
        // Exclude ICE/IC when long-distance is turned off
        val excludeLong: Boolean? = if (!includeLongDistance) false else null

        val response = if (isDeparture) {
            api.getJourneys(from = fromId, to = toId, departure = isoDateTime,
                results = results, nationalExpress = excludeLong, national = excludeLong)
        } else {
            api.getJourneys(from = fromId, to = toId, arrival = isoDateTime,
                results = results, nationalExpress = excludeLong, national = excludeLong)
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
                platform = firstLeg.departurePlatform ?: firstLeg.plannedDeparturePlatform ?: firstLeg.platform ?: firstLeg.plannedPlatform ?: "",
                legs = legs,
                from = firstLeg.origin?.name ?: "",
                to = lastLeg.destination?.name ?: "",
                fromId = firstLeg.origin?.id ?: fromId,
                toId = lastLeg.destination?.id ?: toId,
                refreshToken = journey.refreshToken ?: ""
            )
        } ?: emptyList()
    }

    suspend fun refreshJourney(refreshToken: String): JourneyUi? {
        val journey = runCatching { api.refreshJourney(refreshToken).journey }
            .getOrNull() ?: return null
        val legs = journey.legs ?: return null
        val firstLeg = legs.firstOrNull() ?: return null
        val lastLeg = legs.lastOrNull() ?: return null
        val depTime = firstLeg.departure ?: firstLeg.plannedDeparture ?: return null
        val arrTime = lastLeg.arrival ?: lastLeg.plannedArrival ?: return null
        val plannedDep = firstLeg.plannedDeparture ?: depTime
        val plannedArr = lastLeg.plannedArrival ?: arrTime
        val durationMin = ChronoUnit.MINUTES.between(Instant.parse(depTime), Instant.parse(arrTime)).toInt()
        val transfers = legs.count { !(it.walking ?: false) } - 1
        return JourneyUi(
            departure = formatTime(depTime),
            plannedDeparture = formatTime(plannedDep),
            arrival = formatTime(arrTime),
            plannedArrival = formatTime(plannedArr),
            durationMin = durationMin,
            transfers = maxOf(0, transfers),
            hasWalking = legs.any { it.walking == true },
            cancelled = legs.any { it.cancelled == true },
            departureDelay = (firstLeg.departureDelay ?: 0) / 60,
            arrivalDelay = (lastLeg.arrivalDelay ?: 0) / 60,
            platform = firstLeg.departurePlatform ?: firstLeg.plannedDeparturePlatform ?: firstLeg.platform ?: firstLeg.plannedPlatform ?: "",
            legs = legs,
            from = firstLeg.origin?.name ?: "",
            to = lastLeg.destination?.name ?: "",
            fromId = firstLeg.origin?.id ?: "",
            toId = lastLeg.destination?.id ?: "",
            refreshToken = journey.refreshToken ?: refreshToken
        )
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
        // Use journey search (from→to) instead of departure board so we get the
        // correct connection and not just any train leaving fromId
        val fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        val timeFrom = runCatching {
            java.time.LocalTime.parse(fav.timeFrom, fmt)
        }.getOrNull()

        val now = java.time.LocalDateTime.now()
        val searchTime = if (timeFrom != null) {
            var t = now.toLocalDate().atTime(timeFrom)
            // If the scheduled time is more than 30 min in the past, look at tomorrow
            if (t.isBefore(now.minusMinutes(30))) t = t.plusDays(1)
            t
        } else {
            now
        }
        val isoTime = searchTime.atZone(ZoneId.systemDefault()).toInstant().toString()

        val journeys = runCatching {
            searchJourneys(fav.fromId, fav.toId, isoTime, isDeparture = true, results = 3)
        }.getOrDefault(emptyList())

        val journey = journeys.firstOrNull()
            ?: return FavoriteStatus(fav.id, "unknown", 0, "", "")

        val status = when {
            journey.cancelled -> "cancelled"
            journey.departureDelay > 0 -> "delay"
            else -> "ok"
        }
        return FavoriteStatus(
            favoriteId = fav.id,
            status = status,
            delay = journey.departureDelay,
            platform = journey.platform,
            nextDeparture = journey.departure
        )
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
