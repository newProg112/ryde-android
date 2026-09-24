package uk.rydeapp.ryde.data.connected

import uk.rydeapp.ryde.domain.PlaceResolution
import uk.rydeapp.ryde.domain.PlaceResolver
import uk.rydeapp.ryde.domain.model.GeographicCoordinate

/**
 * Deterministic emulator/development fixtures only. These points are not production geocoding
 * results and must not be expanded into a heuristic fallback for unknown labels.
 */
internal class DevelopmentFixturePlaceResolver : PlaceResolver {
    override suspend fun resolve(broadPlace: String): PlaceResolution =
        fixtures[broadPlace.trim().lowercase()]
            ?.let(PlaceResolution::Resolved)
            ?: PlaceResolution.NoResult

    private companion object {
        val fixtures = mapOf(
            "mansfield" to GeographicCoordinate(53.1432, -1.1984),
            "nottingham" to GeographicCoordinate(52.9548, -1.1581),
        )
    }
}
