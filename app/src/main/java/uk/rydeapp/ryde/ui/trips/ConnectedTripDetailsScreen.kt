package uk.rydeapp.ryde.ui.trips

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import uk.rydeapp.ryde.R
import uk.rydeapp.ryde.data.connected.ConnectedJourneyStatus
import uk.rydeapp.ryde.ui.components.formatConnectedJourneyDeparture

/** Displays shared repository presentation and forwards commands; owns only transient confirmations. */
@Composable
internal fun ConnectedTripDetailsScreen(
    content: ConnectedTripDetailsContent,
    busy: Boolean,
    actionsEnabled: Boolean,
    message: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRequestSeat: (String) -> Unit,
    onDecideRequest: (String, Boolean) -> Unit,
    onCancelSeat: (String) -> Unit,
    onCancelJourney: (String) -> Unit,
    modifier: Modifier = Modifier,
    onWithdrawRequest: (String) -> Unit = {},
) {
    val summary = content.summary
    val journey = content.journey
    // Never save an armed destructive confirmation.
    var selectedTripId by remember { mutableStateOf<String?>(null) }
    var selectedRequestId by remember { mutableStateOf<String?>(null) }
    var selectedJourneyId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(summary?.cancellableTripId, actionsEnabled) {
        if (!actionsEnabled || selectedTripId != summary?.cancellableTripId) selectedTripId = null
    }
    LaunchedEffect(summary?.cancellableJourneyId, actionsEnabled) {
        if (!actionsEnabled || selectedJourneyId != summary?.cancellableJourneyId) selectedJourneyId = null
    }
    LaunchedEffect(summary?.cancellableRequestId, actionsEnabled) {
        if (!actionsEnabled || selectedRequestId != summary?.cancellableRequestId) selectedRequestId = null
    }
    LazyColumn(
        modifier.fillMaxSize().testTag("connected-trip-details-list"),
        contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth()) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.connected_trip_details_back)) }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onRefresh, enabled = !busy) { Text(stringResource(R.string.connected_refresh)) }
            }
            Text(stringResource(R.string.connected_trip_details_title), style = MaterialTheme.typography.headlineSmall)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            if (!actionsEnabled) Text(stringResource(R.string.connected_trips_refresh_required), color = MaterialTheme.colorScheme.error)
        }
        if (journey == null) item {
            Text(stringResource(R.string.connected_trip_details_unavailable))
        }
        item {
            // A booking uses the same persisted/history fields as Trips, including unavailable links.
            val origin = if (summary != null) summary.origin else journey?.originArea
            val destination = if (summary != null) summary.destination else journey?.destinationArea
            val departure = if (summary != null) summary.departureEpochMillis else journey?.departureEpochMillis
            Text(if (origin != null && destination != null) stringResource(R.string.connected_route, origin, destination)
                else stringResource(R.string.connected_trips_details_unavailable), style = MaterialTheme.typography.titleLarge)
            departure?.let { Text(formatConnectedJourneyDeparture(it)) }
            if (summary != null) {
                Text(stringResource(summary.roleText))
                Text(stringResource(summary.statusText), color = MaterialTheme.colorScheme.primary)
                summary.journeyStatusText?.let { Text(stringResource(it)) }
            } else if (journey != null) {
                Text(stringResource(R.string.connected_trips_rider))
                Text(stringResource(if (journey.status == ConnectedJourneyStatus.CANCELLED)
                    R.string.connected_trips_offer_cancelled else R.string.connected_trips_offer_open))
                Text(stringResource(R.string.connected_trip_details_no_request))
            }
            journey?.let { Text(stringResource(R.string.connected_seats, it.seatsRemaining, it.seatCapacity)) }
        }
        if (content.canRequest && journey != null) item {
            Button(enabled = !busy && actionsEnabled, onClick = { onRequestSeat(journey.id) }) {
                Text(stringResource(R.string.connected_request_seat))
            }
        }
        if (summary?.roleText == R.string.connected_trips_driver) {
            item {
                Text(stringResource(R.string.connected_incoming_heading), style = MaterialTheme.typography.titleMedium)
                if (summary.incoming.isEmpty()) Text(stringResource(R.string.connected_incoming_empty))
            }
            summary.incoming.forEach { request -> item(key = "incoming:${request.id}") {
                Column(Modifier.fillMaxWidth().testTag("details-incoming:${request.id}"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(request.statusText))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (request.canAccept) Button(enabled = !busy && actionsEnabled,
                            onClick = { onDecideRequest(request.id, true) }) { Text(stringResource(R.string.connected_accept)) }
                        if (request.canDecline) OutlinedButton(enabled = !busy && actionsEnabled,
                            onClick = { onDecideRequest(request.id, false) }) { Text(stringResource(R.string.connected_decline)) }
                    }
                }
            } }
        }
        summary?.cancellableTripId?.let { id -> item {
            OutlinedButton(enabled = !busy && actionsEnabled, onClick = { selectedTripId = id }) {
                Text(stringResource(R.string.connected_trips_cancel_seat))
            }
        } }
        summary?.cancellableRequestId?.let { id -> item {
            OutlinedButton(enabled = !busy && actionsEnabled, onClick = { selectedRequestId = id }) {
                Text(stringResource(R.string.connected_withdraw_request))
            }
        } }
        summary?.cancellableJourneyId?.let { id -> item {
            OutlinedButton(enabled = !busy && actionsEnabled, onClick = { selectedJourneyId = id }) {
                Text(stringResource(R.string.connected_cancel_journey))
            }
        } }
        item {
            Text(stringResource(R.string.connected_find_privacy), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.connected_refresh_hint), style = MaterialTheme.typography.bodySmall)
        }
    }
    if (selectedTripId != null && selectedTripId == summary?.cancellableTripId && actionsEnabled) AlertDialog(
        onDismissRequest = { if (!busy) selectedTripId = null },
        title = { Text(stringResource(R.string.connected_trips_cancel_title)) },
        text = { Text(stringResource(R.string.connected_trips_cancel_body)) },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            val id = selectedTripId
            selectedTripId = null
            if (id != null && !busy && actionsEnabled) onCancelSeat(id)
        }) { Text(stringResource(R.string.connected_trips_confirm_cancel)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { selectedTripId = null }) {
            Text(stringResource(R.string.connected_trips_keep_seat))
        } },
    )
    if (selectedRequestId != null && selectedRequestId == summary?.cancellableRequestId && actionsEnabled) AlertDialog(
        onDismissRequest = { if (!busy) selectedRequestId = null },
        title = { Text(stringResource(R.string.connected_withdraw_title)) },
        text = { Text(stringResource(R.string.connected_withdraw_body)) },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            val id = selectedRequestId
            selectedRequestId = null
            if (id != null && !busy && actionsEnabled) onWithdrawRequest(id)
        }) { Text(stringResource(R.string.connected_withdraw_confirm)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { selectedRequestId = null }) {
            Text(stringResource(R.string.connected_withdraw_keep))
        } },
    )
    if (selectedJourneyId != null && selectedJourneyId == summary?.cancellableJourneyId && actionsEnabled) AlertDialog(
        onDismissRequest = { if (!busy) selectedJourneyId = null },
        title = { Text(stringResource(R.string.connected_cancel_journey_title)) },
        text = { Text(stringResource(R.string.connected_cancel_journey_body)) },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            val id = selectedJourneyId
            selectedJourneyId = null
            if (id != null && !busy && actionsEnabled) onCancelJourney(id)
        }) { Text(stringResource(R.string.connected_cancel_journey_confirm)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { selectedJourneyId = null }) {
            Text(stringResource(R.string.connected_keep_journey))
        } },
    )
}
