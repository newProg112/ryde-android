package uk.rydeapp.ryde.domain.model

enum class DemoTravelDate(val displayName: String) {
    TODAY("Today"),
    TOMORROW("Tomorrow"),
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
) {
    val riderTotalPence: Int get() = contributionPence + serviceFeePence
    val driverReceivesPence: Int get() = contributionPence
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
