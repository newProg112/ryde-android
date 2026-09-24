package uk.rydeapp.ryde.domain

import uk.rydeapp.ryde.domain.model.GeographicCoordinate

/** The minimum provider-neutral information Ryde needs for a broad-area choice. */
data class PlaceMatch(
    val broadAreaLabel: String,
    val coordinate: GeographicCoordinate,
) {
    init {
        require(broadAreaLabel.isNotBlank() && broadAreaLabel.length <= 80)
        require(broadAreaLabel.none { it.isDigit() || it == ',' || it.isISOControl() })
    }
}

/** Provider-neutral result of resolving one validated broad place label. */
sealed interface PlaceResolution {
    data object NoMatches : PlaceResolution
    data class Unique(val match: PlaceMatch) : PlaceResolution
    data class Multiple(val matches: List<PlaceMatch>) : PlaceResolution {
        init {
            require(matches.size >= 2)
            require(matches.distinct() == matches)
            require(matches.distinctBy { it.broadAreaLabel.lowercase() }.size == matches.size)
        }
    }
    data object Failure : PlaceResolution
}

/** A port for turning broad town/district labels into geographic coordinates. */
fun interface PlaceResolver {
    suspend fun resolve(broadPlace: String): PlaceResolution
}

/** Default when no resolver has been composed. It never invents a coordinate. */
object NoPlaceResolver : PlaceResolver {
    override suspend fun resolve(broadPlace: String): PlaceResolution = PlaceResolution.NoMatches
}
