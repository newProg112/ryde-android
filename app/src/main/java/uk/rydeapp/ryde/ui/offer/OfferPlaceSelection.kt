package uk.rydeapp.ryde.ui.offer

import uk.rydeapp.ryde.domain.PlaceMatch
import uk.rydeapp.ryde.domain.PlaceResolution
import uk.rydeapp.ryde.domain.model.GeographicCoordinate

internal enum class OfferPlaceEndpoint { ORIGIN, DESTINATION }

internal data class OfferPlaceSelectionPrompt(
    val endpoint: OfferPlaceEndpoint,
    val typedBroadArea: String,
    val candidates: List<PlaceMatch>,
)

internal data class OfferJourneyCoordinates(
    val origin: GeographicCoordinate,
    val destination: GeographicCoordinate,
)

/**
 * Provider-neutral state for resolving an offer. Ambiguous results remain incomplete until the
 * user explicitly chooses one of the supplied matches. Missing/failed endpoints stay unavailable.
 */
internal class OfferPlaceSelection private constructor(
    private val originArea: String,
    private val destinationArea: String,
    private val origin: EndpointSelection,
    private val destination: EndpointSelection,
) {
    val prompt: OfferPlaceSelectionPrompt?
        get() = origin.prompt(OfferPlaceEndpoint.ORIGIN, originArea)
            ?: destination.prompt(OfferPlaceEndpoint.DESTINATION, destinationArea)

    val isComplete: Boolean get() = prompt == null

    val coordinates: OfferJourneyCoordinates?
        get() {
            check(isComplete) { "Ambiguous places must be selected before reading coordinates." }
            val selectedOrigin = (origin as? EndpointSelection.Selected)?.match ?: return null
            val selectedDestination = (destination as? EndpointSelection.Selected)?.match ?: return null
            return OfferJourneyCoordinates(selectedOrigin.coordinate, selectedDestination.coordinate)
        }

    fun select(endpoint: OfferPlaceEndpoint, match: PlaceMatch): OfferPlaceSelection {
        val current = if (endpoint == OfferPlaceEndpoint.ORIGIN) origin else destination
        require(current is EndpointSelection.Ambiguous && match in current.matches) {
            "The selected broad area must be one of the offered candidates."
        }
        val selected = EndpointSelection.Selected(match)
        return if (endpoint == OfferPlaceEndpoint.ORIGIN) {
            OfferPlaceSelection(originArea, destinationArea, selected, destination)
        } else {
            OfferPlaceSelection(originArea, destinationArea, origin, selected)
        }
    }

    private fun EndpointSelection.prompt(
        endpoint: OfferPlaceEndpoint,
        typedBroadArea: String,
    ): OfferPlaceSelectionPrompt? = (this as? EndpointSelection.Ambiguous)?.let {
        OfferPlaceSelectionPrompt(endpoint, typedBroadArea, it.matches)
    }

    private sealed interface EndpointSelection {
        data class Selected(val match: PlaceMatch) : EndpointSelection
        data class Ambiguous(val matches: List<PlaceMatch>) : EndpointSelection
        data object Unavailable : EndpointSelection
    }

    internal companion object {
        fun from(
            originArea: String,
            destinationArea: String,
            origin: PlaceResolution,
            destination: PlaceResolution,
        ) = OfferPlaceSelection(
            originArea = originArea,
            destinationArea = destinationArea,
            origin = origin.toEndpointSelection(),
            destination = destination.toEndpointSelection(),
        )

        private fun PlaceResolution.toEndpointSelection(): EndpointSelection = when (this) {
            PlaceResolution.NoMatches, PlaceResolution.Failure -> EndpointSelection.Unavailable
            is PlaceResolution.Unique -> EndpointSelection.Selected(match)
            is PlaceResolution.Multiple -> EndpointSelection.Ambiguous(matches)
        }
    }
}
