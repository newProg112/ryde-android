package uk.rydeapp.ryde.domain.model

import java.util.Locale

data class OfferRideCriteria(
    val originArea: String,
    val destinationArea: String,
    val travelDate: DemoTravelDate,
    val departureMinutes: Int,
    val flexibility: Flexibility,
    val spareSeats: Int,
    val maximumDetourMiles: Int,
)

data class OfferRideContent(
    val savedPlaces: List<SavedPlace>,
    val defaultCriteria: OfferRideCriteria,
)

enum class OfferedJourneyStatus { OPEN, CONFIRMED, CANCELLED }

data class OfferedJourney(
    val id: String,
    val status: OfferedJourneyStatus,
    val originArea: String,
    val destinationArea: String,
    val travelDate: DemoTravelDate,
    val departureMinutes: Int,
    val flexibility: Flexibility,
    val spareSeats: Int,
    val maximumDetourMiles: Int,
)

enum class OfferRideField { ORIGIN, DESTINATION, ENDPOINTS, DEPARTURE_TIME, SEATS, DETOUR }

data class OfferRideValidationError(
    val field: OfferRideField,
    val message: String,
)

sealed interface CreateOfferedJourneyResult {
    data class Created(val journey: OfferedJourney) : CreateOfferedJourneyResult
    data class DuplicateActive(val journey: OfferedJourney) : CreateOfferedJourneyResult
    data class Invalid(val errors: List<OfferRideValidationError>) : CreateOfferedJourneyResult
}

sealed interface CancelOfferedJourneyResult {
    data class Cancelled(val journey: OfferedJourney) : CancelOfferedJourneyResult
    data class NotOpen(val journey: OfferedJourney?) : CancelOfferedJourneyResult
}

object OfferRideValidator {
    private val whitespace = Regex("\\s+")
    private val validDetours = setOf(1, 3, 5)

    fun normalizeArea(value: String): String = value.trim().replace(whitespace, " ")

    fun routeKey(value: String): String = normalizeArea(value).lowercase(Locale.ROOT)

    fun normalize(criteria: OfferRideCriteria): OfferRideCriteria = criteria.copy(
        originArea = normalizeArea(criteria.originArea),
        destinationArea = normalizeArea(criteria.destinationArea),
    )

    fun validate(criteria: OfferRideCriteria): List<OfferRideValidationError> {
        val normalized = normalize(criteria)
        return buildList {
            if (normalized.originArea.isEmpty()) {
                add(OfferRideValidationError(OfferRideField.ORIGIN, "Enter an origin area"))
            }
            if (normalized.destinationArea.isEmpty()) {
                add(OfferRideValidationError(OfferRideField.DESTINATION, "Enter a destination area"))
            }
            if (
                normalized.originArea.isNotEmpty() &&
                normalized.destinationArea.isNotEmpty() &&
                routeKey(normalized.originArea) == routeKey(normalized.destinationArea)
            ) {
                add(OfferRideValidationError(OfferRideField.ENDPOINTS, "Origin and destination must be different"))
            }
            if (criteria.departureMinutes !in 0 until 24 * 60) {
                add(OfferRideValidationError(OfferRideField.DEPARTURE_TIME, "Choose a valid departure time"))
            }
            if (criteria.spareSeats !in 1..4) {
                add(OfferRideValidationError(OfferRideField.SEATS, "Choose between 1 and 4 spare seats"))
            }
            if (criteria.maximumDetourMiles !in validDetours) {
                add(OfferRideValidationError(OfferRideField.DETOUR, "Choose a maximum detour of 1, 3 or 5 miles"))
            }
        }
    }
}
