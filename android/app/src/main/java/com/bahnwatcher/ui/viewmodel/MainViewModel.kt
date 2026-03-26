package com.bahnwatcher.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bahnwatcher.data.model.*
import com.bahnwatcher.data.repository.AppSettings
import com.bahnwatcher.data.repository.BahnRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repo = BahnRepository(application)

    val favorites: StateFlow<List<Favorite>> = repo.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<AppSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    // ---- Search state ----
    private val _fromStation = MutableStateFlow<StopLocation?>(null)
    val fromStation = _fromStation.asStateFlow()

    private val _toStation = MutableStateFlow<StopLocation?>(null)
    val toStation = _toStation.asStateFlow()

    private val _stationSuggestions = MutableStateFlow<List<StopLocation>>(emptyList())
    val stationSuggestions = _stationSuggestions.asStateFlow()

    private val _journeys = MutableStateFlow<List<JourneyUi>>(emptyList())
    val journeys = _journeys.asStateFlow()

    private val _searchLoading = MutableStateFlow(false)
    val searchLoading = _searchLoading.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore = _loadingMore.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError = _searchError.asStateFlow()

    // current search params – needed for "load more"
    private var lastDateTime: LocalDateTime = LocalDateTime.now()
    private var lastIsDeparture: Boolean = true
    private var lastResults: Int = 5

    val includeLongDistance = MutableStateFlow(true)

    // ---- Favorites status ----
    private val _favoriteStatuses = MutableStateFlow<Map<String, FavoriteStatus>>(emptyMap())
    val favoriteStatuses = _favoriteStatuses.asStateFlow()

    private val _refreshingFav = MutableStateFlow<Set<String>>(emptySet())
    val refreshingFav = _refreshingFav.asStateFlow()

    // ---- Nearby stops (legacy, kept for compatibility) ----
    private val _nearbyStops = MutableStateFlow<List<NearbyStopUi>>(emptyList())
    val nearbyStops = _nearbyStops.asStateFlow()

    private val _nearbyLoading = MutableStateFlow(false)
    val nearbyLoading = _nearbyLoading.asStateFlow()

    // ---- Alternatives state ----
    private val _altFromStation = MutableStateFlow<StopLocation?>(null)
    val altFromStation = _altFromStation.asStateFlow()

    private val _altToStation = MutableStateFlow<StopLocation?>(null)
    val altToStation = _altToStation.asStateFlow()

    private val _altSuggestions = MutableStateFlow<List<StopLocation>>(emptyList())
    val altSuggestions = _altSuggestions.asStateFlow()

    private val _alternativeResults = MutableStateFlow<List<AlternativeResult>>(emptyList())
    val alternativeResults = _alternativeResults.asStateFlow()

    private val _alternativesLoading = MutableStateFlow(false)
    val alternativesLoading = _alternativesLoading.asStateFlow()

    private val _alternativesError = MutableStateFlow<String?>(null)
    val alternativesError = _alternativesError.asStateFlow()

    private val _pendingAlternativeSearch = MutableStateFlow(false)
    val pendingAlternativeSearch = _pendingAlternativeSearch.asStateFlow()

    // ---- Alternatives station search ----

    private var searchAltStationsJob: Job? = null

    fun searchAltStations(query: String) {
        searchAltStationsJob?.cancel()
        if (query.length < 2) { _altSuggestions.value = emptyList(); return }
        searchAltStationsJob = viewModelScope.launch {
            delay(300)
            val results = repo.searchStations(query)
            _altSuggestions.value = results.filter { it.type == "stop" || it.type == "station" }
        }
    }

    fun clearAltSuggestions() { _altSuggestions.value = emptyList() }

    fun setAltFromStation(stop: StopLocation) {
        _altFromStation.value = stop
        _altSuggestions.value = emptyList()
    }

    fun setAltToStation(stop: StopLocation) {
        _altToStation.value = stop
        _altSuggestions.value = emptyList()
    }

    fun prefillAlternatives(fromId: String, fromName: String, toId: String, toName: String) {
        _altFromStation.value = StopLocation(id = fromId, name = fromName, type = "stop", location = null)
        _altToStation.value = StopLocation(id = toId, name = toName, type = "stop", location = null)
        _pendingAlternativeSearch.value = true
    }

    fun clearPendingAlternativeSearch() { _pendingAlternativeSearch.value = false }

    fun searchAlternatives(lat: Double? = null, lon: Double? = null) {
        val from = _altFromStation.value ?: return
        val to = _altToStation.value ?: return
        val fromId = from.id ?: return
        val toId = to.id ?: return

        viewModelScope.launch {
            _alternativesLoading.value = true
            _alternativesError.value = null
            _alternativeResults.value = emptyList()
            try {
                val nowIso = java.time.Instant.now().toString()
                val results = mutableListOf<AlternativeResult>()

                // Direct alternatives from the given station
                val direct = repo.searchJourneys(fromId, toId, nowIso, isDeparture = true, results = 5)
                if (direct.isNotEmpty()) {
                    results.add(AlternativeResult(walkMinutes = 0, stationName = from.name ?: "", journeys = direct))
                }

                // Nearby stations reachable on foot (only if GPS provided)
                if (lat != null && lon != null) {
                    val nearby = repo.getNearbyStops(lat, lon)
                    nearby.filter { it.type == "stop" || it.type == "station" }
                        .take(8)
                        .forEach { stop ->
                            val dist = if (stop.location != null)
                                repo.haversineDistance(lat, lon,
                                    stop.location.latitude ?: 0.0,
                                    stop.location.longitude ?: 0.0)
                            else (stop.distance?.toDouble() ?: 9999.0)
                            val walkMin = repo.walkingMinutes(dist)
                            if (walkMin in 1..15 && stop.id != null && stop.id != fromId) {
                                val journeys = repo.searchJourneys(
                                    stop.id, toId, nowIso, isDeparture = true, results = 3)
                                if (journeys.isNotEmpty()) {
                                    results.add(AlternativeResult(
                                        walkMinutes = walkMin,
                                        stationName = stop.name ?: "",
                                        journeys = journeys
                                    ))
                                }
                            }
                        }
                }

                _alternativeResults.value = results
                if (results.isEmpty()) _alternativesError.value = "Keine Alternativen gefunden."
            } catch (e: Exception) {
                _alternativesError.value = "Fehler: ${e.message}"
            } finally {
                _alternativesLoading.value = false
            }
        }
    }

    // ---- Station search ----

    private var searchStationsJob: Job? = null

    fun searchStations(query: String) {
        searchStationsJob?.cancel()
        if (query.length < 2) {
            _stationSuggestions.value = emptyList()
            return
        }
        searchStationsJob = viewModelScope.launch {
            delay(300)
            val results = repo.searchStations(query)
            _stationSuggestions.value = results.filter { it.type == "stop" || it.type == "station" }
        }
    }

    fun clearSuggestions() {
        _stationSuggestions.value = emptyList()
    }

    fun setFromStation(stop: StopLocation) {
        _fromStation.value = stop
        _stationSuggestions.value = emptyList()
    }

    fun setToStation(stop: StopLocation) {
        _toStation.value = stop
        _stationSuggestions.value = emptyList()
    }

    fun swapStations() {
        val temp = _fromStation.value
        _fromStation.value = _toStation.value
        _toStation.value = temp
    }

    // ---- Journey search ----

    fun searchJourneys(dateTime: LocalDateTime, isDeparture: Boolean) {
        val from = _fromStation.value ?: return
        val to = _toStation.value ?: return
        val fromId = from.id ?: return
        val toId = to.id ?: return

        lastDateTime = dateTime
        lastIsDeparture = isDeparture
        lastResults = 5

        viewModelScope.launch {
            _searchLoading.value = true
            _searchError.value = null
            _journeys.value = emptyList()
            try {
                val iso = dateTime.atZone(ZoneId.systemDefault()).toInstant().toString()
                val results = repo.searchJourneys(
                    fromId, toId, iso, isDeparture,
                    results = lastResults,
                    includeLongDistance = includeLongDistance.value
                )
                _journeys.value = results
                if (results.isEmpty()) _searchError.value = "Keine Verbindungen gefunden."
            } catch (e: Exception) {
                _searchError.value = "Fehler: ${e.message}"
            } finally {
                _searchLoading.value = false
            }
        }
    }

    fun loadMoreJourneys() {
        val from = _fromStation.value ?: return
        val to = _toStation.value ?: return
        val fromId = from.id ?: return
        val toId = to.id ?: return

        lastResults += 5

        viewModelScope.launch {
            _loadingMore.value = true
            try {
                val iso = lastDateTime.atZone(ZoneId.systemDefault()).toInstant().toString()
                val results = repo.searchJourneys(
                    fromId, toId, iso, lastIsDeparture,
                    results = lastResults,
                    includeLongDistance = includeLongDistance.value
                )
                _journeys.value = results
            } catch (e: Exception) {
                _searchError.value = "Fehler: ${e.message}"
            } finally {
                _loadingMore.value = false
            }
        }
    }

    // ---- Favorites ----

    fun saveAsFavorite(journey: JourneyUi, name: String, days: List<Int>) {
        viewModelScope.launch {
            val depHour = journey.plannedDeparture.take(5)
            val arrHour = journey.plannedArrival.take(5)
            val fav = Favorite(
                id = "fav_${System.currentTimeMillis()}",
                name = name,
                fromId = journey.fromId,
                fromName = journey.from,
                toId = journey.toId,
                toName = journey.to,
                timeFrom = depHour,
                timeTo = arrHour,
                days = days.joinToString(",")
            )
            repo.addFavorite(fav)
        }
    }

    fun deleteFavorite(id: String) {
        viewModelScope.launch { repo.deleteFavorite(id) }
    }

    fun refreshFavorite(fav: Favorite) {
        viewModelScope.launch {
            _refreshingFav.value = _refreshingFav.value + fav.id
            try {
                val status = repo.checkFavoriteStatus(fav)
                _favoriteStatuses.value = _favoriteStatuses.value + (fav.id to status)
                repo.updateFavorite(
                    fav.copy(
                        lastStatus = status.status,
                        lastDelay = status.delay,
                        lastPlatform = status.platform,
                        lastChecked = System.currentTimeMillis()
                    )
                )
            } finally {
                _refreshingFav.value = _refreshingFav.value - fav.id
            }
        }
    }

    fun refreshAllFavorites() {
        viewModelScope.launch {
            favorites.value.forEach { refreshFavorite(it) }
        }
    }

    // ---- Alternatives / Nearby ----

    fun loadNearbyStops(lat: Double, lon: Double, walkMinuteRadius: Int = 15) {
        viewModelScope.launch {
            _nearbyLoading.value = true
            try {
                val stops = repo.getNearbyStops(lat, lon)
                val uiStops = stops
                    .filter { it.type == "stop" || it.type == "station" }
                    .map { stop ->
                        val dist = if (stop.location != null)
                            repo.haversineDistance(lat, lon, stop.location.latitude ?: 0.0, stop.location.longitude ?: 0.0)
                        else (stop.distance?.toDouble() ?: 0.0)
                        val walkMin = repo.walkingMinutes(dist)
                        val departures = if (walkMin <= walkMinuteRadius)
                            repo.getDepartures(stop.id ?: "")
                        else emptyList()
                        NearbyStopUi(
                            stop = stop,
                            walkMinutes = walkMin,
                            reachable = walkMin <= walkMinuteRadius,
                            departures = departures.take(5)
                        )
                    }
                    .sortedBy { it.walkMinutes }
                _nearbyStops.value = uiStops
            } finally {
                _nearbyLoading.value = false
            }
        }
    }

    // ---- Favorite detail journey (for dialog leg view) ----

    private val _favoriteDetailJourneys = MutableStateFlow<Map<String, JourneyUi?>>(emptyMap())
    val favoriteDetailJourneys = _favoriteDetailJourneys.asStateFlow()

    private val _favoriteDetailLoading = MutableStateFlow<Set<String>>(emptySet())
    val favoriteDetailLoading = _favoriteDetailLoading.asStateFlow()

    fun loadFavoriteDetail(fav: Favorite) {
        if (fav.id in _favoriteDetailLoading.value) return
        viewModelScope.launch {
            _favoriteDetailLoading.value = _favoriteDetailLoading.value + fav.id
            try {
                val nowIso = Instant.now().toString()
                val journeys = repo.searchJourneys(fav.fromId, fav.toId, nowIso, isDeparture = true, results = 1)
                _favoriteDetailJourneys.value = _favoriteDetailJourneys.value + (fav.id to journeys.firstOrNull())
            } catch (_: Exception) {
            } finally {
                _favoriteDetailLoading.value = _favoriteDetailLoading.value - fav.id
            }
        }
    }

    // ---- Notification deep-link state ----

    private val _pendingFavoriteId = MutableStateFlow<String?>(null)
    val pendingFavoriteId = _pendingFavoriteId.asStateFlow()

    private val _pendingOpenSearch = MutableStateFlow(false)
    val pendingOpenSearch = _pendingOpenSearch.asStateFlow()

    fun handleNotificationFavoriteIntent(favoriteId: String) {
        _pendingFavoriteId.value = favoriteId
    }

    fun handleNotificationSearchIntent(fromId: String, fromName: String, toId: String, toName: String) {
        _fromStation.value = StopLocation(id = fromId, name = fromName, type = "stop", location = null)
        _toStation.value = StopLocation(id = toId, name = toName, type = "stop", location = null)
        searchJourneys(LocalDateTime.now(), isDeparture = true)
        _pendingOpenSearch.value = true
    }

    fun clearPendingFavorite() {
        _pendingFavoriteId.value = null
    }

    fun clearPendingSearch() {
        _pendingOpenSearch.value = false
    }

    // ---- Settings ----

    fun saveSettings(s: AppSettings) {
        viewModelScope.launch { repo.saveSettings(s) }
    }

    fun setMonitoring(enabled: Boolean) {
        viewModelScope.launch {
            val current = settings.value
            repo.saveSettings(current.copy(monitoring = enabled))
        }
    }

    fun deleteAllData() {
        viewModelScope.launch {
            favorites.value.forEach { repo.deleteFavorite(it.id) }
            repo.saveSettings(AppSettings())
        }
    }
}
