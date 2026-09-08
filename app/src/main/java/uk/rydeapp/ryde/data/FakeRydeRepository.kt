package uk.rydeapp.ryde.data

import uk.rydeapp.ryde.domain.ContributionCalculator
import uk.rydeapp.ryde.domain.model.DriverProfile
import uk.rydeapp.ryde.domain.model.HomeContent
import uk.rydeapp.ryde.domain.model.HostedCircle
import uk.rydeapp.ryde.domain.model.Journey
import uk.rydeapp.ryde.domain.model.RydeUser
import uk.rydeapp.ryde.domain.model.SavedPlace
import uk.rydeapp.ryde.domain.model.SuggestedMatch

object FakeRydeRepository : RydeRepository {
    override fun getHomeContent(): HomeContent {
        val sharedMiles = 17
        return HomeContent(
            currentUser = RydeUser(
                firstName = "Sam",
                savedPlaces = listOf(
                    SavedPlace(label = "Home", area = "Sutton-in-Ashfield"),
                    SavedPlace(label = "Work", area = "Nottingham"),
                ),
            ),
            suggestedMatch = SuggestedMatch(
                driver = DriverProfile(
                    firstName = "Alex",
                    rating = 4.9,
                    isDemoVerified = true,
                ),
                driverJourney = Journey(
                    origin = "Mansfield",
                    destination = "Nottingham",
                    departureTime = "08:00",
                ),
                riderJourney = Journey(
                    origin = "Sutton-in-Ashfield",
                    destination = "Nottingham",
                    departureTime = "08:05",
                ),
                sharedMiles = sharedMiles,
                contributionPence = ContributionCalculator.calculatePence(sharedMiles),
                serviceFeePence = 50,
            ),
            hostedCircle = HostedCircle(
                name = "Nottingham Live — Event Travel",
                summary = "Share routes with people heading to the same fictional event.",
                serviceFeeCoveredByHost = true,
            ),
        )
    }
}
