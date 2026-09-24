package uk.rydeapp.ryde.domain

import uk.rydeapp.ryde.domain.model.GeographicCoordinate

/** Provider-neutral result of resolving one validated broad place label. */
sealed interface PlaceResolution {
    data class Resolved(val coordinate: GeographicCoordinate) : PlaceResolution
    data object NoResult : PlaceResolution
    data object Failure : PlaceResolution
}

/** A port for turning broad town/district labels into geographic coordinates. */
fun interface PlaceResolver {
    suspend fun resolve(broadPlace: String): PlaceResolution
}

/** Default when no resolver has been composed. It never invents a coordinate. */
object NoPlaceResolver : PlaceResolver {
    override suspend fun resolve(broadPlace: String): PlaceResolution = PlaceResolution.NoResult
}
