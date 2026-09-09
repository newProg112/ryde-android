package uk.rydeapp.ryde.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import uk.rydeapp.ryde.R
import uk.rydeapp.ryde.data.FakeRydeRepository
import uk.rydeapp.ryde.domain.model.HomeContent
import uk.rydeapp.ryde.domain.model.HostedCircle
import uk.rydeapp.ryde.domain.model.SavedPlace
import uk.rydeapp.ryde.domain.model.SuggestedMatch
import uk.rydeapp.ryde.ui.components.LabelPill
import uk.rydeapp.ryde.ui.components.LabeledValue
import uk.rydeapp.ryde.ui.components.RouteMark
import uk.rydeapp.ryde.ui.theme.ElectricBlue
import uk.rydeapp.ryde.ui.theme.Mint
import uk.rydeapp.ryde.ui.theme.RydeTheme
import java.text.NumberFormat
import java.util.Locale

@Composable
fun HomeScreen(
    content: HomeContent,
    onFindRide: () -> Unit,
    onOfferRide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { BrandHeader() }
        item {
            Column {
                Text(
                    text = stringResource(R.string.greeting, content.currentUser.firstName),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = stringResource(R.string.tagline),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.supporting_promise),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onFindRide,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(stringResource(R.string.find_a_ride))
                }
                OutlinedButton(
                    onClick = onOfferRide,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
                ) {
                    Text(stringResource(R.string.offer_a_ride))
                }
            }
        }
        item { JourneySearchCard(content.currentUser.savedPlaces) }
        item { SuggestedMatchCard(content.suggestedMatch) }
        item { HostedCircleCard(content.hostedCircle) }
    }
}

@Composable
private fun BrandHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RouteMark(contentDescription = stringResource(R.string.route_mark_description))
        Spacer(Modifier.size(9.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        LabelPill(text = stringResource(R.string.demo_badge))
    }
}

@Composable
private fun JourneySearchCard(savedPlaces: List<SavedPlace>) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(stringResource(R.string.where_heading), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.saved_places_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                savedPlaces.forEach { place ->
                    val placeDescription = stringResource(
                        R.string.saved_place_description,
                        place.label,
                        place.area,
                    )
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = placeDescription },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Text(place.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                place.area,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestedMatchCard(match: SuggestedMatch) {
    val ratingDescription = stringResource(R.string.rating_description, match.driver.rating)
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.suggested_match), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.match_demo_label),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        text = match.driver.firstName.take(1),
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.semantics { contentDescription = ratingDescription },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${match.driver.firstName} · ★ ${match.driver.rating}",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (match.driver.isDemoVerified) {
                    Spacer(Modifier.size(8.dp))
                    LabelPill(text = stringResource(R.string.demo_verified))
                }
            }
            Spacer(Modifier.height(14.dp))
            RouteOverlapVisual()
            Spacer(Modifier.height(12.dp))
            JourneyLine(
                label = stringResource(R.string.driver_journey),
                origin = match.driverJourney.origin,
                destination = match.driverJourney.destination,
                time = match.driverJourney.departureTime,
            )
            Spacer(Modifier.height(7.dp))
            JourneyLine(
                label = stringResource(R.string.your_overlap),
                origin = match.riderJourney.origin,
                destination = match.riderJourney.destination,
                time = stringResource(R.string.around_time, match.riderJourney.departureTime),
            )
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledValue(
                    label = stringResource(R.string.shared_distance),
                    value = pluralStringResource(
                        R.plurals.shared_miles,
                        match.sharedMiles,
                        match.sharedMiles,
                    ),
                    modifier = Modifier.weight(1f),
                )
                LabeledValue(
                    label = stringResource(R.string.suggested_contribution),
                    value = formatPence(match.contributionPence),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(9.dp))
            Text(
                text = pluralStringResource(
                    R.plurals.contribution_basis,
                    match.sharedMiles,
                    match.sharedMiles,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LabeledValue(
                        label = stringResource(R.string.rider_total),
                        value = formatPence(match.riderTotalPence),
                        supportingText = stringResource(R.string.fee_breakdown, formatPence(match.serviceFeePence)),
                        modifier = Modifier.weight(1f),
                    )
                    LabeledValue(
                        label = stringResource(R.string.driver_receives),
                        value = formatPence(match.driverReceivesPence),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.demo_disclaimer),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun JourneyLine(label: String, origin: String, destination: String, time: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) { drawCircle(ElectricBlue) }
        }
        Spacer(Modifier.size(10.dp))
        Column {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            Text(
                text = stringResource(R.string.journey_summary, origin, destination, time),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun RouteOverlapVisual() {
    val description = stringResource(R.string.route_visual_description)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .semantics { contentDescription = description },
    ) {
        val lineWidth = 5.dp.toPx()
        val middleY = size.height * .52f
        drawLine(
            color = ElectricBlue.copy(alpha = .35f),
            start = Offset(0f, size.height * .18f),
            end = Offset(size.width * .38f, middleY),
            strokeWidth = lineWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Mint,
            start = Offset(0f, size.height * .84f),
            end = Offset(size.width * .38f, middleY),
            strokeWidth = lineWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = ElectricBlue,
            start = Offset(size.width * .38f, middleY),
            end = Offset(size.width, middleY),
            strokeWidth = lineWidth,
            cap = StrokeCap.Round,
        )
        drawCircle(Mint, radius = 6.dp.toPx(), center = Offset(size.width * .38f, middleY), style = Stroke(2.dp.toPx()))
    }
}

@Composable
private fun HostedCircleCard(circle: HostedCircle) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ryde_circle), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                LabelPill(
                    text = stringResource(R.string.host_covers_fee),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(circle.name, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(circle.summary, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.circle_demo_disclaimer),
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = .8f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun formatPence(pence: Int): String =
    NumberFormat.getCurrencyInstance(Locale.UK).format(pence / 100.0)

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun HomeScreenPreview() {
    RydeTheme(darkTheme = false) {
        HomeScreen(
            content = FakeRydeRepository().getHomeContent(),
            onFindRide = {},
            onOfferRide = {},
        )
    }
}
