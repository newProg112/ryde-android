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
    val plan: ConnectedJourneyPlan? = null,
)

data class ConnectedJourneyPlan(
    val pickupDetails: String,
    val dropOffDetails: String,
    val revision: Int,
    val proposedAtEpochMillis: Long,
    val acceptedRevision: Int,
    val acceptedAtEpochMillis: Long?,
) {
    val isAgreed: Boolean
        get() = acceptedRevision == revision && acceptedAtEpochMillis != null
}

enum class ConnectedJourneyPlanStatus { NOT_SET, WAITING_FOR_RIDER, AGREED }

internal fun ConnectedJourneyPlan?.status(): ConnectedJourneyPlanStatus = when {
    this == null -> ConnectedJourneyPlanStatus.NOT_SET
    isAgreed -> ConnectedJourneyPlanStatus.AGREED
    else -> ConnectedJourneyPlanStatus.WAITING_FOR_RIDER
}

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
    val plan: ConnectedJourneyPlan? = null,
    val canProposePlan: Boolean = false,
    val canAgreePlan: Boolean = false,
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

sealed interface ConnectedJourneyPlanCommandResult {
    data object Success : ConnectedJourneyPlanCommandResult
    data class InvalidInput(val userMessage: String) : ConnectedJourneyPlanCommandResult
    data class ReadOnly(val userMessage: String) : ConnectedJourneyPlanCommandResult
    data class Failure(val userMessage: String) : ConnectedJourneyPlanCommandResult
}

data class ValidatedConnectedJourneyPlan(
    val pickupDetails: String,
    val dropOffDetails: String,
)

sealed interface ConnectedJourneyPlanValidationResult {
    data class Valid(val plan: ValidatedConnectedJourneyPlan) : ConnectedJourneyPlanValidationResult
    data class Invalid(val userMessage: String) : ConnectedJourneyPlanValidationResult
}

object ConnectedJourneyPlanPolicy {
    const val MAX_DETAIL_LENGTH = 160
    private val repeatedWhitespace = Regex("\\s+")

    fun validate(pickupDetails: String, dropOffDetails: String): ConnectedJourneyPlanValidationResult {
        val pickup = normalize(pickupDetails)
        val dropOff = normalize(dropOffDetails)
        return when {
            pickupDetails.any(Char::isISOControl) || dropOffDetails.any(Char::isISOControl) ->
                ConnectedJourneyPlanValidationResult.Invalid("Pickup and drop-off details cannot contain control characters.")
            pickup.isEmpty() || dropOff.isEmpty() ->
                ConnectedJourneyPlanValidationResult.Invalid("Add both pickup and drop-off details.")
            pickup.length > MAX_DETAIL_LENGTH || dropOff.length > MAX_DETAIL_LENGTH ->
                ConnectedJourneyPlanValidationResult.Invalid(
                    "Keep each pickup and drop-off detail to $MAX_DETAIL_LENGTH characters or fewer.",
                )
            else -> ConnectedJourneyPlanValidationResult.Valid(ValidatedConnectedJourneyPlan(pickup, dropOff))
        }
    }

    fun structurallyValid(value: String): Boolean = value.length in 1..MAX_DETAIL_LENGTH &&
        value.any { !it.isWhitespace() } && value.none(Char::isISOControl) && value == normalize(value)

    private fun normalize(value: String): String = value.trim().replace(repeatedWhitespace, " ")
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

object FirestoreConnectedJourneyPlanMapper {
    private val fields = setOf(
        "pickupDetails", "dropOffDetails", "revision", "proposedAt", "acceptedRevision", "acceptedAt",
    )

    fun proposalData(plan: ValidatedConnectedJourneyPlan, revision: Int): Map<String, Any?> = mapOf(
        "pickupDetails" to plan.pickupDetails,
        "dropOffDetails" to plan.dropOffDetails,
        "revision" to revision,
        "proposedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
        "acceptedRevision" to 0,
        "acceptedAt" to null,
    )

    fun plan(data: Map<String, Any?>): ConnectedJourneyPlan? {
        if (data.keys != fields) return null
        val pickup = (data["pickupDetails"] as? String)
            ?.takeIf(ConnectedJourneyPlanPolicy::structurallyValid) ?: return null
        val dropOff = (data["dropOffDetails"] as? String)
            ?.takeIf(ConnectedJourneyPlanPolicy::structurallyValid) ?: return null
        val revision = data["revision"].positiveInt() ?: return null
        val acceptedRevision = data["acceptedRevision"].nonNegativeInt() ?: return null
        val proposedAt = (data["proposedAt"] as? Timestamp)?.toDate()?.time ?: return null
        val acceptedAt = when (val value = data["acceptedAt"]) {
            null -> null
            is Timestamp -> value.toDate().time
            else -> return null
        }
        if ((acceptedRevision == 0) != (acceptedAt == null)) return null
        if (acceptedRevision != 0 && acceptedRevision != revision) return null
        return ConnectedJourneyPlan(pickup, dropOff, revision, proposedAt, acceptedRevision, acceptedAt)
    }

    private fun Any?.positiveInt(): Int? = exactInt()?.takeIf { it > 0 }
    private fun Any?.nonNegativeInt(): Int? = exactInt()?.takeIf { it >= 0 }
    private fun Any?.exactInt(): Int? {
        val number = when (this) {
            is Byte, is Short, is Int, is Long -> this as Number
            else -> return null
        }
        val long = number.toLong()
        return long.toInt().takeIf { it.toLong() == long }
    }
}

internal class ConnectedCoordinationUnavailableException : Exception()

internal fun orderedLatestConnectedMessages(messages: List<ConnectedMessage>): List<ConnectedMessage> =
    messages.sortedWith(compareBy(ConnectedMessage::sentAtEpochMillis, ConnectedMessage::id)).takeLast(100)
