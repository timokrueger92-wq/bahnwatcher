package com.bahnwatcher.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bahnwatcher.data.model.Favorite
import com.bahnwatcher.data.model.FavoriteStatus
import com.bahnwatcher.ui.theme.*
import com.bahnwatcher.ui.viewmodel.MainViewModel

@Composable
fun FavoritesScreen(vm: MainViewModel) {
    val favorites by vm.favorites.collectAsState()
    val statuses by vm.favoriteStatuses.collectAsState()
    val refreshing by vm.refreshingFav.collectAsState()
    val settings by vm.settings.collectAsState()

    LaunchedEffect(Unit) {
        if (favorites.isNotEmpty()) vm.refreshAllFavorites()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Favoriten",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurface
            )
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
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Success, shape = RoundedCornerShape(50))
                        )
                        Text("Monitoring aktiv", color = Success, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

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
                        onDelete = { vm.deleteFavorite(fav.id) }
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
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

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
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Cyan
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null,
                            modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Aktualisieren", fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
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
