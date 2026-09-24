package uk.rydeapp.ryde.ui.map

import uk.rydeapp.ryde.domain.model.GeographicCoordinate

internal enum class JourneyMapPointRole {
    JOURNEY_START,
    RIDER_PICKUP,
    RIDER_DESTINATION,
    JOURNEY_DESTINATION,
}

internal data class JourneyMapPoint(
    val label: String,
    val role: JourneyMapPointRole,
    val coordinate: GeographicCoordinate? = null,
) {
    init {
        require(label.isNotBlank())
    }
}

/**
 * A line must state what it means. The fallback deliberately cannot be mistaken for road geometry.
 */
internal sealed interface JourneyMapLine {
    data class RoadRoute(val geometry: List<GeographicCoordinate>) : JourneyMapLine {
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

/** Builds a route boundary from persisted truth; two endpoints never imply road geometry. */
internal fun journeyMap(
    originArea: String,
    destinationArea: String,
    originCoordinate: GeographicCoordinate? = null,
    destinationCoordinate: GeographicCoordinate? = null,
): JourneyMapPresentation {
    require((originCoordinate == null) == (destinationCoordinate == null))
    return JourneyMapPresentation(
        points = listOf(
            JourneyMapPoint(originArea, JourneyMapPointRole.JOURNEY_START, originCoordinate),
            JourneyMapPoint(destinationArea, JourneyMapPointRole.JOURNEY_DESTINATION, destinationCoordinate),
        ),
        line = JourneyMapLine.VisualConnection,
    )
}

/** Legacy/missing-coordinate fallback retained as an explicit compatibility boundary. */
internal fun broadAreaJourneyMap(
    originArea: String,
    destinationArea: String,
): JourneyMapPresentation = journeyMap(originArea, destinationArea)
