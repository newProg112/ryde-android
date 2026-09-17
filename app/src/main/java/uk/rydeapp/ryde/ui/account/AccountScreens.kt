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
import uk.rydeapp.ryde.data.connected.ConnectedJourney
import uk.rydeapp.ryde.data.connected.ConnectedJourneySnapshot
import uk.rydeapp.ryde.data.connected.ConnectedConfirmedTrip
import uk.rydeapp.ryde.data.connected.ConnectedRequestStatus
import uk.rydeapp.ryde.data.connected.ConnectedRydeRepository
import uk.rydeapp.ryde.data.connected.ConnectedSeatRequest
import uk.rydeapp.ryde.data.connected.ConnectedJourneyStatus
import uk.rydeapp.ryde.data.connected.ConnectedJourneyLifecycle
import uk.rydeapp.ryde.data.connected.ConnectedTripLifecycle
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
    modifier: Modifier = Modifier,
    onOpenJourneyLab: (() -> Unit)? = null,
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
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Your Ryde profile", style = MaterialTheme.typography.headlineSmall)
        Text("Connected to local Firebase emulators", color = MaterialTheme.colorScheme.primary)
        onOpenJourneyLab?.let { openLab ->
            OutlinedButton(onClick = openLab, enabled = !busy) { Text("Journey Lab") }
            Text("Development and testing tools", style = MaterialTheme.typography.bodySmall)
        }
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
    YOUR_REQUESTS("Your requests"),
    TRIPS("Trips"),
    YOUR_OFFERS("Your offers"),
    INCOMING("Incoming requests"),
}

internal const val CONNECTED_TRIPS_EMPTY_STATE =
    "No confirmed trips yet. A trip appears when a driver accepts a seat request."

internal fun connectedSeatAvailabilityLabel(seatsRemaining: Int, seatCapacity: Int): String =
    "$seatsRemaining/$seatCapacity seats remaining"

internal fun connectedRequestStatusLabel(status: ConnectedRequestStatus): String =
    "Status: ${status.name}"

internal fun connectedRequestStatusLabel(request: ConnectedSeatRequest, journey: ConnectedJourney?): String =
    if (ConnectedJourneyLifecycle.requestCancelledByDriver(request, journey)) {
        "Status: CANCELLED_BY_DRIVER · Journey cancelled by driver"
    } else if (!ConnectedJourneyLifecycle.requestJourneyOpen(request, journey) && request.status in listOf(ConnectedRequestStatus.PENDING, ConnectedRequestStatus.ACCEPTED)) {
        "Status: UNAVAILABLE"
    } else connectedRequestStatusLabel(request.status)

internal fun connectedTripRouteLabel(trip: ConnectedConfirmedTrip): String =
    "${trip.originArea} → ${trip.destinationArea}"

internal fun connectedTripStatusLabel(trip: ConnectedConfirmedTrip, journey: ConnectedJourney? = null): String = when (ConnectedJourneyLifecycle.trip(trip, journey)) {
    ConnectedTripLifecycle.CONFIRMED -> "Status: CONFIRMED"
    ConnectedTripLifecycle.CANCELLED_BY_RIDER -> "Status: CANCELLED_BY_RIDER · Cancelled by rider"
    ConnectedTripLifecycle.CANCELLED_BY_DRIVER -> "Status: CANCELLED_BY_DRIVER · Journey cancelled by driver"
    ConnectedTripLifecycle.UNAVAILABLE -> "Status: UNAVAILABLE"
}

internal fun canCancelConnectedConfirmedSeat(
    trip: ConnectedConfirmedTrip,
    viewerUid: String,
    nowEpochMillis: Long = System.currentTimeMillis(),
    journey: ConnectedJourney? = null,
): Boolean = trip.riderUid == viewerUid && ConnectedJourneyLifecycle.trip(trip, journey) == ConnectedTripLifecycle.CONFIRMED &&
    trip.departureEpochMillis > nowEpochMillis

