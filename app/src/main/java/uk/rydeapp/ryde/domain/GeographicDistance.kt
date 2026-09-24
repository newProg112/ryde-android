package uk.rydeapp.ryde.domain

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import uk.rydeapp.ryde.domain.model.GeographicCoordinate

/** Provider-neutral straight-line surface distance, explicitly measured in kilometres. */
data class GeographicDistance(val kilometres: Double) : Comparable<GeographicDistance> {
    init {
        require(kilometres.isFinite() && kilometres >= 0.0) {
            "Geographic distance must be a finite, non-negative number of kilometres."
        }
    }

    override fun compareTo(other: GeographicDistance): Int = kilometres.compareTo(other.kilometres)
}

fun interface GeographicDistanceCalculator {
    fun between(from: GeographicCoordinate, to: GeographicCoordinate): GeographicDistance
}

/**
 * Deterministic great-circle distance over the WGS84 mean Earth radius. This is suitable for
 * broad-area comparison; it is not road distance, route geometry or a detour calculation.
 */
object HaversineGeographicDistanceCalculator : GeographicDistanceCalculator {
    private const val MEAN_EARTH_RADIUS_KILOMETRES = 6_371.0088

    override fun between(from: GeographicCoordinate, to: GeographicCoordinate): GeographicDistance {
        if (from == to) return GeographicDistance(0.0)
        val fromLatitude = Math.toRadians(from.latitude)
        val toLatitude = Math.toRadians(to.latitude)
        val latitudeDelta = toLatitude - fromLatitude
        val longitudeDelta = Math.toRadians(to.longitude - from.longitude)
        val haversine = sin(latitudeDelta / 2).let { it * it } +
            cos(fromLatitude) * cos(toLatitude) *
            sin(longitudeDelta / 2).let { it * it }
        // Floating-point rounding near antipodal points can otherwise put the value just above 1.
        val centralAngle = 2 * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
        return GeographicDistance(MEAN_EARTH_RADIUS_KILOMETRES * centralAngle)
    }
}
