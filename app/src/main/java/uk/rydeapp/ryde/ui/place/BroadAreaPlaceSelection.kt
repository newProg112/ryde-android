package uk.rydeapp.ryde.ui.place

import uk.rydeapp.ryde.domain.PlaceMatch
import uk.rydeapp.ryde.domain.PlaceResolution
import uk.rydeapp.ryde.domain.model.GeographicCoordinate

internal enum class BroadAreaEndpoint { FROM, VIA, TO }

internal data class BroadAreaPlaceSelectionPrompt(
    val endpoint: BroadAreaEndpoint,
    val typedBroadArea: String,
    val candidates: List<PlaceMatch>,
)

/** Find resolves endpoints; Offer may additionally resolve one privacy-preserving Via area. */
internal data class BroadAreaCoordinates(
    val from: GeographicCoordinate?,
    val to: GeographicCoordinate?,
    val via: GeographicCoordinate? = null,
)

/**
 * Shared provider-neutral state for broad-area endpoint fields and Offer's optional Via. Ambiguous results remain incomplete
 * until the user chooses one of the supplied matches. Missing, blank and failed endpoints remain
 * safely coordinate-less.
 */
internal class BroadAreaPlaceSelection private constructor(
    private val fromArea: String,
    private val toArea: String,
    private val from: EndpointSelection,
    private val to: EndpointSelection,
    private val viaArea: String? = null,
    private val via: EndpointSelection? = null,
) {
    val prompt: BroadAreaPlaceSelectionPrompt?
        get() = from.prompt(BroadAreaEndpoint.FROM, fromArea)
            ?: via?.prompt(BroadAreaEndpoint.VIA, checkNotNull(viaArea))
            ?: to.prompt(BroadAreaEndpoint.TO, toArea)

    val isComplete: Boolean get() = prompt == null
    val hasUnavailableVia: Boolean get() = via is EndpointSelection.Unavailable

    val coordinates: BroadAreaCoordinates
        get() {
            check(isComplete) { "Ambiguous places must be selected before reading coordinates." }
            return BroadAreaCoordinates(
                from = (from as? EndpointSelection.Selected)?.match?.coordinate,
                to = (to as? EndpointSelection.Selected)?.match?.coordinate,
                via = (via as? EndpointSelection.Selected)?.match?.coordinate,
            )
        }

    fun select(endpoint: BroadAreaEndpoint, match: PlaceMatch): BroadAreaPlaceSelection {
        val current = when (endpoint) {
            BroadAreaEndpoint.FROM -> from
            BroadAreaEndpoint.VIA -> via
            BroadAreaEndpoint.TO -> to
        }
        require(current is EndpointSelection.Ambiguous && match in current.matches) {
            "The selected broad area must be one of the offered candidates."
        }
        val selected = EndpointSelection.Selected(match)
        return when (endpoint) {
            BroadAreaEndpoint.FROM -> BroadAreaPlaceSelection(fromArea, toArea, selected, to, viaArea, via)
            BroadAreaEndpoint.VIA -> BroadAreaPlaceSelection(fromArea, toArea, from, to, viaArea, selected)
            BroadAreaEndpoint.TO -> BroadAreaPlaceSelection(fromArea, toArea, from, selected, viaArea, via)
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

        fun forOffer(
            fromArea: String,
            viaArea: String,
            toArea: String,
            from: PlaceResolution,
            via: PlaceResolution,
            to: PlaceResolution,
        ) = BroadAreaPlaceSelection(
            fromArea = fromArea,
            toArea = toArea,
            from = from.toEndpointSelection(),
            to = to.toEndpointSelection(),
            viaArea = viaArea.takeIf(String::isNotBlank),
            via = via.takeIf { viaArea.isNotBlank() }?.toEndpointSelection(),
        )

        private fun PlaceResolution.toEndpointSelection(): EndpointSelection = when (this) {
            PlaceResolution.NoMatches, PlaceResolution.Failure -> EndpointSelection.Unavailable
            is PlaceResolution.Unique -> EndpointSelection.Selected(match)
            is PlaceResolution.Multiple -> EndpointSelection.Ambiguous(matches)
        }
    }
}
