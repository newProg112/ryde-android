package uk.rydeapp.ryde.ui.destinations

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import uk.rydeapp.ryde.R
import uk.rydeapp.ryde.domain.model.SavedPlace
import uk.rydeapp.ryde.domain.model.SeatRequest
import uk.rydeapp.ryde.domain.model.SeatRequestStatus
import uk.rydeapp.ryde.domain.model.OfferedJourney
import uk.rydeapp.ryde.domain.model.OfferedJourneyStatus
import uk.rydeapp.ryde.domain.model.ConfirmedSharedTrip
import uk.rydeapp.ryde.domain.model.DecideIncomingRequestResult
import uk.rydeapp.ryde.domain.model.IncomingRequestDecision
import uk.rydeapp.ryde.domain.model.IncomingSeatRequest
import uk.rydeapp.ryde.domain.model.IncomingSeatRequestStatus
import uk.rydeapp.ryde.domain.model.CircleIdentity
import uk.rydeapp.ryde.domain.model.JourneyPricing
import uk.rydeapp.ryde.domain.model.formatDemoTime
import uk.rydeapp.ryde.domain.model.CompletedJourneyHistory
import uk.rydeapp.ryde.domain.model.ProfileContent
import uk.rydeapp.ryde.domain.model.UpdateTrustedPersonResult
import uk.rydeapp.ryde.domain.model.CompleteJourneyResult
import uk.rydeapp.ryde.domain.model.ConversationId
import uk.rydeapp.ryde.domain.model.ConversationThread
import uk.rydeapp.ryde.domain.model.CoordinationActivityItem
import uk.rydeapp.ryde.domain.model.CoordinationActivityType
import uk.rydeapp.ryde.domain.model.CoordinationUnreadCounts
import uk.rydeapp.ryde.domain.model.GetConversationResult
import uk.rydeapp.ryde.domain.model.JourneyLifecyclePolicy
import uk.rydeapp.ryde.domain.model.JourneyLifecycleStatus
import uk.rydeapp.ryde.domain.model.JourneyStatusUpdateResult
import uk.rydeapp.ryde.domain.model.MessageRejectionReason
import uk.rydeapp.ryde.domain.model.SendMessageResult
import uk.rydeapp.ryde.ui.components.DestinationIcon
import uk.rydeapp.ryde.ui.components.DestinationIconType
import uk.rydeapp.ryde.ui.components.InfoCard
import uk.rydeapp.ryde.ui.components.LabelPill
import uk.rydeapp.ryde.ui.components.RouteMark
import uk.rydeapp.ryde.ui.theme.Mint
import java.text.NumberFormat
import java.util.Locale

