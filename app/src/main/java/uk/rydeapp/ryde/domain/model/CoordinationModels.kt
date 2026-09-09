package uk.rydeapp.ryde.domain.model

@JvmInline
value class ConversationId(val value: String)

enum class JourneyParticipantRole { DRIVER, RIDER }

data class JourneyParticipant(
    val id: String,
    val firstName: String,
    val role: JourneyParticipantRole,
)

data class CoordinationMessage(
    val id: String,
    val senderId: String,
    val body: String,
    val sentAtEpochMillis: Long,
    val displayTime: String,
    val isRead: Boolean,
)

data class ConversationThread(
    val id: ConversationId,
    val confirmedTripId: String,
    val journeyLabel: String,
    val participants: List<JourneyParticipant>,
    val messages: List<CoordinationMessage>,
    val canSendMessages: Boolean,
    val messagingUnavailableReason: MessagingUnavailableReason? = null,
)

enum class JourneyLifecycleStatus {
    CONFIRMED,
    DRIVER_EN_ROUTE,
    READY_AT_PICKUP,
    JOURNEY_UNDERWAY,
    COMPLETED,
    CANCELLED,
}

object JourneyLifecyclePolicy {
    private val validTransitions = mapOf(
        JourneyLifecycleStatus.CONFIRMED to setOf(
            JourneyLifecycleStatus.DRIVER_EN_ROUTE,
            JourneyLifecycleStatus.CANCELLED,
        ),
        JourneyLifecycleStatus.DRIVER_EN_ROUTE to setOf(
            JourneyLifecycleStatus.READY_AT_PICKUP,
            JourneyLifecycleStatus.CANCELLED,
        ),
        JourneyLifecycleStatus.READY_AT_PICKUP to setOf(
            JourneyLifecycleStatus.JOURNEY_UNDERWAY,
            JourneyLifecycleStatus.CANCELLED,
        ),
        JourneyLifecycleStatus.JOURNEY_UNDERWAY to setOf(
            JourneyLifecycleStatus.COMPLETED,
            JourneyLifecycleStatus.CANCELLED,
        ),
    )

    fun canTransition(from: JourneyLifecycleStatus, to: JourneyLifecycleStatus): Boolean =
        to in (validTransitions[from] ?: emptySet())

    fun nextProgressStatus(current: JourneyLifecycleStatus): JourneyLifecycleStatus? = when (current) {
        JourneyLifecycleStatus.CONFIRMED -> JourneyLifecycleStatus.DRIVER_EN_ROUTE
        JourneyLifecycleStatus.DRIVER_EN_ROUTE -> JourneyLifecycleStatus.READY_AT_PICKUP
        JourneyLifecycleStatus.READY_AT_PICKUP -> JourneyLifecycleStatus.JOURNEY_UNDERWAY
        JourneyLifecycleStatus.JOURNEY_UNDERWAY -> JourneyLifecycleStatus.COMPLETED
        JourneyLifecycleStatus.COMPLETED,
        JourneyLifecycleStatus.CANCELLED,
        -> null
    }
}

enum class MessagingUnavailableReason {
    NO_CONFIRMED_TRIP,
    PARTICIPANT_BLOCKED_OR_REPORTED,
}

enum class MessageRejectionReason {
    CONVERSATION_NOT_FOUND,
    PARTICIPANT_BLOCKED_OR_REPORTED,
    JOURNEY_CANCELLED,
    JOURNEY_COMPLETED,
    EMPTY_MESSAGE,
    PRIVATE_OR_LIVE_LOCATION,
}

object CoordinationMessagePolicy {
    private val privateAddress = Regex(
        """\b\d{1,4}\s+[a-z]+(?:\s+[a-z]+){0,3}\s+(street|road|avenue|lane|close|drive|court)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val postcode = Regex("""\b[A-Z]{1,2}\d[A-Z\d]?\s*\d[A-Z]{2}\b""", RegexOption.IGNORE_CASE)
    private val unsafePhrases = listOf(
        "home address",
        "my address",
        "house number",
        "exact location",
        "live location",
        "real-time location",
        "realtime location",
        "location pin",
    )

    fun rejectionReason(body: String): MessageRejectionReason? {
        val normalized = body.trim()
        if (normalized.isEmpty()) return MessageRejectionReason.EMPTY_MESSAGE
        if (
            privateAddress.containsMatchIn(normalized) ||
            postcode.containsMatchIn(normalized) ||
            unsafePhrases.any { it in normalized.lowercase() }
        ) {
            return MessageRejectionReason.PRIVATE_OR_LIVE_LOCATION
        }
        return null
    }
}

enum class CoordinationActivityType {
    REQUEST_ACCEPTED,
    NEW_MESSAGE,
    DRIVER_EN_ROUTE,
    READY_AT_PICKUP,
    JOURNEY_COMPLETED,
}

data class CoordinationActivityItem(
    val id: String,
    val confirmedTripId: String,
    val conversationId: ConversationId? = null,
    val type: CoordinationActivityType,
    val title: String,
    val body: String,
    val displayTime: String,
    val isRead: Boolean,
)

data class CoordinationUnreadCounts(
    val messages: Int,
    val activity: Int,
) {
    val total: Int get() = messages + activity
}

sealed interface GetConversationResult {
    data class Available(val conversation: ConversationThread) : GetConversationResult
    data class Unavailable(val reason: MessagingUnavailableReason) : GetConversationResult
}

sealed interface SendMessageResult {
    data class Sent(
        val message: CoordinationMessage,
        val conversation: ConversationThread,
    ) : SendMessageResult

    data class Rejected(val reason: MessageRejectionReason) : SendMessageResult
}

sealed interface JourneyStatusUpdateResult {
    data class Updated(val trip: ConfirmedSharedTrip) : JourneyStatusUpdateResult
    data class Rejected(
        val currentStatus: JourneyLifecycleStatus?,
        val requestedStatus: JourneyLifecycleStatus,
    ) : JourneyStatusUpdateResult
}

sealed interface CompleteJourneyResult {
    data class Completed(val journey: CompletedJourneyHistory) : CompleteJourneyResult
    data class AlreadyCompleted(val journey: CompletedJourneyHistory) : CompleteJourneyResult
    data class Rejected(val currentStatus: JourneyLifecycleStatus?) : CompleteJourneyResult
}
