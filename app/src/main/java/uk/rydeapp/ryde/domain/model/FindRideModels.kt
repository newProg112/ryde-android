package uk.rydeapp.ryde.domain.model

enum class DemoTravelDate(
    private val dayLabel: String,
    val calendarDate: String? = null,
) {
    TODAY("Today"),
    TOMORROW("Tomorrow"),
    EVENT_DAY("Event day", "18 October 2026");

    val displayName: String
        get() = calendarDate?.let { "$dayLabel · $it" } ?: dayLabel

    val isOrdinaryChoice: Boolean get() = this != EVENT_DAY

    companion object {
        val ordinaryChoices: List<DemoTravelDate> = listOf(TODAY, TOMORROW)
    }
}

object TravelDatePolicy {
    fun forMode(ordinaryDate: DemoTravelDate, circle: HostedCircle?): DemoTravelDate =
        circle?.eventDate ?: ordinaryDate.takeIf { it.isOrdinaryChoice } ?: DemoTravelDate.TODAY
}

data class DemoMatchTiming(
    val driverDepartureMinutes: Int,
    val pickupMinutes: Int,
)

object DemoDepartureTimePolicy {
    const val ORDINARY_FIND_DEFAULT = 8 * 60 + 5
    const val ORDINARY_OFFER_DEFAULT = 8 * 60
    const val CIRCLE_DEFAULT = 17 * 60 + 30
    const val EVENT_DOORS = 18 * 60 + 30

    val ordinaryFindChoices = listOf(7 * 60 + 35, ORDINARY_FIND_DEFAULT, 8 * 60 + 35)
    val ordinaryOfferChoices = listOf(7 * 60 + 30, ORDINARY_OFFER_DEFAULT, 8 * 60 + 30)
    val circleChoices = listOf(17 * 60, CIRCLE_DEFAULT, 18 * 60)

    private val circleMatchTimings = listOf(
        DemoMatchTiming(driverDepartureMinutes = 17 * 60 + 25, pickupMinutes = 17 * 60 + 30),
        DemoMatchTiming(driverDepartureMinutes = 16 * 60 + 55, pickupMinutes = 17 * 60 + 5),
        DemoMatchTiming(driverDepartureMinutes = 17 * 60 + 45, pickupMinutes = 18 * 60),
    )

    fun resolveFind(requestedMinutes: Int, isCircleMode: Boolean): Int = when {
        isCircleMode && requestedMinutes in circleChoices -> requestedMinutes
        isCircleMode -> CIRCLE_DEFAULT
        requestedMinutes in ordinaryFindChoices -> requestedMinutes
        else -> ORDINARY_FIND_DEFAULT
    }

    fun resolveOffer(requestedMinutes: Int, isCircleMode: Boolean): Int = when {
        isCircleMode && requestedMinutes in circleChoices -> requestedMinutes
        isCircleMode -> CIRCLE_DEFAULT
        requestedMinutes in ordinaryOfferChoices -> requestedMinutes
        else -> ORDINARY_OFFER_DEFAULT
    }

    fun circleMatchTiming(rank: Int): DemoMatchTiming =
        circleMatchTimings.getOrElse(rank) { circleMatchTimings.last() }

    val eventDoorsDisplayName: String get() = "Doors ${formatDemoTime(EVENT_DOORS)} · fictional demo time"
}

enum class Flexibility(val minutes: Int, val displayName: String) {
    FIFTEEN(15, "±15 min"),
    THIRTY(30, "±30 min"),
    SIXTY(60, "±60 min"),
}

data class FindRideCriteria(
    val origin: String,
    val destination: String,
    val travelDate: DemoTravelDate,
    val departureMinutes: Int,
    val flexibility: Flexibility,
    val seatsRequired: Int,
    val circleId: String? = null,
)

enum class FindRideField { ORIGIN, DESTINATION, ENDPOINTS, SEATS }

data class FindRideValidationError(
    val field: FindRideField,
    val message: String,
)

data class RouteMatch(
    val id: String,
    val driver: DriverProfile,
    val driverJourney: Journey,
    val compatibleOrigin: String,
    val compatibleDestination: String,
    val travelDate: DemoTravelDate,
    val pickupArea: String,
    val pickupMinutes: Int,
    val walkMinutes: Int,
    val sharedMiles: Int,
    val detourMiles: Double,
    val availableSeats: Int,
    val matchScore: Int,
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

data class FindRideContent(
    val savedPlaces: List<SavedPlace>,
    val defaultCriteria: FindRideCriteria,
)

data class FindRideSearchResult(
    val criteria: FindRideCriteria,
    val validationErrors: List<FindRideValidationError>,
    val matches: List<RouteMatch>,
) {
    val isValid: Boolean get() = validationErrors.isEmpty()
}

fun formatDemoTime(minutesAfterMidnight: Int): String {
    require(minutesAfterMidnight in 0 until 24 * 60)
    return "%02d:%02d".format(minutesAfterMidnight / 60, minutesAfterMidnight % 60)
}
