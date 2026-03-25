package com.bahnwatcher

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bahnwatcher.ui.screens.*
import com.bahnwatcher.ui.theme.*
import com.bahnwatcher.ui.viewmodel.MainViewModel
import com.bahnwatcher.worker.MonitoringWorker
import kotlinx.coroutines.launch

data class NavItem(val label: String, val icon: ImageVector, val route: String)

val navItems = listOf(
    NavItem("Favoriten", Icons.Default.Star, "favorites"),
    NavItem("Suche", Icons.Default.Search, "search"),
    NavItem("Alternativen", Icons.Default.DirectionsWalk, "alternatives"),
    NavItem("Einstellungen", Icons.Default.Settings, "settings")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            com.bahnwatcher.ui.theme.BahnWatcherTheme {
                BahnWatcherApp()
            }
        }
    }
}

@Composable
fun BahnWatcherApp() {
    val vm: MainViewModel = viewModel()
    val settings by vm.settings.collectAsState()
    var selectedRoute by remember { mutableStateOf("favorites") }
    val context = LocalContext.current

    // Request POST_NOTIFICATIONS permission on Android 13+ at first launch.
    // We launch this once; the result is handled silently – the SettingsScreen
    // shows the current state and lets the user re-grant if denied.
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* permission result visible in SettingsScreen */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(settings.monitoring) {
        if (settings.monitoring) MonitoringWorker.schedule(context)
        else MonitoringWorker.cancel(context)
    }

    if (!settings.consentGiven) {
        ConsentScreen(vm = vm)
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceDark,
                contentColor = OnSurface
            ) {
                navItems.forEach { item ->
                    NavigationBarItem(
                        selected = selectedRoute == item.route,
                        onClick = { selectedRoute = item.route },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Cyan,
                            selectedTextColor = Cyan,
                            indicatorColor = Cyan.copy(alpha = 0.15f),
                            unselectedIconColor = OnSurfaceMuted,
                            unselectedTextColor = OnSurfaceMuted
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedRoute) {
                "favorites" -> FavoritesScreen(vm = vm)
                "search" -> SearchScreen(vm = vm)
                "alternatives" -> AlternativesScreen(vm = vm)
                "settings" -> SettingsScreen(vm = vm)
            }
        }
    }
}

@Composable
fun ConsentScreen(vm: MainViewModel) {
    var gpsChecked by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = {},
        containerColor = SurfaceDark,
        title = {
            Text("Datenschutz & Einwilligung", color = OnSurface, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "BahnWatcher ruft Echtzeitdaten der Deutschen Bahn über die öffentliche " +
                    "transport.rest API ab. Favoriten und Einstellungen werden nur lokal auf " +
                    "diesem Gerät gespeichert.",
                    color = OnSurfaceMuted, fontSize = 13.sp, lineHeight = 18.sp
                )

                HorizontalDivider(color = Border)

                ConsentItem(
                    checked = true,
                    enabled = false,
                    title = "Pflicht: transport.rest API & lokale Speicherung",
                    subtitle = "Erforderlich für den Betrieb der App",
                    onCheckedChange = {}
                )

                ConsentItem(
                    checked = true,
                    enabled = false,
                    title = "Pflicht: Android-Benachrichtigungen",
                    subtitle = "Für Zugstatus-Alerts bei aktivem Monitoring",
                    onCheckedChange = {}
                )

                ConsentItem(
                    checked = gpsChecked,
                    title = "Optional: GPS / Standort",
                    subtitle = "Für die Alternativen-Suche (Haltestellen in der Nähe)",
                    onCheckedChange = { gpsChecked = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        vm.repo.updateConsent(given = true, gps = gpsChecked)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = BackgroundDark)
            ) {
                Text("Zustimmen & App starten", fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Composable
fun ConsentItem(
    checked: Boolean,
    enabled: Boolean = true,
    title: String,
    subtitle: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            colors = CheckboxDefaults.colors(
                checkedColor = Cyan,
                disabledCheckedColor = Cyan
            )
        )
        Column {
            Text(title, color = OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = OnSurfaceMuted, fontSize = 12.sp)
        }
    }
}
