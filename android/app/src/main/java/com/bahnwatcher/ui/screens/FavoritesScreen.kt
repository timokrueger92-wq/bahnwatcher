package com.bahnwatcher.ui.screens

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bahnwatcher.data.model.Favorite
import com.bahnwatcher.data.model.FavoriteStatus
import com.bahnwatcher.data.repository.AppSettings
import com.bahnwatcher.ui.theme.*
import com.bahnwatcher.ui.viewmodel.MainViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun FavoritesScreen(vm: MainViewModel, onNavigateToSettings: () -> Unit = {}) {
    val favorites by vm.favorites.collectAsState()
    val statuses by vm.favoriteStatuses.collectAsState()
    val refreshing by vm.refreshingFav.collectAsState()
    val settings by vm.settings.collectAsState()
    val pendingFavoriteId by vm.pendingFavoriteId.collectAsState()

    // Auto-open detail dialog when arriving from a notification tap
    var notifDetailFavId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingFavoriteId) {
        if (pendingFavoriteId != null) {
            notifDetailFavId = pendingFavoriteId
            vm.clearPendingFavorite()
        }
    }
    val notifDetailFav = favorites.find { it.id == notifDetailFavId }
    if (notifDetailFav != null) {
        FavoriteDetailDialog(
            fav = notifDetailFav,
            status = statuses[notifDetailFav.id],
            onDismiss = { notifDetailFavId = null },
            onFindAlternatives = {
                vm.prefillAlternatives(notifDetailFav.fromId, notifDetailFav.fromName,
                    notifDetailFav.toId, notifDetailFav.toName)
                notifDetailFavId = null
            }
        )
    }

    LaunchedEffect(Unit) {
        if (favorites.isNotEmpty()) vm.refreshAllFavorites()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Favoriten", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OnSurface)
            if (settings.monitoring) {
                Surface(
                    color = Success.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(modifier = Modifier.size(6.dp).background(Success, shape = RoundedCornerShape(50)))
                        Text("Monitoring aktiv", color = Success, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        MonitoringStatusBanner(
            favorites = favorites,
            settings = settings,
            onGoToSettings = onNavigateToSettings
        )

        if (favorites.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Star, contentDescription = null,
                        tint = OnSurfaceMuted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Keine Favoriten gespeichert.", color = OnSurfaceMuted)
                    Text("Verbindungen suchen und als Favorit speichern.",
                        color = OnSurfaceMuted, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(favorites, key = { it.id }) { fav ->
                    FavoriteCard(
                        fav = fav,
                        status = statuses[fav.id],
                        isRefreshing = fav.id in refreshing,
                        onRefresh = { vm.refreshFavorite(fav) },
                        onDelete = { vm.deleteFavorite(fav.id) },
                        onFindAlternatives = {
                            vm.prefillAlternatives(fav.fromId, fav.fromName, fav.toId, fav.toName)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FavoriteCard(
    fav: Favorite,
    status: FavoriteStatus?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onFindAlternatives: () -> Unit = {}
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDetail by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Favorit löschen?") },
            text = { Text("\"${fav.name}\" wird entfernt.") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text("Löschen", color = Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Abbrechen") }
            },
            containerColor = SurfaceDark
        )
    }

    if (showDetail) {
        FavoriteDetailDialog(
            fav = fav,
            status = status,
            onDismiss = { showDetail = false },
            onFindAlternatives = { onFindAlternatives(); showDetail = false }
        )
    }

    val statusColor = when (status?.status) {
        "ok" -> Success
        "delay" -> Warning
        "cancelled" -> Error
        else -> OnSurfaceMuted
    }

    val statusText = when (status?.status) {
        "ok" -> "pünktlich"
        "delay" -> "+${status.delay} min"
        "cancelled" -> "ausgefallen"
        else -> "–"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDetail = true },
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(12.dp)
    ) {
        // Status indicator bar on left
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(IntrinsicSize.Min)
                    .background(statusColor, shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                    .defaultMinSize(minHeight = 60.dp)
            )
            Column(modifier = Modifier.padding(14.dp).weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(fav.name, fontWeight = FontWeight.SemiBold, color = OnSurface, fontSize = 15.sp,
                        modifier = Modifier.weight(1f))
                    Surface(
                        color = statusColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            statusText,
                            color = statusColor,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TrendingFlat, contentDescription = null,
                        tint = OnSurfaceMuted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${fav.fromName}  →  ${fav.toName}",
                        color = OnSurfaceMuted, fontSize = 13.sp)
                }

                if (status?.nextDeparture?.isNotEmpty() == true) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null,
                            tint = OnSurfaceMuted, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Nächste: ${status.nextDeparture}",
                            color = OnSurfaceMuted, fontSize = 13.sp)
                        if (status.platform.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Text("Gleis ${status.platform}", color = Cyan, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = Border)
                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onRefresh,
                        enabled = !isRefreshing,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan),
                        modifier = Modifier.height(34.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Cyan)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Aktualisieren", fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("Details →", color = Cyan, fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.CenterVertically))
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Löschen",
                            tint = Error, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun FavoriteDetailDialog(
    fav: Favorite,
    status: FavoriteStatus?,
    onDismiss: () -> Unit,
    onFindAlternatives: (() -> Unit)? = null
) {
    val dayLabels = listOf(1 to "Mo", 2 to "Di", 3 to "Mi", 4 to "Do", 5 to "Fr", 6 to "Sa", 0 to "So")
    val activeDays = fav.days.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()

    val statusColor = when (status?.status) {
        "ok" -> Success
        "delay" -> Warning
        "cancelled" -> Error
        else -> OnSurfaceMuted
    }
    val statusText = when (status?.status) {
        "ok" -> "Pünktlich"
        "delay" -> "+${status.delay} min Verspätung"
        "cancelled" -> "Ausgefallen"
        else -> "Noch nicht geprüft"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = {
            Text(fav.name, color = OnSurface, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Route
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TrendingFlat, null, tint = Cyan, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(fav.fromName, color = OnSurface, fontSize = 14.sp)
                        Text("↓", color = OnSurfaceMuted, fontSize = 12.sp)
                        Text(fav.toName, color = OnSurface, fontSize = 14.sp)
                    }
                }

                HorizontalDivider(color = Border)

                // Monitoring window
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, tint = OnSurfaceMuted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Überwachungsfenster: ${fav.timeFrom} – ${fav.timeTo}",
                        color = OnSurface, fontSize = 13.sp)
                }

                // Days
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    dayLabels.forEach { (day, label) ->
                        val active = day in activeDays
                        Surface(
                            color = if (active) Cyan.copy(alpha = 0.2f) else Border,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                label,
                                color = if (active) Cyan else OnSurfaceMuted,
                                fontSize = 12.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = Border)

                // Current status
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(10.dp).background(statusColor, RoundedCornerShape(50)))
                    Text(statusText, color = statusColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }

                if (status?.nextDeparture?.isNotEmpty() == true) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Train, null, tint = OnSurfaceMuted, modifier = Modifier.size(14.dp))
                        Text("Nächste Abfahrt: ${status.nextDeparture}", color = OnSurface, fontSize = 13.sp)
                        if (status.platform.isNotEmpty()) {
                            Text("Gleis ${status.platform}", color = Cyan, fontSize = 12.sp)
                        }
                    }
                }

                if (fav.lastChecked > 0) {
                    val checkedTime = Instant.ofEpochMilli(fav.lastChecked)
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("dd.MM. HH:mm"))
                    Text("Zuletzt geprüft: $checkedTime", color = OnSurfaceMuted, fontSize = 11.sp)
                }

                // Show alternatives button when connection is problematic
                if (onFindAlternatives != null &&
                    (status?.status == "delay" || status?.status == "cancelled")) {
                    HorizontalDivider(color = Border)
                    OutlinedButton(
                        onClick = onFindAlternatives,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Warning),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DirectionsWalk, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Alternativen suchen", fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Schließen", color = Cyan) }
        }
    )
}

