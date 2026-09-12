package uk.rydeapp.ryde.ui.offer

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.rydeapp.ryde.domain.model.CreateOfferedJourneyResult
import uk.rydeapp.ryde.domain.model.DemoTravelDate
import uk.rydeapp.ryde.domain.model.Flexibility
import uk.rydeapp.ryde.domain.model.OfferRideContent
import uk.rydeapp.ryde.domain.model.OfferRideCriteria
import uk.rydeapp.ryde.domain.model.OfferRideField
import uk.rydeapp.ryde.domain.model.OfferRideValidationError
import uk.rydeapp.ryde.domain.model.OfferedJourney
import uk.rydeapp.ryde.domain.model.OfferedJourneyStatus
import uk.rydeapp.ryde.domain.model.HostedCircle
import uk.rydeapp.ryde.domain.model.TravelDatePolicy
import uk.rydeapp.ryde.domain.model.DemoDepartureTimePolicy
import uk.rydeapp.ryde.domain.model.formatDemoTime
import uk.rydeapp.ryde.ui.components.InfoCard
import uk.rydeapp.ryde.ui.components.LabelPill
import uk.rydeapp.ryde.ui.components.RouteMark
import kotlinx.coroutines.launch

private enum class OfferStage { FORM, REVIEW, SUCCESS }

