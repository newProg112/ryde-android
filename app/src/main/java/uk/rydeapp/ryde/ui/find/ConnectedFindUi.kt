package uk.rydeapp.ryde.ui.find

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import uk.rydeapp.ryde.ui.home.ConnectedHomeJourney

/** Filters the supplied discovery presentation; never loads or changes journey state. */
internal data class ConnectedFindCriteria(
    val origin: String = "",
    val destination: String = "",
    val departureDate: LocalDate? = null,
) {
    val hasFilters: Boolean
        get() = origin.isNotBlank() || destination.isNotBlank() || departureDate != null
}

internal fun filterConnectedFindJourneys(
    journeys: List<ConnectedHomeJourney>,
    criteria: ConnectedFindCriteria,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<ConnectedHomeJourney> {
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
