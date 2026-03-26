package com.bahnwatcher.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bahnwatcher.data.model.AlternativeResult
import com.bahnwatcher.data.model.JourneyUi
import com.bahnwatcher.ui.theme.*
import com.bahnwatcher.ui.viewmodel.MainViewModel

@Composable
fun AlternativesScreen(vm: MainViewModel) {
    val altFrom by vm.altFromStation.collectAsState()
    val altTo by vm.altToStation.collectAsState()
    val altSuggestions by vm.altSuggestions.collectAsState()
    val alternatives by vm.alternativeResults.collectAsState()
    val loading by vm.alternativesLoading.collectAsState()
    val error by vm.alternativesError.collectAsState()
    val pendingAlt by vm.pendingAlternativeSearch.collectAsState()

    var fromQuery by remember { mutableStateOf(altFrom?.name ?: "") }
    var toQuery by remember { mutableStateOf(altTo?.name ?: "") }
    var activeAltField by remember { mutableStateOf<String?>(null) }
    var gpsLat by remember { mutableStateOf<Double?>(null) }
    var gpsLon by remember { mutableStateOf<Double?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(altFrom) { altFrom?.name?.let { fromQuery = it } }
    LaunchedEffect(altTo) { altTo?.name?.let { toQuery = it } }

    // Auto-search when pre-filled from FavoriteDetailDialog
    LaunchedEffect(pendingAlt) {
        if (pendingAlt) {
            vm.clearPendingAlternativeSearch()
            vm.searchAlternatives(gpsLat, gpsLon)
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            getLocation(context) { lat, lon ->
                gpsLat = lat; gpsLon = lon
                vm.searchAlternatives(lat, lon)
            }
        } else {
            permissionDenied = true
            vm.searchAlternatives()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Alternativen", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OnSurface)
            Text("Notfallplan wenn deine Verbindung ausfällt",
                color = OnSurfaceMuted, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))

            // Station inputs
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    StationField(
                        label = "Aktuelle Haltestelle (Von)",
                        value = fromQuery,
                        icon = Icons.Default.TripOrigin,
                        suggestions = if (activeAltField == "from") altSuggestions else emptyList(),
                        isActive = activeAltField == "from",
                        onValueChange = {
                            fromQuery = it
                            activeAltField = "from"
                            vm.searchAltStations(it)
                        },
                        onClear = { fromQuery = ""; vm.clearAltSuggestions() },
                        onSuggestionSelected = { stop ->
                            fromQuery = stop.name ?: ""
                            vm.setAltFromStation(stop)
                            activeAltField = null
                        },
                        onDismissSuggestions = { activeAltField = null }
                    )
                    HorizontalDivider(color = Border, modifier = Modifier.padding(vertical = 8.dp))
                    StationField(
                        label = "Ziel",
                        value = toQuery,
                        icon = Icons.Default.Place,
                        suggestions = if (activeAltField == "to") altSuggestions else emptyList(),
                        isActive = activeAltField == "to",
                        onValueChange = {
                            toQuery = it
                            activeAltField = "to"
                            vm.searchAltStations(it)
                        },
                        onClear = { toQuery = ""; vm.clearAltSuggestions() },
                        onSuggestionSelected = { stop ->
                            toQuery = stop.name ?: ""
                            vm.setAltToStation(stop)
                            activeAltField = null
                        },
                        onDismissSuggestions = { activeAltField = null }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.searchAlternatives(gpsLat, gpsLon) },
                    enabled = altFrom != null && altTo != null && !loading,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Cyan, contentColor = BackgroundDark)
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = BackgroundDark)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Alternativen suchen", fontWeight = FontWeight.SemiBold)
                }
                // GPS button: include nearby stations
                OutlinedButton(
                    onClick = {
                        locationLauncher.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ))
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Standort",
                        modifier = Modifier.size(18.dp))
                }
            }

            if (permissionDenied) {
                Spacer(Modifier.height(4.dp))
                Text("Kein GPS – Suche nur direkte Verbindungen.",
                    color = Warning, fontSize = 12.sp)
            } else if (gpsLat == null && alternatives.isEmpty() && !loading) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Info, null, tint = OnSurfaceMuted,
                        modifier = Modifier.size(13.dp))
                    Text("Tipp: GPS-Button drücken um auch Umgehungsstationen zu finden.",
                        color = OnSurfaceMuted, fontSize = 12.sp)
                }
            }
        }

        error?.let {
            Text(it, color = Error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            alternatives.forEach { group ->
                item(key = "header_${group.stationName}_${group.walkMinutes}") {
                    AlternativeGroupHeader(group)
                }
                items(group.journeys,
                    key = { j -> "${group.stationName}_${j.departure}_${j.arrival}" }
                ) { journey ->
                    AlternativeJourneyCard(journey)
                }
            }
        }
    }
}

@Composable
fun AlternativeGroupHeader(group: AlternativeResult) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    ) {
        if (group.walkMinutes == 0) {
            Icon(Icons.Default.Train, null, tint = Cyan, modifier = Modifier.size(15.dp))
            Text("Direkt ab ${group.stationName}",
                color = Cyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        } else {
            Icon(Icons.Default.DirectionsWalk, null, tint = Warning, modifier = Modifier.size(15.dp))
            Text("🚶 ${group.walkMinutes} min → ${group.stationName}",
                color = Warning, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun AlternativeJourneyCard(journey: JourneyUi) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (journey.cancelled) {
            Surface(
                color = Error.copy(alpha = 0.12f),
                shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
            ) {
                Text("AUSGEFALLEN", color = Error, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp))
            }
        }
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Departure
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(journey.departure, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        color = if (journey.cancelled) Error else OnSurface)
                    if (journey.departureDelay > 0) {
                        Spacer(Modifier.width(4.dp))
                        Text("+${journey.departureDelay}'", color = Warning, fontSize = 13.sp)
                    }
                }
                if (journey.platform.isNotEmpty()) {
                    Text("Gleis ${journey.platform}", color = Cyan, fontSize = 11.sp)
                }
            }

            // Center info
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${journey.durationMin / 60}h ${journey.durationMin % 60}min",
                    color = OnSurfaceMuted, fontSize = 11.sp)
                Icon(Icons.Default.TrendingFlat, null,
                    tint = OnSurfaceMuted, modifier = Modifier.size(18.dp))
                if (journey.transfers > 0)
                    Text("${journey.transfers} Umst.", color = OnSurfaceMuted, fontSize = 11.sp)
            }

            // Arrival + status
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (journey.arrivalDelay > 0) {
                        Text("+${journey.arrivalDelay}'", color = Warning, fontSize = 13.sp)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(journey.arrival, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        color = if (journey.cancelled) Error else OnSurface)
                }
                if (!journey.cancelled) {
                    val onTime = journey.departureDelay == 0 && journey.arrivalDelay == 0
                    Text(
                        if (onTime) "pünktlich" else "+${journey.arrivalDelay}' Verspätung",
                        color = if (onTime) Success else Warning,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

private fun getLocation(
    context: android.content.Context,
    onLocation: (Double, Double) -> Unit
) {
    try {
        val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE)
                as android.location.LocationManager
        val provider = when {
            locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ->
                android.location.LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) ->
                android.location.LocationManager.NETWORK_PROVIDER
            else -> return
        }
        @Suppress("MissingPermission")
        locationManager.getCurrentLocation(provider, null, context.mainExecutor) { location ->
            location?.let { onLocation(it.latitude, it.longitude) }
        }
    } catch (_: Exception) {}
}
