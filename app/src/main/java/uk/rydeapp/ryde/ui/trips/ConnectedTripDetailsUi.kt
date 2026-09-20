package uk.rydeapp.ryde.ui.trips

import uk.rydeapp.ryde.data.connected.ConnectedJourney
import uk.rydeapp.ryde.data.connected.ConnectedJourneySnapshot
import uk.rydeapp.ryde.ui.home.ConnectedHomeJourney

/** A selection of existing presentation records, never a second journey store or lifecycle. */
internal data class ConnectedTripDetailsContent(
    val summary: ConnectedTripsItem? = null,
    val journey: ConnectedJourney? = null,
    val canRequest: Boolean = false,
    val unrequestedStatusText: Int? = null,
)

internal fun connectedTripDetailsContent(
    snapshot: ConnectedJourneySnapshot,
    uid: String,
    journeyId: String,
    discovery: List<ConnectedHomeJourney>,
    trips: ConnectedTripsContent,
    nowEpochMillis: Long,
): ConnectedTripDetailsContent {
    if (journeyId.isBlank()) return ConnectedTripDetailsContent()
    val summary = trips.driver.firstOrNull { it.journeyId == journeyId }
        ?: trips.rider.firstOrNull { it.journeyId == journeyId }
    val trip = snapshot.confirmedTrips.firstOrNull { it.journeyId == journeyId && it.riderUid == uid }
    val request = snapshot.requests.firstOrNull { it.journeyId == journeyId && it.riderUid == uid }
    val journey = snapshot.journeys.firstOrNull { it.id == journeyId }?.takeIf {
        when {
            it.driverUid == uid -> true
            trip != null -> it.driverUid == trip.driverUid
            request != null -> it.driverUid == request.driverUid
            else -> true
        }
    }
    // Existing projections own statuses and eligibility. Blank action IDs fail closed here.
    val safeSummary = summary?.copy(
        cancellableTripId = summary.cancellableTripId?.takeIf { journey != null && it.isNotBlank() && trip?.id == it },
        cancellableRequestId = summary.cancellableRequestId?.takeIf {
            journey != null && it.isNotBlank() && request?.id == it
        },
        cancellableJourneyId = summary.cancellableJourneyId?.takeIf { journey?.driverUid == uid && it.isNotBlank() && journey.id == it },
        incoming = summary.incoming.map {
            if (it.id.isBlank() || journey?.driverUid != uid) it.copy(canAccept = false, canDecline = false) else it
        },
    )
    return ConnectedTripDetailsContent(
        safeSummary, journey,
        journey != null && trip == null && discovery.any {
            it.journey.id == journeyId && (it.canRequest || it.canRerequest)
        },
        journey?.takeIf { safeSummary == null }?.let {
            connectedOfferedJourneyStatusText(it, nowEpochMillis)
        },
    )
}
