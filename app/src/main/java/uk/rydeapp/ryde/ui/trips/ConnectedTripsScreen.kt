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
) {
    var selectedTripId by remember { mutableStateOf<String?>(null) }
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
        if (content.rider.isEmpty() && content.driver.isEmpty()) item {
            InfoCard(stringResource(R.string.connected_trips_empty), stringResource(R.string.connected_trips_empty_body))
        }
        if (content.rider.isNotEmpty()) item {
            Text(stringResource(R.string.connected_trips_rider_heading), style = MaterialTheme.typography.titleMedium)
        }
        items(content.rider, key = { it.key }) { item ->
            TripCard(item, busy, actionsEnabled) { selectedTripId = it }
        }
        if (content.driver.isNotEmpty()) item {
            Text(stringResource(R.string.connected_trips_driver_heading), style = MaterialTheme.typography.titleMedium)
        }
        items(content.driver, key = { it.key }) { item -> TripCard(item, busy, actionsEnabled) {} }
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
}

@Composable
private fun TripCard(item: ConnectedTripsItem, busy: Boolean, actionsEnabled: Boolean, onCancel: (String) -> Unit) {
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
            item.cancellableTripId?.let { id ->
                OutlinedButton(onClick = { onCancel(id) }, enabled = !busy && actionsEnabled) {
                    Text(stringResource(R.string.connected_trips_cancel_seat))
                }
            }
        }
    }
}
