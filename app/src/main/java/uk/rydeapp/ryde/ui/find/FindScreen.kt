package uk.rydeapp.ryde.ui.find

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale
import uk.rydeapp.ryde.data.FakeRydeRepository
import uk.rydeapp.ryde.domain.model.DemoTravelDate
import uk.rydeapp.ryde.domain.model.FindRideContent
import uk.rydeapp.ryde.domain.model.FindRideCriteria
import uk.rydeapp.ryde.domain.model.FindRideField
import uk.rydeapp.ryde.domain.model.FindRideSearchResult
import uk.rydeapp.ryde.domain.model.Flexibility
import uk.rydeapp.ryde.domain.model.RouteMatch
import uk.rydeapp.ryde.domain.model.formatDemoTime
import uk.rydeapp.ryde.ui.components.LabelPill
import uk.rydeapp.ryde.ui.components.RouteMark
import uk.rydeapp.ryde.ui.theme.ElectricBlue
import uk.rydeapp.ryde.ui.theme.Mint
import uk.rydeapp.ryde.ui.theme.RydeTheme

private enum class FindStage { FORM, RESULTS, DETAILS }

@Composable
fun FindScreen(
    content: FindRideContent,
    onSearch: (FindRideCriteria) -> FindRideSearchResult,
    modifier: Modifier = Modifier,
) {
    val defaults = content.defaultCriteria
    var origin by rememberSaveable { mutableStateOf(defaults.origin) }
    var destination by rememberSaveable { mutableStateOf(defaults.destination) }
    var date by rememberSaveable { mutableStateOf(defaults.travelDate) }
    var departureMinutes by rememberSaveable { mutableIntStateOf(defaults.departureMinutes) }
    var flexibility by rememberSaveable { mutableStateOf(defaults.flexibility) }
    var seats by rememberSaveable { mutableIntStateOf(defaults.seatsRequired) }
    var stage by rememberSaveable { mutableStateOf(FindStage.FORM) }
    var result by remember { mutableStateOf<FindRideSearchResult?>(null) }
    var selectedMatchId by rememberSaveable { mutableStateOf<String?>(null) }

    val currentCriteria = FindRideCriteria(origin, destination, date, departureMinutes, flexibility, seats)
    val selectedMatch = result?.matches?.firstOrNull { it.id == selectedMatchId }

    when {
        stage == FindStage.DETAILS && selectedMatch != null -> MatchDetails(
            criteria = result!!.criteria,
            match = selectedMatch,
            onBack = { stage = FindStage.RESULTS },
            modifier = modifier,
        )
        stage == FindStage.RESULTS && result != null -> Results(
            result = result!!,
            onEdit = { stage = FindStage.FORM },
            onMatch = {
                selectedMatchId = it.id
                stage = FindStage.DETAILS
            },
            modifier = modifier,
        )
        else -> SearchForm(
            content = content,
            criteria = currentCriteria,
            errors = result?.takeIf { !it.isValid }?.validationErrors.orEmpty(),
            onOriginChange = { origin = it },
            onDestinationChange = { destination = it },
            onSwap = {
                val previousOrigin = origin
                origin = destination
                destination = previousOrigin
            },
            onDateChange = { date = it },
            onTimeChange = { departureMinutes = it },
            onFlexibilityChange = { flexibility = it },
            onSeatsChange = { seats = it.coerceIn(1, 4) },
            onSearch = {
                result = onSearch(currentCriteria)
                if (result?.isValid == true) stage = FindStage.RESULTS
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun SearchForm(
    content: FindRideContent,
    criteria: FindRideCriteria,
    errors: List<uk.rydeapp.ryde.domain.model.FindRideValidationError>,
    onOriginChange: (String) -> Unit,
    onDestinationChange: (String) -> Unit,
    onSwap: () -> Unit,
    onDateChange: (DemoTravelDate) -> Unit,
    onTimeChange: (Int) -> Unit,
    onFlexibilityChange: (Flexibility) -> Unit,
    onSeatsChange: (Int) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier,
) {
    val originError = errors.firstOrNull { it.field == FindRideField.ORIGIN }?.message
    val destinationError = errors.firstOrNull {
        it.field == FindRideField.DESTINATION || it.field == FindRideField.ENDPOINTS
    }?.message
    FindList(modifier) {
        item { FindHeader("Find a shared route", "Share the route. Split the cost.") }
        item {
            Text(
                "Local demo search using fictional journeys — no live routing or availability.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item {
            OutlinedTextField(
                value = criteria.origin,
                onValueChange = onOriginChange,
                label = { Text("Origin area") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = originError != null,
                supportingText = originError?.let { { Text(it) } },
            )
            content.savedPlaces.firstOrNull { it.label == "Home" }?.let { place ->
                AssistChip(
                    onClick = { onOriginChange(place.area) },
                    label = { Text("Home · ${place.area}") },
                )
            }
        }
        item {
            OutlinedButton(
                onClick = onSwap,
                modifier = Modifier.semantics { contentDescription = "Swap origin and destination" },
            ) { Text("⇅  Swap route") }
        }
        item {
            OutlinedTextField(
                value = criteria.destination,
                onValueChange = onDestinationChange,
                label = { Text("Destination area") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = destinationError != null,
                supportingText = destinationError?.let { { Text(it) } },
            )
            content.savedPlaces.firstOrNull { it.label == "Work" }?.let { place ->
                AssistChip(
                    onClick = { onDestinationChange(place.area) },
                    label = { Text("Work · ${place.area}") },
                )
            }
        }
        item { ChoiceSection("Demo travel date", DemoTravelDate.entries, criteria.travelDate, { it.displayName }, onDateChange) }
        item {
            ChoiceSection(
                "Preferred departure",
                listOf(7 * 60 + 35, 8 * 60 + 5, 8 * 60 + 35),
                criteria.departureMinutes,
                ::formatDemoTime,
                onTimeChange,
            )
        }
        item { ChoiceSection("Flexibility", Flexibility.entries, criteria.flexibility, { it.displayName }, onFlexibilityChange) }
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Seats required", style = MaterialTheme.typography.titleMedium)
                        Text("Choose 1–4", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = { onSeatsChange(criteria.seatsRequired - 1) }, enabled = criteria.seatsRequired > 1) { Text("−") }
                    Text("${criteria.seatsRequired}", modifier = Modifier.padding(horizontal = 14.dp), style = MaterialTheme.typography.titleLarge)
                    OutlinedButton(onClick = { onSeatsChange(criteria.seatsRequired + 1) }, enabled = criteria.seatsRequired < 4) { Text("+") }
                }
            }
        }
        item {
            Button(onClick = onSearch, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Text("Find matching routes", modifier = Modifier.padding(vertical = 5.dp))
            }
        }
    }
}

@Composable
private fun <T> ChoiceSection(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelected(option) },
                    label = { Text(label(option)) },
                )
            }
        }
    }
}

