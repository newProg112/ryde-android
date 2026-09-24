package uk.rydeapp.ryde.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import uk.rydeapp.ryde.R

/** Area-only fallback shown until repository truth includes coordinates and route geometry. */
@Composable
internal fun JourneyRouteVisualisation(
    route: JourneyMapPresentation,
    modifier: Modifier = Modifier,
) {
    val start = route.points.first { it.role == JourneyMapPointRole.JOURNEY_START }
    val destination = route.points.last { it.role == JourneyMapPointRole.JOURNEY_DESTINATION }
    val description = stringResource(
        R.string.connected_route_visual_description,
        start.label,
        destination.label,
    )
    val routeColor = MaterialTheme.colorScheme.primary
    val destinationColor = MaterialTheme.colorScheme.tertiary
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .55f)

    Card(
        modifier = modifier.fillMaxWidth().testTag("connected-route-visual"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.connected_route_overview), style = MaterialTheme.typography.titleMedium)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .semantics { contentDescription = description },
            ) {
                val from = Offset(size.width * .12f, size.height * .72f)
                val to = Offset(size.width * .88f, size.height * .28f)
                val path = Path().apply {
                    moveTo(from.x, from.y)
                    cubicTo(
                        size.width * .36f,
                        size.height * .78f,
                        size.width * .60f,
                        size.height * .20f,
                        to.x,
                        to.y,
                    )
                }
                drawPath(
                    path,
                    trackColor,
                    style = Stroke(
                        width = 6.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 9.dp.toPx())),
                    ),
                )
                drawCircle(routeColor.copy(alpha = .20f), 13.dp.toPx(), from)
                drawCircle(routeColor, 7.dp.toPx(), from)
                drawCircle(destinationColor.copy(alpha = .20f), 13.dp.toPx(), to)
                drawCircle(destinationColor, 7.dp.toPx(), to)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f).testTag("connected-route-start")) {
                    Text(stringResource(R.string.connected_route_start), style = MaterialTheme.typography.labelMedium)
                    Text(start.label, style = MaterialTheme.typography.bodyLarge)
                }
                Column(Modifier.weight(1f).testTag("connected-route-destination")) {
                    Text(
                        stringResource(R.string.connected_route_destination),
                        Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.End,
                    )
                    Text(
                        destination.label,
                        Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.End,
                    )
                }
            }
            Text(
                stringResource(R.string.connected_route_visual_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.connected_route_pickup_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
