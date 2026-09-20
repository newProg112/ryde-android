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
    val cancellableRequestId: String? = null,
    val cancellableJourneyId: String? = null,
    val seatsRemaining: Int? = null,
    val seatCapacity: Int? = null,
    val incoming: List<ConnectedIncomingRequest> = emptyList(),
    val journeyStatusText: Int? = null,
    val journeyId: String? = null,
    val driverDisplayName: String? = null,
)

internal data class ConnectedIncomingRequest(
    val id: String,
    val riderDisplayName: String?,
    val statusText: Int,
    val canAccept: Boolean,
    val canDecline: Boolean,
)

internal data class ConnectedTripsContent(
    val rider: List<ConnectedTripsItem>,
    val driver: List<ConnectedTripsItem>,
    val unavailableIncoming: List<ConnectedIncomingRequest> = emptyList(),
)

/** Mirrors existing gateway preconditions; transactions remain authoritative. */
internal fun canDecideConnectedRequest(
    request: ConnectedSeatRequest, journey: ConnectedJourney?, uid: String, accept: Boolean,
    nowEpochMillis: Long,
): Boolean = request.driverUid == uid && request.riderUid != uid &&
    request.status == ConnectedRequestStatus.PENDING &&
    ConnectedJourneyLifecycle.requestJourneyOpen(request, journey) &&
    (!accept || (journey!!.seatsRemaining > 0 && journey.departureEpochMillis > nowEpochMillis))

private fun incomingRequest(
    request: ConnectedSeatRequest, journey: ConnectedJourney?, uid: String, now: Long,
): ConnectedIncomingRequest = ConnectedIncomingRequest(
    request.id,
    request.riderDisplayName,
    when (ConnectedJourneyLifecycle.request(request, journey, now)) {
        ConnectedRequestLifecycle.PENDING -> R.string.connected_incoming_pending
        ConnectedRequestLifecycle.ACCEPTED -> R.string.connected_incoming_accepted
        ConnectedRequestLifecycle.DECLINED -> R.string.connected_incoming_declined
        ConnectedRequestLifecycle.CANCELLED -> R.string.connected_incoming_cancelled
        ConnectedRequestLifecycle.CANCELLED_AFTER_ACCEPTANCE -> R.string.connected_incoming_seat_cancelled
        ConnectedRequestLifecycle.DEPARTURE_PASSED_PENDING -> R.string.connected_incoming_pending_departed
        ConnectedRequestLifecycle.DEPARTURE_PASSED_ACCEPTED -> R.string.connected_incoming_accepted_departed
        ConnectedRequestLifecycle.CANCELLED_BY_DRIVER -> R.string.connected_trips_offer_cancelled
        ConnectedRequestLifecycle.UNAVAILABLE -> R.string.connected_trips_unavailable
    },
    canDecideConnectedRequest(request, journey, uid, true, now),
    canDecideConnectedRequest(request, journey, uid, false, now),
)

private fun List<ConnectedTripsItem>.orderedForTrips(nowEpochMillis: Long): List<ConnectedTripsItem> =
    sortedWith { first, second ->
        fun group(item: ConnectedTripsItem): Int = when {
            item.departureEpochMillis == null -> 2
            item.departureEpochMillis > nowEpochMillis -> 0
            else -> 1
        }
        val firstGroup = group(first)
        val groupComparison = firstGroup.compareTo(group(second))
        if (groupComparison != 0) groupComparison
        else if (firstGroup == 1) compareValues(second.departureEpochMillis, first.departureEpochMillis)
        else compareValues(first.departureEpochMillis, second.departureEpochMillis)
    }

internal fun connectedOfferedJourneyStatusText(
    journey: ConnectedJourney,
    nowEpochMillis: Long,
): Int = when {
    journey.status == ConnectedJourneyStatus.CANCELLED -> R.string.connected_trips_offer_cancelled
    journey.departureEpochMillis <= nowEpochMillis -> R.string.connected_offer_departed
    journey.seatsRemaining == 0 -> R.string.connected_offer_full
    else -> R.string.connected_trips_offer_open
}

