package uk.rydeapp.ryde.ui.offer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import uk.rydeapp.ryde.R
import uk.rydeapp.ryde.data.connected.ConnectedJourneyValidator
import uk.rydeapp.ryde.data.connected.ValidationResult
import uk.rydeapp.ryde.ui.account.*
import uk.rydeapp.ryde.ui.components.RouteMark

/** A form only: validation and submission use the existing connected contract. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConnectedOfferScreen(
    busy: Boolean,
    actionsEnabled: Boolean,
    message: String?,
    createdVersion: Int,
    onCreate: (String, String, String, String) -> Unit,
    onRefresh: () -> Unit,
    onManageOffers: () -> Unit,
    modifier: Modifier = Modifier,
    placeSelectionPrompt: OfferPlaceSelectionPrompt? = null,
    onPlaceSelected: (OfferPlaceEndpoint, uk.rydeapp.ryde.domain.PlaceMatch) -> Unit = { _, _ -> },
    onDismissPlaceSelection: () -> Unit = {},
) {
    var origin by rememberSaveable { mutableStateOf("") }
    var destination by rememberSaveable { mutableStateOf("") }
    var seats by rememberSaveable { mutableStateOf("1") }
    val default = remember { defaultConnectedDeparture() }
    var date by rememberSaveable { mutableLongStateOf(default.dateEpochDay) }
    var minute by rememberSaveable { mutableIntStateOf(default.minuteOfDay) }
    var appliedVersion by rememberSaveable { mutableIntStateOf(0) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(createdVersion) {
        if (createdVersion != appliedVersion) {
            origin = ""
            destination = ""
            seats = "1"
            validationMessage = null
            appliedVersion = createdVersion
        }
    }
    val departure = ConnectedDepartureSelection(date, minute)
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            RouteMark(contentDescription = stringResource(R.string.route_mark_description))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
        }
        Row(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.connected_offer_title), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = onRefresh, enabled = !busy) { Text(stringResource(R.string.connected_refresh)) }
        }
        Text(stringResource(R.string.connected_offer_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        if (!actionsEnabled) Text(stringResource(R.string.connected_trips_refresh_required), color = MaterialTheme.colorScheme.error)
        OutlinedTextField(origin, { origin = it; validationMessage = null }, enabled = !busy,
            label = { Text(stringResource(R.string.connected_offer_origin)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(destination, { destination = it; validationMessage = null }, enabled = !busy,
            label = { Text(stringResource(R.string.connected_offer_destination)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.connected_find_privacy), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.connected_offer_departure), style = MaterialTheme.typography.titleMedium)
        Text(formatConnectedDepartureForDisplay(departure))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickDate = true }, enabled = !busy) { Text(stringResource(R.string.connected_choose_date)) }
            OutlinedButton(onClick = { pickTime = true }, enabled = !busy) { Text(stringResource(R.string.connected_choose_time)) }
        }
        if (!isConnectedDepartureFuture(departure)) Text(stringResource(R.string.connected_offer_future), color = MaterialTheme.colorScheme.error)
        OutlinedTextField(seats, { seats = it; validationMessage = null }, enabled = !busy,
            label = { Text(stringResource(R.string.connected_offer_seats)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        validationMessage?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error) }
        Button(enabled = !busy && actionsEnabled && isConnectedDepartureFuture(departure), onClick = {
            val submission = formatConnectedDepartureForSubmission(departure)
            when (val validation = ConnectedJourneyValidator.offer(origin, destination, submission, seats)) {
                is ValidationResult.Invalid -> validationMessage = validation.userMessage
                is ValidationResult.Valid -> {
                    validationMessage = null
                    onCreate(origin, destination, submission, seats)
                }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.connected_create_offer)) }
        TextButton(onClick = onManageOffers) { Text(stringResource(R.string.connected_manage_offers)) }
    }
    if (pickDate) {
        val today = LocalDate.now().toEpochDay()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.ofEpochDay(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = remember(today) { object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() >= today
            } },
        )
        DatePickerDialog(onDismissRequest = { pickDate = false }, confirmButton = {
            TextButton(enabled = !busy && state.selectedDateMillis != null, onClick = {
                state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                pickDate = false
            }) { Text(stringResource(R.string.connected_picker_ok)) }
        }, dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.connected_picker_cancel)) } }) {
            DatePicker(state = state)
        }
    }
    if (pickTime) {
        val state = rememberTimePickerState(initialHour = minute / 60, initialMinute = minute % 60, is24Hour = true)
        AlertDialog(onDismissRequest = { pickTime = false }, title = { Text(stringResource(R.string.connected_choose_time)) },
            text = { TimePicker(state = state) }, confirmButton = {
                TextButton(enabled = !busy, onClick = { minute = state.hour * 60 + state.minute; pickTime = false }) {
                    Text(stringResource(R.string.connected_picker_ok))
                }
            }, dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(R.string.connected_picker_cancel)) } })
    }
    placeSelectionPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = onDismissPlaceSelection,
            title = { Text(stringResource(R.string.connected_offer_choose_area)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(
                            if (prompt.endpoint == OfferPlaceEndpoint.ORIGIN) {
                                R.string.connected_offer_choose_origin
                            } else {
                                R.string.connected_offer_choose_destination
                            },
                            prompt.typedBroadArea,
                        ),
                    )
                    Text(
                        stringResource(R.string.connected_offer_choice_privacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    prompt.candidates.forEach { candidate ->
                        OutlinedButton(
                            onClick = { onPlaceSelected(prompt.endpoint, candidate) },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(candidate.broadAreaLabel) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissPlaceSelection, enabled = !busy) {
                    Text(stringResource(R.string.connected_picker_cancel))
                }
            },
        )
    }
}
