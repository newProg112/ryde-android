package uk.rydeapp.ryde.domain

import uk.rydeapp.ryde.domain.model.GeographicCoordinate

/** Ordered broad-area centres declared by a driver; this is not road geometry. */
data class JourneyGeographicRoute(val points: List<GeographicCoordinate>) {
    init {
        require(points.size >= 3) { "A route overlap requires origin, Via and destination points." }
    }
}

/**
 * Associates each rider endpoint with its nearest declared route point, then enforces route order.
 * Nearest-point association prevents a generous broad-area radius from making a reversed journey
 * appear forward-compatible through a different, less appropriate point.
 */
class BroadAreaRouteOverlapPolicy(
    private val distanceCalculator: GeographicDistanceCalculator = HaversineGeographicDistanceCalculator,
    val maximumEndpointDistance: GeographicDistance = BroadAreaJourneyMatchDefaults.maximumEndpointDistance,
) {
    fun assess(
        rider: JourneyGeographicEndpoints?,
        offeredRoute: JourneyGeographicRoute?,
    ): GeographicJourneyMatch {
        if (rider == null || offeredRoute == null) {
            return GeographicJourneyMatch.InsufficientGeographicData
        }
        val originDistances = offeredRoute.points.map { distanceCalculator.between(rider.origin, it) }
        val destinationDistances = offeredRoute.points.map { distanceCalculator.between(rider.destination, it) }
        val originIndex = originDistances.indices.minBy { originDistances[it] }
        val destinationIndex = destinationDistances.indices.minBy { destinationDistances[it] }
        val originDistance = originDistances[originIndex]
        val destinationDistance = destinationDistances[destinationIndex]
        return if (
            originIndex <= destinationIndex &&
            originDistance <= maximumEndpointDistance &&
            destinationDistance <= maximumEndpointDistance
        ) {
            GeographicJourneyMatch.Compatible(originDistance, destinationDistance)
        } else {
            GeographicJourneyMatch.Incompatible(originDistance, destinationDistance)
        }
    }
}
