package com.bahnwatcher.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
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
import com.bahnwatcher.data.model.Departure
import com.bahnwatcher.data.model.NearbyStopUi
import com.bahnwatcher.ui.theme.*
import com.bahnwatcher.ui.viewmodel.MainViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AlternativesScreen(vm: MainViewModel) {
    val nearbyStops by vm.nearbyStops.collectAsState()
    val loading by vm.nearbyLoading.collectAsState()
    val context = LocalContext.current
    var walkRadius by remember { mutableIntStateOf(15) }
    var permissionDenied by remember { mutableStateOf(false) }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            getLocation(context) { lat, lon ->
                vm.loadNearbyStops(lat, lon, walkRadius)
            }
        } else {
            permissionDenied = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(16.dp)
    ) {
        Text("Alternativen", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OnSurface)
        Spacer(Modifier.height(4.dp))
        Text("Erreichbare Haltestellen in der Nähe",
            color = OnSurfaceMuted, fontSize = 13.sp)

        Spacer(Modifier.height(16.dp))

        // Walk radius selector
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Fußweg-Radius: $walkRadius Min.", color = OnSurface, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15).forEach { min ->
                        FilterChip(
                            selected = walkRadius == min,
                            onClick = { walkRadius = min },
                            label = { Text("$min min", fontSize = 13.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Cyan.copy(alpha = 0.2f),
                                selectedLabelColor = Cyan
                            )
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (permissionDenied) {
            Card(colors = CardDefaults.cardColors(containerColor = Error.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(12.dp)) {
                Text("GPS-Berechtigung benötigt. Bitte in den Einstellungen aktivieren.",
                    color = Error, modifier = Modifier.padding(14.dp))
            }
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = {
                locationLauncher.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = BackgroundDark)
        ) {
            Icon(Icons.Default.MyLocation, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Standort verwenden", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(16.dp))

        if (loading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Cyan)
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(nearbyStops) { stopUi ->
                NearbyStopCard(stopUi = stopUi, context = context)
            }
        }
    }
}

@Composable
fun NearbyStopCard(stopUi: NearbyStopUi, context: android.content.Context) {
    val reachabilityColor = when {
        stopUi.walkMinutes <= 5 -> Success
        stopUi.walkMinutes <= 10 -> Warning
        else -> Error
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stopUi.stop.name ?: "Unbekannt",
                    fontWeight = FontWeight.SemiBold, color = OnSurface, fontSize = 15.sp,
                    modifier = Modifier.weight(1f))
                Surface(color = reachabilityColor.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DirectionsWalk, null,
                            tint = reachabilityColor, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("${stopUi.walkMinutes} min", color = reachabilityColor, fontSize = 12.sp)
                    }
                }
            }

            // Distance
            stopUi.stop.distance?.let { dist ->
                Spacer(Modifier.height(4.dp))
                Text("${dist}m entfernt", color = OnSurfaceMuted, fontSize = 12.sp)
            }

            if (stopUi.departures.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = Border)
                Spacer(Modifier.height(8.dp))
                Text("Abfahrten:", color = OnSurfaceMuted, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                stopUi.departures.take(4).forEach { dep ->
                    DepartureRow(dep = dep)
                }
            } else if (!stopUi.reachable) {
                Spacer(Modifier.height(6.dp))
                Text("Zu weit entfernt für aktuellen Radius",
                    color = OnSurfaceMuted, fontSize = 12.sp)
            }

            // Google Maps link
            stopUi.stop.location?.let { loc ->
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        val uri = Uri.parse("https://maps.google.com/maps?daddr=${loc.latitude},${loc.longitude}&dirflg=w")
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Map, null, tint = Cyan, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Route in Google Maps", color = Cyan, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun DepartureRow(dep: Departure) {
    val time = dep.`when` ?: dep.plannedWhen ?: ""
    val formattedTime = runCatching {
        val instant = Instant.parse(time)
        DateTimeFormatter.ofPattern("HH:mm").format(instant.atZone(ZoneId.systemDefault()))
    }.getOrDefault(time)

    val delay = (dep.delay ?: 0) / 60
    val cancelled = dep.cancelled == true

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(formattedTime,
            color = if (cancelled) Error else OnSurface,
            fontSize = 14.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.width(40.dp))

        if (delay > 0 && !cancelled) {
            Text("+${delay}'", color = Warning, fontSize = 12.sp)
        }

        dep.line?.name?.let { lineName ->
            Surface(color = Cyan.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                Text(lineName, color = Cyan, fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
            }
        }

        Text(dep.direction ?: "", color = OnSurfaceMuted, fontSize = 12.sp,
            modifier = Modifier.weight(1f), maxLines = 1)

        if (cancelled) {
            Text("Ausf.", color = Error, fontSize = 11.sp)
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
        locationManager.getCurrentLocation(provider, null,
            context.mainExecutor
        ) { location ->
            location?.let { onLocation(it.latitude, it.longitude) }
        }
    } catch (_: Exception) {}
}
