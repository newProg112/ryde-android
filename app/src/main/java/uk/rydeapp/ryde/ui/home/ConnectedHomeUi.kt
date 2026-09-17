package uk.rydeapp.ryde.ui.home

import uk.rydeapp.ryde.data.connected.ConnectedJourney
import uk.rydeapp.ryde.data.connected.ConnectedJourneyLifecycle
import uk.rydeapp.ryde.data.connected.ConnectedJourneySnapshot
import uk.rydeapp.ryde.data.connected.ConnectedSeatRequest
import uk.rydeapp.ryde.ui.account.ConnectedRiderRequestItem
import uk.rydeapp.ryde.ui.account.canRerequestConnectedSeat

/** Shared Home/Find presentation of existing connected records, never a second journey store. */
internal data class ConnectedHomeJourney(
    val journey: ConnectedJourney,
    val request: ConnectedSeatRequest?,
    val canRequest: Boolean,
    val canRerequest: Boolean = false,
)

internal fun connectedHomeJourneys(
    snapshot: ConnectedJourneySnapshot,
    viewerUid: String,
    nowEpochMillis: Long,
): List<ConnectedHomeJourney> {
    val requests = snapshot.requests.filter { it.riderUid == viewerUid }.associateBy { it.journeyId }
    return snapshot.journeys
        .filter { ConnectedJourneyLifecycle.discoverable(it, viewerUid, nowEpochMillis) }
        .sortedBy { it.departureEpochMillis }
        .map { journey ->
            val request = requests[journey.id]
            ConnectedHomeJourney(
                journey = journey,
                request = request,
                canRequest = journey.seatsRemaining > 0 && request == null,
                canRerequest = request != null && canRerequestConnectedSeat(
                    ConnectedRiderRequestItem(request, journey), nowEpochMillis,
                ),
            )
        }
}
