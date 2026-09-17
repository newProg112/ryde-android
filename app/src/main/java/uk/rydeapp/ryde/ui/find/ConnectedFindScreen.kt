package uk.rydeapp.ryde.ui.find

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import uk.rydeapp.ryde.R
import uk.rydeapp.ryde.ui.components.ConnectedJourneyCard
import uk.rydeapp.ryde.ui.components.InfoCard
import uk.rydeapp.ryde.ui.components.RouteMark
import uk.rydeapp.ryde.ui.home.ConnectedHomeJourney

@Composable
internal fun ConnectedFindScreen(
    journeys: List<ConnectedHomeJourney>,
    busy: Boolean,
    requestsEnabled: Boolean,
    message: String?,
    onRefresh: () -> Unit,
    onRequestSeat: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("connected-find-list"),
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
            Text(stringResource(R.string.connected_find_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.connected_find_intro),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pluralStringResource(R.plurals.connected_find_count, journeys.size, journeys.size),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedButton(onClick = onRefresh, enabled = !busy) {
                    Text(stringResource(R.string.connected_refresh))
                }
            }
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            message?.let {
                Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            if (!requestsEnabled) {
                Text(
                    stringResource(R.string.connected_refresh_before_request),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (journeys.isEmpty()) {
            item {
                InfoCard(
                    stringResource(R.string.connected_find_empty),
                    stringResource(R.string.connected_find_empty_body),
                )
            }
        }
        items(journeys, key = { it.journey.id }) { item ->
            ConnectedJourneyCard(
                item, busy, requestsEnabled, onRequestSeat, allowRerequest = true,
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.connected_find_privacy), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.connected_refresh_hint), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
