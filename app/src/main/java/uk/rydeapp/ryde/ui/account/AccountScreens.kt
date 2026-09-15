package uk.rydeapp.ryde.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uk.rydeapp.ryde.data.AccountCommandResult
import uk.rydeapp.ryde.data.AccountSession
import uk.rydeapp.ryde.domain.model.ProfileContent
import uk.rydeapp.ryde.data.connected.ConnectedJourneyCommandResult
import uk.rydeapp.ryde.data.connected.ConnectedRequestStatus
import uk.rydeapp.ryde.data.connected.ConnectedRydeRepository
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

@Composable
fun SignedOutAccountScreen(
    onRegister: suspend (String, String, String) -> AccountCommandResult?,
    onSignIn: suspend (String, String) -> AccountCommandResult?,
) {
    var creatingAccount by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Ryde connected account", style = MaterialTheme.typography.headlineSmall)
        Text("Local Firebase emulators only. No production account or data is used.")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (creatingAccount) {
                OutlinedButton(onClick = { creatingAccount = false; message = null }) { Text("Sign in") }
                Button(onClick = {}, enabled = false) { Text("Create account") }
            } else {
                Button(onClick = {}, enabled = false) { Text("Sign in") }
                OutlinedButton(onClick = { creatingAccount = true; message = null }) { Text("Create account") }
            }
        }
        if (creatingAccount) {
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Use at least 8 characters. Emulator accounts are disposable local test data.", style = MaterialTheme.typography.bodySmall)
        Button(
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                busy = true
                message = null
                scope.launch {
                    try {
                        val result = if (creatingAccount) {
                            onRegister(email, password, displayName)
                        } else {
                            onSignIn(email, password)
                        }
                        message = result.userMessageOrNull()
                    } finally {
                        busy = false
                    }
                }
            },
        ) { Text(if (busy) "Please wait…" else if (creatingAccount) "Create local account" else "Sign in") }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
