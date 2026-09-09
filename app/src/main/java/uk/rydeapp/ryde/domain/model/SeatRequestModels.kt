package uk.rydeapp.ryde.domain.model

enum class SeatRequestStatus {
    PENDING,
    CANCELLED,
}

data class SeatRequest(
    val id: String,
    val matchId: String,
    val status: SeatRequestStatus,
    val driver: DriverProfile,
    val originArea: String,
    val destinationArea: String,
    val travelDate: DemoTravelDate,
    val approximatePickupMinutes: Int,
    val pickupArea: String,
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

sealed interface CreateSeatRequestResult {
    data class Created(val request: SeatRequest) : CreateSeatRequestResult
    data class DuplicateActive(val request: SeatRequest) : CreateSeatRequestResult
    data object MatchNotFound : CreateSeatRequestResult
    data object InvalidSeatCount : CreateSeatRequestResult
}

sealed interface CancelSeatRequestResult {
    data class Cancelled(val request: SeatRequest) : CancelSeatRequestResult
    data class NotPending(val request: SeatRequest?) : CancelSeatRequestResult
}
