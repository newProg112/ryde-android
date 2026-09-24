package uk.rydeapp.ryde.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import uk.rydeapp.ryde.R
import uk.rydeapp.ryde.data.connected.ConnectedRequestStatus
import uk.rydeapp.ryde.ui.home.ConnectedHomeJourney
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Renders only rider-safe fields. Eligibility and command ownership stay outside the card. */
@Composable
internal fun ConnectedJourneyCard(
    item: ConnectedHomeJourney,
    busy: Boolean,
    requestsEnabled: Boolean,
    onRequestSeat: (String) -> Unit,
    modifier: Modifier = Modifier,
    allowRerequest: Boolean = false,
    onManageRequests: (() -> Unit)? = null,
    additionalContent: @Composable (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.connected_route, item.journey.originArea, item.journey.destinationArea),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(formatConnectedJourneyDeparture(item.journey.departureEpochMillis))
            Text(
                stringResource(R.string.connected_seats, item.journey.seatsRemaining, item.journey.seatCapacity),
                color = MaterialTheme.colorScheme.primary,
            )
            additionalContent?.invoke()
            item.request?.let { request ->
                Text(stringResource(when (request.status) {
                    ConnectedRequestStatus.PENDING -> R.string.connected_request_pending
                    ConnectedRequestStatus.ACCEPTED -> R.string.connected_request_accepted
                    ConnectedRequestStatus.DECLINED -> R.string.connected_request_declined
                    ConnectedRequestStatus.CANCELLED -> R.string.connected_request_cancelled
                    ConnectedRequestStatus.CANCELLED_AFTER_ACCEPTANCE -> R.string.connected_seat_cancelled
                }))
            }
            if (item.canRequest || (allowRerequest && item.canRerequest)) {
                Button(onClick = { onRequestSeat(item.journey.id) }, enabled = !busy && requestsEnabled) {
                    Text(stringResource(R.string.connected_request_seat))
                }
            } else if (item.request != null) {
                if (onManageRequests != null) {
                    OutlinedButton(onClick = onManageRequests, enabled = !busy) {
                        Text(stringResource(R.string.connected_manage_requests))
                    }
                } else {
                    Text(
                        stringResource(R.string.connected_request_recorded),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(stringResource(R.string.connected_no_seats))
            }
        }
    }
}

internal fun formatConnectedJourneyDeparture(epochMillis: Long): String =
    DateTimeFormatter.ofPattern("EEE, d MMM yyyy 'at' HH:mm", Locale.UK)
        .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epochMillis))
