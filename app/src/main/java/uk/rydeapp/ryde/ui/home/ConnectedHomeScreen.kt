package uk.rydeapp.ryde.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import uk.rydeapp.ryde.R
import uk.rydeapp.ryde.domain.model.SavedPlace
import uk.rydeapp.ryde.ui.components.InfoCard
import uk.rydeapp.ryde.ui.components.ConnectedJourneyCard
import uk.rydeapp.ryde.ui.components.LabeledValue
import uk.rydeapp.ryde.ui.components.RouteMark

@Composable
internal fun ConnectedHomeScreen(
    displayName: String,
    savedPlaces: List<SavedPlace>,
    journeys: List<ConnectedHomeJourney>,
    busy: Boolean,
    requestsEnabled: Boolean,
    message: String?,
    onFind: () -> Unit,
    onOffer: () -> Unit,
    onRefresh: () -> Unit,
    onRequestSeat: (String) -> Unit,
    onManageRequests: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenJourney: (String) -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RouteMark(contentDescription = stringResource(R.string.route_mark_description))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
            }
        }
        item {
            Text(stringResource(R.string.greeting, displayName), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.connected_home_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onFind, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Text(stringResource(R.string.connected_find_shortcut))
                }
                OutlinedButton(onClick = onOffer, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Text(stringResource(R.string.connected_offer_shortcut))
                }
            }
        }
        if (savedPlaces.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.connected_saved_areas), style = MaterialTheme.typography.titleMedium)
                        savedPlaces.forEach { LabeledValue(it.label, it.area) }
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.connected_available_journeys),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                )
                OutlinedButton(onClick = onRefresh, enabled = !busy) {
                    Text(stringResource(if (busy) R.string.connected_working else R.string.connected_refresh))
                }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
        if (journeys.isEmpty()) {
            item {
                InfoCard(
                    stringResource(R.string.connected_no_journeys),
                    stringResource(R.string.connected_no_journeys_body),
                )
            }
        }
        items(journeys, key = { it.journey.id }) { item ->
            Column {
                ConnectedJourneyCard(item, busy, requestsEnabled, onRequestSeat, onManageRequests = onManageRequests)
                TextButton(onClick = { onOpenJourney(item.journey.id) }) {
                    Text(stringResource(R.string.connected_view_trip_details))
                }
            }
        }
        item {
            OutlinedButton(onClick = onFind, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.connected_browse_all))
            }
        }
        item {
            Text(stringResource(R.string.connected_refresh_hint), style = MaterialTheme.typography.bodySmall)
        }
    }
}
