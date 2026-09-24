package uk.rydeapp.ryde.ui.find

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import uk.rydeapp.ryde.domain.BroadAreaJourneyMatchPolicy
import uk.rydeapp.ryde.domain.GeographicJourneyMatch
import uk.rydeapp.ryde.domain.JourneyGeographicEndpoints
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
): List<ConnectedHomeJourney> {
    // Coordinates intentionally cross this boundary for a later matching phase. Current CONNECTED
    // fixture semantics remain broad-area text/date matching only.
    val origin = criteria.origin.trim()
    val destination = criteria.destination.trim()
    return journeys.filter { item ->
        item.journey.originArea.contains(origin, ignoreCase = true) &&
            item.journey.destinationArea.contains(destination, ignoreCase = true) &&
            (criteria.departureDate == null ||
                Instant.ofEpochMilli(item.journey.departureEpochMillis)
                    .atZone(zoneId).toLocalDate() == criteria.departureDate)
    }
}

/** A text/date-visible Find item plus its separately evaluated geographic state. */
internal data class ConnectedFindJourneyResult(
    val item: ConnectedHomeJourney,
    val geographicMatch: GeographicJourneyMatch,
)

/**
 * Preserves every existing text/date result, including legacy journeys with no coordinates, while
 * exposing geographic compatibility for later filtering or presentation decisions.
 */
internal fun assessConnectedFindJourneys(
    journeys: List<ConnectedHomeJourney>,
    criteria: ConnectedFindCriteria,
    zoneId: ZoneId = ZoneId.systemDefault(),
    policy: BroadAreaJourneyMatchPolicy = BroadAreaJourneyMatchPolicy(),
): List<ConnectedFindJourneyResult> {
    val riderEndpoints = geographicEndpoints(
        criteria.originCoordinate,
        criteria.destinationCoordinate,
    )
    return filterConnectedFindJourneys(journeys, criteria, zoneId).map { item ->
        ConnectedFindJourneyResult(
            item = item,
            geographicMatch = policy.assess(
                rider = riderEndpoints,
                offeredJourney = geographicEndpoints(
                    item.journey.originCoordinate,
                    item.journey.destinationCoordinate,
                ),
            ),
        )
    }
}

private fun geographicEndpoints(
    origin: GeographicCoordinate?,
    destination: GeographicCoordinate?,
): JourneyGeographicEndpoints? = if (origin != null && destination != null) {
    JourneyGeographicEndpoints(origin, destination)
} else {
    null
}
