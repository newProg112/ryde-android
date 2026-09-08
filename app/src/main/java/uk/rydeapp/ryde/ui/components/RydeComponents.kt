package uk.rydeapp.ryde.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uk.rydeapp.ryde.ui.theme.ElectricBlue
import uk.rydeapp.ryde.ui.theme.Mint

@Composable
fun RouteMark(
    contentDescription: String,
    modifier: Modifier = Modifier,
    primaryColor: Color = ElectricBlue,
    mergeColor: Color = Mint,
) {
    Canvas(
        modifier = modifier
            .size(38.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        val stroke = size.minDimension * 0.13f
        val route = Path().apply {
            moveTo(size.width * 0.22f, size.height * 0.82f)
            cubicTo(
                size.width * 0.22f,
                size.height * 0.43f,
                size.width * 0.44f,
                size.height * 0.22f,
                size.width * 0.68f,
                size.height * 0.22f,
            )
            cubicTo(
                size.width * 0.90f,
                size.height * 0.22f,
                size.width * 0.89f,
                size.height * 0.54f,
                size.width * 0.70f,
                size.height * 0.54f,
            )
        }
        drawPath(route, primaryColor, style = Stroke(stroke, cap = StrokeCap.Round))
        drawLine(
            color = mergeColor,
            start = Offset(size.width * 0.05f, size.height * 0.35f),
            end = Offset(size.width * 0.40f, size.height * 0.58f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

enum class DestinationIconType { HOME, FIND, OFFER, TRIPS, PROFILE }

@Composable
fun DestinationIcon(
    type: DestinationIconType,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        modifier = modifier
            .size(24.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        val stroke = size.minDimension * 0.09f
        when (type) {
            DestinationIconType.HOME -> {
                val roof = Path().apply {
                    moveTo(size.width * .14f, size.height * .48f)
                    lineTo(size.width * .5f, size.height * .18f)
                    lineTo(size.width * .86f, size.height * .48f)
                }
                drawPath(roof, color, style = Stroke(stroke, cap = StrokeCap.Round))
                drawRoundRect(
                    color,
                    topLeft = Offset(size.width * .24f, size.height * .43f),
                    size = Size(size.width * .52f, size.height * .42f),
                    style = Stroke(stroke),
                )
            }
            DestinationIconType.FIND -> {
                drawCircle(color, radius = size.width * .28f, center = Offset(size.width * .43f, size.height * .42f), style = Stroke(stroke))
                drawLine(color, Offset(size.width * .64f, size.height * .64f), Offset(size.width * .84f, size.height * .84f), stroke, StrokeCap.Round)
            }
            DestinationIconType.OFFER -> {
                drawCircle(color, radius = size.width * .35f, style = Stroke(stroke))
                drawLine(color, Offset(size.width * .5f, size.height * .30f), Offset(size.width * .5f, size.height * .70f), stroke, StrokeCap.Round)
                drawLine(color, Offset(size.width * .30f, size.height * .5f), Offset(size.width * .70f, size.height * .5f), stroke, StrokeCap.Round)
            }
            DestinationIconType.TRIPS -> {
                drawLine(color, Offset(size.width * .22f, size.height * .82f), Offset(size.width * .22f, size.height * .25f), stroke, StrokeCap.Round)
                drawLine(color, Offset(size.width * .22f, size.height * .25f), Offset(size.width * .78f, size.height * .25f), stroke, StrokeCap.Round)
                drawCircle(color, size.width * .09f, Offset(size.width * .22f, size.height * .82f))
                drawCircle(color, size.width * .09f, Offset(size.width * .78f, size.height * .25f))
            }
            DestinationIconType.PROFILE -> {
                drawCircle(color, radius = size.width * .17f, center = Offset(size.width * .5f, size.height * .34f), style = Stroke(stroke))
                drawArc(color, 200f, 140f, false, topLeft = Offset(size.width * .2f, size.height * .49f), size = Size(size.width * .6f, size.height * .45f), style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
    }
}

@Composable
fun LabelPill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Surface(modifier = modifier, shape = CircleShape, color = containerColor) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun InfoCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(Mint, CircleShape),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(5.dp))
                Text(
                    body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
fun LabeledValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (supportingText != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = supportingText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
