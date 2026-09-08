package uk.rydeapp.ryde.domain.model

data class RydeUser(
    val firstName: String,
    val savedPlaces: List<SavedPlace>,
)

data class SavedPlace(
    val label: String,
    val area: String,
)

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
    val riderTotalPence: Int get() = contributionPence + serviceFeePence
    val driverReceivesPence: Int get() = contributionPence
}

data class HostedCircle(
    val name: String,
    val summary: String,
    val serviceFeeCoveredByHost: Boolean,
)

data class HomeContent(
    val currentUser: RydeUser,
    val suggestedMatch: SuggestedMatch,
    val hostedCircle: HostedCircle,
)