@Composable
fun TripsScreen(
    requests: List<SeatRequest>,
    offeredJourneys: List<OfferedJourney>,
    incomingRequests: List<IncomingSeatRequest>,
    confirmedTrips: List<ConfirmedSharedTrip>,
    completedJourneyHistory: List<CompletedJourneyHistory>,
    coordinationActivities: List<CoordinationActivityItem>,
    unreadCounts: CoordinationUnreadCounts,
    onCancelRequest: (String) -> Unit,
    onCancelOffer: (String) -> Unit,
    onDecideIncomingRequest: (String, IncomingRequestDecision) -> DecideIncomingRequestResult,
    onOpenConversation: (String) -> GetConversationResult,
    onSendMessage: (ConversationId, String) -> SendMessageResult,
    onMarkActivityRead: (String) -> Unit,
    onUpdateJourneyStatus: (String, JourneyLifecycleStatus) -> JourneyStatusUpdateResult,
    onCompleteJourney: (String) -> CompleteJourneyResult,
    onTravelTogetherAgain: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedRequestId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedOfferId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedIncomingRequestId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedConfirmedTripId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCompletedJourneyId by rememberSaveable { mutableStateOf<String?>(null) }
    var openConversation by remember { mutableStateOf<ConversationThread?>(null) }
    var conversationNotice by rememberSaveable { mutableStateOf<String?>(null) }
    var showCancelConfirmation by rememberSaveable { mutableStateOf(false) }
    var showCompleteConfirmation by rememberSaveable { mutableStateOf(false) }
    var pendingDecision by rememberSaveable { mutableStateOf<IncomingRequestDecision?>(null) }
    val selectedRequest = requests.firstOrNull { it.id == selectedRequestId }
    val selectedOffer = offeredJourneys.firstOrNull { it.id == selectedOfferId }
    val selectedIncomingRequest = incomingRequests.firstOrNull { it.id == selectedIncomingRequestId }
    val selectedConfirmedTrip = confirmedTrips.firstOrNull { it.id == selectedConfirmedTripId }
    val selectedCompletedJourney = completedJourneyHistory.firstOrNull { it.id == selectedCompletedJourneyId }

    BackHandler(
        enabled = openConversation != null || selectedRequest != null || selectedOffer != null ||
            selectedIncomingRequest != null || selectedConfirmedTrip != null || selectedCompletedJourney != null,
    ) {
        if (openConversation != null) {
            openConversation = null
            conversationNotice = null
        } else if (selectedIncomingRequest != null) {
            selectedIncomingRequestId = null
        } else {
            selectedRequestId = null
            selectedOfferId = null
            selectedConfirmedTripId = null
            selectedCompletedJourneyId = null
        }
        showCancelConfirmation = false
        showCompleteConfirmation = false
        pendingDecision = null
    }

    if (showCompleteConfirmation && selectedConfirmedTrip != null) {
        AlertDialog(
            onDismissRequest = { showCompleteConfirmation = false },
            title = { Text("Mark journey completed?") },
            text = {
                Text("This moves the fictional trip into completed history and unlocks the existing trust and repeat-journey options for Jamie.")
            },
            confirmButton = {
                Button(onClick = {
                    when (val result = onCompleteJourney(selectedConfirmedTrip.id)) {
                        is CompleteJourneyResult.Completed -> {
                            selectedConfirmedTripId = null
                            selectedCompletedJourneyId = result.journey.id
                        }
                        is CompleteJourneyResult.AlreadyCompleted -> {
                            selectedConfirmedTripId = null
                            selectedCompletedJourneyId = result.journey.id
                        }
                        is CompleteJourneyResult.Rejected -> Unit
                    }
                    showCompleteConfirmation = false
                }) { Text("Complete journey") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCompleteConfirmation = false }) { Text("Not yet") }
            },
        )
    }

    if (pendingDecision != null && selectedIncomingRequest != null) {
        val decision = pendingDecision ?: IncomingRequestDecision.DECLINE
        val accepting = decision == IncomingRequestDecision.ACCEPT
        AlertDialog(
            onDismissRequest = { pendingDecision = null },
            title = { Text(if (accepting) "Accept demo request?" else "Decline demo request?") },
            text = {
                Text(
                    if (accepting) {
                        "This confirms a fictional shared trip locally. No payment, contact or live location sharing will occur."
                    } else {
                        "The fictional request stays as declined local-demo history and your offer remains open."
                    },
                )
            },
            confirmButton = {
                Button(onClick = {
                    val result = onDecideIncomingRequest(selectedIncomingRequest.id, decision)
                    pendingDecision = null
                    if (result is DecideIncomingRequestResult.Decided) {
                        selectedIncomingRequestId = null
                        result.confirmedTrip?.let {
                            selectedOfferId = null
                            selectedConfirmedTripId = it.id
                        }
                    }
                }) { Text(if (accepting) "Accept request" else "Decline request") }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingDecision = null }) { Text("Go back") }
            },
        )
    }

    if (showCancelConfirmation && (selectedRequest != null || selectedOffer != null)) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmation = false },
            title = { Text(if (selectedOffer != null) "Cancel demo offer?" else "Cancel demo request?") },
            text = {
                Text(
                    if (selectedOffer != null) {
                        "It will remain visible in Trips as Cancelled. No real rider has seen this offer."
                    } else {
                        "It will remain visible in Trips as Cancelled. No real driver has been contacted."
                    },
                )
            },
            confirmButton = {
                Button(onClick = {
                    selectedOffer?.let { onCancelOffer(it.id) }
                    selectedRequest?.let { onCancelRequest(it.id) }
                    showCancelConfirmation = false
                }) { Text(if (selectedOffer != null) "Cancel offer" else "Cancel request") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCancelConfirmation = false }) {
                    Text(if (selectedOffer != null) "Keep offer" else "Keep request")
                }
            },
        )
    }

    DestinationShell(
        headingRes = R.string.trips_heading,
        bodyRes = R.string.trips_body,
        iconType = DestinationIconType.TRIPS,
        phaseLabel = "PHASE 8 · LOCAL DEMO",
        modifier = modifier,
    ) {
        when {
            openConversation != null -> ConversationDetail(
                conversation = openConversation!!,
                notice = conversationNotice,
                onBack = {
                    openConversation = null
                    conversationNotice = null
                },
                onSendMessage = { body ->
                    when (val result = onSendMessage(openConversation!!.id, body)) {
                        is SendMessageResult.Sent -> {
                            openConversation = result.conversation
                            conversationNotice = null
                            true
                        }
                        is SendMessageResult.Rejected -> {
                            conversationNotice = messageRejectionLabel(result)
                            false
                        }
                    }
                },
            )
            selectedCompletedJourney != null -> CompletedJourneyDetail(
                journey = selectedCompletedJourney,
                onBack = { selectedCompletedJourneyId = null },
                onTravelTogetherAgain = {
                    onTravelTogetherAgain(selectedCompletedJourney.id, selectedCompletedJourney.personId)
                },
                onOpenMessages = {
                    when (val result = onOpenConversation(selectedCompletedJourney.id)) {
                        is GetConversationResult.Available -> {
                            openConversation = result.conversation
                            conversationNotice = null
                        }
                        is GetConversationResult.Unavailable -> {
                            conversationNotice = "No conversation is stored for this seeded history item."
                        }
                    }
                },
                messageNotice = conversationNotice,
            )
            selectedConfirmedTrip != null -> ConfirmedSharedTripDetail(
                trip = selectedConfirmedTrip,
                onBack = { selectedConfirmedTripId = null },
                onMessage = {
                    when (val result = onOpenConversation(selectedConfirmedTrip.id)) {
                        is GetConversationResult.Available -> {
                            openConversation = result.conversation
                            conversationNotice = null
                        }
                        is GetConversationResult.Unavailable -> {
                            conversationNotice = "Messaging is unavailable because this participant is blocked or reported."
                        }
                    }
                },
                onNextStatus = { status -> onUpdateJourneyStatus(selectedConfirmedTrip.id, status) },
                onComplete = { showCompleteConfirmation = true },
                messageNotice = conversationNotice,
            )
            selectedIncomingRequest != null -> IncomingRequestDetail(
                request = selectedIncomingRequest,
                journey = offeredJourneys.first { it.id == selectedIncomingRequest.offeredJourneyId },
                onBack = { selectedIncomingRequestId = null },
                onAccept = { pendingDecision = IncomingRequestDecision.ACCEPT },
                onDecline = { pendingDecision = IncomingRequestDecision.DECLINE },
            )
            selectedRequest != null -> TripRequestDetail(
                request = selectedRequest,
                onBack = { selectedRequestId = null },
                onCancel = { showCancelConfirmation = true },
            )
            selectedOffer != null -> OfferedJourneyDetail(
                journey = selectedOffer,
                incomingRequest = incomingRequests.firstOrNull { it.offeredJourneyId == selectedOffer.id },
                onBack = { selectedOfferId = null },
                onCancel = { showCancelConfirmation = true },
                onReviewRequest = { selectedIncomingRequestId = it },
            )
            requests.isEmpty() && offeredJourneys.isEmpty() && confirmedTrips.isEmpty() && completedJourneyHistory.isEmpty() -> EmptyStateCard()
            else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CoordinationActivitySection(
                    activities = coordinationActivities,
                    unreadCounts = unreadCounts,
                    onOpen = { activity ->
                        onMarkActivityRead(activity.id)
                        if (activity.type == CoordinationActivityType.NEW_MESSAGE) {
                            when (val result = onOpenConversation(activity.confirmedTripId)) {
                                is GetConversationResult.Available -> {
                                    openConversation = result.conversation
                                    conversationNotice = null
                                }
                                is GetConversationResult.Unavailable -> {
                                    conversationNotice = "Messaging is unavailable for this participant."
                                }
                            }
                        } else if (confirmedTrips.any { it.id == activity.confirmedTripId }) {
                            selectedConfirmedTripId = activity.confirmedTripId
                        } else if (completedJourneyHistory.any { it.id == activity.confirmedTripId }) {
                            selectedCompletedJourneyId = activity.confirmedTripId
                        }
                    },
                )
                if (requests.isNotEmpty()) {
                    Text("Your outgoing seat requests", style = MaterialTheme.typography.titleMedium)
                }
                requests.forEach { request ->
                    TripRequestCard(request, onClick = {
                        selectedOfferId = null
                        selectedRequestId = request.id
                    })
                }
                if (offeredJourneys.any { it.status != OfferedJourneyStatus.CONFIRMED }) {
                    Text("Journeys you offered", style = MaterialTheme.typography.titleMedium)
                }
                offeredJourneys.filter { it.status != OfferedJourneyStatus.CONFIRMED }.forEach { journey ->
                    OfferedJourneyCard(
                        journey = journey,
                        incomingRequest = incomingRequests.firstOrNull { it.offeredJourneyId == journey.id },
                        onClick = {
                            selectedRequestId = null
                            selectedOfferId = journey.id
                        },
                    )
                }
                if (confirmedTrips.isNotEmpty()) {
                    Text("Confirmed shared trips", style = MaterialTheme.typography.titleMedium)
                }
                confirmedTrips.forEach { trip ->
                    ConfirmedSharedTripCard(trip, onClick = { selectedConfirmedTripId = trip.id })
                }
                if (completedJourneyHistory.isNotEmpty()) {
                    Text("Completed journey history", style = MaterialTheme.typography.titleMedium)
                }
                completedJourneyHistory.forEach { journey ->
                    CompletedJourneyCard(journey, onClick = { selectedCompletedJourneyId = journey.id })
                }
            }
        }
    }
}