@Composable
fun OfferScreen(
    content: OfferRideContent,
    joinedCircle: HostedCircle?,
    offeredJourneys: List<OfferedJourney>,
    validate: (OfferRideCriteria) -> List<OfferRideValidationError>,
    onCreateOffer: suspend (OfferRideCriteria) -> CreateOfferedJourneyResult?,
    onOpenTrips: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val commandScope = rememberCoroutineScope()
    val defaults = content.defaultCriteria
    val initialActive = offeredJourneys.lastOrNull { it.status == OfferedJourneyStatus.OPEN }
    var origin by rememberSaveable { mutableStateOf(defaults.originArea) }
    var destination by rememberSaveable { mutableStateOf(defaults.destinationArea) }
    var date by rememberSaveable { mutableStateOf(defaults.travelDate) }
    var ordinaryDepartureMinutes by rememberSaveable { mutableIntStateOf(defaults.departureMinutes) }
    var circleDepartureMinutes by rememberSaveable { mutableIntStateOf(DemoDepartureTimePolicy.CIRCLE_DEFAULT) }
    var flexibility by rememberSaveable { mutableStateOf(defaults.flexibility) }
    var spareSeats by rememberSaveable { mutableIntStateOf(defaults.spareSeats) }
    var maximumDetourMiles by rememberSaveable { mutableIntStateOf(defaults.maximumDetourMiles) }
    var stage by rememberSaveable { mutableStateOf(if (initialActive == null) OfferStage.FORM else OfferStage.SUCCESS) }
    var errors by remember { mutableStateOf(emptyList<OfferRideValidationError>()) }
    var acknowledgement by rememberSaveable { mutableStateOf(false) }
    var publishedOfferId by rememberSaveable { mutableStateOf(initialActive?.id) }
    var duplicateMessage by rememberSaveable { mutableStateOf(false) }
    var offerWithinCircle by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(joinedCircle?.id) {
        if (joinedCircle == null) offerWithinCircle = false
    }

    val criteria = OfferRideCriteria(
        originArea = origin,
        destinationArea = destination,
        travelDate = TravelDatePolicy.forMode(date, joinedCircle.takeIf { offerWithinCircle }),
        departureMinutes = DemoDepartureTimePolicy.resolveOffer(
            requestedMinutes = if (offerWithinCircle) circleDepartureMinutes else ordinaryDepartureMinutes,
            isCircleMode = joinedCircle != null && offerWithinCircle,
        ),
        flexibility = flexibility,
        spareSeats = spareSeats,
        maximumDetourMiles = maximumDetourMiles,
        circleId = joinedCircle?.id?.takeIf { offerWithinCircle },
    )
    val publishedOffer = offeredJourneys.firstOrNull { it.id == publishedOfferId }
        ?: initialActive

    BackHandler(enabled = stage != OfferStage.FORM) {
        stage = when (stage) {
            OfferStage.REVIEW -> OfferStage.FORM
            OfferStage.SUCCESS -> OfferStage.FORM
            OfferStage.FORM -> OfferStage.FORM
        }
    }

    when (stage) {
        OfferStage.FORM -> OfferForm(
            content = content,
            joinedCircle = joinedCircle,
            offerWithinCircle = offerWithinCircle,
            activeOffer = offeredJourneys.lastOrNull { it.status == OfferedJourneyStatus.OPEN },
            criteria = criteria,
            errors = errors,
            onOriginChange = { origin = it; errors = errors.without(OfferRideField.ORIGIN, OfferRideField.ENDPOINTS) },
            onDestinationChange = { destination = it; errors = errors.without(OfferRideField.DESTINATION, OfferRideField.ENDPOINTS) },
            onSwap = { val oldOrigin = origin; origin = destination; destination = oldOrigin; errors = emptyList() },
            onDateChange = { date = it },
            onTimeChange = {
                if (offerWithinCircle) circleDepartureMinutes = it else ordinaryDepartureMinutes = it
            },
            onFlexibilityChange = { flexibility = it },
            onSeatsChange = { spareSeats = it },
            onDetourChange = { maximumDetourMiles = it },
            onOfferWithinCircleChange = { offerWithinCircle = it },
            onReview = {
                errors = validate(criteria)
                if (errors.isEmpty()) {
                    acknowledgement = false
                    duplicateMessage = false
                    stage = OfferStage.REVIEW
                }
            },
            modifier = modifier,
        )
        OfferStage.REVIEW -> OfferReview(
            criteria = criteria,
            acknowledged = acknowledgement,
            onAcknowledgedChange = { acknowledgement = it },
            onBack = { stage = OfferStage.FORM },
            onPublish = {
                commandScope.launch {
                    when (val result = onCreateOffer(criteria)) {
                        is CreateOfferedJourneyResult.Created -> {
                            publishedOfferId = result.journey.id
                            duplicateMessage = false
                            stage = OfferStage.SUCCESS
                        }
                        is CreateOfferedJourneyResult.DuplicateActive -> {
                            publishedOfferId = result.journey.id
                            duplicateMessage = true
                            stage = OfferStage.SUCCESS
                        }
                        is CreateOfferedJourneyResult.Invalid -> {
                            errors = result.errors
                            stage = OfferStage.FORM
                        }
                        null -> Unit
                    }
                }
            },
            modifier = modifier,
        )
        OfferStage.SUCCESS -> OfferSuccess(
            journey = publishedOffer,
            duplicate = duplicateMessage,
            onOpenTrips = onOpenTrips,
            onOfferAnother = {
                publishedOfferId = null
                duplicateMessage = false
                stage = OfferStage.FORM
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun OfferForm(
    content: OfferRideContent,
    joinedCircle: HostedCircle?,
    offerWithinCircle: Boolean,
    activeOffer: OfferedJourney?,
    criteria: OfferRideCriteria,
    errors: List<OfferRideValidationError>,
    onOriginChange: (String) -> Unit,
    onDestinationChange: (String) -> Unit,
    onSwap: () -> Unit,
    onDateChange: (DemoTravelDate) -> Unit,
    onTimeChange: (Int) -> Unit,
    onFlexibilityChange: (Flexibility) -> Unit,
    onSeatsChange: (Int) -> Unit,
    onDetourChange: (Int) -> Unit,
    onOfferWithinCircleChange: (Boolean) -> Unit,
    onReview: () -> Unit,
    modifier: Modifier,
) {
    val originError = errors.firstOrNull { it.field == OfferRideField.ORIGIN }?.message
    val destinationError = errors.firstOrNull { it.field == OfferRideField.DESTINATION || it.field == OfferRideField.ENDPOINTS }?.message
    OfferList(modifier) {
        item { OfferHeader("Offer your spare seats", "Only offer journeys you already plan to make.") }
        if (activeOffer != null) {
            item {
                InfoCard(
                    title = "An offer is already open",
                    body = "${activeOffer.originArea} → ${activeOffer.destinationArea} · ${activeOffer.travelDate.displayName} at ${formatDemoTime(activeOffer.departureMinutes)}. You can view or cancel it in Trips.",
                )
            }
        }
        item {
            InfoCard(
                title = "A planned journey, not a taxi service",
                body = "You are offering spare seats on a journey you already intend to make, not operating an on-demand taxi service.",
            )
        }
        if (joinedCircle != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Offer visibility", style = MaterialTheme.typography.titleMedium)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = !offerWithinCircle,
                            onClick = { onOfferWithinCircleChange(false) },
                            label = { Text("Ordinary Ryde offer") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FilterChip(
                            selected = offerWithinCircle,
                            onClick = { onOfferWithinCircleChange(true) },
                            label = { Text("Offer within Nottingham Live") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (offerWithinCircle) Text("Ryde Circle journey · host-covered service fee", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item {
            OutlinedTextField(
                value = criteria.originArea,
                onValueChange = onOriginChange,
                label = { Text("Origin area") },
                singleLine = true,
                isError = originError != null,
                supportingText = originError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            content.savedPlaces.firstOrNull { it.label == "Home" }?.let { place ->
                AssistChip(onClick = { onOriginChange(place.area) }, label = { Text("Home · ${place.area}") })
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
                value = criteria.destinationArea,
                onValueChange = onDestinationChange,
                label = { Text("Destination area") },
                singleLine = true,
                isError = destinationError != null,
                supportingText = destinationError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            content.savedPlaces.firstOrNull { it.label == "Work" }?.let { place ->
                AssistChip(onClick = { onDestinationChange(place.area) }, label = { Text("Work · ${place.area}") })
            }
        }
        if (criteria.circleId != null) {
            item {
                InfoCard(
                    title = "Circle event date",
                    body = criteria.travelDate.displayName,
                )
            }
        } else {
            item {
                OfferChoices(
                    "Demo travel date",
                    DemoTravelDate.ordinaryChoices,
                    criteria.travelDate,
                    { it.displayName },
                    onDateChange,
                )
            }
        }
        item {
            OfferChoices(
                "Planned departure time",
                if (criteria.circleId == null) {
                    DemoDepartureTimePolicy.ordinaryOfferChoices
                } else {
                    DemoDepartureTimePolicy.circleChoices
                },
                criteria.departureMinutes,
                ::formatDemoTime,
                onTimeChange,
            )
        }
        item { OfferChoices("Departure flexibility", Flexibility.entries, criteria.flexibility, { it.displayName }, onFlexibilityChange) }
        item {
            NumberChoiceCard(
                title = "Spare seats",
                supporting = errors.firstOrNull { it.field == OfferRideField.SEATS }?.message ?: "Choose 1–4 seats already spare",
                value = criteria.spareSeats,
                decreaseEnabled = criteria.spareSeats > 1,
                increaseEnabled = criteria.spareSeats < 4,
                onDecrease = { onSeatsChange(criteria.spareSeats - 1) },
                onIncrease = { onSeatsChange(criteria.spareSeats + 1) },
            )
        }
        item {
            OfferChoices(
                "Maximum acceptable pickup detour",
                listOf(1, 3, 5),
                criteria.maximumDetourMiles,
                { "Up to $it ${if (it == 1) "mile" else "miles"}" },
                onDetourChange,
            )
            errors.firstOrNull { it.field == OfferRideField.DETOUR }?.let {
                Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            InfoCard(
                title = "Broad areas only",
                body = "No exact home address should be displayed or published. This local demo stores only the broad areas entered above.",
            )
        }
        item {
            Text(
                "Contributions will be based on shared distance, not demand, delays or surge pricing.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onReview, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Text("Review journey", modifier = Modifier.padding(vertical = 5.dp))
            }
        }
    }
}

@Composable
private fun OfferReview(
    criteria: OfferRideCriteria,
    acknowledged: Boolean,
    onAcknowledgedChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onPublish: () -> Unit,
    modifier: Modifier,
) {
    OfferList(modifier) {
        item {
            OutlinedButton(onClick = onBack) { Text("← Back to edit") }
            Spacer(Modifier.height(12.dp))
            OfferHeader("Review your journey", "Nothing is published outside this local demo")
            if (criteria.circleId != null) LabelPill("Ryde Circle · Nottingham Live — Event Travel")
        }
        item {
            OfferCard("Journey offered") {
                OfferRow("Route", "${criteria.originArea.trim()} → ${criteria.destinationArea.trim()}")
                OfferRow("Date and time", "${criteria.travelDate.displayName} · ${formatDemoTime(criteria.departureMinutes)}")
                OfferRow("Flexibility", criteria.flexibility.displayName)
                OfferRow("Spare seats", criteria.spareSeats.toString())
                OfferRow("Maximum detour", detourLabel(criteria.maximumDetourMiles))
            }
        }
        item {
            OfferCard("Privacy and demo acknowledgement") {
                Text("Only broad journey areas are stored in this local demo.", fontWeight = FontWeight.Bold)
                Text("No exact home address is displayed. No real journey, location or personal details will be published, and no real rider will see this offer.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = acknowledged, onCheckedChange = onAcknowledgedChange)
                    Text("I understand this is a local fictional demo", modifier = Modifier.weight(1f))
                }
            }
        }
        item {
            InfoCard(
                title = "Transparent contributions",
                body = if (criteria.circleId == null) {
                    "Any future rider contribution will be calculated from the distance actually shared—not demand, delays or surge pricing."
                } else {
                    "The rider contribution still uses shared distance. The fictional Circle host covers Ryde’s £0.50 fee; no real sponsorship or payment occurs."
                },
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onPublish, enabled = acknowledged, modifier = Modifier.fillMaxWidth()) {
                Text("Offer spare seats")
            }
        }
    }
}

@Composable
private fun OfferSuccess(
    journey: OfferedJourney?,
    duplicate: Boolean,
    onOpenTrips: () -> Unit,
    onOfferAnother: () -> Unit,
    modifier: Modifier,
) {
    OfferList(modifier) {
        item { OfferHeader(if (duplicate) "Journey already offered" else "Spare seats offered", "Open for requests · local demo") }
        item {
            OfferCard("Local-demo status") {
                if (journey != null) {
                    journey.circle?.let { LabelPill("Ryde Circle · ${it.name}") }
                    Text("${journey.originArea} → ${journey.destinationArea}", style = MaterialTheme.typography.titleLarge)
                    Text("${journey.travelDate.displayName} · ${formatDemoTime(journey.departureMinutes)} · ${journey.spareSeats} ${if (journey.spareSeats == 1) "seat" else "seats"}")
                    Text("Maximum detour ${detourLabel(journey.maximumDetourMiles)}")
                }
                Text(
                    if (duplicate) "The matching offer was already open, so no duplicate was created."
                    else "Your offered journey is now saved for this app session.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (journey?.circle == null) {
                        "No real rider has seen it, and no journey, personal details or location were published."
                    } else {
                        "No real rider or host was contacted. No payment, sponsorship, journey, personal details or location were published."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Button(onClick = onOpenTrips, modifier = Modifier.fillMaxWidth()) { Text("Open Trips") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOfferAnother, modifier = Modifier.fillMaxWidth()) { Text("Offer another planned journey") }
        }
    }
}

@Composable
private fun NumberChoiceCard(
    title: String,
    supporting: String,
    value: Int,
    decreaseEnabled: Boolean,
    increaseEnabled: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(supporting, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = onDecrease, enabled = decreaseEnabled) { Text("−") }
            Text(value.toString(), modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = onIncrease, enabled = increaseEnabled) { Text("+") }
        }
    }
}

@Composable
private fun <T> OfferChoices(title: String, options: List<T>, selected: T, label: (T) -> String, onSelected: (T) -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(selected = option == selected, onClick = { onSelected(option) }, label = { Text(label(option)) })
            }
        }
    }
}

@Composable
private fun OfferHeader(title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RouteMark("Ryde merging route mark", Modifier.size(38.dp))
        Spacer(Modifier.weight(1f))
        LabelPill("PHASE 4 · LOCAL DEMO")
    }
    Spacer(Modifier.height(14.dp))
    Text(title, style = MaterialTheme.typography.headlineMedium)
    Text(subtitle, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun OfferCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun OfferRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun OfferList(modifier: Modifier, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

private fun List<OfferRideValidationError>.without(vararg fields: OfferRideField) = filterNot { it.field in fields }
private fun detourLabel(miles: Int) = "up to $miles ${if (miles == 1) "mile" else "miles"}"
