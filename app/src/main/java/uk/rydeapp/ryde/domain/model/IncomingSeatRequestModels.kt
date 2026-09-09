package uk.rydeapp.ryde.domain.model

data class DemoRiderProfile(
    val firstName: String,
    val rating: Double,
    val isDemoVerified: Boolean,
)

enum class IncomingSeatRequestStatus { PENDING, ACCEPTED, DECLINED }

data class IncomingSeatRequest(
    val id: String,
    val offeredJourneyId: String,
    val status: IncomingSeatRequestStatus,
    val rider: DemoRiderProfile,
    val originArea: String,
    val destinationArea: String,
    val travelDate: DemoTravelDate,
    val approximatePickupMinutes: Int,
    val pickupArea: String,
    val walkMinutes: Int,
    val detourMiles: Double,
    val requestedSeats: Int,
    val sharedMiles: Int,
    val contributionPence: Int,
    val serviceFeePence: Int,
    val serviceFeeResponsibility: ServiceFeeResponsibility = ServiceFeeResponsibility.RIDER,
    val circle: CircleIdentity? = null,
) {
    val pricing: JourneyPricing
        get() = JourneyPricing(contributionPence, serviceFeePence, serviceFeeResponsibility)
    val riderTotalPence: Int get() = pricing.riderTotalPence
    val driverReceivesPence: Int get() = pricing.driverReceivesPence
}

enum class IncomingRequestDecision { ACCEPT, DECLINE }

data class ConfirmedSharedTrip(
    val id: String,
    val offeredJourneyId: String,
    val incomingRequestId: String,
    val driverName: String,
    val rider: DemoRiderProfile,
    val originArea: String,
    val destinationArea: String,
    val travelDate: DemoTravelDate,
    val approximatePickupMinutes: Int,
    val pickupArea: String,
    val requestedSeats: Int,
    val remainingSpareSeats: Int,
    val sharedMiles: Int,
    val contributionPence: Int,
    val serviceFeePence: Int,
    val serviceFeeResponsibility: ServiceFeeResponsibility = ServiceFeeResponsibility.RIDER,
    val circle: CircleIdentity? = null,
) {
    val pricing: JourneyPricing
        get() = JourneyPricing(contributionPence, serviceFeePence, serviceFeeResponsibility)
    val riderTotalPence: Int get() = pricing.riderTotalPence
    val driverReceivesPence: Int get() = pricing.driverReceivesPence
}

sealed interface DecideIncomingRequestResult {
    data class Decided(
        val request: IncomingSeatRequest,
        val journey: OfferedJourney,
        val confirmedTrip: ConfirmedSharedTrip?,
    ) : DecideIncomingRequestResult

    data class AlreadyDecided(val request: IncomingSeatRequest?) : DecideIncomingRequestResult
    data class RelatedOfferUnavailable(val journey: OfferedJourney?) : DecideIncomingRequestResult
    data class NotEnoughSeats(val journey: OfferedJourney) : DecideIncomingRequestResult
}
