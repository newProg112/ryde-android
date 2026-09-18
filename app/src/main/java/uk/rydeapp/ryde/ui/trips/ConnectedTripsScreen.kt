package uk.rydeapp.ryde.ui.trips

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import uk.rydeapp.ryde.R
import uk.rydeapp.ryde.ui.components.InfoCard
import uk.rydeapp.ryde.ui.components.RouteMark
import uk.rydeapp.ryde.ui.components.formatConnectedJourneyDeparture

@Composable
internal fun ConnectedTripsScreen(
    content: ConnectedTripsContent,
    busy: Boolean,
    actionsEnabled: Boolean,
    message: String?,
    onRefresh: () -> Unit,
    onCancelSeat: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDecideRequest: (String, Boolean) -> Unit = { _, _ -> },
    onCancelJourney: (String) -> Unit = {},
    onOpenJourney: (String) -> Unit = {},
) {
    var selectedTripId by remember { mutableStateOf<String?>(null) }
    var selectedJourneyId by remember { mutableStateOf<String?>(null) }
    // Dialog visibility follows the same resolved eligibility as the cards.
    val selected = content.rider.firstOrNull {
        it.cancellableTripId != null && it.cancellableTripId == selectedTripId
    }.takeIf { actionsEnabled }
    LazyColumn(
        modifier.fillMaxSize().testTag("connected-trips-list"),
        contentPadding = PaddingValues(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                RouteMark(contentDescription = stringResource(R.string.route_mark_description))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.connected_trips_title), Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall)
                OutlinedButton(onClick = onRefresh, enabled = !busy) {
                    Text(stringResource(R.string.connected_refresh))
                }
            }
            Text(stringResource(R.string.connected_trips_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            if (!actionsEnabled) Text(stringResource(R.string.connected_trips_refresh_required),
                color = MaterialTheme.colorScheme.error)
        }
        if (content.rider.isEmpty() && content.driver.isEmpty() && content.unavailableIncoming.isEmpty()) item {
            InfoCard(stringResource(R.string.connected_trips_empty), stringResource(R.string.connected_trips_empty_body))
        }
        if (content.rider.isNotEmpty()) item {
            Text(stringResource(R.string.connected_trips_rider_heading), style = MaterialTheme.typography.titleMedium)
        }
        items(content.rider, key = { it.key }) { item ->
            Column {
                TripCard(item, busy, actionsEnabled, onCancel = { selectedTripId = it })
                item.journeyId?.takeIf(String::isNotBlank)?.let { id ->
                    TextButton(onClick = { onOpenJourney(id) }) { Text(stringResource(R.string.connected_view_trip_details)) }
                }
            }
        }
        if (content.driver.isNotEmpty()) item {
            Text(stringResource(R.string.connected_trips_driver_heading), style = MaterialTheme.typography.titleMedium)
        }
        items(content.driver, key = { it.key }) { item ->
            Column {
                TripCard(item, busy, actionsEnabled, {}, onDecideRequest) { selectedJourneyId = it }
                item.journeyId?.takeIf(String::isNotBlank)?.let { id ->
                    TextButton(onClick = { onOpenJourney(id) }) { Text(stringResource(R.string.connected_view_trip_details)) }
                }
            }
        }
        if (content.unavailableIncoming.isNotEmpty()) item {
            InfoCard(stringResource(R.string.connected_trips_details_unavailable),
                stringResource(R.string.connected_incoming_unavailable))
        }
        items(content.unavailableIncoming, key = { "unavailable:${it.id}" }) {
            IncomingRequest(it, busy, actionsEnabled, onDecideRequest)
        }
        item { Text(stringResource(R.string.connected_trips_refresh_hint), style = MaterialTheme.typography.bodySmall) }
    }
    if (selected != null) AlertDialog(
        onDismissRequest = { if (!busy) selectedTripId = null },
        title = { Text(stringResource(R.string.connected_trips_cancel_title)) },
        text = { Text(stringResource(R.string.connected_trips_cancel_body)) },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                // Consume this confirmation before forwarding the single command.
                val id = selectedTripId
                selectedTripId = null
                if (id != null && !busy && actionsEnabled) onCancelSeat(id)
            }) { Text(stringResource(R.string.connected_trips_confirm_cancel)) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = { selectedTripId = null }) {
                Text(stringResource(R.string.connected_trips_keep_seat))
            }
        },
    )
    val selectedJourney = content.driver.firstOrNull {
        it.cancellableJourneyId != null && it.cancellableJourneyId == selectedJourneyId
    }.takeIf { actionsEnabled }
    if (selectedJourney != null) AlertDialog(
        onDismissRequest = { if (!busy) selectedJourneyId = null },
        title = { Text(stringResource(R.string.connected_cancel_journey_title)) },
        text = { Text(stringResource(R.string.connected_cancel_journey_body)) },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                val id = selectedJourneyId
                selectedJourneyId = null
                if (id != null && !busy && actionsEnabled) onCancelJourney(id)
            }) { Text(stringResource(R.string.connected_cancel_journey_confirm)) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = { selectedJourneyId = null }) {
                Text(stringResource(R.string.connected_keep_journey))
            }
        },
    )
}

@Composable
private fun TripCard(
    item: ConnectedTripsItem, busy: Boolean, actionsEnabled: Boolean, onCancel: (String) -> Unit,
    onDecide: (String, Boolean) -> Unit = { _, _ -> }, onCancelJourney: (String) -> Unit = {},
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (item.origin != null && item.destination != null)
                stringResource(R.string.connected_route, item.origin, item.destination)
                else stringResource(R.string.connected_trips_details_unavailable),
                style = MaterialTheme.typography.titleMedium)
            item.departureEpochMillis?.let { Text(formatConnectedJourneyDeparture(it)) }
            Text(stringResource(item.roleText), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(item.statusText), color = MaterialTheme.colorScheme.primary)
            item.journeyStatusText?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (item.seatsRemaining != null && item.seatCapacity != null) {
                Text(stringResource(R.string.connected_seats, item.seatsRemaining, item.seatCapacity))
                Text(stringResource(R.string.connected_incoming_heading), style = MaterialTheme.typography.titleSmall)
                if (item.incoming.isEmpty()) Text(stringResource(R.string.connected_incoming_empty))
                item.incoming.forEach { IncomingRequest(it, busy, actionsEnabled, onDecide) }
            }
            item.cancellableTripId?.let { id ->
                OutlinedButton(onClick = { onCancel(id) }, enabled = !busy && actionsEnabled) {
                    Text(stringResource(R.string.connected_trips_cancel_seat))
                }
            }
            item.cancellableJourneyId?.let { id ->
                OutlinedButton(onClick = { onCancelJourney(id) }, enabled = !busy && actionsEnabled) {
                    Text(stringResource(R.string.connected_cancel_journey))
                }
            }
        }
    }
}

@Composable
private fun IncomingRequest(
    request: ConnectedIncomingRequest, busy: Boolean, actionsEnabled: Boolean,
    onDecide: (String, Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth().testTag("incoming:${request.id}"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HorizontalDivider()
        Text(stringResource(request.statusText))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (request.canAccept) Button(enabled = !busy && actionsEnabled,
                onClick = { onDecide(request.id, true) }) { Text(stringResource(R.string.connected_accept)) }
            if (request.canDecline) OutlinedButton(enabled = !busy && actionsEnabled,
                onClick = { onDecide(request.id, false) }) { Text(stringResource(R.string.connected_decline)) }
        }
    }
}
