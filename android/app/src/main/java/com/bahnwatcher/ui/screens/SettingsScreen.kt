package com.bahnwatcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.bahnwatcher.data.repository.AppSettings
import com.bahnwatcher.ui.theme.*
import com.bahnwatcher.ui.viewmodel.MainViewModel
import com.bahnwatcher.worker.MonitoringWorker

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var testSent by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Alle Daten löschen?") },
            text = { Text("Alle Favoriten und Einstellungen werden unwiderruflich gelöscht.") },
            containerColor = SurfaceDark,
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; vm.deleteAllData() }) {
                    Text("Löschen", color = Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Einstellungen", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OnSurface)

        // Push notifications
        SettingsSection(title = "Push-Benachrichtigungen") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null,
                    tint = Success, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Native Android-Benachrichtigungen aktiv",
                    color = OnSurface, fontSize = 14.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Bei Statusänderungen (Verspätung, Ausfall, Normalisierung) erscheinen " +
                "Benachrichtigungen direkt im Android-Benachrichtigungsbereich.",
                color = OnSurfaceMuted, fontSize = 13.sp, lineHeight = 18.sp
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    MonitoringWorker.sendNotification(
                        context,
                        "BahnWatcher Test",
                        "Push-Benachrichtigungen funktionieren!"
                    )
                    testSent = true
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan)
            ) {
                Icon(Icons.Default.Send, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Test-Benachrichtigung senden")
            }
            if (testSent) {
                Spacer(Modifier.height(4.dp))
                Text("Test gesendet! Sieh in den Benachrichtigungen nach.",
                    color = Success, fontSize = 12.sp)
            }
        }

        // Monitoring
        SettingsSection(title = "Auto-Monitoring") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Automatisch prüfen", color = OnSurface)
                    Text("Alle 2 Minuten im Zeitfenster", color = OnSurfaceMuted, fontSize = 12.sp)
                }
                Switch(
                    checked = settings.monitoring,
                    onCheckedChange = { vm.setMonitoring(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Cyan,
                        checkedTrackColor = Cyan.copy(alpha = 0.3f)
                    )
                )
            }

            Spacer(Modifier.height(12.dp))
            Text("Verspätungsschwelle", color = OnSurfaceMuted, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 5, 10).forEach { min ->
                    FilterChip(
                        selected = settings.delayThreshold == min,
                        onClick = { vm.saveSettings(settings.copy(delayThreshold = min)) },
                        label = { Text("$min min", fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Cyan.copy(alpha = 0.2f),
                            selectedLabelColor = Cyan
                        )
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Normalisierung melden", color = OnSurface)
                    Text("Wenn Zug wieder pünktlich ist", color = OnSurfaceMuted, fontSize = 12.sp)
                }
                Switch(
                    checked = settings.notifyNorm,
                    onCheckedChange = { vm.saveSettings(settings.copy(notifyNorm = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Cyan,
                        checkedTrackColor = Cyan.copy(alpha = 0.3f)
                    )
                )
            }
        }

        // Data management
        SettingsSection(title = "Datenverwaltung") {
            Text(
                "BahnWatcher speichert Favoriten und Einstellungen lokal auf diesem Gerät. " +
                "Es werden keine Daten an Dritte übermittelt (außer an die transport.rest API).",
                color = OnSurfaceMuted, fontSize = 13.sp, lineHeight = 18.sp
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Error)
            ) {
                Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Alle Daten löschen")
            }
        }

        // About
        SettingsSection(title = "Über") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoChip("Version 1.0")
                InfoChip("transport.rest API")
                InfoChip("Android Notifications")
            }
            Spacer(Modifier.height(8.dp))
            Text("Echtzeit-Bahndaten: transport.rest (FOSS)",
                color = OnSurfaceMuted, fontSize = 12.sp)
            Text("Push-Benachrichtigungen: native Android-API",
                color = OnSurfaceMuted, fontSize = 12.sp)
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = Cyan, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
fun InfoChip(label: String) {
    Surface(color = SurfaceVariant, shape = RoundedCornerShape(8.dp)) {
        Text(label, color = OnSurfaceMuted, fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}