internal fun connectedTripRoleLabel(trip: ConnectedConfirmedTrip, viewerUid: String, journey: ConnectedJourney? = null): String? = when (viewerUid) {
    trip.driverUid -> if (ConnectedJourneyLifecycle.trip(trip, journey) == ConnectedTripLifecycle.CONFIRMED) "You're driving" else "Driver"
    trip.riderUid -> if (ConnectedJourneyLifecycle.trip(trip, journey) == ConnectedTripLifecycle.CONFIRMED) "You're riding" else "Rider"
    else -> null
}

internal fun connectedTripsForParticipant(
    trips: List<ConnectedConfirmedTrip>,
    viewerUid: String,
): List<ConnectedConfirmedTrip> = trips
    .filter { it.driverUid == viewerUid || it.riderUid == viewerUid }
    .sortedBy { it.departureEpochMillis }

internal data class ConnectedRiderRequestItem(
    val request: ConnectedSeatRequest,
    val journey: ConnectedJourney,
)

internal fun connectedRiderRequestItems(
    snapshot: ConnectedJourneySnapshot,
    riderUid: String,
): List<ConnectedRiderRequestItem> = snapshot.requests
    .asSequence()
    .filter { it.riderUid == riderUid }
    .mapNotNull { request ->
        snapshot.journeys.firstOrNull { it.id == request.journeyId }
            ?.let { journey -> ConnectedRiderRequestItem(request, journey) }
    }
    .sortedBy { it.journey.departureEpochMillis }
    .toList()

