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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import uk.rydeapp.ryde.ui.place.BroadAreaEndpoint
import uk.rydeapp.ryde.ui.place.BroadAreaPlaceSelectionDialog
import uk.rydeapp.ryde.ui.place.BroadAreaPlaceSelectionPrompt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConnectedFindScreen(
    journeys: List<ConnectedHomeJourney>,
    busy: Boolean,
    requestsEnabled: Boolean,
    message: String?,
    onRefresh: () -> Unit,
    onRequestSeat: (String) -> Unit,
    onManageRequests: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenJourney: (String) -> Unit = {},
    resolvedCriteria: ConnectedFindCriteria? = null,
    onResolveCriteria: (ConnectedFindCriteria) -> Unit = {},
    onPlaceDraftChanged: () -> Unit = {},
    placeSelectionPrompt: BroadAreaPlaceSelectionPrompt? = null,
    onPlaceSelected: (BroadAreaEndpoint, uk.rydeapp.ryde.domain.PlaceMatch) -> Unit = { _, _ -> },
    onDismissPlaceSelection: () -> Unit = {},
) {
    var origin by rememberSaveable { mutableStateOf("") }
    var destination by rememberSaveable { mutableStateOf("") }
    var dateEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickDate by remember { mutableStateOf(false) }
    val draftCriteria = ConnectedFindCriteria(origin, destination, dateEpochDay?.let(LocalDate::ofEpochDay))
    val matchingResolution = resolvedCriteria?.takeIf { it.sameTypedAreasAs(draftCriteria) }
    val criteria = draftCriteria.copy(
        originCoordinate = matchingResolution?.originCoordinate,
        destinationCoordinate = matchingResolution?.destinationCoordinate,
    )
    val results = matchConnectedFindJourneys(journeys, criteria)

    fun clearFilters() {
        onPlaceDraftChanged()
        origin = ""
        destination = ""
        dateEpochDay = null
    }

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
            OutlinedTextField(
                value = origin,
                onValueChange = {
                    onPlaceDraftChanged()
                    origin = it
                },
                label = { Text(stringResource(R.string.connected_find_origin)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("connected-find-origin"),
            )
        }
        item {
            OutlinedButton(onClick = {
                onPlaceDraftChanged()
                val previousOrigin = origin
                origin = destination
                destination = previousOrigin
            }) { Text(stringResource(R.string.connected_find_swap)) }
            OutlinedTextField(
                value = destination,
                onValueChange = {
                    onPlaceDraftChanged()
                    destination = it
                },
                label = { Text(stringResource(R.string.connected_find_destination)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("connected-find-destination"),
            )
            OutlinedButton(
                onClick = { onResolveCriteria(draftCriteria) },
                enabled = !busy && (origin.isNotBlank() || destination.isNotBlank()),
                modifier = Modifier.fillMaxWidth().testTag("connected-find-search"),
            ) { Text(stringResource(R.string.connected_find_search_areas)) }
        }
        item {
            Text(stringResource(R.string.connected_find_departure_date), style = MaterialTheme.typography.titleMedium)
            Text(
                criteria.departureDate?.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.UK))
                    ?: stringResource(R.string.connected_find_any_date),
                modifier = Modifier.testTag("connected-find-date"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickDate = true }) { Text(stringResource(R.string.connected_choose_date)) }
                if (dateEpochDay != null) {
                    TextButton(onClick = { dateEpochDay = null }) { Text(stringResource(R.string.connected_find_any_date)) }
                }
            }
            TextButton(onClick = ::clearFilters, enabled = criteria.hasFilters) {
                Text(stringResource(R.string.connected_find_clear))
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pluralStringResource(
                        if (criteria.hasFilters) R.plurals.connected_find_matching_count else R.plurals.connected_find_count,
                        results.size, results.size,
                    ),
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
        } else if (results.isEmpty()) {
            item {
                InfoCard(
                    stringResource(R.string.connected_find_no_matches),
                    stringResource(R.string.connected_find_no_matches_body),
                )
                TextButton(onClick = ::clearFilters) { Text(stringResource(R.string.connected_find_clear)) }
            }
        }
        items(results, key = { it.item.journey.id }) { result ->
            val item = result.item
            Column {
                ConnectedJourneyCard(
                    item, busy, requestsEnabled, onRequestSeat, allowRerequest = true,
                    onManageRequests = onManageRequests,
                )
                TextButton(onClick = { onOpenJourney(item.journey.id) }) {
                    Text(stringResource(R.string.connected_view_trip_details))
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.connected_find_privacy), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.connected_refresh_hint), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (pickDate) {
        // Material's picker encodes calendar dates at UTC midnight, not as local instants.
        val pickerDate = criteria.departureDate ?: LocalDate.now()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = pickerDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(enabled = state.selectedDateMillis != null, onClick = {
                    state.selectedDateMillis?.let {
                        dateEpochDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    pickDate = false
                }) { Text(stringResource(R.string.connected_picker_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.connected_picker_cancel)) }
            },
        ) { DatePicker(state = state) }
    }
    placeSelectionPrompt?.let { prompt ->
        BroadAreaPlaceSelectionDialog(
            prompt = prompt,
            busy = busy,
            question = stringResource(
                if (prompt.endpoint == BroadAreaEndpoint.FROM) {
                    R.string.connected_find_choose_origin
                } else {
                    R.string.connected_find_choose_destination
                },
                prompt.typedBroadArea,
            ),
            onPlaceSelected = onPlaceSelected,
            onDismiss = onDismissPlaceSelection,
        )
    }
}
