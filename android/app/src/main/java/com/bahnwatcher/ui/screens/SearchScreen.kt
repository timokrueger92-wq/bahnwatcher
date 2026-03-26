package com.bahnwatcher.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bahnwatcher.data.model.JourneyUi
import com.bahnwatcher.data.model.Leg
import com.bahnwatcher.data.model.StopLocation
import com.bahnwatcher.ui.theme.*
import com.bahnwatcher.ui.viewmodel.MainViewModel
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SearchScreen(vm: MainViewModel) {
    val fromStation by vm.fromStation.collectAsState()
    val toStation by vm.toStation.collectAsState()
    val suggestions by vm.stationSuggestions.collectAsState()
    val journeys by vm.journeys.collectAsState()
    val loading by vm.searchLoading.collectAsState()
    val loadingMore by vm.loadingMore.collectAsState()
    val error by vm.searchError.collectAsState()
    val includeLongDistance by vm.includeLongDistance.collectAsState()

    var fromQuery by remember { mutableStateOf(fromStation?.name ?: "") }
    var toQuery by remember { mutableStateOf(toStation?.name ?: "") }
    var activeField by remember { mutableStateOf<String?>(null) }
    var dateTime by remember { mutableStateOf(LocalDateTime.now()) }
    var isDeparture by remember { mutableStateOf(true) }
    var savingJourney by remember { mutableStateOf<JourneyUi?>(null) }

    LaunchedEffect(fromStation) { fromStation?.name?.let { fromQuery = it } }
    LaunchedEffect(toStation) { toStation?.name?.let { toQuery = it } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Verbindungen", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OnSurface)
            Spacer(Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    StationField(
                        label = "Von",
                        value = fromQuery,
                        icon = Icons.Default.TripOrigin,
                        onValueChange = {
                            fromQuery = it
                            activeField = "from"
                            vm.searchStations(it)
                        },
                        onClear = { fromQuery = ""; vm.clearSuggestions() }
                    )
                    HorizontalDivider(color = Border, modifier = Modifier.padding(vertical = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StationField(
                            label = "Nach",
                            value = toQuery,
                            icon = Icons.Default.Place,
                            onValueChange = {
                                toQuery = it
                                activeField = "to"
                                vm.searchStations(it)
                            },
                            onClear = { toQuery = ""; vm.clearSuggestions() },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            vm.swapStations()
                            val tmp = fromQuery; fromQuery = toQuery; toQuery = tmp
                        }) {
                            Icon(Icons.Default.SwapVert, contentDescription = "Tauschen", tint = Cyan)
                        }
                    }
                }
            }

            if (suggestions.isNotEmpty() && activeField != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    suggestions.take(6).forEach { stop ->
                        StationSuggestionItem(stop = stop, onClick = {
                            if (activeField == "from") {
                                fromQuery = stop.name ?: ""
                                vm.setFromStation(stop)
                            } else {
                                toQuery = stop.name ?: ""
                                vm.setToStation(stop)
                            }
                            activeField = null
                        })
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = isDeparture,
                    onClick = { isDeparture = true },
                    label = { Text("Abfahrt", fontSize = 13.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Cyan.copy(alpha = 0.2f),
                        selectedLabelColor = Cyan
                    )
                )
                FilterChip(
                    selected = !isDeparture,
                    onClick = { isDeparture = false },
                    label = { Text("Ankunft", fontSize = 13.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Cyan.copy(alpha = 0.2f),
                        selectedLabelColor = Cyan
                    )
                )
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Train,
                        contentDescription = null,
                        tint = if (includeLongDistance) Cyan else OnSurfaceMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text("Fernverkehr (ICE/IC/EC)", color = OnSurface, fontSize = 13.sp)
                        Text(
                            if (includeLongDistance) "wird berücksichtigt" else "wird ausgeblendet",
                            color = OnSurfaceMuted, fontSize = 11.sp
                        )
                    }
                }
                Switch(
                    checked = includeLongDistance,
                    onCheckedChange = { vm.includeLongDistance.value = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Cyan,
                        checkedTrackColor = Cyan.copy(alpha = 0.3f)
                    )
                )
            }

            Spacer(Modifier.height(8.dp))
            DateTimeRow(dateTime = dateTime, onChange = { dateTime = it })

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { vm.searchJourneys(dateTime, isDeparture) },
                enabled = fromStation != null && toStation != null && !loading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = BackgroundDark)
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = BackgroundDark)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Verbindungen suchen", fontWeight = FontWeight.SemiBold)
            }
        }

        error?.let {
            Text(it, color = Error, modifier = Modifier.padding(horizontal = 16.dp))
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(journeys) { journey ->
                JourneyCard(journey = journey, onSave = { savingJourney = journey })
            }

            if (journeys.isNotEmpty()) {
                item {
                    OutlinedButton(
                        onClick = { vm.loadMoreJourneys() },
                        enabled = !loadingMore,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan)
                    ) {
                        if (loadingMore) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Cyan)
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Default.ExpandMore, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("Mehr Verbindungen laden", fontSize = 14.sp)
                    }
                }
            }
        }
    }

    savingJourney?.let { journey ->
        SaveFavoriteDialog(
            journey = journey,
            onSave = { name, days ->
                vm.saveAsFavorite(journey, name, days)
                savingJourney = null
            },
            onDismiss = { savingJourney = null }
        )
    }
}

