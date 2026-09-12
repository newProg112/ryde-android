package uk.rydeapp.ryde.ui.find

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import uk.rydeapp.ryde.data.AppMode
import uk.rydeapp.ryde.data.RydeAppComposition
import uk.rydeapp.ryde.domain.model.DemoTravelDate
import uk.rydeapp.ryde.domain.model.FindRideContent
import uk.rydeapp.ryde.domain.model.FindRideCriteria
import uk.rydeapp.ryde.domain.model.FindRideField
import uk.rydeapp.ryde.domain.model.FindRideSearchResult
import uk.rydeapp.ryde.domain.model.Flexibility
import uk.rydeapp.ryde.domain.model.HostedCircle
import uk.rydeapp.ryde.domain.model.TravelDatePolicy
import uk.rydeapp.ryde.domain.model.DemoDepartureTimePolicy
import uk.rydeapp.ryde.domain.model.RouteMatch
import uk.rydeapp.ryde.domain.model.CreateSeatRequestResult
import uk.rydeapp.ryde.domain.model.SeatRequest
import uk.rydeapp.ryde.domain.model.SeatRequestStatus
import uk.rydeapp.ryde.domain.model.RepeatJourneyPrefill
import uk.rydeapp.ryde.domain.model.formatDemoTime
import uk.rydeapp.ryde.ui.components.InfoCard
import uk.rydeapp.ryde.ui.components.LabelPill
import uk.rydeapp.ryde.ui.components.RouteMark
import uk.rydeapp.ryde.ui.theme.ElectricBlue
import uk.rydeapp.ryde.ui.theme.Mint
import uk.rydeapp.ryde.ui.theme.RydeTheme
import kotlinx.coroutines.launch

private enum class FindStage { FORM, RESULTS, DETAILS, CONFIRM, SUCCESS }

