package uk.rydeapp.ryde.domain.model

data class RydeUser(
    val firstName: String,
    val savedPlaces: List<SavedPlace>,
)

data class SavedPlace(
    val label: String,
    val area: String,
) {
    val displayArea: String get() = area
}

data class DriverProfile(
    val firstName: String,
    val rating: Double,
    val isDemoVerified: Boolean,
)

data class Journey(
    val origin: String,
    val destination: String,
    val departureTime: String,
)

data class SuggestedMatch(
    val driver: DriverProfile,
    val driverJourney: Journey,
    val riderJourney: Journey,
    val sharedMiles: Int,
    val contributionPence: Int,
    val serviceFeePence: Int,
) {
    val pricing: JourneyPricing
        get() = JourneyPricing(contributionPence, serviceFeePence, ServiceFeeResponsibility.RIDER)
    val riderTotalPence: Int get() = pricing.riderTotalPence
    val driverReceivesPence: Int get() = pricing.driverReceivesPence
}

data class HostedCircle(
    val id: String,
    val name: String,
    val hostName: String,
    val type: String,
    val location: String,
    val status: String,
    val summary: String,
    val purpose: String,
    val destinationArea: String,
    val eventDate: DemoTravelDate,
    val eventTime: String,
    val illustrativeMembers: Int,
    val illustrativeTrips: Int,
    val serviceFeeCoveredByHost: Boolean,
) {
    val identity: CircleIdentity get() = CircleIdentity(id, name)
}

data class HomeContent(
    val currentUser: RydeUser,
    val suggestedMatch: SuggestedMatch,
    val hostedCircle: HostedCircle,
)