internal fun canRerequestConnectedSeat(
    item: ConnectedRiderRequestItem,
    nowEpochMillis: Long = System.currentTimeMillis(),
): Boolean = item.request.status == ConnectedRequestStatus.CANCELLED &&
    ConnectedJourneyLifecycle.requestJourneyOpen(item.request, item.journey) &&
    item.journey.departureEpochMillis > nowEpochMillis &&
    item.journey.seatsRemaining > 0

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
internal fun ConnectedJourneyScreen(
    session: AccountSession.Authenticated,
    profile: ProfileContent,
    repository: ConnectedRydeRepository,
    onSave: suspend (String, String, String) -> AccountCommandResult?,
    onSignOut: suspend () -> AccountCommandResult?,
    onRefresh: () -> Unit,
    initialSection: ConnectedJourneySection = ConnectedJourneySection.PROFILE,
    onBusyChanged: (Boolean) -> Unit = {},
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
    var requestToCancelId by rememberSaveable { mutableStateOf<String?>(null) }
    var seats by rememberSaveable { mutableStateOf("1") }
    var selectedSection by rememberSaveable(session.accountId, initialSection) { mutableStateOf(initialSection) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val mine = snapshot.journeys.filter { it.driverUid == session.accountId }
    val requestByJourney = snapshot.requests.filter { it.riderUid == session.accountId }.associateBy { it.journeyId }
    val riderRequests = connectedRiderRequestItems(snapshot, session.accountId)
    val confirmedTrips = connectedTripsForParticipant(snapshot.confirmedTrips, session.accountId)
    val discoverable = snapshot.journeys.filter {
        ConnectedJourneyLifecycle.discoverable(it, session.accountId, System.currentTimeMillis())
    }
    val incoming = snapshot.requests.filter { it.driverUid == session.accountId }
    val departureSelection = ConnectedDepartureSelection(departureDateEpochDay, departureMinuteOfDay)
    val departureIsFuture = isConnectedDepartureFuture(departureSelection)

    fun runCommand(action: suspend () -> Any?) {
        busy = true
        onBusyChanged(true)
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
                onBusyChanged(false)
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
                    ConnectedJourneySection.YOUR_REQUESTS -> riderRequests.size
                    ConnectedJourneySection.TRIPS -> confirmedTrips.size
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

            ConnectedJourneySection.YOUR_REQUESTS -> ConnectedSection(modifier = Modifier.weight(1f)) {
                Text("Your requests", style = MaterialTheme.typography.titleLarge)
                if (riderRequests.isEmpty()) {
                    Text("No connected seat requests yet. Request a seat from Discover offers.")
                }
                riderRequests.forEach { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                "${item.journey.originArea} → ${item.journey.destinationArea}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(formatDeparture(item.journey.departureEpochMillis))
                            Text(
                                connectedRequestStatusLabel(item.request, item.journey),
                                fontWeight = FontWeight.Bold,
                            )
                            when {
                                item.journey.status == ConnectedJourneyStatus.CANCELLED -> {
                                    Text("This journey was cancelled and cannot be booked again.")
                                }
                                item.request.status == ConnectedRequestStatus.PENDING -> {
                                    OutlinedButton(
                                        enabled = !busy,
                                        onClick = { requestToCancelId = item.request.id },
                                    ) { Text("Cancel request") }
                                }
                                canRerequestConnectedSeat(item) -> {
                                    Button(
                                        enabled = !busy,
                                        onClick = { runCommand { repository.requestConnectedSeat(item.journey.id) } },
                                    ) { Text("Re-request seat") }
                                }
                                item.request.status == ConnectedRequestStatus.CANCELLED -> {
                                    Text(
                                        if (item.journey.seatsRemaining <= 0) {
                                            "Re-request unavailable because no seats remain."
                                        } else {
                                            "Re-request unavailable because this departure has passed."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
                ConnectedUnavailableNote()
            }

            ConnectedJourneySection.TRIPS -> ConnectedTripsSection(
                trips = confirmedTrips,
                viewerUid = session.accountId,
                modifier = Modifier.weight(1f),
                busy = busy,
                onCancelSeat = { tripId -> runCommand { repository.cancelConnectedConfirmedSeat(tripId) } },
                journeys = snapshot.journeys,
            )

            ConnectedJourneySection.YOUR_OFFERS -> ConnectedOffersSection(
                journeys = mine,
                viewerUid = session.accountId,
                modifier = Modifier.weight(1f),
                busy = busy,
                onCancelJourney = { journeyId -> runCommand { repository.cancelConnectedJourney(journeyId) } },
            )

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
                                    connectedJourneyAvailabilityLabel(it),
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(connectedRequestStatusLabel(request, journey), fontWeight = FontWeight.Bold)
                            if (request.status == ConnectedRequestStatus.PENDING && ConnectedJourneyLifecycle.requestJourneyOpen(request, journey)) {
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

    val requestToCancel = riderRequests.firstOrNull {
        it.request.id == requestToCancelId && it.request.status == ConnectedRequestStatus.PENDING &&
            ConnectedJourneyLifecycle.requestJourneyOpen(it.request, it.journey)
    }
    if (requestToCancel != null) {
        AlertDialog(
            onDismissRequest = { requestToCancelId = null },
            title = { Text("Cancel seat request?") },
            text = {
                Text(
                    "Cancel your request for ${requestToCancel.journey.originArea} to " +
                        "${requestToCancel.journey.destinationArea}? You can re-request later only while " +
                        "the journey is still upcoming and has a seat available.",
                )
            },
            confirmButton = {
                Button(
                    enabled = !busy,
                    onClick = {
                        requestToCancelId = null
                        runCommand { repository.cancelConnectedRequest(requestToCancel.request.id) }
                    },
                ) { Text("Cancel request") }
            },
            dismissButton = {
                OutlinedButton(onClick = { requestToCancelId = null }) { Text("Keep request") }
            },
        )
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
internal fun ConnectedTripsSection(
    trips: List<ConnectedConfirmedTrip>,
    viewerUid: String,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    onCancelSeat: ((String) -> Unit)? = null,
    journeys: List<ConnectedJourney> = emptyList(),
) {
    var tripToCancelId by rememberSaveable(viewerUid) { mutableStateOf<String?>(null) }
    ConnectedSection(modifier = modifier) {
        Text("Trips", style = MaterialTheme.typography.titleLarge)
        if (trips.isEmpty()) Text(CONNECTED_TRIPS_EMPTY_STATE)
        trips.forEach { trip ->
            val journey = journeys.firstOrNull { it.id == trip.journeyId }
            val role = connectedTripRoleLabel(trip, viewerUid, journey) ?: return@forEach
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(connectedTripRouteLabel(trip), style = MaterialTheme.typography.titleMedium)
                    Text(formatDeparture(trip.departureEpochMillis))
                    Text(connectedTripStatusLabel(trip, journey), fontWeight = FontWeight.Bold)
                    Text(role)
                    if (onCancelSeat != null && canCancelConnectedConfirmedSeat(trip, viewerUid, journey = journey)) {
                        OutlinedButton(enabled = !busy, onClick = { tripToCancelId = trip.id }) {
                            Text("Cancel my seat")
                        }
                    }
                }
            }
        }
        ConnectedUnavailableNote()
    }
    val tripToCancel = trips.firstOrNull {
        it.id == tripToCancelId && canCancelConnectedConfirmedSeat(it, viewerUid,
            journey = journeys.firstOrNull { journey -> journey.id == it.journeyId })
    }
    if (tripToCancel != null && onCancelSeat != null) {
        AlertDialog(
            onDismissRequest = { tripToCancelId = null },
            title = { Text("Cancel your confirmed seat?") },
            text = { Text("Your seat will be returned to the journey. This booking cannot be reopened.") },
            confirmButton = {
                Button(enabled = !busy, onClick = {
                    tripToCancelId = null
                    onCancelSeat(tripToCancel.id)
                }) { Text("Confirm cancellation") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { tripToCancelId = null }) { Text("Keep my seat") }
            },
        )
    }
}

internal fun connectedJourneyAvailabilityLabel(journey: ConnectedJourney): String =
    if (journey.status == ConnectedJourneyStatus.CANCELLED) {
        "Historical capacity: ${journey.seatsRemaining}/${journey.seatCapacity} · Unavailable for booking"
    } else connectedSeatAvailabilityLabel(journey.seatsRemaining, journey.seatCapacity)

@Composable
internal fun ConnectedOffersSection(
    journeys: List<ConnectedJourney>,
    viewerUid: String,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    onCancelJourney: ((String) -> Unit)? = null,
) {
    var journeyToCancelId by rememberSaveable(viewerUid) { mutableStateOf<String?>(null) }
    val mine = journeys.filter { it.driverUid == viewerUid }
    ConnectedSection(modifier = modifier) {
        Text("Your offers", style = MaterialTheme.typography.titleLarge)
        if (mine.isEmpty()) Text("No connected offers yet.")
        mine.forEach { journey ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${journey.originArea} → ${journey.destinationArea}", style = MaterialTheme.typography.titleMedium)
                    Text(formatDeparture(journey.departureEpochMillis))
                    Text("Status: ${journey.status.name}", fontWeight = FontWeight.Bold)
                    Text(connectedJourneyAvailabilityLabel(journey))
                    if (onCancelJourney != null && ConnectedJourneyLifecycle.canCancelJourney(journey, viewerUid, System.currentTimeMillis())) {
                        OutlinedButton(enabled = !busy, onClick = { journeyToCancelId = journey.id }) { Text("Cancel journey") }
                    }
                }
            }
        }
        ConnectedUnavailableNote()
    }
    val toCancel = mine.firstOrNull {
        it.id == journeyToCancelId && ConnectedJourneyLifecycle.canCancelJourney(it, viewerUid, System.currentTimeMillis())
    }
    if (toCancel != null && onCancelJourney != null) {
        AlertDialog(
            onDismissRequest = { journeyToCancelId = null },
            title = { Text("Cancel this journey?") },
            text = { Text("This cancels the whole journey for all confirmed riders and pending requests. It cannot be undone.") },
            confirmButton = {
                Button(enabled = !busy, onClick = {
                    journeyToCancelId = null
                    onCancelJourney(toCancel.id)
                }) { Text("Confirm journey cancellation") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { journeyToCancelId = null }) { Text("Keep journey") }
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
        "Unavailable here: payments, GPS/maps, messaging, notifications, Circles, trust/ratings and journey completion.",
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
