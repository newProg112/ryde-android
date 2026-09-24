package uk.rydeapp.ryde.ui.find

import uk.rydeapp.ryde.domain.PlaceResolution
import uk.rydeapp.ryde.ui.place.BroadAreaPlaceSelection

/** Resolves only entered broad areas; blank optional filters never reach a provider. */
internal suspend fun resolveConnectedFindPlaces(
    criteria: ConnectedFindCriteria,
    resolvePlace: suspend (String) -> PlaceResolution,
): BroadAreaPlaceSelection = BroadAreaPlaceSelection.from(
    fromArea = criteria.origin,
    toArea = criteria.destination,
    from = criteria.origin.resolveIfEntered(resolvePlace),
    to = criteria.destination.resolveIfEntered(resolvePlace),
)

private suspend fun String.resolveIfEntered(
    resolvePlace: suspend (String) -> PlaceResolution,
): PlaceResolution = if (isBlank()) PlaceResolution.NoMatches else resolvePlace(this)