@Composable
private fun CoordinationActivitySection(
    activities: List<CoordinationActivityItem>,
    unreadCounts: CoordinationUnreadCounts,
    onOpen: (CoordinationActivityItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("In-app activity", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (unreadCounts.activity > 0) {
                LabelPill("${unreadCounts.activity} unread", containerColor = MaterialTheme.colorScheme.primaryContainer)
            }
        }
        Text(
            "Local session only — these are not Android push notifications.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        if (activities.isEmpty()) {
            Text("Coordination updates will appear after a demo request is accepted.")
        } else {
            activities.take(5).forEach { item ->
                Card(
                    onClick = { onOpen(item) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (item.isRead) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                    ),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(item.displayTime, style = MaterialTheme.typography.labelSmall)
                        }
                        Text(item.body, style = MaterialTheme.typography.bodySmall)
                        if (!item.isRead) Text("Unread · open update", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (unreadCounts.messages > 0) {
            Text(
                "${unreadCounts.messages} unread message${if (unreadCounts.messages == 1) "" else "s"}",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun ConversationDetail(
    conversation: ConversationThread,
    notice: String?,
    onBack: () -> Unit,
    onSendMessage: (String) -> Boolean,
) {
    var draft by rememberSaveable(conversation.id.value) { mutableStateOf("") }
    val currentUserId = conversation.participants.firstOrNull {
        it.role == uk.rydeapp.ryde.domain.model.JourneyParticipantRole.DRIVER
    }?.id
    val otherParticipant = conversation.participants.firstOrNull { it.id != currentUserId }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onBack) { Text("← Back to journey") }
        Text("Messages with ${otherParticipant?.firstName ?: "participant"}", style = MaterialTheme.typography.headlineSmall)
        Text(conversation.journeyLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        InfoCard(
            title = "Fictional local-demo conversation",
            body = "Messages stay in this app session. Discuss public pickup areas only — never a private address or exact/live location.",
        )
        conversation.messages.forEach { message ->
            val mine = message.senderId == currentUserId
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(.84f),
                    shape = RoundedCornerShape(16.dp),
                    color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(if (mine) "You · Sam" else otherParticipant?.firstName.orEmpty(), fontWeight = FontWeight.Bold)
                        Text(message.body)
                        Text(message.displayTime, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (conversation.canSendMessages) {
            Text("Quick replies", style = MaterialTheme.typography.titleSmall)
            listOf(
                "I’ve arrived at the pickup point",
                "I’m running five minutes late",
                "Thanks — I’m on my way",
            ).forEach { reply ->
                OutlinedButton(
                    onClick = { draft = reply },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(reply) }
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Message") },
                supportingText = { Text("Public pickup coordination only") },
            )
            Button(
                onClick = { if (onSendMessage(draft)) draft = "" },
                enabled = draft.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Send demo message") }
        } else {
            InfoCard(
                title = "Conversation history only",
                body = "This journey is completed or cancelled, so new messages are disabled.",
            )
        }
        notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

private fun messageRejectionLabel(result: SendMessageResult.Rejected): String = when (result.reason) {
    MessageRejectionReason.EMPTY_MESSAGE -> "Write a message first."
    MessageRejectionReason.PRIVATE_OR_LIVE_LOCATION ->
        "Keep messages to public pickup areas; private addresses and exact/live locations are not allowed."
    MessageRejectionReason.JOURNEY_CANCELLED -> "This journey is cancelled, so new messages are disabled."
    MessageRejectionReason.JOURNEY_COMPLETED -> "This journey is completed, so new messages are disabled."
    MessageRejectionReason.PARTICIPANT_BLOCKED_OR_REPORTED ->
        "Messaging is unavailable because this participant is blocked or reported."
    MessageRejectionReason.CONVERSATION_NOT_FOUND -> "This local-demo conversation is unavailable."
}

@Composable
fun ProfileScreen(
    content: ProfileContent,
    onSetPersonTrusted: (String, Boolean) -> UpdateTrustedPersonResult,
    onTravelTogetherAgain: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    DestinationShell(
        headingRes = R.string.profile_heading,
        bodyRes = R.string.profile_body,
        iconType = DestinationIconType.PROFILE,
        phaseLabel = "PHASE 7 · LOCAL DEMO",
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ProfileIdentityCard(content)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.saved_places), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    content.savedPlaces.forEach { place -> SavedPlaceCard(place, Modifier.weight(1f)) }
                }
                Text(
                    "Broad areas only — never a private street address. Tap Home or Work in Find and Offer, then edit as needed.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Trusted people", style = MaterialTheme.typography.titleMedium)
                if (content.people.isEmpty()) {
                    InfoCard("No eligible people yet", "Someone appears here only after a completed shared trip in this local demo.")
                } else {
                    content.people.forEach { person ->
                        val completedTrip = content.completedJourneys.firstOrNull { it.id in person.completedTripIds }
                        TrustedPersonCard(
                            personName = person.firstName,
                            initials = person.initials,
                            rating = person.rating,
                            isTrusted = person.isTrusted,
                            onToggleTrust = { onSetPersonTrusted(person.id, !person.isTrusted) },
                            onRepeat = completedTrip?.takeIf { person.isTrusted }?.let { trip ->
                                { onTravelTogetherAgain(trip.id, person.id) }
                            },
                        )
                    }
                }
                Text(
                    "Trust is your personal, session-local reminder — not identity verification. A future blocked or reported status always overrides it.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            InfoCard(
                title = "Location sharing · Not shared",
                body = "Ryde is not using device location. Exact or live location would require a separate, clear consent step before sharing could start.",
            )
        }
    }
}

@Composable
private fun ProfileIdentityCard(content: ProfileContent) {
    val identity = content.identity
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        identity.initials,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(identity.firstName, style = MaterialTheme.typography.headlineSmall)
                    Text(identity.memberSince, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LabelPill("FICTIONAL · LOCAL DEMO")
                }
            }
            HorizontalDivider()
            TripDetailRow("Completed shared journeys", identity.completedSharedJourneys.toString())
            TripDetailRow("Reliability", "${identity.reliabilityPercent}%")
            TripDetailRow("Demo rating", "★ ${identity.rating}")
            TripDetailRow("Fictional vehicle", "${identity.vehicle.colour} ${identity.vehicle.description}")
            Text(
                "These illustrative details are not verified identity, licence, vehicle or rating checks.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TrustedPersonCard(
    personName: String,
    initials: String,
    rating: Double,
    isTrusted: Boolean,
    onToggleTrust: () -> Unit,
    onRepeat: (() -> Unit)?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(initials, modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(personName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("1 completed shared trip · demo rating ★ $rating", style = MaterialTheme.typography.bodySmall)
                }
                if (isTrusted) LabelPill("Trusted", containerColor = Mint.copy(alpha = .22f))
            }
            if (onRepeat != null) {
                Button(onClick = onRepeat, modifier = Modifier.fillMaxWidth()) { Text("Travel together again") }
            }
            OutlinedButton(onClick = onToggleTrust, modifier = Modifier.fillMaxWidth()) {
                Text(if (isTrusted) "Remove from trusted" else "Add to trusted people")
            }
        }
    }
}

@Composable
private fun CompletedJourneyCard(journey: CompletedJourneyHistory, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LabelPill("Completed · local demo", containerColor = Mint.copy(alpha = .22f))
            Text("${journey.originArea} → ${journey.destinationArea}", style = MaterialTheme.typography.titleLarge)
            Text("Shared with fictional ${journey.personName} · ${journey.completedLabel}")
            Text("Open journey history →", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CompletedJourneyDetail(
    journey: CompletedJourneyHistory,
    onBack: () -> Unit,
    onTravelTogetherAgain: () -> Unit,
    onOpenMessages: () -> Unit,
    messageNotice: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedButton(onClick = onBack) { Text("← Back to trips") }
        LabelPill("Completed · local demo", containerColor = Mint.copy(alpha = .22f))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${journey.originArea} → ${journey.destinationArea}", style = MaterialTheme.typography.titleLarge)
                TripDetailRow("Travelled with", "Fictional ${journey.personName}")
                TripDetailRow("Status", journey.completedLabel)
                Text("Only broad journey areas are retained for this session.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Button(onClick = onTravelTogetherAgain, modifier = Modifier.fillMaxWidth()) {
            Text("Travel together again")
        }
        OutlinedButton(onClick = onOpenMessages, modifier = Modifier.fillMaxWidth()) {
            Text("View messages from this journey")
        }
        messageNotice?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        InfoCard(
            "Review before continuing",
            "Find will open with this route and person as context. Nothing is created or confirmed automatically.",
        )
    }
}

@Composable
private fun DestinationShell(
    @StringRes headingRes: Int,
    @StringRes bodyRes: Int,
    iconType: DestinationIconType,
    modifier: Modifier = Modifier,
    phaseLabel: String? = null,
    content: @Composable () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RouteMark(stringResource(R.string.route_mark_description), modifier = Modifier.size(34.dp))
                Spacer(Modifier.weight(1f))
                LabelPill(phaseLabel ?: stringResource(R.string.phase_one_label))
            }
        }
        item {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                DestinationIcon(
                    type = iconType,
                    contentDescription = stringResource(headingRes),
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
        item {
            Column {
                Text(stringResource(headingRes), style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(bodyRes),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        item { content() }
    }
}

@Composable
private fun TripRequestCard(request: SeatRequest, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Seat request", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            CircleTripLabel(request.circle)
            Row(verticalAlignment = Alignment.CenterVertically) {
                LabelPill(
                    if (request.status == SeatRequestStatus.PENDING) "Pending driver response" else "Cancelled",
                    containerColor = if (request.status == SeatRequestStatus.PENDING) Mint.copy(alpha = .22f) else MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text("LOCAL DEMO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            Text("${request.originArea} → ${request.destinationArea}", style = MaterialTheme.typography.titleLarge)
            Text("${request.travelDate.displayName} · around ${formatDemoTime(request.approximatePickupMinutes)}")
            Text("Fictional driver ${request.driver.firstName} · ${request.requestedSeats} ${seatWord(request.requestedSeats)}")
            Text("Public pickup: ${request.pickupArea}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Row {
                Text("Rider total", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text(money(request.riderTotalPence), fontWeight = FontWeight.Bold)
            }
            Text("Open request details →", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun OfferedJourneyCard(
    journey: OfferedJourney,
    incomingRequest: IncomingSeatRequest?,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Journey offered", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            CircleTripLabel(journey.circle)
            Row(verticalAlignment = Alignment.CenterVertically) {
                LabelPill(
                    if (journey.status == OfferedJourneyStatus.OPEN) "Open for requests" else "Cancelled",
                    containerColor = if (journey.status == OfferedJourneyStatus.OPEN) Mint.copy(alpha = .22f) else MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text("LOCAL DEMO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            Text("${journey.originArea} → ${journey.destinationArea}", style = MaterialTheme.typography.titleLarge)
            Text("${journey.travelDate.displayName} · ${formatDemoTime(journey.departureMinutes)}")
            Text("${journey.spareSeats} spare ${seatWord(journey.spareSeats)} · Maximum detour ${tripDetourLabel(journey.maximumDetourMiles)}")
            if (journey.status == OfferedJourneyStatus.OPEN && incomingRequest?.status == IncomingSeatRequestStatus.PENDING) {
                LabelPill("1 demo seat request", containerColor = MaterialTheme.colorScheme.primaryContainer)
            } else if (incomingRequest?.status == IncomingSeatRequestStatus.DECLINED) {
                Text("1 demo seat request · Declined", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Open offered-journey details →", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TripRequestDetail(
    request: SeatRequest,
    onBack: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedButton(onClick = onBack) { Text("← Back to trips") }
        CircleTripLabel(request.circle)
        LabelPill(if (request.status == SeatRequestStatus.PENDING) "Pending driver response" else "Cancelled")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("${request.originArea} → ${request.destinationArea}", style = MaterialTheme.typography.titleLarge)
                TripDetailRow("Fictional driver", request.driver.firstName)
                TripDetailRow("Date and time", "${request.travelDate.displayName} · around ${formatDemoTime(request.approximatePickupMinutes)}")
                TripDetailRow("Requested seats", request.requestedSeats.toString())
                TripDetailRow("Public pickup area", request.pickupArea)
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                TripDetailRow("Shared-distance contribution", money(request.contributionPence))
                Text("${request.sharedMiles} shared miles × £0.20, rounded to the nearest 50p", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                TripDetailRow("Ryde service fee", serviceFeeValue(request.pricing))
                TripDetailRow("Rider total", money(request.riderTotalPence), bold = true)
                TripDetailRow("Driver receives", money(request.driverReceivesPence), bold = true)
            }
        }
        InfoCard(
            title = "Fictional local-demo data",
            body = if (request.circle == null) {
                "No request reached a real driver. No payment, verification, contact, map or exact/live location sharing occurred."
            } else {
                "No request reached a real driver. No payment or real host sponsorship occurred, and no exact/live location was shared."
            },
        )
        if (request.status == SeatRequestStatus.PENDING) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel demo request") }
        } else {
            Text("This cancelled request is kept here as local-demo history.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OfferedJourneyDetail(
    journey: OfferedJourney,
    incomingRequest: IncomingSeatRequest?,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onReviewRequest: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedButton(onClick = onBack) { Text("← Back to trips") }
        Text("Journey offered", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        CircleTripLabel(journey.circle)
        LabelPill(if (journey.status == OfferedJourneyStatus.OPEN) "Open for requests" else "Cancelled")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("${journey.originArea} → ${journey.destinationArea}", style = MaterialTheme.typography.titleLarge)
                TripDetailRow("Date and time", "${journey.travelDate.displayName} · ${formatDemoTime(journey.departureMinutes)}")
                TripDetailRow("Departure flexibility", journey.flexibility.displayName)
                TripDetailRow("Spare seats", journey.spareSeats.toString())
                TripDetailRow("Maximum pickup detour", tripDetourLabel(journey.maximumDetourMiles))
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("Any future contribution is based on distance actually shared—not demand, delays or surge pricing.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        InfoCard(
            title = "Fictional local-demo data",
            body = "Only broad journey areas are stored for this app session. No exact home address, real journey, personal details or live location were published, and no real rider has seen this offer.",
        )
        if (incomingRequest != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Incoming seat request", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    CircleTripLabel(incomingRequest.circle)
                    Text(
                        when (incomingRequest.status) {
                            IncomingSeatRequestStatus.PENDING -> "1 demo seat request from fictional rider ${incomingRequest.rider.firstName}"
                            IncomingSeatRequestStatus.ACCEPTED -> "Accepted · now shown as a confirmed shared trip"
                            IncomingSeatRequestStatus.DECLINED -> "Declined · retained as local-demo history"
                        },
                    )
                    Text(
                        "This was generated locally for the portfolio demo; no real rider submitted it.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { onReviewRequest(incomingRequest.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Review incoming request") }
                }
            }
        }
        if (journey.status == OfferedJourneyStatus.OPEN) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel demo offer") }
        } else {
            Text("This cancelled offer is kept here as local-demo history.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun IncomingRequestDetail(
    request: IncomingSeatRequest,
    journey: OfferedJourney,
    onBack: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedButton(onClick = onBack) { Text("← Back to offered journey") }
        Text("Incoming seat request", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        CircleTripLabel(request.circle)
        LabelPill(incomingStatusLabel(request.status))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RouteMark(
                        contentDescription = "Illustration of rider and driver routes merging; this is not a map",
                        modifier = Modifier.size(52.dp),
                    )
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text(request.rider.firstName, style = MaterialTheme.typography.titleLarge)
                        Text("★ ${request.rider.rating} · Demo-verified fictional profile")
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                TripDetailRow("Requested route", "${request.originArea} → ${request.destinationArea}")
                TripDetailRow("Your planned route", "${journey.originArea} → ${journey.destinationArea}")
                TripDetailRow("Date", request.travelDate.displayName)
                TripDetailRow("Public pickup area", request.pickupArea)
                TripDetailRow("Approximate pickup", "around ${formatDemoTime(request.approximatePickupMinutes)}")
                TripDetailRow("Walk to pickup", "about ${request.walkMinutes} minutes")
                TripDetailRow("Additional detour", "${request.detourMiles} miles")
                TripDetailRow("Seats requested", request.requestedSeats.toString())
                TripDetailRow("Seats remaining if accepted", (journey.spareSeats - request.requestedSeats).coerceAtLeast(0).toString())
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                TripDetailRow("Shared-distance contribution", money(request.contributionPence))
                Text(
                    "${request.sharedMiles} shared miles × £0.20, rounded to the nearest 50p",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                TripDetailRow("Ryde service fee", serviceFeeValue(request.pricing))
                TripDetailRow("Rider total", money(request.riderTotalPence), bold = true)
                TripDetailRow("Driver receives", money(request.driverReceivesPence), bold = true)
            }
        }
        InfoCard(
            title = "Privacy by design",
            body = "Only public, approximate areas are shown. Exact and live locations have not been shared.",
        )
        InfoCard(
            title = "Fictional local-demo request",
            body = if (request.circle == null) {
                "Jamie is fictional and this request was generated on this device. No real person submitted it, and no payment or contact has occurred."
            } else {
                "Jamie and the Circle are fictional. No real person submitted this, and no payment, host sponsorship or host contact occurred."
            },
        )
        if (request.status == IncomingSeatRequestStatus.PENDING && journey.status == OfferedJourneyStatus.OPEN) {
            Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) { Text("Accept request") }
            OutlinedButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) { Text("Decline request") }
        } else if (journey.status == OfferedJourneyStatus.CANCELLED && request.status == IncomingSeatRequestStatus.PENDING) {
            Text(
                "This request cannot be accepted or declined because the related offer is cancelled.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text("This decision is final for the current local-demo session.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ConfirmedSharedTripCard(trip: ConfirmedSharedTrip, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            CircleTripLabel(trip.circle)
            Row(verticalAlignment = Alignment.CenterVertically) {
                LabelPill("${journeyStatusLabel(trip.lifecycleStatus)} · local demo", containerColor = Mint.copy(alpha = .22f))
                Spacer(Modifier.weight(1f))
                Text("FICTIONAL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            Text("${trip.originArea} → ${trip.destinationArea}", style = MaterialTheme.typography.titleLarge)
            Text("${trip.travelDate.displayName} · around ${formatDemoTime(trip.approximatePickupMinutes)}")
            Text("Driver ${trip.driverName} · Rider ${trip.rider.firstName}")
            Text("Open confirmed trip details →", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ConfirmedSharedTripDetail(
    trip: ConfirmedSharedTrip,
    onBack: () -> Unit,
    onMessage: () -> Unit,
    onNextStatus: (JourneyLifecycleStatus) -> JourneyStatusUpdateResult,
    onComplete: () -> Unit,
    messageNotice: String?,
) {
    val nextStatus = JourneyLifecyclePolicy.nextProgressStatus(trip.lifecycleStatus)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedButton(onClick = onBack) { Text("← Back to trips") }
        CircleTripLabel(trip.circle)
        LabelPill("${journeyStatusLabel(trip.lifecycleStatus)} · local demo", containerColor = Mint.copy(alpha = .22f))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RouteMark(
                        contentDescription = "Illustration of the confirmed shared route; this is not a map",
                        modifier = Modifier.size(52.dp),
                    )
                    Spacer(Modifier.size(12.dp))
                    Text("${trip.originArea} → ${trip.destinationArea}", style = MaterialTheme.typography.titleLarge)
                }
                TripDetailRow("Driver", trip.driverName)
                TripDetailRow("Fictional rider", "${trip.rider.firstName} · ★ ${trip.rider.rating}")
                TripDetailRow("Date and pickup", "${trip.travelDate.displayName} · around ${formatDemoTime(trip.approximatePickupMinutes)}")
                TripDetailRow("Public pickup area", trip.pickupArea)
                TripDetailRow("Seats", trip.requestedSeats.toString())
                TripDetailRow("Spare seats remaining", trip.remainingSpareSeats.toString())
                TripDetailRow("Journey status", journeyStatusLabel(trip.lifecycleStatus))
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                TripDetailRow("Shared-distance contribution", money(trip.contributionPence))
                Text(
                    "${trip.sharedMiles} shared miles × £0.20, rounded to the nearest 50p",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                TripDetailRow("Ryde service fee", serviceFeeValue(trip.pricing))
                TripDetailRow("Rider total", money(trip.riderTotalPence), bold = true)
                TripDetailRow("Driver receives", money(trip.driverReceivesPence), bold = true)
            }
        }
        Button(onClick = onMessage, modifier = Modifier.fillMaxWidth()) { Text("Message ${trip.rider.firstName}") }
        messageNotice?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (nextStatus != null) {
            Button(
                onClick = {
                    if (nextStatus == JourneyLifecycleStatus.COMPLETED) onComplete() else onNextStatus(nextStatus)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(journeyActionLabel(nextStatus))
            }
        }
        InfoCard(
            title = "Location sharing",
            body = "Exact and live location is still not being shared in this demo. A real product would show a clear warning immediately before location sharing begins.",
        )
        InfoCard(
            title = "Local demo only",
            body = if (trip.circle == null) {
                "No real request, payment, contact or journey occurred. This confirmed state exists only in the current app session."
            } else {
                "No real request, payment, host sponsorship, contact or journey occurred. The Circle label remains as local-demo history."
            },
        )
    }
}

private fun journeyStatusLabel(status: JourneyLifecycleStatus): String = when (status) {
    JourneyLifecycleStatus.CONFIRMED -> "Confirmed"
    JourneyLifecycleStatus.DRIVER_EN_ROUTE -> "Driver en route"
    JourneyLifecycleStatus.READY_AT_PICKUP -> "Ready at pickup"
    JourneyLifecycleStatus.JOURNEY_UNDERWAY -> "Journey underway"
    JourneyLifecycleStatus.COMPLETED -> "Completed"
    JourneyLifecycleStatus.CANCELLED -> "Cancelled"
}

private fun journeyActionLabel(status: JourneyLifecycleStatus): String = when (status) {
    JourneyLifecycleStatus.DRIVER_EN_ROUTE -> "Start driving to pickup"
    JourneyLifecycleStatus.READY_AT_PICKUP -> "Mark ready at pickup"
    JourneyLifecycleStatus.JOURNEY_UNDERWAY -> "Start journey"
    JourneyLifecycleStatus.COMPLETED -> "Mark journey completed"
    JourneyLifecycleStatus.CONFIRMED,
    JourneyLifecycleStatus.CANCELLED,
    -> "Update journey"
}

private fun incomingStatusLabel(status: IncomingSeatRequestStatus): String = when (status) {
    IncomingSeatRequestStatus.PENDING -> "Pending · fictional demo"
    IncomingSeatRequestStatus.ACCEPTED -> "Accepted · local demo"
    IncomingSeatRequestStatus.DECLINED -> "Declined · local demo"
}

@Composable
private fun TripDetailRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(
            value,
            modifier = Modifier.weight(1f),
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.End,
        )
    }
}

private fun money(pence: Int): String = NumberFormat.getCurrencyInstance(Locale.UK).format(pence / 100.0)
private fun serviceFeeValue(pricing: JourneyPricing): String =
    if (pricing.hostCoversServiceFee) "${money(pricing.serviceFeePence)} · covered by host" else money(pricing.serviceFeePence)

@Composable
private fun CircleTripLabel(circle: CircleIdentity?) {
    circle?.let { LabelPill("Ryde Circle · ${it.name}") }
}
private fun seatWord(count: Int): String = if (count == 1) "seat" else "seats"
private fun tripDetourLabel(miles: Int): String = "up to $miles ${if (miles == 1) "mile" else "miles"}"

@Composable
private fun EmptyStateCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val description = stringResource(R.string.trips_info_title)
            Canvas(
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = description },
            ) {
                drawCircle(Mint.copy(alpha = .22f))
                drawCircle(Mint, radius = size.minDimension * .10f)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.trips_info_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                stringResource(R.string.trips_info_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SavedPlaceCard(place: SavedPlace, modifier: Modifier = Modifier) {
    val placeDescription = stringResource(
        R.string.saved_place_description,
        place.label,
        place.area,
    )
    Card(
        modifier = modifier.semantics { contentDescription = placeDescription },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(15.dp)) {
            Text(place.label, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(3.dp))
            Text(
                place.area,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
