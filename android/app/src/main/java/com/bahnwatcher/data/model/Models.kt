package com.bahnwatcher.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

// ---- Room Entities ----

@Entity(tableName = "favorites")
data class Favorite(
    @PrimaryKey val id: String,
    val name: String,
    val fromId: String,
    val fromName: String,
    val toId: String,
    val toName: String,
    val timeFrom: String,   // "HH:mm" monitoring window start
    val timeTo: String,     // "HH:mm" monitoring window end
    val days: String,       // comma-separated ints 0-6 (Sun=0)
    val lastStatus: String = "unknown", // "ok", "delay", "cancelled", "unknown"
    val lastDelay: Int = 0,
    val lastPlatform: String = "",
    val lastChecked: Long = 0L,
    val refreshToken: String = ""  // HAFAS refresh token to reconstruct exact journey
)

// ---- API Response Models ----

data class StopLocation(
    val id: String?,
    val name: String?,
    val type: String?,
    val location: Location?
)

data class Location(
    val latitude: Double?,
    val longitude: Double?
)

data class JourneysResponse(
    val journeys: List<Journey>?
)

data class Journey(
    val legs: List<Leg>?,
    val refreshToken: String?
)

data class RefreshJourneyResponse(
    val journey: Journey?
)

data class Leg(
    val origin: StopLocation?,
    val destination: StopLocation?,
    val departure: String?,
    val arrival: String?,
    val plannedDeparture: String?,
    val plannedArrival: String?,
    val departureDelay: Int?,
    val arrivalDelay: Int?,
    val cancelled: Boolean?,
    val walking: Boolean?,
    val line: Line?,
    val direction: String?,
    val platform: String?,
    val plannedPlatform: String?,
    val departurePlatform: String?,
    val plannedDeparturePlatform: String?
)

data class Line(
    val id: String?,
    val name: String?,
    val mode: String?,
    val product: String?
)

data class DeparturesResponse(
    val departures: List<Departure>?
)

data class Departure(
    val tripId: String?,
    val stop: StopLocation?,
    val `when`: String?,
    val plannedWhen: String?,
    val delay: Int?,
    val cancelled: Boolean?,
    val line: Line?,
    val direction: String?,
    val platform: String?,
    val plannedPlatform: String?
)

data class NearbyStop(
    val id: String?,
    val name: String?,
    val type: String?,
    val location: Location?,
    val distance: Int?
)

// ---- UI State Models ----

data class JourneyUi(
    val departure: String,
    val plannedDeparture: String,
    val arrival: String,
    val plannedArrival: String,
    val durationMin: Int,
    val transfers: Int,
    val hasWalking: Boolean,
    val cancelled: Boolean,
    val departureDelay: Int,
    val arrivalDelay: Int,
    val platform: String,
    val legs: List<Leg>,
    val from: String,
    val to: String,
    val fromId: String,
    val toId: String,
    val refreshToken: String = ""
)

data class FavoriteStatus(
    val favoriteId: String,
    val status: String,    // "ok", "delay", "cancelled", "unknown"
    val delay: Int,
    val platform: String,
    val nextDeparture: String
)

data class NearbyStopUi(
    val stop: NearbyStop,
    val walkMinutes: Int,
    val reachable: Boolean,
    val departures: List<Departure>
)

data class AlternativeResult(
    val walkMinutes: Int,       // 0 = direct from fromStation, >0 = walk to another station
    val stationName: String,    // the station these journeys depart from
    val journeys: List<JourneyUi>
)
