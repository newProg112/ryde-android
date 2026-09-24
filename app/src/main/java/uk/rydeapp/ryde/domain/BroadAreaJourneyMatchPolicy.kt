package uk.rydeapp.ryde.domain

import uk.rydeapp.ryde.domain.model.GeographicCoordinate

/** A complete pair of broad-area endpoints; labels and provider identifiers do not belong here. */
data class JourneyGeographicEndpoints(
    val origin: GeographicCoordinate,
    val destination: GeographicCoordinate,
)

sealed interface GeographicJourneyMatch {
    data class Compatible(
        val originDistance: GeographicDistance,
        val destinationDistance: GeographicDistance,
    ) : GeographicJourneyMatch

    data class Incompatible(
        val originDistance: GeographicDistance,
        val destinationDistance: GeographicDistance,
    ) : GeographicJourneyMatch

    data object InsufficientGeographicData : GeographicJourneyMatch
}

/** Centralized initial assumptions for replaceable broad-area matching. */
object BroadAreaJourneyMatchDefaults {
    /**
     * Fifteen kilometres is a conservative development tolerance for nearby town/district centres.
     * It is deliberately not a pickup radius, road distance or permitted driver detour.
     */
    val maximumEndpointDistance = GeographicDistance(15.0)
}

/** Both origin and destination must independently fall within the configured straight-line limit. */
class BroadAreaJourneyMatchPolicy(
    private val distanceCalculator: GeographicDistanceCalculator = HaversineGeographicDistanceCalculator,
    val maximumEndpointDistance: GeographicDistance = BroadAreaJourneyMatchDefaults.maximumEndpointDistance,
) {
    fun assess(
        rider: JourneyGeographicEndpoints?,
        offeredJourney: JourneyGeographicEndpoints?,
    ): GeographicJourneyMatch {
        if (rider == null || offeredJourney == null) {
            return GeographicJourneyMatch.InsufficientGeographicData
        }
        val originDistance = distanceCalculator.between(rider.origin, offeredJourney.origin)
        val destinationDistance = distanceCalculator.between(rider.destination, offeredJourney.destination)
        return if (
            originDistance <= maximumEndpointDistance &&
            destinationDistance <= maximumEndpointDistance
        ) {
            GeographicJourneyMatch.Compatible(originDistance, destinationDistance)
        } else {
            GeographicJourneyMatch.Incompatible(originDistance, destinationDistance)
        }
    }
}