fun ConnectedProfileScreen(
    session: AccountSession.Authenticated,
    profile: ProfileContent,
    onSave: suspend (String, String, String) -> AccountCommandResult?,
    onSignOut: suspend () -> AccountCommandResult?,
) {
    val initialHome = profile.savedPlaces.firstOrNull { it.label == "Home" }?.area.orEmpty()
    val initialWork = profile.savedPlaces.firstOrNull { it.label == "Work" }?.area.orEmpty()
    var displayName by rememberSaveable(session.accountId, session.displayName) { mutableStateOf(session.displayName) }
    var homeArea by rememberSaveable(session.accountId, initialHome) { mutableStateOf(initialHome) }
    var workArea by rememberSaveable(session.accountId, initialWork) { mutableStateOf(initialWork) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Your Ryde profile", style = MaterialTheme.typography.headlineSmall)
        Text("Connected to local Firebase emulators", color = MaterialTheme.colorScheme.primary)
        OutlinedTextField(displayName, { displayName = it }, label = { Text("Display name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(homeArea, { homeArea = it }, label = { Text("Home broad area") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(workArea, { workArea = it }, label = { Text("Work broad area") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text(
            "Use town, district or broad-area names only. Never enter a street address, postcode, exact location or live location.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                busy = true
                message = null
                scope.launch {
                    try {
                        val result = onSave(displayName, homeArea, workArea)
                        message = when (result) {
                            AccountCommandResult.Success -> "Profile saved to the local emulator."
                            else -> result.userMessageOrNull()
                        }
                    } finally {
                        busy = false
                    }
                }
            },
        ) { Text(if (busy) "Saving…" else "Save profile") }
        OutlinedButton(
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                busy = true
                message = null
                scope.launch {
                    try {
                        message = onSignOut().userMessageOrNull()
                    } finally {
                        busy = false
                    }
                }
            },
        ) { Text("Sign out") }
        message?.let {
            Text(it, color = if (it.startsWith("Profile saved")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        }
    }
}

internal enum class ConnectedJourneySection(val label: String) {
    PROFILE("Profile"),
    OFFER("Offer a journey"),
    DISCOVER("Discover offers"),
    YOUR_OFFERS("Your offers"),
    INCOMING("Incoming requests"),
}

internal fun connectedSeatAvailabilityLabel(seatsRemaining: Int, seatCapacity: Int): String =
    "$seatsRemaining/$seatCapacity seats remaining"

internal fun connectedRequestStatusLabel(status: ConnectedRequestStatus): String =
    "Status: ${status.name}"

internal data class ConnectedDepartureSelection(
    val dateEpochDay: Long,
    val minuteOfDay: Int,
)

private val connectedDepartureContractFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val connectedDepartureDisplayFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy 'at' HH:mm", Locale.UK)

internal fun defaultConnectedDeparture(
    nowEpochMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): ConnectedDepartureSelection = ConnectedDepartureSelection(
    dateEpochDay = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId).toLocalDate().plusDays(1).toEpochDay(),
    minuteOfDay = 9 * 60,
)

internal fun formatConnectedDepartureForSubmission(selection: ConnectedDepartureSelection): String =
    selection.toLocalDateTime().format(connectedDepartureContractFormatter)

internal fun formatConnectedDepartureForDisplay(selection: ConnectedDepartureSelection): String =
    selection.toLocalDateTime().format(connectedDepartureDisplayFormatter)

internal fun isConnectedDepartureFuture(
    selection: ConnectedDepartureSelection,
    nowEpochMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): Boolean = selection.toLocalDateTime().atZone(zoneId).toInstant().toEpochMilli() > nowEpochMillis

private fun ConnectedDepartureSelection.toLocalDateTime() =
    LocalDate.ofEpochDay(dateEpochDay).atTime(minuteOfDay / 60, minuteOfDay % 60)

private fun datePickerUtcMillis(dateEpochDay: Long): Long =
    LocalDate.ofEpochDay(dateEpochDay).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun datePickerEpochDay(utcMillis: Long): Long =
    Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectedJourneyScreen(
    session: AccountSession.Authenticated,
    profile: ProfileContent,
    repository: ConnectedRydeRepository,
    onSave: suspend (String, String, String) -> AccountCommandResult?,
    onSignOut: suspend () -> AccountCommandResult?,
    onRefresh: () -> Unit,
) {
    val snapshot by repository.journeyState.collectAsState()
    val initialHome = profile.savedPlaces.firstOrNull { it.label == "Home" }?.area.orEmpty()
    val initialWork = profile.savedPlaces.firstOrNull { it.label == "Work" }?.area.orEmpty()
    var displayName by rememberSaveable(session.accountId, session.displayName) { mutableStateOf(session.displayName) }
    var homeArea by rememberSaveable(session.accountId, initialHome) { mutableStateOf(initialHome) }
    var workArea by rememberSaveable(session.accountId, initialWork) { mutableStateOf(initialWork) }
    var origin by rememberSaveable { mutableStateOf("") }
    var destination by rememberSaveable { mutableStateOf("") }
    val defaultDeparture = remember(session.accountId) { defaultConnectedDeparture() }
    var departureDateEpochDay by rememberSaveable(session.accountId) { mutableLongStateOf(defaultDeparture.dateEpochDay) }
    var departureMinuteOfDay by rememberSaveable(session.accountId) { mutableIntStateOf(defaultDeparture.minuteOfDay) }
    var showDepartureDatePicker by rememberSaveable { mutableStateOf(false) }
    var showDepartureTimePicker by rememberSaveable { mutableStateOf(false) }
    var seats by rememberSaveable { mutableStateOf("1") }
    var selectedSection by rememberSaveable { mutableStateOf(ConnectedJourneySection.PROFILE) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val mine = snapshot.journeys.filter { it.driverUid == session.accountId }
    val requestByJourney = snapshot.requests.filter { it.riderUid == session.accountId }.associateBy { it.journeyId }
    val discoverable = snapshot.journeys.filter {
        it.driverUid != session.accountId && it.departureEpochMillis > System.currentTimeMillis()
    }
    val incoming = snapshot.requests.filter { it.driverUid == session.accountId }
    val departureSelection = ConnectedDepartureSelection(departureDateEpochDay, departureMinuteOfDay)
    val departureIsFuture = isConnectedDepartureFuture(departureSelection)

    fun runCommand(action: suspend () -> Any?) {
        busy = true
        message = null
        scope.launch {
            try {
                message = when (val result = action()) {
                    AccountCommandResult.Success, ConnectedJourneyCommandResult.Success -> "Saved to the local emulators."
                    is AccountCommandResult.InvalidInput -> result.userMessage
                    is AccountCommandResult.Failure -> result.userMessage
                    is ConnectedJourneyCommandResult.InvalidInput -> result.userMessage
                    is ConnectedJourneyCommandResult.Failure -> result.userMessage
                    else -> "Refreshed from the local emulators."
                }
            } finally {
                busy = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Ryde journey lab", style = MaterialTheme.typography.headlineSmall)
            Text("EMULATOR-ONLY · disposable Auth and Firestore data", color = MaterialTheme.colorScheme.primary)
            Text("Signed in as ${session.displayName}. Connected mode never shows fictional people, ratings, trust, pricing or Circles.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        onRefresh()
                        message = "Refreshing from the local emulators…"
                    },
                ) { Text("Refresh") }
                OutlinedButton(enabled = !busy, onClick = { runCommand { onSignOut() } }) { Text("Sign out") }
            }
            message?.let {
                Text(
                    it,
                    color = if (it.contains("couldn't") || it.startsWith("Use") || it.startsWith("Enter")) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        }

        PrimaryScrollableTabRow(selectedTabIndex = selectedSection.ordinal, edgePadding = 12.dp) {
            ConnectedJourneySection.entries.forEach { section ->
                val itemCount = when (section) {
                    ConnectedJourneySection.DISCOVER -> discoverable.size
                    ConnectedJourneySection.YOUR_OFFERS -> mine.size
                    ConnectedJourneySection.INCOMING -> incoming.size
                    else -> null
                }
                Tab(
                    selected = selectedSection == section,
                    onClick = { selectedSection = section },
                    text = { Text(itemCount?.let { "${section.label} ($it)" } ?: section.label) },
                )
            }
        }

        when (selectedSection) {
            ConnectedJourneySection.PROFILE -> ConnectedSection(modifier = Modifier.weight(1f)) {
                Text("Profile", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(displayName, { displayName = it }, label = { Text("Display name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(homeArea, { homeArea = it }, label = { Text("Home broad area") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(workArea, { workArea = it }, label = { Text("Work broad area") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Never enter a street address, postcode, exact location or live location.", style = MaterialTheme.typography.bodySmall)
                Button(
                    enabled = !busy,
                    onClick = { runCommand { onSave(displayName, homeArea, workArea) } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save profile") }
                ConnectedUnavailableNote()
            }

            ConnectedJourneySection.OFFER -> ConnectedSection(modifier = Modifier.weight(1f)) {
                Text("Offer a journey", style = MaterialTheme.typography.titleLarge)
                Text("Use broad areas only. Do not enter an address, postcode, exact location or live location.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(origin, { origin = it }, label = { Text("Origin broad area") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(destination, { destination = it }, label = { Text("Destination broad area") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Departure", style = MaterialTheme.typography.titleMedium)
                Text(formatConnectedDepartureForDisplay(departureSelection), fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showDepartureDatePicker = true },
                        modifier = Modifier.weight(1f),
                    ) { Text("Choose date") }
                    OutlinedButton(
                        onClick = { showDepartureTimePicker = true },
                        modifier = Modifier.weight(1f),
                    ) { Text("Choose time") }
                }
                if (!departureIsFuture) {
                    Text(
                        "Choose a departure in the future.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedTextField(seats, { seats = it }, label = { Text("Seats (1–8)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Button(
                    enabled = !busy && departureIsFuture,
                    onClick = {
                        runCommand {
                            repository.createConnectedJourney(
                                origin,
                                destination,
                                formatConnectedDepartureForSubmission(departureSelection),
                                seats,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Create emulator offer") }
                ConnectedUnavailableNote()
            }

            ConnectedJourneySection.DISCOVER -> ConnectedSection(modifier = Modifier.weight(1f)) {
                Text("Discover offers", style = MaterialTheme.typography.titleLarge)
                if (discoverable.isEmpty()) {
                    Text("No offers from other emulator accounts. Tap Refresh after the driver creates one.")
                }
                discoverable.forEach { journey ->
                    val request = requestByJourney[journey.id]
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("${journey.originArea} → ${journey.destinationArea}", style = MaterialTheme.typography.titleMedium)
                            Text(formatDeparture(journey.departureEpochMillis))
                            Text(
                                connectedSeatAvailabilityLabel(journey.seatsRemaining, journey.seatCapacity),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (request == null && journey.seatsRemaining > 0) {
                                Button(
                                    enabled = !busy,
                                    onClick = { runCommand { repository.requestConnectedSeat(journey.id) } },
                                ) { Text("Request one seat") }
                            } else {
                                Text(
                                    request?.let { "Your request · ${connectedRequestStatusLabel(it.status)}" }
                                        ?: "No seats available",
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
                ConnectedUnavailableNote()
            }

            ConnectedJourneySection.YOUR_OFFERS -> ConnectedSection(modifier = Modifier.weight(1f)) {
                Text("Your offers", style = MaterialTheme.typography.titleLarge)
                if (mine.isEmpty()) Text("No connected offers yet.")
                mine.forEach { journey ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("${journey.originArea} → ${journey.destinationArea}", style = MaterialTheme.typography.titleMedium)
                            Text(formatDeparture(journey.departureEpochMillis))
                            Text(
                                connectedSeatAvailabilityLabel(journey.seatsRemaining, journey.seatCapacity),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                ConnectedUnavailableNote()
            }

            ConnectedJourneySection.INCOMING -> ConnectedSection(modifier = Modifier.weight(1f)) {
                Text("Incoming requests", style = MaterialTheme.typography.titleLarge)
                if (incoming.isEmpty()) Text("No requests for your offers. Tap Refresh after the rider requests.")
                incoming.forEach { request ->
                    val journey = snapshot.journeys.firstOrNull { it.id == request.journeyId }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                "${journey?.originArea ?: "Journey"} → ${journey?.destinationArea ?: request.journeyId}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            journey?.let {
                                Text(
                                    connectedSeatAvailabilityLabel(it.seatsRemaining, it.seatCapacity),
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(connectedRequestStatusLabel(request.status), fontWeight = FontWeight.Bold)
                            if (request.status == ConnectedRequestStatus.PENDING) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        enabled = !busy,
                                        onClick = { runCommand { repository.decideConnectedRequest(request.id, true) } },
                                    ) { Text("Accept") }
                                    OutlinedButton(
                                        enabled = !busy,
                                        onClick = { runCommand { repository.decideConnectedRequest(request.id, false) } },
                                    ) { Text("Decline") }
                                }
                            }
                        }
                    }
                }
                ConnectedUnavailableNote()
            }
        }
    }

    if (showDepartureDatePicker) {
        val todayEpochDay = Instant.ofEpochMilli(System.currentTimeMillis())
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toEpochDay()
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = datePickerUtcMillis(departureDateEpochDay),
            selectableDates = remember(todayEpochDay) {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                        datePickerEpochDay(utcTimeMillis) >= todayEpochDay
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDepartureDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { departureDateEpochDay = datePickerEpochDay(it) }
                        showDepartureDatePicker = false
                    },
                    enabled = datePickerState.selectedDateMillis != null,
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDepartureDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(
                state = datePickerState,
                title = { Text("Choose departure date", modifier = Modifier.padding(24.dp)) },
            )
        }
    }

    if (showDepartureTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = departureMinuteOfDay / 60,
            initialMinute = departureMinuteOfDay % 60,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showDepartureTimePicker = false },
            title = { Text("Choose departure time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        departureMinuteOfDay = timePickerState.hour * 60 + timePickerState.minute
                        showDepartureTimePicker = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDepartureTimePicker = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ConnectedSection(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun ConnectedUnavailableNote() {
    Text(
        "Unavailable here: payments, GPS/maps, messaging, notifications, Circles, trust/ratings and journey lifecycle.",
        style = MaterialTheme.typography.bodySmall,
    )
}

private fun formatDeparture(epochMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.UK).format(Date(epochMillis))

private fun AccountCommandResult?.userMessageOrNull(): String? = when (this) {
    AccountCommandResult.Success -> null
    is AccountCommandResult.InvalidInput -> userMessage
    is AccountCommandResult.Failure -> userMessage
    null -> "Ryde couldn't complete that request. Please try again."
}
