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
import uk.rydeapp.ryde.data.connected.ConnectedRydeRepository
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
            retryVersion++
        },
        onBack = onBack,
        modifier = modifier,
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
    LaunchedEffect(messages.map(ConnectedMessage::id)) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(modifier.fillMaxSize().imePadding().testTag("connected-conversation")) {
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
        if (listenerFailed) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp).semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text((state as ConnectedConversationState.Error).userMessage, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry, modifier = Modifier.testTag("messages-retry")) {
                    Text(stringResource(R.string.connected_messages_retry))
                }
            }
        }
        conversation?.let {
            Text(
                stringResource(R.string.connected_route, it.trip.originArea, it.trip.destinationArea),
                Modifier.padding(horizontal = 18.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(formatConnectedJourneyDeparture(it.trip.departureEpochMillis), Modifier.padding(horizontal = 18.dp))
        }
        Text(
            stringResource(R.string.connected_messages_privacy),
            Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        if (conversation == null) {
            Text(stringResource(R.string.connected_messages_unavailable), Modifier.padding(18.dp))
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().testTag("messages-list"),
                state = listState,
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (messages.isEmpty()) item { Text(stringResource(R.string.connected_messages_empty)) }
                items(messages, key = ConnectedMessage::id) { message ->
                    MessageBubble(message, message.senderUid == accountId)
                }
            }
        }
        if (conversation != null && !canSend) {
            Text(
                stringResource(conversation.readOnlyText()),
                Modifier.fillMaxWidth().padding(18.dp).testTag("messages-read-only")
                    .semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (conversation != null) {
            sendError?.let {
                Text(it, Modifier.padding(horizontal = 18.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    color = MaterialTheme.colorScheme.error)
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
                    modifier = Modifier.weight(1f).testTag("message-compose"),
                    enabled = !sending,
                    singleLine = true,
                    label = { Text(stringResource(R.string.connected_messages_compose)) },
                    supportingText = { Text("${draft.length}/${ConnectedMessagePolicy.MAX_BODY_LENGTH}") },
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
