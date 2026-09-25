package uk.rydeapp.ryde.ui.find

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import uk.rydeapp.ryde.domain.BroadAreaJourneyMatchPolicy
import uk.rydeapp.ryde.domain.GeographicJourneyMatch
import uk.rydeapp.ryde.domain.GeographicJourneyMatchScore
import uk.rydeapp.ryde.domain.JourneyGeographicEndpoints
import uk.rydeapp.ryde.domain.combinedEndpointScore
import uk.rydeapp.ryde.domain.model.GeographicCoordinate
import uk.rydeapp.ryde.ui.home.ConnectedHomeJourney
import uk.rydeapp.ryde.ui.place.BroadAreaCoordinates

/** Filters the supplied discovery presentation; never loads or changes journey state. */
internal data class ConnectedFindCriteria(
    val origin: String = "",
    val destination: String = "",
    val departureDate: LocalDate? = null,
    val originCoordinate: GeographicCoordinate? = null,
    val destinationCoordinate: GeographicCoordinate? = null,
) {
    val hasFilters: Boolean
        get() = origin.isNotBlank() || destination.isNotBlank() || departureDate != null
}

internal fun ConnectedFindCriteria.sameTypedAreasAs(other: ConnectedFindCriteria): Boolean =
    origin == other.origin && destination == other.destination

internal fun ConnectedFindCriteria.withResolvedBroadAreas(
    coordinates: BroadAreaCoordinates,
): ConnectedFindCriteria = copy(
    originCoordinate = coordinates.from,
    destinationCoordinate = coordinates.to,
)

internal fun filterConnectedFindJourneys(
    journeys: List<ConnectedHomeJourney>,
    criteria: ConnectedFindCriteria,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<ConnectedHomeJourney> = journeys.filter { item ->
    textRouteMatches(item, criteria) && dateMatches(item, criteria, zoneId)
}

/** A Find item accepted by either geographic matching or the safe legacy text fallback. */
internal data class ConnectedFindJourneyResult(
    val item: ConnectedHomeJourney,
    val geographicMatch: GeographicJourneyMatch,
) {
    val geographicScore: GeographicJourneyMatchScore?
        get() = (geographicMatch as? GeographicJourneyMatch.Compatible)?.combinedEndpointScore()

    val geographicMatchExplanation: ConnectedFindGeographicMatchExplanation?
        get() = (geographicMatch as? GeographicJourneyMatch.Compatible)?.let { compatible ->
            ConnectedFindGeographicMatchExplanation(
                pickupAreaKilometres = compatible.originDistance.kilometres.roundToInt(),
                dropOffAreaKilometres = compatible.destinationDistance.kilometres.roundToInt(),
            )
        }
}

/** Compact rider-facing explanation derived from an already-compatible geographic match. */
internal data class ConnectedFindGeographicMatchExplanation(
    val pickupAreaKilometres: Int,
    val dropOffAreaKilometres: Int,
)

/**
 * Matches the already-authorized CONNECTED discovery presentation without loading journey state.
 *
 * Complete rider and journey coordinates are assessed by [BroadAreaJourneyMatchPolicy], which
 * compares both endpoints. If either coordinate pair is unavailable, matching falls back to the
 * existing broad-area text rule. That keeps legacy journeys discoverable by their entered areas
 * without treating missing geography as a positive or unbounded match. Date filtering always
 * applies.
 */
internal fun matchConnectedFindJourneys(
    journeys: List<ConnectedHomeJourney>,
    criteria: ConnectedFindCriteria,
    zoneId: ZoneId = ZoneId.systemDefault(),
    policy: BroadAreaJourneyMatchPolicy = BroadAreaJourneyMatchPolicy(),
): List<ConnectedFindJourneyResult> {
    val riderEndpoints = geographicEndpoints(
        criteria.originCoordinate,
        criteria.destinationCoordinate,
    )
    return journeys.mapNotNull { item ->
        if (!dateMatches(item, criteria, zoneId)) return@mapNotNull null
        val geographicMatch = policy.assess(
            rider = riderEndpoints,
            offeredJourney = geographicEndpoints(
                item.journey.originCoordinate,
                item.journey.destinationCoordinate,
            ),
        )
        val routeMatches = when (geographicMatch) {
            is GeographicJourneyMatch.Compatible -> true
            is GeographicJourneyMatch.Incompatible -> false
            GeographicJourneyMatch.InsufficientGeographicData -> textRouteMatches(item, criteria)
        }
        if (routeMatches) ConnectedFindJourneyResult(item, geographicMatch) else null
    }.sortedWith(connectedFindResultRanking)
}

private val connectedFindResultRanking =
    compareBy<ConnectedFindJourneyResult> { it.geographicScore == null }
        .thenBy { it.geographicScore }
        .thenBy { it.item.journey.departureEpochMillis }
        .thenBy { it.item.journey.id }

private fun textRouteMatches(
    item: ConnectedHomeJourney,
    criteria: ConnectedFindCriteria,
): Boolean = item.journey.originArea.contains(criteria.origin.trim(), ignoreCase = true) &&
    item.journey.destinationArea.contains(criteria.destination.trim(), ignoreCase = true)

private fun dateMatches(
    item: ConnectedHomeJourney,
    criteria: ConnectedFindCriteria,
    zoneId: ZoneId,
): Boolean = criteria.departureDate == null ||
    Instant.ofEpochMilli(item.journey.departureEpochMillis)
        .atZone(zoneId).toLocalDate() == criteria.departureDate

private fun geographicEndpoints(
    origin: GeographicCoordinate?,
    destination: GeographicCoordinate?,
): JourneyGeographicEndpoints? = if (origin != null && destination != null) {
    JourneyGeographicEndpoints(origin, destination)
} else {
    null
}
