package uk.rydeapp.ryde.ui.trips

import uk.rydeapp.ryde.R
import uk.rydeapp.ryde.data.connected.*
import uk.rydeapp.ryde.ui.account.canCancelConnectedConfirmedSeat

/** Presentation of repository truth only; no optimistic trips or synthetic route fields. */
internal data class ConnectedTripsItem(
    val key: String,
    val origin: String?,
    val destination: String?,
    val departureEpochMillis: Long?,
    val statusText: Int,
    val roleText: Int,
    val cancellableTripId: String? = null,
)

internal data class ConnectedTripsContent(
    val rider: List<ConnectedTripsItem>,
    val driver: List<ConnectedTripsItem>,
)

internal fun connectedTripsContent(
    snapshot: ConnectedJourneySnapshot,
    uid: String,
    nowEpochMillis: Long,
): ConnectedTripsContent {
    val journeys = snapshot.journeys.associateBy { it.id }
    val trips = snapshot.confirmedTrips.filter { it.riderUid == uid }
    val riderTrips = trips.map { trip ->
        val journey = journeys[trip.journeyId]
        val lifecycle = ConnectedJourneyLifecycle.trip(trip, journey)
        ConnectedTripsItem(
            "trip:${trip.id}", trip.originArea, trip.destinationArea, trip.departureEpochMillis,
            when (lifecycle) {
                ConnectedTripLifecycle.CONFIRMED -> R.string.connected_request_accepted
                ConnectedTripLifecycle.CANCELLED_BY_RIDER -> R.string.connected_trips_cancelled
                ConnectedTripLifecycle.CANCELLED_BY_DRIVER -> R.string.connected_trips_driver_cancelled
                ConnectedTripLifecycle.UNAVAILABLE -> R.string.connected_trips_unavailable
            },
            R.string.connected_trips_rider,
            trip.id.takeIf { canCancelConnectedConfirmedSeat(trip, uid, nowEpochMillis, journey) },
        )
    }
    val representedRequests = trips.map { it.acceptedRequestId }.toSet()
    val requests = snapshot.requests.filter { it.riderUid == uid && it.id !in representedRequests }.map { request ->
        val journey = journeys[request.journeyId]?.takeIf { it.driverUid == request.driverUid }
        val status = when {
            ConnectedJourneyLifecycle.requestCancelledByDriver(request, journey) -> R.string.connected_trips_driver_cancelled
            request.status in listOf(ConnectedRequestStatus.PENDING, ConnectedRequestStatus.ACCEPTED) &&
                !ConnectedJourneyLifecycle.requestJourneyOpen(request, journey) -> R.string.connected_trips_unavailable
            else -> when (request.status) {
                ConnectedRequestStatus.PENDING -> R.string.connected_request_pending
                ConnectedRequestStatus.ACCEPTED -> R.string.connected_request_accepted
                ConnectedRequestStatus.DECLINED -> R.string.connected_request_declined
                ConnectedRequestStatus.CANCELLED -> R.string.connected_request_cancelled
                ConnectedRequestStatus.CANCELLED_AFTER_ACCEPTANCE -> R.string.connected_seat_cancelled
            }
        }
        ConnectedTripsItem("request:${request.id}", journey?.originArea, journey?.destinationArea,
            journey?.departureEpochMillis, status, R.string.connected_trips_rider)
    }
    val driver = snapshot.journeys.filter { it.driverUid == uid }.map { journey ->
        ConnectedTripsItem("journey:${journey.id}", journey.originArea, journey.destinationArea,
            journey.departureEpochMillis,
            if (journey.status == ConnectedJourneyStatus.CANCELLED) R.string.connected_trips_offer_cancelled
            else R.string.connected_trips_offer_open, R.string.connected_trips_driver)
    }
    return ConnectedTripsContent(
        (riderTrips + requests).sortedBy { it.departureEpochMillis ?: Long.MAX_VALUE },
        driver.sortedBy { it.departureEpochMillis },
    )
}
