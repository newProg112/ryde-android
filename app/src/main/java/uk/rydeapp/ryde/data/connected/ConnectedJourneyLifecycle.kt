package uk.rydeapp.ryde.data.connected

/** Resolved lifecycle; driver cancellation is persisted once on the linked journey. */
enum class ConnectedTripLifecycle { CONFIRMED, CANCELLED_BY_RIDER, CANCELLED_BY_DRIVER, UNAVAILABLE }

object ConnectedJourneyLifecycle {
    fun trip(trip: ConnectedConfirmedTrip, journey: ConnectedJourney?): ConnectedTripLifecycle = when {
        trip.status == ConnectedTripStatus.CANCELLED_BY_RIDER -> ConnectedTripLifecycle.CANCELLED_BY_RIDER
        journey == null || journey.id != trip.journeyId || journey.driverUid != trip.driverUid -> ConnectedTripLifecycle.UNAVAILABLE
        journey.status == ConnectedJourneyStatus.CANCELLED -> ConnectedTripLifecycle.CANCELLED_BY_DRIVER
        else -> ConnectedTripLifecycle.CONFIRMED
    }

    fun requestCancelledByDriver(request: ConnectedSeatRequest, journey: ConnectedJourney?): Boolean =
        (request.status == ConnectedRequestStatus.PENDING || request.status == ConnectedRequestStatus.ACCEPTED) &&
            journey != null && journey.id == request.journeyId && journey.driverUid == request.driverUid &&
            journey.status == ConnectedJourneyStatus.CANCELLED

    fun requestJourneyOpen(request: ConnectedSeatRequest, journey: ConnectedJourney?): Boolean =
        journey != null && journey.id == request.journeyId && journey.driverUid == request.driverUid && journey.status == ConnectedJourneyStatus.OPEN

    fun canCancelJourney(journey: ConnectedJourney, uid: String, nowEpochMillis: Long): Boolean =
        journey.driverUid == uid && journey.status == ConnectedJourneyStatus.OPEN && journey.departureEpochMillis > nowEpochMillis

    fun discoverable(journey: ConnectedJourney, uid: String, nowEpochMillis: Long): Boolean =
        journey.driverUid != uid && journey.status == ConnectedJourneyStatus.OPEN && journey.departureEpochMillis > nowEpochMillis
}