@Composable
fun MonitoringStatusBanner(
    favorites: List<Favorite>,
    settings: AppSettings,
    onGoToSettings: () -> Unit
) {
    val context = LocalContext.current
    val batteryOptIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        pm.isIgnoringBatteryOptimizations(context.packageName)
    } else true

    var monitoringHintDismissed by remember { mutableStateOf(false) }

    when {
        // Critical: monitoring is on but battery optimization blocks background work
        settings.monitoring && !batteryOptIgnored -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = Warning.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Warning, null, tint = Warning, modifier = Modifier.size(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Akkuoptimierung aktiv",
                            color = Warning, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Hintergrundchecks können unterbrochen werden",
                            color = OnSurfaceMuted, fontSize = 11.sp)
                    }
                    TextButton(
                        onClick = onGoToSettings,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("Beheben →", color = Warning, fontSize = 12.sp)
                    }
                }
            }
        }
        // Soft hint: favorites exist but monitoring is off (dismissable per session)
        !settings.monitoring && favorites.isNotEmpty() && !monitoringHintDismissed -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = Cyan.copy(alpha = 0.08f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.NotificationsOff, null,
                        tint = Cyan, modifier = Modifier.size(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Monitoring nicht aktiv",
                            color = OnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Aktiviere es in den Einstellungen für Push-Benachrichtigungen",
                            color = OnSurfaceMuted, fontSize = 11.sp)
                    }
                    TextButton(
                        onClick = onGoToSettings,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text("Aktivieren", color = Cyan, fontSize = 12.sp)
                    }
                    IconButton(
                        onClick = { monitoringHintDismissed = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, null,
                            tint = OnSurfaceMuted, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}