@Composable
private fun Results(
    result: FindRideSearchResult,
    onEdit: () -> Unit,
    onMatch: (RouteMatch) -> Unit,
    modifier: Modifier,
) {
    FindList(modifier) {
        item { FindHeader("Matching routes", "Ranked fictional route-overlap options") }
        item {
            CriteriaCard(result.criteria)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onEdit) { Text("Edit search") }
        }
        item {
            Text(
                "${result.matches.size} compatible fictional ${if (result.matches.size == 1) "route" else "routes"}",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (result.matches.isEmpty()) {
            item { EmptyResults(onEdit) }
        } else {
            items(result.matches.size, key = { result.matches[it].id }) { index ->
                MatchCard(result.matches[index], isBest = index == 0, onClick = { onMatch(result.matches[index]) })
            }
        }
    }
}

@Composable
private fun CriteriaCard(criteria: FindRideCriteria) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${criteria.origin} → ${criteria.destination}", fontWeight = FontWeight.Bold)
            Text(
                "${criteria.travelDate.displayName} · ${formatDemoTime(criteria.departureMinutes)} · ${criteria.flexibility.displayName} · ${criteria.seatsRequired} ${if (criteria.seatsRequired == 1) "seat" else "seats"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun MatchCard(match: RouteMatch, isBest: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = "Open ${match.driver.firstName}'s fictional route match, ${match.matchScore} percent"
        },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isBest) 3.dp else 1.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isBest) LabelPill("Best match", containerColor = Mint.copy(alpha = .22f))
                Spacer(Modifier.weight(1f))
                Text("${match.matchScore}% match", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Text("${match.driver.firstName}  ·  ★ ${match.driver.rating}", style = MaterialTheme.typography.titleLarge)
            Text("Demo-verified fictional profile", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Text("${match.driverJourney.origin} → ${match.driverJourney.destination} · ${match.driverJourney.departureTime}")
            HorizontalDivider()
            Text("Pickup: ${match.pickupArea} at around ${formatDemoTime(match.pickupMinutes)}")
            Text("${match.walkMinutes}-minute walk · ${seatLabel(match.availableSeats)} · ${match.sharedMiles} shared miles")
            Text("Suggested contribution ${money(match.contributionPence)}", style = MaterialTheme.typography.titleMedium)
            Text("View overlap and price breakdown →", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyResults(onEdit: () -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            RouteMark("No routes found illustration", Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text("No compatible demo routes", style = MaterialTheme.typography.titleLarge)
            Text("Try another demo time, date, route or fewer seats.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onEdit) { Text("Edit search") }
        }
    }
}

@Composable
private fun MatchDetails(
    criteria: FindRideCriteria,
    match: RouteMatch,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    FindList(modifier) {
        item {
            OutlinedButton(onClick = onBack) { Text("← Back to results") }
            Spacer(Modifier.height(10.dp))
            FindHeader("${match.driver.firstName}'s route overlap", "Fictional demo match · ${match.matchScore}%")
        }
        item {
            DetailCard("Driver profile") {
                Text("${match.driver.firstName}  ·  ★ ${match.driver.rating}", style = MaterialTheme.typography.titleLarge)
                Text("Demo verified — not a real person or real verification", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            DetailCard("Shared journey") {
                Text("Driver’s planned journey")
                Text("${match.driverJourney.origin} → ${match.driverJourney.destination} · ${match.driverJourney.departureTime}", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Rider’s requested overlap")
                Text("${criteria.origin} → ${criteria.destination} · around ${formatDemoTime(criteria.departureMinutes)}", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                RouteOverlapIllustration()
                Text("Route-overlap illustration — not a real map", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            DetailCard("Pickup and timing") {
                Text("Suggested public pickup area", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(match.pickupArea, style = MaterialTheme.typography.titleMedium)
                Text("Around ${formatDemoTime(match.pickupMinutes)} · ${match.walkMinutes}-minute walk")
                Text("Driver detour ${formatDetour(match.detourMiles)} · ${seatLabel(match.availableSeats)} available")
                Text("${match.sharedMiles} shared miles · ${match.matchScore}% route match")
                Spacer(Modifier.height(8.dp))
                Text("Exact or live location is not shared at this stage.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            DetailCard("Transparent contribution") {
                PriceRow("Shared-distance contribution", money(match.contributionPence))
                Text("${match.sharedMiles} miles × £0.20, rounded to the nearest 50p", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                PriceRow("Ryde service fee", money(match.serviceFeePence))
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                PriceRow("Rider total", money(match.riderTotalPence), bold = true)
                PriceRow("Driver receives", money(match.driverReceivesPence), bold = true)
                Spacer(Modifier.height(6.dp))
                Text("Contributions are based on shared distance—not demand, delays or surge pricing.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("Seat requests coming in Phase 3") }
            Text("No request has been sent in this demo.", modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DetailCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun RouteOverlapIllustration() {
    val description = "Two fictional routes merge for a shared section; illustration only, not a real map"
    Canvas(Modifier.fillMaxWidth().height(92.dp).semantics { contentDescription = description }) {
        val stroke = 6.dp.toPx()
        val join = Offset(size.width * .38f, size.height * .52f)
        drawLine(ElectricBlue.copy(alpha = .38f), Offset(size.width * .04f, size.height * .15f), join, stroke, StrokeCap.Round)
        drawLine(Mint, Offset(size.width * .04f, size.height * .86f), join, stroke, StrokeCap.Round)
        drawLine(ElectricBlue, join, Offset(size.width * .95f, size.height * .52f), stroke, StrokeCap.Round)
        drawCircle(Mint, 8.dp.toPx(), join, style = Stroke(3.dp.toPx()))
        drawCircle(ElectricBlue, 7.dp.toPx(), Offset(size.width * .95f, size.height * .52f))
    }
}

@Composable
private fun PriceRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(value, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun FindHeader(title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RouteMark("Ryde merging route mark", Modifier.size(38.dp))
        Spacer(Modifier.weight(1f))
        LabelPill("PHASE 2 · LOCAL DEMO")
    }
    Spacer(Modifier.height(14.dp))
    Text(title, style = MaterialTheme.typography.headlineMedium)
    Text(subtitle, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun FindList(
    modifier: Modifier,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

private fun money(pence: Int): String = NumberFormat.getCurrencyInstance(Locale.UK).format(pence / 100.0)
private fun formatDetour(miles: Double): String = if (miles <= 1.0) "1 mile or less" else "${miles} miles"
private fun seatLabel(count: Int): String = "$count ${if (count == 1) "seat" else "seats"}"

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun FindScreenPreview() {
    RydeTheme(darkTheme = false) {
        FindScreen(FakeRydeRepository.getFindRideContent(), FakeRydeRepository::findRides)
    }
}
