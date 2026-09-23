package uk.rydeapp.ryde.data.connected

import com.google.firebase.Timestamp

data class ConnectedMessage(
    val id: String,
    val senderUid: String,
    val body: String,
    val sentAtEpochMillis: Long,
)

data class ConnectedConversationSnapshot(
    val trip: ConnectedConfirmedTrip,
    val journey: ConnectedJourney?,
    val messages: List<ConnectedMessage>,
)

enum class ConnectedConversationReadOnlyReason {
    CANCELLED_BY_RIDER,
    CANCELLED_BY_DRIVER,
    COMPLETED,
    UNAVAILABLE,
}

data class ConnectedConversation(
    val trip: ConnectedConfirmedTrip,
    val journey: ConnectedJourney?,
    val messages: List<ConnectedMessage>,
    val canSendMessages: Boolean,
    val readOnlyReason: ConnectedConversationReadOnlyReason?,
)

sealed interface ConnectedConversationState {
    data object Loading : ConnectedConversationState
    data class Data(val conversation: ConnectedConversation) : ConnectedConversationState
    data class Error(
        val userMessage: String,
        val previousConversation: ConnectedConversation? = null,
    ) : ConnectedConversationState
}

sealed interface ConnectedMessageCommandResult {
    data class Success(val messageId: String) : ConnectedMessageCommandResult
    data class InvalidInput(val userMessage: String) : ConnectedMessageCommandResult
    data class ReadOnly(val userMessage: String) : ConnectedMessageCommandResult
    data class Failure(val userMessage: String) : ConnectedMessageCommandResult
}

sealed interface ConnectedMessageValidationResult {
    data class Valid(val body: String) : ConnectedMessageValidationResult
    data class Invalid(val userMessage: String) : ConnectedMessageValidationResult
}

object ConnectedMessagePolicy {
    const val MAX_BODY_LENGTH = 500
    private val validMessageId = Regex("^[A-Za-z0-9_-]{1,128}$")

    fun validate(body: String): ConnectedMessageValidationResult {
        val normalized = body.trim()
        return when {
            normalized.isEmpty() -> ConnectedMessageValidationResult.Invalid("Write a message first.")
            normalized.length > MAX_BODY_LENGTH -> ConnectedMessageValidationResult.Invalid(
                "Keep messages to $MAX_BODY_LENGTH characters or fewer.",
            )
            normalized.any(Char::isISOControl) -> ConnectedMessageValidationResult.Invalid(
                "Messages cannot contain control characters.",
            )
            else -> ConnectedMessageValidationResult.Valid(normalized)
        }
    }

    fun isStructurallyValidBody(value: String): Boolean =
        value.length <= MAX_BODY_LENGTH && value.any { !it.isWhitespace() } && value.none(Char::isISOControl)

    fun validMessageId(value: String): Boolean = validMessageId.matches(value)
}

object FirestoreConnectedMessageMapper {
    private val fields = setOf("senderUid", "body", "sentAt")

    fun messageData(senderUid: String, body: String): Map<String, Any> = mapOf(
        "senderUid" to senderUid,
        "body" to body,
        "sentAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
    )

    fun message(id: String, data: Map<String, Any?>): ConnectedMessage? {
        if (data.keys != fields || !ConnectedMessagePolicy.validMessageId(id)) return null
        val senderUid = (data["senderUid"] as? String)?.takeIf(String::isNotBlank) ?: return null
        val body = data["body"] as? String ?: return null
        if (!ConnectedMessagePolicy.isStructurallyValidBody(body)) return null
        return ConnectedMessage(
            id = id,
            senderUid = senderUid,
            body = body,
            sentAtEpochMillis = (data["sentAt"] as? Timestamp)?.toDate()?.time ?: return null,
        )
    }
}

internal class ConnectedCoordinationUnavailableException : Exception()

internal fun orderedLatestConnectedMessages(messages: List<ConnectedMessage>): List<ConnectedMessage> =
    messages.sortedWith(compareBy(ConnectedMessage::sentAtEpochMillis, ConnectedMessage::id)).takeLast(100)
