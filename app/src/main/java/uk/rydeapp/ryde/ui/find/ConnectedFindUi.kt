package uk.rydeapp.ryde.ui.find

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