internal fun connectedTripsContent(
    snapshot: ConnectedJourneySnapshot,
    uid: String,
    nowEpochMillis: Long,
): ConnectedTripsContent {
    val journeys = snapshot.journeys.associateBy { it.id }
    val trips = snapshot.confirmedTrips.filter { it.riderUid == uid }
    val riderTrips = trips.map { trip ->
        val journey = journeys[trip.journeyId]
        val lifecycle = ConnectedJourneyLifecycle.trip(trip, journey, nowEpochMillis)
        ConnectedTripsItem(
            "trip:${trip.id}", trip.originArea, trip.destinationArea, trip.departureEpochMillis,
            when (lifecycle) {
                ConnectedTripLifecycle.CONFIRMED -> R.string.connected_request_accepted
                ConnectedTripLifecycle.DEPARTURE_PASSED -> R.string.connected_trips_departure_passed
                ConnectedTripLifecycle.CANCELLED_BY_RIDER -> R.string.connected_trips_cancelled
                ConnectedTripLifecycle.CANCELLED_BY_DRIVER -> R.string.connected_trips_driver_cancelled
                ConnectedTripLifecycle.UNAVAILABLE -> R.string.connected_trips_unavailable
            },
            R.string.connected_trips_rider,
            trip.id.takeIf { canCancelConnectedConfirmedSeat(trip, uid, nowEpochMillis, journey) },
            journeyId = trip.journeyId,
            driverDisplayName = trip.driverDisplayName,
        )
    }
    val representedRequests = trips.map { it.acceptedRequestId }.toSet()
    val requests = snapshot.requests.filter { it.riderUid == uid && it.id !in representedRequests }.map { request ->
        val journey = journeys[request.journeyId]?.takeIf { it.driverUid == request.driverUid }
        val status = when (ConnectedJourneyLifecycle.request(request, journey, nowEpochMillis)) {
            ConnectedRequestLifecycle.PENDING -> R.string.connected_request_pending
            ConnectedRequestLifecycle.ACCEPTED -> R.string.connected_request_accepted
            ConnectedRequestLifecycle.DECLINED -> R.string.connected_request_declined
            ConnectedRequestLifecycle.CANCELLED -> R.string.connected_request_cancelled
            ConnectedRequestLifecycle.CANCELLED_AFTER_ACCEPTANCE -> R.string.connected_seat_cancelled
            ConnectedRequestLifecycle.DEPARTURE_PASSED_PENDING -> R.string.connected_request_pending_departed
            ConnectedRequestLifecycle.DEPARTURE_PASSED_ACCEPTED -> R.string.connected_trips_departure_passed
            ConnectedRequestLifecycle.CANCELLED_BY_DRIVER -> R.string.connected_trips_driver_cancelled
            ConnectedRequestLifecycle.UNAVAILABLE -> R.string.connected_trips_unavailable
        }
        ConnectedTripsItem("request:${request.id}", journey?.originArea, journey?.destinationArea,
            journey?.departureEpochMillis, status, R.string.connected_trips_rider,
            cancellableRequestId = request.id.takeIf {
                it.isNotBlank() && ConnectedJourneyLifecycle.canCancelRequest(request, journey, uid)
            },
            // A terminal request remains historical even if its linked journey is later cancelled.
            journeyStatusText = R.string.connected_trips_driver_cancelled.takeIf {
                journey?.status == ConnectedJourneyStatus.CANCELLED && status != R.string.connected_trips_driver_cancelled
            }, journeyId = request.journeyId)
    }
    val owned = snapshot.journeys.filter { it.driverUid == uid }
    val incoming = snapshot.requests.filter { it.driverUid == uid && it.riderUid != uid }
    val driver = owned.map { journey ->
        ConnectedTripsItem("journey:${journey.id}", journey.originArea, journey.destinationArea,
            journey.departureEpochMillis,
            connectedOfferedJourneyStatusText(journey, nowEpochMillis), R.string.connected_trips_driver,
            cancellableJourneyId = journey.id.takeIf { ConnectedJourneyLifecycle.canCancelJourney(journey, uid, nowEpochMillis) },
            seatsRemaining = journey.seatsRemaining, seatCapacity = journey.seatCapacity,
            incoming = incoming.filter { it.journeyId == journey.id }.map { incomingRequest(it, journey, uid, nowEpochMillis) },
            journeyId = journey.id)
    }
    return ConnectedTripsContent(
        (riderTrips + requests).orderedForTrips(nowEpochMillis),
        driver.orderedForTrips(nowEpochMillis),
        incoming.filter { request -> owned.none { it.id == request.journeyId } }
            .map { incomingRequest(it, null, uid, nowEpochMillis) },
    )
}
