package uk.rydeapp.ryde.domain

/** Provider-neutral score for an already-compatible broad-area journey. */
data class GeographicJourneyMatchScore(
    val combinedEndpointDistance: GeographicDistance,
) : Comparable<GeographicJourneyMatchScore> {
    override fun compareTo(other: GeographicJourneyMatchScore): Int =
        combinedEndpointDistance.compareTo(other.combinedEndpointDistance)
}

/**
 * Scores only a result that has already qualified under [BroadAreaJourneyMatchPolicy].
 * A lower sum of origin and destination mismatch is the better broad-area fit.
 */
fun GeographicJourneyMatch.Compatible.combinedEndpointScore(): GeographicJourneyMatchScore =
    GeographicJourneyMatchScore(
        GeographicDistance(originDistance.kilometres + destinationDistance.kilometres),
    )
