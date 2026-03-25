package com.bahnwatcher.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.bahnwatcher.ui.theme.*
import com.bahnwatcher.ui.viewmodel.MainViewModel
import com.bahnwatcher.worker.MonitoringWorker

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var testSent by remember { mutableStateOf(false) }

    // Notification permission state (Android 13+)
    var hasNotifPerm by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                        PermissionChecker.PERMISSION_GRANTED
            else true
        )
    }
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasNotifPerm = granted }

    // Battery optimization exemption state
    val powerManager = context.getSystemService(PowerManager::class.java)
    var isBatteryExempt by remember {
        mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false)
    }

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

        // ---- Monitoring ----
        SettingsSection(title = "Auto-Monitoring") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Im Hintergrund prüfen", color = OnSurface)
                    Text(
                        "Alle ~15 min · auch wenn App geschlossen",
                        color = OnSurfaceMuted, fontSize = 12.sp
                    )
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

        // ---- Benachrichtigungen & Systemberechtigungen ----
        SettingsSection(title = "Benachrichtigungen & Hintergrund") {

            // Notification permission row (only relevant on Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionRow(
                    icon = if (hasNotifPerm) Icons.Default.NotificationsActive
                           else Icons.Default.NotificationsOff,
                    iconTint = if (hasNotifPerm) Success else Error,
                    title = "Benachrichtigungen",
                    subtitle = if (hasNotifPerm) "Erlaubt" else "Verweigert – Benachrichtigungen funktionieren nicht",
                    buttonLabel = if (hasNotifPerm) null else "Erlauben",
                    onButtonClick = {
                        notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                )
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = Border)
                Spacer(Modifier.height(10.dp))
            }

            // Battery optimization row
            PermissionRow(
                icon = if (isBatteryExempt) Icons.Default.BatteryFull else Icons.Default.BatterySaver,
                iconTint = if (isBatteryExempt) Success else Warning,
                title = "Akkuoptimierung",
                subtitle = if (isBatteryExempt)
                    "Deaktiviert – Monitoring läuft zuverlässig"
                else
                    "Aktiv – Android kann Hintergrundprüfungen verzögern oder überspringen",
                buttonLabel = if (isBatteryExempt) null else "Deaktivieren",
                onButtonClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                    // Re-read state after returning (user may have changed it)
                    isBatteryExempt =
                        powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
                }
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Border)
            Spacer(Modifier.height(10.dp))

            // Test notification
            OutlinedButton(
                onClick = {
                    MonitoringWorker.sendNotification(
                        context,
                        "BahnWatcher Test",
                        "Benachrichtigungen funktionieren im Hintergrund!"
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
                Text("Test gesendet! Sieh in der Benachrichtigungsleiste nach.",
                    color = Success, fontSize = 12.sp)
            }
        }

        // ---- Datenverwaltung ----
        SettingsSection(title = "Datenverwaltung") {
            Text(
                "Alle Daten werden lokal auf diesem Gerät gespeichert. " +
                "Es werden keine Daten an Dritte weitergegeben (außer API-Anfragen an transport.rest).",
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

        // ---- Über ----
        SettingsSection(title = "Über") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip("Version 1.0")
                InfoChip("transport.rest")
                InfoChip("WorkManager")
            }
            Spacer(Modifier.height(8.dp))
            Text("Echtzeit-Bahndaten: transport.rest (FOSS)",
                color = OnSurfaceMuted, fontSize = 12.sp)
            Text("Hintergrund-Job: Android WorkManager (15 min Intervall)",
                color = OnSurfaceMuted, fontSize = 12.sp)
            Text("Push: native Android-Benachrichtigungen (kein externer Dienst)",
                color = OnSurfaceMuted, fontSize = 12.sp)
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun PermissionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    buttonLabel: String?,
    onButtonClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = OnSurfaceMuted, fontSize = 12.sp, lineHeight = 16.sp)
        }
        if (buttonLabel != null) {
            OutlinedButton(
                onClick = onButtonClick,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(buttonLabel, fontSize = 12.sp)
            }
        }
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
