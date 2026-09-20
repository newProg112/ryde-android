package uk.rydeapp.ryde.data.connected

/** Resolved lifecycle; driver cancellation is persisted once on the linked journey. */
enum class ConnectedTripLifecycle {
    CONFIRMED,
    DEPARTURE_PASSED,
    CANCELLED_BY_RIDER,
    CANCELLED_BY_DRIVER,
    UNAVAILABLE,
}

/** Request presentation derived from persisted request and linked-journey truth. */
enum class ConnectedRequestLifecycle {
    PENDING,
    ACCEPTED,
    DECLINED,
    CANCELLED,
    CANCELLED_AFTER_ACCEPTANCE,
    DEPARTURE_PASSED_PENDING,
    DEPARTURE_PASSED_ACCEPTED,
    CANCELLED_BY_DRIVER,
    UNAVAILABLE,
}

object ConnectedJourneyLifecycle {
    fun trip(
        trip: ConnectedConfirmedTrip,
        journey: ConnectedJourney?,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): ConnectedTripLifecycle = when {
        trip.status == ConnectedTripStatus.CANCELLED_BY_RIDER -> ConnectedTripLifecycle.CANCELLED_BY_RIDER
        journey == null || journey.id != trip.journeyId || journey.driverUid != trip.driverUid -> ConnectedTripLifecycle.UNAVAILABLE
        journey.status == ConnectedJourneyStatus.CANCELLED -> ConnectedTripLifecycle.CANCELLED_BY_DRIVER
        trip.departureEpochMillis <= nowEpochMillis -> ConnectedTripLifecycle.DEPARTURE_PASSED
        else -> ConnectedTripLifecycle.CONFIRMED
    }

    fun request(
        request: ConnectedSeatRequest,
        journey: ConnectedJourney?,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): ConnectedRequestLifecycle = when {
        request.status == ConnectedRequestStatus.CANCELLED_AFTER_ACCEPTANCE ->
            ConnectedRequestLifecycle.CANCELLED_AFTER_ACCEPTANCE
        request.status == ConnectedRequestStatus.CANCELLED -> ConnectedRequestLifecycle.CANCELLED
        request.status == ConnectedRequestStatus.DECLINED -> ConnectedRequestLifecycle.DECLINED
        requestCancelledByDriver(request, journey) -> ConnectedRequestLifecycle.CANCELLED_BY_DRIVER
        !requestJourneyOpen(request, journey) -> ConnectedRequestLifecycle.UNAVAILABLE
        journey != null && journey.departureEpochMillis <= nowEpochMillis && request.status == ConnectedRequestStatus.PENDING ->
            ConnectedRequestLifecycle.DEPARTURE_PASSED_PENDING
        journey != null && journey.departureEpochMillis <= nowEpochMillis && request.status == ConnectedRequestStatus.ACCEPTED ->
            ConnectedRequestLifecycle.DEPARTURE_PASSED_ACCEPTED
        request.status == ConnectedRequestStatus.PENDING -> ConnectedRequestLifecycle.PENDING
        else -> ConnectedRequestLifecycle.ACCEPTED
    }

    fun requestCancelledByDriver(request: ConnectedSeatRequest, journey: ConnectedJourney?): Boolean =
        (request.status == ConnectedRequestStatus.PENDING || request.status == ConnectedRequestStatus.ACCEPTED) &&
            journey != null && journey.id == request.journeyId && journey.driverUid == request.driverUid &&
            journey.status == ConnectedJourneyStatus.CANCELLED

    fun requestJourneyOpen(request: ConnectedSeatRequest, journey: ConnectedJourney?): Boolean =
        journey != null && journey.id == request.journeyId && journey.driverUid == request.driverUid && journey.status == ConnectedJourneyStatus.OPEN

    fun canCancelRequest(request: ConnectedSeatRequest, journey: ConnectedJourney?, uid: String): Boolean =
        request.riderUid == uid && request.status == ConnectedRequestStatus.PENDING &&
            requestJourneyOpen(request, journey)

    fun canCancelJourney(journey: ConnectedJourney, uid: String, nowEpochMillis: Long): Boolean =
        journey.driverUid == uid && journey.status == ConnectedJourneyStatus.OPEN && journey.departureEpochMillis > nowEpochMillis

    fun discoverable(journey: ConnectedJourney, uid: String, nowEpochMillis: Long): Boolean =
        journey.driverUid != uid && journey.status == ConnectedJourneyStatus.OPEN && journey.departureEpochMillis > nowEpochMillis

    fun canReadMessages(trip: ConnectedConfirmedTrip, uid: String): Boolean =
        validConfirmedTripIdentity(trip) && (trip.driverUid == uid || trip.riderUid == uid)

    fun validConfirmedTripIdentity(trip: ConnectedConfirmedTrip): Boolean =
        trip.id.isNotBlank() && trip.id == trip.acceptedRequestId &&
            trip.acceptedRequestId == "${trip.journeyId}_${trip.riderUid}" &&
            trip.driverUid.isNotBlank() && trip.riderUid.isNotBlank() && trip.driverUid != trip.riderUid &&
            ConnectedJourneyValidator.isBroadArea(trip.originArea) &&
            ConnectedJourneyValidator.isBroadArea(trip.destinationArea) &&
            !trip.originArea.equals(trip.destinationArea, ignoreCase = true)

    /** Departure passing is presentation truth, not proof that real-world coordination has ended. */
    fun canSendMessages(
        trip: ConnectedConfirmedTrip,
        journey: ConnectedJourney?,
        uid: String,
    ): Boolean = canReadMessages(trip, uid) &&
        trip.status == ConnectedTripStatus.CONFIRMED &&
        coordinationJourneyMatches(trip, journey) &&
        journey?.status == ConnectedJourneyStatus.OPEN

    fun coordinationJourneyMatches(trip: ConnectedConfirmedTrip, journey: ConnectedJourney?): Boolean =
        journey != null && journey.id == trip.journeyId && journey.driverUid == trip.driverUid &&
            journey.originArea == trip.originArea && journey.destinationArea == trip.destinationArea &&
            journey.departureEpochMillis == trip.departureEpochMillis
}
