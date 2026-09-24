package uk.rydeapp.ryde.ui.map

/** Provider-neutral coordinates for a future map implementation. */
internal data class GeoCoordinate(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
    }
}

internal enum class JourneyMapPointRole {
    JOURNEY_START,
    RIDER_PICKUP,
    RIDER_DESTINATION,
    JOURNEY_DESTINATION,
}

internal data class JourneyMapPoint(
    val label: String,
    val role: JourneyMapPointRole,
    val coordinate: GeoCoordinate? = null,
) {
    init {
        require(label.isNotBlank())
    }
}

/**
 * A line must state what it means. The fallback deliberately cannot be mistaken for road geometry.
 */
internal sealed interface JourneyMapLine {
    data class RoadRoute(val geometry: List<GeoCoordinate>) : JourneyMapLine {
        init {
            require(geometry.size >= 2)
        }
    }

    data object VisualConnection : JourneyMapLine
}

/** UI-facing map contract; it contains no SDK/provider types and owns no journey state. */
internal data class JourneyMapPresentation(
    val points: List<JourneyMapPoint>,
    val line: JourneyMapLine,
) {
    init {
        require(points.size >= 2)
    }
}

/**
 * CONNECTED currently stores broad area names only. Keep that limitation explicit at the boundary.
 */
internal fun broadAreaJourneyMap(
    originArea: String,
    destinationArea: String,
): JourneyMapPresentation = JourneyMapPresentation(
    points = listOf(
        JourneyMapPoint(originArea, JourneyMapPointRole.JOURNEY_START),
        JourneyMapPoint(destinationArea, JourneyMapPointRole.JOURNEY_DESTINATION),
    ),
    line = JourneyMapLine.VisualConnection,
)
