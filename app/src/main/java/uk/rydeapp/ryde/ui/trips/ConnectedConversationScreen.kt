package uk.rydeapp.ryde.ui.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import uk.rydeapp.ryde.R
import uk.rydeapp.ryde.data.connected.ConnectedConversation
import uk.rydeapp.ryde.data.connected.ConnectedConversationReadOnlyReason
import uk.rydeapp.ryde.data.connected.ConnectedConversationState
import uk.rydeapp.ryde.data.connected.ConnectedMessage
import uk.rydeapp.ryde.data.connected.ConnectedMessageCommandResult
import uk.rydeapp.ryde.data.connected.ConnectedMessagePolicy
import uk.rydeapp.ryde.data.connected.ConnectedJourneyPlanCommandResult
import uk.rydeapp.ryde.data.connected.ConnectedJourneyPlanPolicy
import uk.rydeapp.ryde.data.connected.ConnectedJourneyPlanStatus
import uk.rydeapp.ryde.data.connected.ConnectedRydeRepository
import uk.rydeapp.ryde.data.connected.status
import uk.rydeapp.ryde.ui.components.formatConnectedJourneyDeparture

@Composable
internal fun ConnectedConversationRoute(
    accountId: String,
    target: ConnectedMessageTarget,
    repository: ConnectedRydeRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var retryVersion by rememberSaveable(accountId, target.tripId) { mutableIntStateOf(0) }
    val conversationFlow = remember(accountId, target.tripId, retryVersion) {
        repository.observeConnectedConversation(target.tripId)
    }
    val state by conversationFlow.collectAsState(initial = ConnectedConversationState.Loading)
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable(accountId, target.tripId) { mutableStateOf("") }
    var pendingMessageId by rememberSaveable(accountId, target.tripId) { mutableStateOf<String?>(null) }
    var sending by remember(accountId, target.tripId) { mutableStateOf(false) }
    var sendError by remember(accountId, target.tripId) { mutableStateOf<String?>(null) }
    var pickupDraft by rememberSaveable(accountId, target.tripId) { mutableStateOf("") }
    var dropOffDraft by rememberSaveable(accountId, target.tripId) { mutableStateOf("") }
    var planBusy by remember(accountId, target.tripId) { mutableStateOf(false) }
    var planError by remember(accountId, target.tripId) { mutableStateOf<String?>(null) }

    val conversation = when (val current = state) {
        is ConnectedConversationState.Data -> current.conversation
        is ConnectedConversationState.Error -> current.previousConversation
        ConnectedConversationState.Loading -> null
    }
    LaunchedEffect(conversation?.messages, pendingMessageId) {
        val pending = pendingMessageId
        if (pending != null && conversation?.messages?.any { it.id == pending } == true) {
            draft = ""
            pendingMessageId = null
            sendError = null
            sending = false
        }
    }
    LaunchedEffect(conversation?.plan?.revision) {
        pickupDraft = conversation?.plan?.pickupDetails.orEmpty()
        dropOffDraft = conversation?.plan?.dropOffDetails.orEmpty()
        planError = null
    }

    fun handlePlanResult(result: ConnectedJourneyPlanCommandResult) {
        planError = when (result) {
            ConnectedJourneyPlanCommandResult.Success -> null
            is ConnectedJourneyPlanCommandResult.InvalidInput -> result.userMessage
            is ConnectedJourneyPlanCommandResult.ReadOnly -> result.userMessage
            is ConnectedJourneyPlanCommandResult.Failure -> result.userMessage
        }
    }

    ConnectedConversationScreen(
        accountId = accountId,
        otherDisplayName = target.otherDisplayName,
        state = state,
        draft = draft,
        sending = sending,
        sendError = sendError,
        onDraftChanged = {
            draft = it.take(ConnectedMessagePolicy.MAX_BODY_LENGTH)
            pendingMessageId = null
            sendError = null
        },
        onSend = {
            if (!sending) {
                val messageId = pendingMessageId ?: UUID.randomUUID().toString().also { pendingMessageId = it }
                sending = true
                sendError = null
                scope.launch {
                    try {
                        when (val result = repository.sendConnectedMessage(target.tripId, messageId, draft)) {
                            is ConnectedMessageCommandResult.Success -> {
                                draft = ""
                                pendingMessageId = null
                            }
                            is ConnectedMessageCommandResult.InvalidInput -> sendError = result.userMessage
                            is ConnectedMessageCommandResult.ReadOnly -> sendError = result.userMessage
                            is ConnectedMessageCommandResult.Failure -> sendError = result.userMessage
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } finally {
                        sending = false
                    }
                }
            }
        },
        onRetry = {
            sendError = null
            planError = null
            retryVersion++
        },
        onBack = onBack,
        modifier = modifier,
        pickupDraft = pickupDraft,
        dropOffDraft = dropOffDraft,
        planBusy = planBusy,
        planError = planError,
        onPickupChanged = {
            pickupDraft = it.take(ConnectedJourneyPlanPolicy.MAX_DETAIL_LENGTH)
            planError = null
        },
        onDropOffChanged = {
            dropOffDraft = it.take(ConnectedJourneyPlanPolicy.MAX_DETAIL_LENGTH)
            planError = null
        },
        onSavePlan = {
            if (!planBusy) {
                planBusy = true
                planError = null
                scope.launch {
                    try {
                        handlePlanResult(repository.proposeConnectedJourneyPlan(
                            target.tripId, pickupDraft, dropOffDraft,
                        ))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } finally {
                        planBusy = false
                    }
                }
            }
        },
        onAgreePlan = { revision ->
            if (!planBusy) {
                planBusy = true
                planError = null
                scope.launch {
                    try {
                        handlePlanResult(repository.agreeConnectedJourneyPlan(target.tripId, revision))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } finally {
                        planBusy = false
                    }
                }
            }
        },
    )
}

@Composable
internal fun ConnectedConversationScreen(
    accountId: String,
    otherDisplayName: String?,
    state: ConnectedConversationState,
    draft: String,
    sending: Boolean,
    sendError: String?,
    onDraftChanged: (String) -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    pickupDraft: String = "",
    dropOffDraft: String = "",
    planBusy: Boolean = false,
    planError: String? = null,
    onPickupChanged: (String) -> Unit = {},
    onDropOffChanged: (String) -> Unit = {},
    onSavePlan: () -> Unit = {},
    onAgreePlan: (Int) -> Unit = {},
) {
    val conversation = when (state) {
        is ConnectedConversationState.Data -> state.conversation
        is ConnectedConversationState.Error -> state.previousConversation
        ConnectedConversationState.Loading -> null
    }
    val listenerFailed = state is ConnectedConversationState.Error
    val canSend = state is ConnectedConversationState.Data && conversation?.canSendMessages == true
    val messages = conversation?.messages.orEmpty()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val composerBringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(messages.map(ConnectedMessage::id)) {
        if (messages.isNotEmpty() && listState.layoutInfo.totalItemsCount > 0) {
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
    }

    Column(modifier.fillMaxSize().testTag("connected-conversation")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.connected_trip_details_back)) }
            Text(
                otherDisplayName?.takeIf(String::isNotBlank)?.let {
                    stringResource(R.string.connected_messages_with, it)
                } ?: stringResource(R.string.connected_messages_title),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        if (state == ConnectedConversationState.Loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().imePadding().testTag("coordinate-scroll-surface"),
            state = listState,
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (listenerFailed) item(key = "listener-error") {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text((state as ConnectedConversationState.Error).userMessage, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry, modifier = Modifier.testTag("messages-retry")) {
                        Text(stringResource(R.string.connected_messages_retry))
                    }
                }
            }
            conversation?.let { current ->
                item(key = "trip-context") {
                    Column(Modifier.padding(horizontal = 18.dp)) {
                        Text(
                            stringResource(
                                R.string.connected_route,
                                current.trip.originArea,
                                current.trip.destinationArea,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(formatConnectedJourneyDeparture(current.trip.departureEpochMillis))
                    }
                }
            }
            item(key = "privacy") {
                Text(
                    stringResource(R.string.connected_messages_privacy),
                    Modifier.padding(horizontal = 18.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            conversation?.let { current ->
                item(key = "journey-plan") {
                    JourneyPlanCard(
                        accountId = accountId,
                        conversation = current,
                        stateIsCurrent = state is ConnectedConversationState.Data,
                        pickupDraft = pickupDraft,
                        dropOffDraft = dropOffDraft,
                        busy = planBusy,
                        error = planError,
                        onPickupChanged = onPickupChanged,
                        onDropOffChanged = onDropOffChanged,
                        onSave = onSavePlan,
                        onAgree = onAgreePlan,
                    )
                }
            }
            if (conversation == null) {
                item(key = "unavailable") {
                    Text(stringResource(R.string.connected_messages_unavailable), Modifier.padding(18.dp))
                }
            } else {
                if (messages.isEmpty()) item(key = "empty-messages") {
                    Text(stringResource(R.string.connected_messages_empty), Modifier.padding(horizontal = 18.dp))
                }
                items(messages, key = ConnectedMessage::id) { message ->
                    Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                        MessageBubble(message, message.senderUid == accountId)
                    }
                }
                if (!canSend) item(key = "read-only") {
                    Text(
                        stringResource(conversation.readOnlyText()),
                        Modifier.fillMaxWidth().padding(18.dp).testTag("messages-read-only")
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else item(key = "composer") {
                    Column {
                        sendError?.let {
                            Text(
                                it,
                                Modifier.padding(horizontal = 18.dp)
                                    .semantics { liveRegion = LiveRegionMode.Polite },
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (sending) LinearProgressIndicator(Modifier.fillMaxWidth())
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = draft,
                                onValueChange = onDraftChanged,
                                modifier = Modifier.weight(1f)
                                    .bringIntoViewRequester(composerBringIntoViewRequester)
                                    .onFocusChanged { focus ->
                                        if (focus.isFocused) scope.launch {
                                            composerBringIntoViewRequester.bringIntoView()
                                        }
                                    }
                                    .testTag("message-compose"),
                                enabled = !sending,
                                singleLine = true,
                                label = { Text(stringResource(R.string.connected_messages_compose)) },
                                supportingText = {
                                    Text("${draft.length}/${ConnectedMessagePolicy.MAX_BODY_LENGTH}")
                                },
                            )
                            Button(
                                onClick = onSend,
                                enabled = !sending && draft.isNotBlank(),
                                modifier = Modifier.testTag("message-send"),
                            ) { Text(stringResource(R.string.connected_messages_send)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JourneyPlanCard(
    accountId: String,
    conversation: ConnectedConversation,
    stateIsCurrent: Boolean,
    pickupDraft: String,
    dropOffDraft: String,
    busy: Boolean,
    error: String?,
    onPickupChanged: (String) -> Unit,
    onDropOffChanged: (String) -> Unit,
    onSave: () -> Unit,
    onAgree: (Int) -> Unit,
) {
    val plan = conversation.plan
    val status = plan.status()
    val isDriver = conversation.trip.driverUid == accountId
    val canEdit = stateIsCurrent && conversation.canProposePlan
    val changed = plan == null || pickupDraft.trim() != plan.pickupDetails ||
        dropOffDraft.trim() != plan.dropOffDetails
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp).testTag("journey-plan"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.connected_plan_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(when (status) {
                    ConnectedJourneyPlanStatus.NOT_SET -> R.string.connected_plan_not_set
                    ConnectedJourneyPlanStatus.WAITING_FOR_RIDER -> R.string.connected_plan_waiting
                    ConnectedJourneyPlanStatus.AGREED -> R.string.connected_plan_agreed
                }),
                color = if (status == ConnectedJourneyPlanStatus.AGREED) {
                    MaterialTheme.colorScheme.primary
                } else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("journey-plan-status"),
            )
            Text(
                stringResource(R.string.connected_plan_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isDriver && canEdit) {
                OutlinedTextField(
                    value = pickupDraft,
                    onValueChange = onPickupChanged,
                    modifier = Modifier.fillMaxWidth().testTag("plan-pickup-input"),
                    label = { Text(stringResource(R.string.connected_plan_pickup)) },
                    supportingText = { Text("${pickupDraft.length}/${ConnectedJourneyPlanPolicy.MAX_DETAIL_LENGTH}") },
                    enabled = !busy,
                    singleLine = true,
                )
                OutlinedTextField(
                    value = dropOffDraft,
                    onValueChange = onDropOffChanged,
                    modifier = Modifier.fillMaxWidth().testTag("plan-dropoff-input"),
                    label = { Text(stringResource(R.string.connected_plan_dropoff)) },
                    supportingText = { Text("${dropOffDraft.length}/${ConnectedJourneyPlanPolicy.MAX_DETAIL_LENGTH}") },
                    enabled = !busy,
                    singleLine = true,
                )
                Button(
                    onClick = onSave,
                    enabled = !busy && changed && pickupDraft.isNotBlank() && dropOffDraft.isNotBlank(),
                    modifier = Modifier.testTag("plan-save"),
                ) {
                    Text(stringResource(if (plan == null) R.string.connected_plan_propose else R.string.connected_plan_update))
                }
            } else if (plan != null) {
                PlanDetail(R.string.connected_plan_pickup, plan.pickupDetails, "plan-pickup")
                PlanDetail(R.string.connected_plan_dropoff, plan.dropOffDetails, "plan-dropoff")
                if (!isDriver && stateIsCurrent && conversation.canAgreePlan) {
                    Button(
                        onClick = { onAgree(plan.revision) },
                        enabled = !busy,
                        modifier = Modifier.testTag("plan-agree"),
                    ) { Text(stringResource(R.string.connected_plan_agree_action)) }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

@Composable
private fun PlanDetail(label: Int, value: String, tag: String) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
        Text(value, modifier = Modifier.testTag(tag))
    }
}

@Composable
private fun MessageBubble(message: ConnectedMessage, own: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (own) Arrangement.End else Arrangement.Start) {
        Card(
            Modifier.widthIn(max = 300.dp).testTag(if (own) "message-own:${message.id}" else "message-other:${message.id}"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (own) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(message.body)
                Text(
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.sentAtEpochMillis)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun ConnectedConversation.readOnlyText(): Int = when (readOnlyReason) {
    ConnectedConversationReadOnlyReason.CANCELLED_BY_RIDER -> R.string.connected_messages_read_only_rider
    ConnectedConversationReadOnlyReason.CANCELLED_BY_DRIVER -> R.string.connected_messages_read_only_driver
    ConnectedConversationReadOnlyReason.COMPLETED -> R.string.connected_messages_read_only_completed
    ConnectedConversationReadOnlyReason.UNAVAILABLE, null -> R.string.connected_messages_read_only_unavailable
}