@Composable
fun FindScreen(
    content: FindRideContent,
    joinedCircle: HostedCircle?,
    repeatPrefill: RepeatJourneyPrefill? = null,
    onSearch: (FindRideCriteria) -> FindRideSearchResult,
    requestForMatch: (String, String?) -> SeatRequest?,
    onCreateRequest: suspend (String, FindRideCriteria) -> CreateSeatRequestResult?,
    onOpenTrips: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val commandScope = rememberCoroutineScope()
    val defaults = content.defaultCriteria
    var origin by rememberSaveable { mutableStateOf(defaults.origin) }
    var destination by rememberSaveable { mutableStateOf(defaults.destination) }
    var date by rememberSaveable { mutableStateOf(defaults.travelDate) }
    var ordinaryDepartureMinutes by rememberSaveable { mutableIntStateOf(defaults.departureMinutes) }
    var circleDepartureMinutes by rememberSaveable { mutableIntStateOf(DemoDepartureTimePolicy.CIRCLE_DEFAULT) }
    var flexibility by rememberSaveable { mutableStateOf(defaults.flexibility) }
    var seats by rememberSaveable { mutableIntStateOf(defaults.seatsRequired) }
    var stage by rememberSaveable { mutableStateOf(FindStage.FORM) }
    var searchAttempt by rememberSaveable { mutableIntStateOf(0) }
    var selectedMatchId by rememberSaveable { mutableStateOf<String?>(null) }
    var searchInCircle by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(joinedCircle?.id) {
        if (joinedCircle == null) searchInCircle = false
    }

    LaunchedEffect(repeatPrefill?.requestId) {
        repeatPrefill?.let {
            origin = it.originArea
            destination = it.destinationArea
            searchInCircle = false
            searchAttempt = 0
            selectedMatchId = null
            stage = FindStage.FORM
        }
    }

    val currentCriteria = FindRideCriteria(
        origin = origin,
        destination = destination,
        travelDate = TravelDatePolicy.forMode(date, joinedCircle.takeIf { searchInCircle }),
        departureMinutes = DemoDepartureTimePolicy.resolveFind(
            requestedMinutes = if (searchInCircle) circleDepartureMinutes else ordinaryDepartureMinutes,
            isCircleMode = joinedCircle != null && searchInCircle,
        ),
        flexibility = flexibility,
        seatsRequired = seats,
        circleId = joinedCircle?.id?.takeIf { searchInCircle },
    )
    val result = remember(currentCriteria, searchAttempt) {
        if (searchAttempt > 0) onSearch(currentCriteria) else null
    }
    val selectedMatch = result?.matches?.firstOrNull { it.id == selectedMatchId }
    val existingRequest = selectedMatch?.let { requestForMatch(it.id, it.circle?.id) }

    BackHandler(enabled = stage != FindStage.FORM) {
        stage = when (stage) {
            FindStage.RESULTS -> FindStage.FORM
            FindStage.DETAILS -> FindStage.RESULTS
            FindStage.CONFIRM, FindStage.SUCCESS -> FindStage.DETAILS
            FindStage.FORM -> FindStage.FORM
        }
    }

    when {
        stage == FindStage.SUCCESS && existingRequest != null -> RequestSuccess(
            request = existingRequest,
            onBackToMatch = { stage = FindStage.DETAILS },
            onOpenTrips = onOpenTrips,
            modifier = modifier,
        )
        stage == FindStage.CONFIRM && selectedMatch != null -> RequestConfirmation(
            criteria = result!!.criteria,
            match = selectedMatch,
            onBack = { stage = FindStage.DETAILS },
            onConfirm = {
                commandScope.launch {
                    when (onCreateRequest(selectedMatch.id, result!!.criteria)) {
                        is CreateSeatRequestResult.Created,
                        is CreateSeatRequestResult.DuplicateActive -> stage = FindStage.SUCCESS
                        CreateSeatRequestResult.InvalidSeatCount,
                        CreateSeatRequestResult.MatchNotFound -> stage = FindStage.DETAILS
                        null -> Unit
                    }
                }
            },
            modifier = modifier,
        )
        stage == FindStage.DETAILS && selectedMatch != null -> MatchDetails(
            criteria = result!!.criteria,
            match = selectedMatch,
            request = existingRequest,
            onBack = { stage = FindStage.RESULTS },
            onRequest = { stage = FindStage.CONFIRM },
            onOpenTrips = onOpenTrips,
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
            joinedCircle = joinedCircle,
            repeatPrefill = repeatPrefill,
            searchInCircle = searchInCircle,
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
            onTimeChange = {
                if (searchInCircle) circleDepartureMinutes = it else ordinaryDepartureMinutes = it
            },
            onFlexibilityChange = { flexibility = it },
            onSeatsChange = { seats = it.coerceIn(1, 4) },
            onSearchInCircleChange = { searchInCircle = it },
            onSearch = {
                val searched = onSearch(currentCriteria)
                searchAttempt += 1
                if (searched.isValid) stage = FindStage.RESULTS
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun SearchForm(
    content: FindRideContent,
    joinedCircle: HostedCircle?,
    repeatPrefill: RepeatJourneyPrefill?,
    searchInCircle: Boolean,
    criteria: FindRideCriteria,
    errors: List<uk.rydeapp.ryde.domain.model.FindRideValidationError>,
    onOriginChange: (String) -> Unit,
    onDestinationChange: (String) -> Unit,
    onSwap: () -> Unit,
    onDateChange: (DemoTravelDate) -> Unit,
    onTimeChange: (Int) -> Unit,
    onFlexibilityChange: (Flexibility) -> Unit,
    onSeatsChange: (Int) -> Unit,
    onSearchInCircleChange: (Boolean) -> Unit,
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
        repeatPrefill?.let { prefill ->
            item {
                InfoCard(
                    title = "Travel together again with ${prefill.preferredPersonName}",
                    body = "The previous broad-area route is pre-filled. Review and edit it before searching; no journey has been created.",
                )
            }
        }
        if (joinedCircle != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Where to search", style = MaterialTheme.typography.titleMedium)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = !searchInCircle,
                            onClick = { onSearchInCircleChange(false) },
                            label = { Text("All Ryde matches") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FilterChip(
                            selected = searchInCircle,
                            onClick = { onSearchInCircleChange(true) },
                            label = { Text("Search within Nottingham Live") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (searchInCircle) {
                        Text("Circle matches · host covers the £0.50 service fee", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
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
        if (criteria.circleId != null) {
            item {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Circle event date", style = MaterialTheme.typography.titleMedium)
                        Text(criteria.travelDate.displayName, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            item {
                ChoiceSection(
                    "Demo travel date",
                    DemoTravelDate.ordinaryChoices,
                    criteria.travelDate,
                    { it.displayName },
                    onDateChange,
                )
            }
        }
        item {
            ChoiceSection(
                "Preferred departure",
                if (criteria.circleId == null) {
                    DemoDepartureTimePolicy.ordinaryFindChoices
                } else {
                    DemoDepartureTimePolicy.circleChoices
                },
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
        item { FindHeader(if (result.criteria.circleId == null) "Matching routes" else "Circle matches", "Ranked fictional route-overlap options") }
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
            match.circle?.let { LabelPill("Ryde Circle · ${it.name}") }
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
    request: SeatRequest?,
    onBack: () -> Unit,
    onRequest: () -> Unit,
    onOpenTrips: () -> Unit,
    modifier: Modifier,
) {
    FindList(modifier) {
        item {
            OutlinedButton(onClick = onBack) { Text("← Back to results") }
            Spacer(Modifier.height(10.dp))
            FindHeader("${match.driver.firstName}'s route overlap", "Fictional demo match · ${match.matchScore}%")
            match.circle?.let { LabelPill("Circle match · ${it.name}") }
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
                PriceRow(
                    "Ryde service fee",
                    if (match.pricing.hostCoversServiceFee) "${money(match.serviceFeePence)} · covered by host" else money(match.serviceFeePence),
                )
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                PriceRow("Rider total", money(match.riderTotalPence), bold = true)
                PriceRow("Driver receives", money(match.driverReceivesPence), bold = true)
                Spacer(Modifier.height(6.dp))
                Text("Contributions are based on shared distance—not demand, delays or surge pricing.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (match.pricing.hostCoversServiceFee) Text("The Circle host covers only Ryde’s fee; the driver contribution is unchanged.", color = MaterialTheme.colorScheme.primary)
            }
        }
        item {
            when (request?.status) {
                SeatRequestStatus.PENDING -> {
                    Button(onClick = onOpenTrips, modifier = Modifier.fillMaxWidth()) { Text("View pending request in Trips") }
                    Text("Pending in this local demo only — no real driver has been contacted.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                SeatRequestStatus.CANCELLED -> {
                    Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) { Text("Request seat again") }
                    Text("The earlier local-demo request is cancelled. No real driver was contacted.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                null -> {
                    Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) { Text("Review seat request") }
                    Text("You will review privacy and the full total before submitting.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun RequestConfirmation(
    criteria: FindRideCriteria,
    match: RouteMatch,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier,
) {
    var privacyAcknowledged by rememberSaveable { mutableStateOf(false) }
    FindList(modifier) {
        item {
            OutlinedButton(onClick = onBack) { Text("← Back to match") }
            Spacer(Modifier.height(10.dp))
            FindHeader("Review your seat request", "Nothing is sent until you confirm")
            match.circle?.let { LabelPill("Ryde Circle · ${it.name}") }
        }
        item {
            DetailCard("Journey") {
                PriceRow("Fictional driver", match.driver.firstName)
                PriceRow("Route", "${criteria.origin} → ${criteria.destination}")
                PriceRow("Date", match.travelDate.displayName)
                PriceRow("Approximate pickup", formatDemoTime(match.pickupMinutes))
                PriceRow("Public pickup area", match.pickupArea)
                PriceRow("Seats requested", criteria.seatsRequired.toString())
            }
        }
        item {
            DetailCard("Transparent total") {
                PriceRow("Shared-distance contribution", money(match.contributionPence))
                Text("${match.sharedMiles} shared miles × £0.20, rounded to the nearest 50p", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                PriceRow(
                    "Ryde service fee",
                    if (match.pricing.hostCoversServiceFee) "${money(match.serviceFeePence)} · covered by host" else money(match.serviceFeePence),
                )
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                PriceRow("Rider total", money(match.riderTotalPence), bold = true)
                PriceRow("Driver receives", money(match.driverReceivesPence), bold = true)
                Text("Based on shared distance — never demand, delays or surge pricing.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (match.pricing.hostCoversServiceFee) Text("Your total excludes the host-covered fee. The driver still receives the full shared-distance contribution.", color = MaterialTheme.colorScheme.primary)
            }
        }
        item {
            DetailCard("Privacy before you request") {
                Text("Your home address is never displayed or shared.", fontWeight = FontWeight.Bold)
                Text("Only the public, approximate pickup area ‘${match.pickupArea}’ is shared with the fictional driver at this stage.")
                Text("Exact or live location would only be shared later when necessary, and only after a clear warning.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = privacyAcknowledged, onCheckedChange = { privacyAcknowledged = it })
                    Text("I understand what this demo shares", modifier = Modifier.weight(1f))
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    if (match.circle == null) {
                        "Fictional local demo: this request will only be stored in this app session. It will not reach a real driver and no payment will be taken."
                    } else {
                        "Fictional local demo: no payment, real host sponsorship or host contact occurred. This Circle request stays only in this app session."
                    },
                    modifier = Modifier.padding(16.dp),
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onConfirm,
                enabled = privacyAcknowledged,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Request seat") }
        }
    }
}

@Composable
private fun RequestSuccess(
    request: SeatRequest,
    onBackToMatch: () -> Unit,
    onOpenTrips: () -> Unit,
    modifier: Modifier,
) {
    FindList(modifier) {
        item {
            FindHeader(
                if (request.status == SeatRequestStatus.PENDING) "Request saved" else "Request cancelled",
                if (request.status == SeatRequestStatus.PENDING) "Pending driver response" else "Cancelled · local demo history",
            )
        }
        request.circle?.let { item { LabelPill("Ryde Circle · ${it.name}") } }
        item {
            DetailCard("Local-demo success") {
                Text(
                    if (request.status == SeatRequestStatus.PENDING) {
                        "Your fictional request for ${request.requestedSeats} ${if (request.requestedSeats == 1) "seat" else "seats"} is now visible in Trips."
                    } else {
                        "This fictional request is cancelled and remains visible in Trips as local-demo history."
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text("It is stored only for this app session. No real driver was contacted and no payment or location sharing occurred.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Button(onClick = onOpenTrips, modifier = Modifier.fillMaxWidth()) { Text("Open Trips") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onBackToMatch, modifier = Modifier.fillMaxWidth()) { Text("Back to match") }
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
        LabelPill("PHASE 3 · LOCAL DEMO")
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
        val repository = remember { RydeAppComposition.repository(AppMode.LOCAL_DEMO) }
        FindScreen(
            content = repository.getFindRideContent(),
            joinedCircle = null,
            onSearch = repository::findRides,
            requestForMatch = repository::getSeatRequestForMatch,
            onCreateRequest = repository::createSeatRequest,
            onOpenTrips = {},
        )
    }
}
