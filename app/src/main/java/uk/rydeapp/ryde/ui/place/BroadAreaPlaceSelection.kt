package uk.rydeapp.ryde.ui.place

import uk.rydeapp.ryde.domain.PlaceMatch
import uk.rydeapp.ryde.domain.PlaceResolution
import uk.rydeapp.ryde.domain.model.GeographicCoordinate

internal enum class BroadAreaEndpoint { FROM, TO }

internal data class BroadAreaPlaceSelectionPrompt(
    val endpoint: BroadAreaEndpoint,
    val typedBroadArea: String,
    val candidates: List<PlaceMatch>,
)

/** Find may resolve either optional endpoint; Offer persists coordinates only when both are set. */
internal data class BroadAreaCoordinates(
    val from: GeographicCoordinate?,
    val to: GeographicCoordinate?,
)

/**
 * Shared provider-neutral state for two broad-area fields. Ambiguous results remain incomplete
 * until the user chooses one of the supplied matches. Missing, blank and failed endpoints remain
 * safely coordinate-less.
 */
internal class BroadAreaPlaceSelection private constructor(
    private val fromArea: String,
    private val toArea: String,
    private val from: EndpointSelection,
    private val to: EndpointSelection,
) {
    val prompt: BroadAreaPlaceSelectionPrompt?
        get() = from.prompt(BroadAreaEndpoint.FROM, fromArea)
            ?: to.prompt(BroadAreaEndpoint.TO, toArea)

    val isComplete: Boolean get() = prompt == null

    val coordinates: BroadAreaCoordinates
        get() {
            check(isComplete) { "Ambiguous places must be selected before reading coordinates." }
            return BroadAreaCoordinates(
                from = (from as? EndpointSelection.Selected)?.match?.coordinate,
                to = (to as? EndpointSelection.Selected)?.match?.coordinate,
            )
        }

    fun select(endpoint: BroadAreaEndpoint, match: PlaceMatch): BroadAreaPlaceSelection {
        val current = if (endpoint == BroadAreaEndpoint.FROM) from else to
        require(current is EndpointSelection.Ambiguous && match in current.matches) {
            "The selected broad area must be one of the offered candidates."
        }
        val selected = EndpointSelection.Selected(match)
        return if (endpoint == BroadAreaEndpoint.FROM) {
            BroadAreaPlaceSelection(fromArea, toArea, selected, to)
        } else {
            BroadAreaPlaceSelection(fromArea, toArea, from, selected)
        }
    }

    private fun EndpointSelection.prompt(
        endpoint: BroadAreaEndpoint,
        typedBroadArea: String,
    ): BroadAreaPlaceSelectionPrompt? = (this as? EndpointSelection.Ambiguous)?.let {
        BroadAreaPlaceSelectionPrompt(endpoint, typedBroadArea, it.matches)
    }

    private sealed interface EndpointSelection {
        data class Selected(val match: PlaceMatch) : EndpointSelection
        data class Ambiguous(val matches: List<PlaceMatch>) : EndpointSelection
        data object Unavailable : EndpointSelection
    }

    internal companion object {
        fun from(
            fromArea: String,
            toArea: String,
            from: PlaceResolution,
            to: PlaceResolution,
        ) = BroadAreaPlaceSelection(
            fromArea = fromArea,
            toArea = toArea,
            from = from.toEndpointSelection(),
            to = to.toEndpointSelection(),
        )

        private fun PlaceResolution.toEndpointSelection(): EndpointSelection = when (this) {
            PlaceResolution.NoMatches, PlaceResolution.Failure -> EndpointSelection.Unavailable
            is PlaceResolution.Unique -> EndpointSelection.Selected(match)
            is PlaceResolution.Multiple -> EndpointSelection.Ambiguous(matches)
        }
    }
}
