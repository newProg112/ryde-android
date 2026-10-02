package uk.rydeapp.ryde.ui.map

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdate
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import uk.rydeapp.ryde.R

private data class GoogleJourneyPoint(
    val label: String,
    val role: JourneyMapPointRole,
    val position: LatLng,
)

/** Google-specific adapter. Provider types and coordinate conversion are contained in this file. */
@Composable
internal fun GoogleJourneyMapPreview(
    route: JourneyMapPresentation,
    accessibilityDescription: String,
    modifier: Modifier = Modifier,
) {
    val mapPoints = remember(route) {
        route.points
            .filter { point ->
                point.role == JourneyMapPointRole.JOURNEY_START ||
                    point.role == JourneyMapPointRole.JOURNEY_VIA ||
                    point.role == JourneyMapPointRole.JOURNEY_DESTINATION
            }
            .map { point ->
                val coordinate = checkNotNull(point.coordinate)
                GoogleJourneyPoint(
                    label = point.label,
                    role = point.role,
                    position = LatLng(coordinate.latitude, coordinate.longitude),
                )
            }
    }
    require(mapPoints.size >= 2)

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(mapPoints.first().position, SINGLE_POINT_ZOOM)
    }
    val cameraPadding = with(LocalDensity.current) { CAMERA_PADDING.roundToPx() }
    var mapLoaded by remember { mutableStateOf(false) }
    val markerDescriptions = mapPoints.associateWith { point ->
        when (point.role) {
            JourneyMapPointRole.JOURNEY_START -> stringResource(
                R.string.connected_route_geographic_marker_start,
                point.label,
            )
            JourneyMapPointRole.JOURNEY_VIA -> stringResource(
                R.string.connected_route_geographic_marker_via,
                point.label,
            )
            JourneyMapPointRole.JOURNEY_DESTINATION -> stringResource(
                R.string.connected_route_geographic_marker_destination,
                point.label,
            )
            JourneyMapPointRole.RIDER_PICKUP,
            JourneyMapPointRole.RIDER_DESTINATION,
            -> error("Rider points are outside Maps Phase 1")
        }
    }

    LaunchedEffect(mapLoaded, mapPoints, cameraPadding) {
        if (mapLoaded) {
            cameraPositionState.move(cameraUpdate(mapPoints.map(GoogleJourneyPoint::position), cameraPadding))
        }
    }

    val visualConnectionColor = MaterialTheme.colorScheme.primary.copy(alpha = .72f)
    GoogleMap(
        modifier = modifier
            .fillMaxWidth()
            .height(MAP_HEIGHT)
            .testTag("connected-route-geographic-preview")
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityDescription
            },
        googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) },
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = false),
        uiSettings = MapUiSettings(
            compassEnabled = false,
            indoorLevelPickerEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
            rotationGesturesEnabled = false,
            scrollGesturesEnabled = false,
            scrollGesturesEnabledDuringRotateOrZoom = false,
            tiltGesturesEnabled = false,
            zoomControlsEnabled = false,
            zoomGesturesEnabled = false,
        ),
        onMapLoaded = { mapLoaded = true },
    ) {
        if (route.line is JourneyMapLine.VisualConnection) {
            Polyline(
                points = mapPoints.map(GoogleJourneyPoint::position),
                clickable = false,
                color = visualConnectionColor,
                geodesic = false,
                pattern = listOf(Dash(16f), Gap(10f)),
                width = 5f,
            )
        }
        mapPoints.forEach { point ->
            Marker(
                state = rememberUpdatedMarkerState(point.position),
                contentDescription = markerDescriptions.getValue(point),
                draggable = false,
                title = point.label,
            )
        }
    }
}

private fun cameraUpdate(points: List<LatLng>, padding: Int): CameraUpdate {
    if (points.distinct().size == 1) {
        return CameraUpdateFactory.newLatLngZoom(points.first(), SINGLE_POINT_ZOOM)
    }
    val bounds = LatLngBounds.builder().apply {
        points.forEach(::include)
    }.build()
    return CameraUpdateFactory.newLatLngBounds(bounds, padding)
}

private val MAP_HEIGHT = 180.dp
private val CAMERA_PADDING = 32.dp
private const val SINGLE_POINT_ZOOM = 11f
