package uk.rydeapp.ryde.ui.map

import uk.rydeapp.ryde.domain.model.GeographicCoordinate

internal enum class JourneyMapPointRole {
    JOURNEY_START,
    JOURNEY_VIA,
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

/** Builds an ordered declared route from persisted truth; its points never imply road geometry. */
internal fun journeyMap(
    originArea: String,
    destinationArea: String,
    originCoordinate: GeographicCoordinate? = null,
    destinationCoordinate: GeographicCoordinate? = null,
    viaArea: String? = null,
    viaCoordinate: GeographicCoordinate? = null,
): JourneyMapPresentation {
    require((originCoordinate == null) == (destinationCoordinate == null))
    require((viaArea == null) == (viaCoordinate == null))
    require(viaArea == null || viaArea.isNotBlank())
    return JourneyMapPresentation(
        points = listOfNotNull(
            JourneyMapPoint(originArea, JourneyMapPointRole.JOURNEY_START, originCoordinate),
            viaArea?.let { JourneyMapPoint(it, JourneyMapPointRole.JOURNEY_VIA, viaCoordinate) },
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
