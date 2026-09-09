package uk.rydeapp.ryde.ui.destinations

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import uk.rydeapp.ryde.R
import uk.rydeapp.ryde.domain.model.SavedPlace
import uk.rydeapp.ryde.domain.model.SeatRequest
import uk.rydeapp.ryde.domain.model.SeatRequestStatus
import uk.rydeapp.ryde.domain.model.formatDemoTime
import uk.rydeapp.ryde.ui.components.DestinationIcon
import uk.rydeapp.ryde.ui.components.DestinationIconType
import uk.rydeapp.ryde.ui.components.InfoCard
import uk.rydeapp.ryde.ui.components.LabelPill
import uk.rydeapp.ryde.ui.components.RouteMark
import uk.rydeapp.ryde.ui.theme.Mint
import uk.rydeapp.ryde.ui.theme.RydeTheme
import java.text.NumberFormat
import java.util.Locale

@Composable
fun OfferScreen(modifier: Modifier = Modifier) {
    DestinationShell(
        headingRes = R.string.offer_heading,
        bodyRes = R.string.offer_body,
        iconType = DestinationIconType.OFFER,
        modifier = modifier,
    ) {
        InfoCard(
            title = stringResource(R.string.offer_info_title),
            body = stringResource(R.string.offer_info_body),
        )
    }
}

@Composable
fun TripsScreen(
    requests: List<SeatRequest>,
    onCancelRequest: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedRequestId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCancelConfirmation by rememberSaveable { mutableStateOf(false) }
    val selectedRequest = requests.firstOrNull { it.id == selectedRequestId }

    BackHandler(enabled = selectedRequest != null) {
        selectedRequestId = null
        showCancelConfirmation = false
    }

    if (showCancelConfirmation && selectedRequest != null) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmation = false },
            title = { Text("Cancel demo request?") },
            text = { Text("It will remain visible in Trips as Cancelled. No real driver has been contacted.") },
            confirmButton = {
                Button(onClick = {
                    onCancelRequest(selectedRequest.id)
                    showCancelConfirmation = false
                }) { Text("Cancel request") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCancelConfirmation = false }) { Text("Keep request") }
            },
        )
    }

    DestinationShell(
        headingRes = R.string.trips_heading,
        bodyRes = R.string.trips_body,
        iconType = DestinationIconType.TRIPS,
        phaseLabel = "PHASE 3 · LOCAL DEMO",
        modifier = modifier,
    ) {
        when {
            selectedRequest != null -> TripRequestDetail(
                request = selectedRequest,
                onBack = { selectedRequestId = null },
                onCancel = { showCancelConfirmation = true },
            )
            requests.isEmpty() -> EmptyStateCard()
            else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                requests.forEach { request ->
                    TripRequestCard(request, onClick = { selectedRequestId = request.id })
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(
    savedPlaces: List<SavedPlace>,
    modifier: Modifier = Modifier,
) {
    DestinationShell(
        headingRes = R.string.profile_heading,
        bodyRes = R.string.profile_body,
        iconType = DestinationIconType.PROFILE,
        modifier = modifier,
    ) {
        Text(stringResource(R.string.saved_places), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            savedPlaces.forEach { place -> SavedPlaceCard(place, Modifier.weight(1f)) }
        }
        InfoCard(
            title = stringResource(R.string.trusted_connections),
            body = stringResource(R.string.trusted_connections_body),
        )
    }
}

@Composable
private fun DestinationShell(
    @StringRes headingRes: Int,
    @StringRes bodyRes: Int,
    iconType: DestinationIconType,
    modifier: Modifier = Modifier,
    phaseLabel: String? = null,
    content: @Composable () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RouteMark(stringResource(R.string.route_mark_description), modifier = Modifier.size(34.dp))
                Spacer(Modifier.weight(1f))
                LabelPill(phaseLabel ?: stringResource(R.string.phase_one_label))
            }
        }
        item {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                DestinationIcon(
                    type = iconType,
                    contentDescription = stringResource(headingRes),
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
        item {
            Column {
                Text(stringResource(headingRes), style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(bodyRes),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        item { content() }
    }
}

@Composable
private fun TripRequestCard(request: SeatRequest, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LabelPill(
                    if (request.status == SeatRequestStatus.PENDING) "Pending driver response" else "Cancelled",
                    containerColor = if (request.status == SeatRequestStatus.PENDING) Mint.copy(alpha = .22f) else MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text("LOCAL DEMO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            Text("${request.originArea} → ${request.destinationArea}", style = MaterialTheme.typography.titleLarge)
            Text("${request.travelDate.displayName} · around ${formatDemoTime(request.approximatePickupMinutes)}")
            Text("Fictional driver ${request.driver.firstName} · ${request.requestedSeats} ${seatWord(request.requestedSeats)}")
            Text("Public pickup: ${request.pickupArea}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Row {
                Text("Rider total", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text(money(request.riderTotalPence), fontWeight = FontWeight.Bold)
            }
            Text("Open request details →", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TripRequestDetail(
    request: SeatRequest,
    onBack: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedButton(onClick = onBack) { Text("← Back to trips") }
        LabelPill(if (request.status == SeatRequestStatus.PENDING) "Pending driver response" else "Cancelled")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("${request.originArea} → ${request.destinationArea}", style = MaterialTheme.typography.titleLarge)
                TripDetailRow("Fictional driver", request.driver.firstName)
                TripDetailRow("Date and time", "${request.travelDate.displayName} · around ${formatDemoTime(request.approximatePickupMinutes)}")
                TripDetailRow("Requested seats", request.requestedSeats.toString())
                TripDetailRow("Public pickup area", request.pickupArea)
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                TripDetailRow("Shared-distance contribution", money(request.contributionPence))
                Text("${request.sharedMiles} shared miles × £0.20, rounded to the nearest 50p", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                TripDetailRow("Ryde service fee", money(request.serviceFeePence))
                TripDetailRow("Rider total", money(request.riderTotalPence), bold = true)
                TripDetailRow("Driver receives", money(request.driverReceivesPence), bold = true)
            }
        }
        InfoCard(
            title = "Fictional local-demo data",
            body = "No request reached a real driver. No payment, verification, contact, map or exact/live location sharing occurred.",
        )
        if (request.status == SeatRequestStatus.PENDING) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel demo request") }
        } else {
            Text("This cancelled request is kept here as local-demo history.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TripDetailRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(value, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

private fun money(pence: Int): String = NumberFormat.getCurrencyInstance(Locale.UK).format(pence / 100.0)
private fun seatWord(count: Int): String = if (count == 1) "seat" else "seats"

@Composable
private fun EmptyStateCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val description = stringResource(R.string.trips_info_title)
            Canvas(
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = description },
            ) {
                drawCircle(Mint.copy(alpha = .22f))
                drawCircle(Mint, radius = size.minDimension * .10f)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.trips_info_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                stringResource(R.string.trips_info_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SavedPlaceCard(place: SavedPlace, modifier: Modifier = Modifier) {
    val placeDescription = stringResource(
        R.string.saved_place_description,
        place.label,
        place.area,
    )
    Card(
        modifier = modifier.semantics { contentDescription = placeDescription },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(15.dp)) {
            Text(place.label, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(3.dp))
            Text(
                place.area,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OfferScreenPreview() {
    RydeTheme(darkTheme = false) { OfferScreen() }
}