@Composable
fun StationField(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = OnSurfaceMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(label, color = OnSurfaceMuted) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedTextColor = OnSurface,
                unfocusedTextColor = OnSurface
            ),
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        if (value.isNotEmpty()) {
            IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Clear, contentDescription = null, tint = OnSurfaceMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun StationSuggestionItem(stop: StopLocation, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Train, contentDescription = null, tint = Cyan, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(stop.name ?: "", color = OnSurface, fontSize = 14.sp)
    }
}

@Composable
fun DateTimeRow(dateTime: LocalDateTime, onChange: (LocalDateTime) -> Unit) {
    val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy  HH:mm")
    var showPicker by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = { showPicker = true },
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(dateTime.format(fmt), fontSize = 14.sp)
    }

    if (showPicker) {
        DateTimePickerDialog(
            current = dateTime,
            onConfirm = { onChange(it); showPicker = false },
            onDismiss = { showPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(
    current: LocalDateTime,
    onConfirm: (LocalDateTime) -> Unit,
    onDismiss: () -> Unit
) {
    var showDatePicker by remember { mutableStateOf(true) }
    var hour by remember { mutableIntStateOf(current.hour) }
    var minute by remember { mutableIntStateOf(current.minute) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = current.toLocalDate()
            .atStartOfDay(ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli()
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Weiter →", color = Cyan)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Uhrzeit", color = OnSurface) },
            containerColor = SurfaceDark,
            text = {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TimeInputField(value = hour, max = 23, label = "Std", onChange = { hour = it })
                    Text(":", color = OnSurface, fontSize = 28.sp,
                        modifier = Modifier.padding(horizontal = 8.dp))
                    TimeInputField(value = minute, max = 59, label = "Min", onChange = { minute = it })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                        ?: current.toLocalDate().atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
                    val date = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                    onConfirm(date.atTime(hour, minute, 0))
                }) { Text("OK", color = Cyan) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = true }) { Text("← Datum") }
            }
        )
    }
}

@Composable
fun TimeInputField(value: Int, max: Int, label: String, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf("%02d".format(value)) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = OnSurfaceMuted, fontSize = 11.sp)
        IconButton(onClick = {
            val v = (value + 1) % (max + 1)
            onChange(v)
            text = "%02d".format(v)
        }) {
            Icon(Icons.Default.KeyboardArrowUp, null, tint = Cyan)
        }
        OutlinedTextField(
            value = text,
            onValueChange = { s ->
                val clean = s.filter { it.isDigit() }.take(2)
                text = clean
                clean.toIntOrNull()?.let { v -> if (v in 0..max) onChange(v) }
            },
            modifier = Modifier.width(72.dp),
            textStyle = TextStyle(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurface,
                textAlign = TextAlign.Center
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Cyan,
                unfocusedBorderColor = Border,
                focusedTextColor = OnSurface,
                unfocusedTextColor = OnSurface
            )
        )
        IconButton(onClick = {
            val v = if (value > 0) value - 1 else max
            onChange(v)
            text = "%02d".format(v)
        }) {
            Icon(Icons.Default.KeyboardArrowDown, null, tint = Cyan)
        }
    }
}

