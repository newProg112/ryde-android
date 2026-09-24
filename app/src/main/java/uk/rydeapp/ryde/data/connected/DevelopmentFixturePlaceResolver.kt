package uk.rydeapp.ryde.data.connected

import uk.rydeapp.ryde.domain.PlaceResolution
import uk.rydeapp.ryde.domain.PlaceResolver
import uk.rydeapp.ryde.domain.PlaceMatch
import uk.rydeapp.ryde.domain.model.GeographicCoordinate

/**
 * Deterministic emulator/development fixtures only. These points are not production geocoding
 * results and must not be expanded into a heuristic fallback for unknown labels.
 */
internal class DevelopmentFixturePlaceResolver : PlaceResolver {
    override suspend fun resolve(broadPlace: String): PlaceResolution =
        fixtures[broadPlace.trim().lowercase()]
            ?: PlaceResolution.NoMatches

    private companion object {
        val fixtures = mapOf(
            "mansfield" to PlaceResolution.Unique(
                PlaceMatch("Mansfield", GeographicCoordinate(53.1432, -1.1984)),
            ),
            "nottingham" to PlaceResolution.Unique(
                PlaceMatch("Nottingham", GeographicCoordinate(52.9548, -1.1581)),
            ),
            "richmond" to PlaceResolution.Multiple(
                listOf(
                    PlaceMatch("Richmond — Greater London", GeographicCoordinate(51.4613, -0.3037)),
                    PlaceMatch("Richmond — North Yorkshire", GeographicCoordinate(54.4037, -1.7375)),
                ),
            ),
        )
    }
}
