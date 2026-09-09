package uk.rydeapp.ryde.domain

import kotlin.math.abs
import uk.rydeapp.ryde.domain.model.FindRideCriteria
import uk.rydeapp.ryde.domain.model.FindRideField
import uk.rydeapp.ryde.domain.model.FindRideValidationError
import uk.rydeapp.ryde.domain.model.RouteMatch

object FindRideMatcher {
    fun validate(criteria: FindRideCriteria): List<FindRideValidationError> = buildList {
        if (criteria.origin.isBlank()) {
            add(FindRideValidationError(FindRideField.ORIGIN, "Enter an origin area"))
        }
        if (criteria.destination.isBlank()) {
            add(FindRideValidationError(FindRideField.DESTINATION, "Enter a destination area"))
        }
        if (criteria.origin.isNotBlank() &&
            criteria.destination.isNotBlank() &&
            criteria.origin.trim().equals(criteria.destination.trim(), ignoreCase = true)
        ) {
            add(FindRideValidationError(FindRideField.ENDPOINTS, "Origin and destination must be different"))
        }
        if (criteria.seatsRequired !in 1..4) {
            add(FindRideValidationError(FindRideField.SEATS, "Choose between 1 and 4 seats"))
        }
    }

    fun isWithinTimeWindow(criteria: FindRideCriteria, candidateMinutes: Int): Boolean =
        abs(candidateMinutes - criteria.departureMinutes) <= criteria.flexibility.minutes

    fun rankCompatible(
        criteria: FindRideCriteria,
        candidates: List<RouteMatch>,
    ): List<RouteMatch> {
        if (validate(criteria).isNotEmpty()) return emptyList()
        return candidates
            .asSequence()
            .filter { it.travelDate == criteria.travelDate }
            .filter { it.compatibleOrigin.equals(criteria.origin.trim(), ignoreCase = true) }
            .filter { it.compatibleDestination.equals(criteria.destination.trim(), ignoreCase = true) }
            .filter { isWithinTimeWindow(criteria, it.pickupMinutes) }
            .filter { it.availableSeats >= criteria.seatsRequired }
            .sortedWith(
                compareByDescending<RouteMatch> { it.matchScore }
                    .thenBy { abs(it.pickupMinutes - criteria.departureMinutes) }
                    .thenBy { it.driver.firstName },
            )
            .toList()
    }
}