@Composable
fun JourneyCard(journey: JourneyUi, onSave: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (journey.cancelled) {
                Surface(color = Error.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                    Text("AUSGEFALLEN", color = Error, fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
                Spacer(Modifier.height(6.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(journey.departure, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            color = if (journey.cancelled) Error else OnSurface)
                        if (journey.departureDelay > 0) {
                            Spacer(Modifier.width(4.dp))
                            Text("+${journey.departureDelay}'", color = Warning, fontSize = 14.sp)
                        }
                    }
                    if (journey.platform.isNotEmpty()) {
                        Text("Gleis ${journey.platform}", color = Cyan, fontSize = 12.sp)
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${journey.durationMin / 60}h ${journey.durationMin % 60}min",
                        color = OnSurfaceMuted, fontSize = 12.sp)
                    Icon(Icons.Default.TrendingFlat, null, tint = OnSurfaceMuted)
                    val info = buildString {
                        if (journey.transfers > 0) append("${journey.transfers} Umst.")
                        if (journey.hasWalking) { if (isNotEmpty()) append(" · "); append("Fußweg") }
                    }
                    if (info.isNotEmpty()) Text(info, color = OnSurfaceMuted, fontSize = 11.sp)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (journey.arrivalDelay > 0) {
                            Text("+${journey.arrivalDelay}'", color = Warning, fontSize = 14.sp)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(journey.arrival, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            color = if (journey.cancelled) Error else OnSurface)
                    }
                }
            }

            // Expand/collapse hint
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Details ausblenden" else "Details anzeigen",
                    tint = OnSurfaceMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Expandable leg details
            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider(color = Border, modifier = Modifier.padding(vertical = 8.dp))
                    JourneyLegsDetail(legs = journey.legs)
                    Spacer(Modifier.height(4.dp))
                }
            }

            HorizontalDivider(color = Border)
            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onSave,
                enabled = !journey.cancelled,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan),
                modifier = Modifier.fillMaxWidth().height(36.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Icon(Icons.Default.BookmarkAdd, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Als Favorit speichern", fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun JourneyLegsDetail(legs: List<Leg>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        legs.forEachIndexed { index, leg ->
            if (leg.walking == true) {
                WalkingLegRow()
            } else {
                TrainLegRow(leg)
            }
            // Transfer time between two train legs
            if (index < legs.lastIndex) {
                val nextLeg = legs[index + 1]
                if (leg.walking != true && nextLeg.walking != true) {
                    val transferMin = minutesBetween(
                        arrival = leg.arrival ?: leg.plannedArrival,
                        departure = nextLeg.departure ?: nextLeg.plannedDeparture
                    )
                    if (transferMin != null && transferMin > 0) {
                        Row(
                            modifier = Modifier.padding(start = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.AccessTime, null,
                                tint = OnSurfaceMuted, modifier = Modifier.size(12.dp))
                            Text("Umstieg: $transferMin min",
                                color = OnSurfaceMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrainLegRow(leg: Leg) {
    val depTime = formatLegTime(leg.departure ?: leg.plannedDeparture)
    val arrTime = formatLegTime(leg.arrival ?: leg.plannedArrival)
    val delay = leg.departureDelay ?: 0

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            color = Cyan.copy(alpha = 0.15f),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.padding(top = 2.dp)
        ) {
            Text(
                leg.line?.name ?: "?",
                color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(depTime, color = OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                if (delay > 0) {
                    Spacer(Modifier.width(4.dp))
                    Text("+${delay}'", color = Warning, fontSize = 11.sp)
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    leg.origin?.name ?: "",
                    color = OnSurfaceMuted, fontSize = 12.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            if (!leg.direction.isNullOrBlank()) {
                Text("→ ${leg.direction}",
                    color = OnSurfaceMuted, fontSize = 11.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(arrTime, color = OnSurface, fontSize = 13.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    leg.destination?.name ?: "",
                    color = OnSurfaceMuted, fontSize = 12.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun WalkingLegRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Default.DirectionsWalk, null, tint = OnSurfaceMuted, modifier = Modifier.size(14.dp))
        Text("Fußweg", color = OnSurfaceMuted, fontSize = 12.sp)
    }
}

private fun formatLegTime(isoString: String?): String {
    if (isoString == null) return "?"
    return try {
        OffsetDateTime.parse(isoString).format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (e: Exception) { "?" }
}

private fun minutesBetween(arrival: String?, departure: String?): Int? {
    if (arrival == null || departure == null) return null
    return try {
        val arr = OffsetDateTime.parse(arrival)
        val dep = OffsetDateTime.parse(departure)
        java.time.Duration.between(arr, dep).toMinutes().toInt()
    } catch (e: Exception) { null }
}

@Composable
fun SaveFavoriteDialog(
    journey: JourneyUi,
    onSave: (String, List<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("${journey.from} → ${journey.to}") }
    val allDays = listOf(1, 2, 3, 4, 5)
    var selectedDays by remember { mutableStateOf(allDays.toSet()) }
    val dayLabels = listOf(1 to "Mo", 2 to "Di", 3 to "Mi", 4 to "Do", 5 to "Fr", 6 to "Sa", 0 to "So")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Als Favorit speichern") },
        containerColor = SurfaceDark,
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = OnSurface,
                        unfocusedTextColor = OnSurface,
                        focusedBorderColor = Cyan,
                        unfocusedBorderColor = Border
                    )
                )
                Spacer(Modifier.height(12.dp))
                Text("Tage überwachen:", color = OnSurfaceMuted, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                // Scrollable row so Sa/So are always reachable
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    dayLabels.forEach { (day, label) ->
                        FilterChip(
                            selected = day in selectedDays,
                            onClick = {
                                selectedDays = if (day in selectedDays)
                                    selectedDays - day else selectedDays + day
                            },
                            label = { Text(label, fontSize = 11.sp) },
                            modifier = Modifier.defaultMinSize(minWidth = 1.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Cyan.copy(alpha = 0.2f),
                                selectedLabelColor = Cyan
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, selectedDays.toList()) },
                enabled = name.isNotBlank() && selectedDays.isNotEmpty()
            ) { Text("Speichern", color = Cyan) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
