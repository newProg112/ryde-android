package uk.rydeapp.ryde.data.connected

import com.google.firebase.Timestamp
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import uk.rydeapp.ryde.domain.model.SavedPlace
import uk.rydeapp.ryde.domain.model.SavedPlacePolicy

data class ConnectedUserProfile(
    val uid: String,
    val displayName: String,
)

data class ConnectedProfile(
    val user: ConnectedUserProfile,
    val savedPlaces: List<SavedPlace>,
)

data class ValidatedAccountInput(
    val email: String,
    val password: String,
    val displayName: String? = null,
)

sealed interface ValidationResult<out T> {
    data class Valid<T>(val value: T) : ValidationResult<T>
    data class Invalid(val userMessage: String) : ValidationResult<Nothing>
}

object ConnectedAccountValidator {
    private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun registration(email: String, password: String, displayName: String): ValidationResult<ValidatedAccountInput> {
        val signIn = credentials(email, password)
        if (signIn is ValidationResult.Invalid) return signIn
        val name = displayName.trim()
        if (name.isEmpty() || name.length > 60 || name.any(Char::isISOControl)) {
            return ValidationResult.Invalid("Enter a display name of up to 60 characters.")
        }
        val credentials = (signIn as ValidationResult.Valid).value
        return ValidationResult.Valid(credentials.copy(displayName = name))
    }

    fun credentials(email: String, password: String): ValidationResult<ValidatedAccountInput> {
        val normalizedEmail = email.trim().lowercase()
        if (normalizedEmail.length > 254 || !emailPattern.matches(normalizedEmail)) {
            return ValidationResult.Invalid("Enter a valid email address.")
        }
        if (password.length !in 8..128) {
            return ValidationResult.Invalid("Use a password between 8 and 128 characters.")
        }
        return ValidationResult.Valid(ValidatedAccountInput(normalizedEmail, password))
    }

    fun profile(displayName: String, homeArea: String, workArea: String): ValidationResult<ConnectedProfileDraft> {
        val name = displayName.trim()
        if (name.isEmpty() || name.length > 60 || name.any(Char::isISOControl)) {
            return ValidationResult.Invalid("Enter a display name of up to 60 characters.")
        }
        val home = homeArea.trim()
        val work = workArea.trim()
        if (!SavedPlacePolicy.isBroadDisplayArea(home) || !SavedPlacePolicy.isBroadDisplayArea(work)) {
            return ValidationResult.Invalid("Use broad Home and Work areas only, without street numbers or commas.")
        }
        return ValidationResult.Valid(ConnectedProfileDraft(name, home, work))
    }
}

data class ConnectedProfileDraft(
    val displayName: String,
    val homeArea: String,
    val workArea: String,
)

enum class ConnectedRequestStatus { PENDING, ACCEPTED, DECLINED }

data class ConnectedJourney(
    val id: String,
    val driverUid: String,
    val originArea: String,
    val destinationArea: String,
    val departureEpochMillis: Long,
    val seatCapacity: Int,
    val seatsRemaining: Int,
)

data class ConnectedSeatRequest(
    val id: String,
    val journeyId: String,
    val driverUid: String,
    val riderUid: String,
    val status: ConnectedRequestStatus,
)

data class ConnectedJourneySnapshot(
    val journeys: List<ConnectedJourney> = emptyList(),
    val requests: List<ConnectedSeatRequest> = emptyList(),
)

data class ConnectedJourneyDraft(
    val originArea: String,
    val destinationArea: String,
    val departureEpochMillis: Long,
    val seats: Int,
)

sealed interface ConnectedJourneyCommandResult {
    data object Success : ConnectedJourneyCommandResult
    data class InvalidInput(val userMessage: String) : ConnectedJourneyCommandResult
    data class Failure(val userMessage: String) : ConnectedJourneyCommandResult
}

object ConnectedJourneyValidator {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun offer(
        originArea: String,
        destinationArea: String,
        departure: String,
        seatsText: String,
        nowEpochMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): ValidationResult<ConnectedJourneyDraft> {
        val origin = originArea.trim()
        val destination = destinationArea.trim()
        if (!isBroadArea(origin) || !isBroadArea(destination)) {
            return ValidationResult.Invalid("Use broad town or district names only, without digits or commas.")
        }
        if (origin.equals(destination, ignoreCase = true)) {
            return ValidationResult.Invalid("Origin and destination must be different broad areas.")
        }
        val seats = seatsText.toIntOrNull()
        if (seats !in 1..8) return ValidationResult.Invalid("Enter between 1 and 8 seats.")
        val departureMillis = try {
            LocalDateTime.parse(departure.trim(), formatter).atZone(zoneId).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            return ValidationResult.Invalid("Use departure format YYYY-MM-DD HH:mm.")
        }
        if (departureMillis <= nowEpochMillis) {
            return ValidationResult.Invalid("Departure must be in the future.")
        }
        return ValidationResult.Valid(ConnectedJourneyDraft(origin, destination, departureMillis, seats!!))
    }

    fun isBroadArea(value: String): Boolean = value.isNotBlank() && value.length <= 60 &&
        value.none { it.isDigit() || it == ',' || it.isISOControl() }
}

object FirestoreJourneyMapper {
    private val journeyFields = setOf("driverUid", "originArea", "destinationArea", "departureAt", "seatCapacity", "seatsRemaining", "status")
    private val requestFields = setOf("journeyId", "driverUid", "riderUid", "status")

    fun journeyData(driverUid: String, draft: ConnectedJourneyDraft): Map<String, Any> = mapOf(
        "driverUid" to driverUid,
        "originArea" to draft.originArea,
        "destinationArea" to draft.destinationArea,
        "departureAt" to Timestamp(draft.departureEpochMillis / 1000, ((draft.departureEpochMillis % 1000) * 1_000_000).toInt()),
        "seatCapacity" to draft.seats,
        "seatsRemaining" to draft.seats,
        "status" to "OPEN",
    )

    fun requestData(journey: ConnectedJourney, riderUid: String): Map<String, Any> = mapOf(
        "journeyId" to journey.id,
        "driverUid" to journey.driverUid,
        "riderUid" to riderUid,
        "status" to ConnectedRequestStatus.PENDING.name,
    )

    fun journey(id: String, data: Map<String, Any?>): ConnectedJourney? {
        if (data.keys != journeyFields || data["status"] != "OPEN") return null
        val capacity = (data["seatCapacity"] as? Number)?.toInt() ?: return null
        val remaining = (data["seatsRemaining"] as? Number)?.toInt() ?: return null
        return ConnectedJourney(
            id, data["driverUid"] as? String ?: return null,
            data["originArea"] as? String ?: return null,
            data["destinationArea"] as? String ?: return null,
            (data["departureAt"] as? Timestamp)?.toDate()?.time ?: return null,
            capacity, remaining,
        ).takeIf { isValidJourney(it) }
    }

    fun request(id: String, data: Map<String, Any?>): ConnectedSeatRequest? {
        if (data.keys != requestFields) return null
        val status = runCatching { ConnectedRequestStatus.valueOf(data["status"] as? String ?: return null) }.getOrNull() ?: return null
        return ConnectedSeatRequest(
            id, data["journeyId"] as? String ?: return null,
            data["driverUid"] as? String ?: return null,
            data["riderUid"] as? String ?: return null, status,
        ).takeIf { it.id == "${it.journeyId}_${it.riderUid}" && it.driverUid != it.riderUid }
    }

    private fun isValidJourney(journey: ConnectedJourney): Boolean =
        journey.driverUid.isNotBlank() && ConnectedJourneyValidator.isBroadArea(journey.originArea) &&
            ConnectedJourneyValidator.isBroadArea(journey.destinationArea) &&
            !journey.originArea.equals(journey.destinationArea, true) &&
            journey.seatCapacity in 1..8 && journey.seatsRemaining in 0..journey.seatCapacity
}

object FirestoreProfileMapper {
    const val UID = "uid"
    const val DISPLAY_NAME = "displayName"
    const val LABEL = "label"
    const val AREA = "area"

    fun userData(profile: ConnectedUserProfile): Map<String, Any> = mapOf(
        UID to profile.uid,
        DISPLAY_NAME to profile.displayName,
    )

    fun placeData(uid: String, place: SavedPlace): Map<String, Any> = mapOf(
        UID to uid,
        LABEL to place.label,
        AREA to place.area,
    )

    fun user(uid: String, data: Map<String, Any?>): ConnectedUserProfile? {
        if (data.keys != setOf(UID, DISPLAY_NAME)) return null
        val storedUid = data[UID] as? String ?: return null
        val displayName = data[DISPLAY_NAME] as? String ?: return null
        return ConnectedUserProfile(storedUid, displayName).takeIf {
            storedUid == uid && displayName.isNotBlank() && displayName.length <= 60
        }
    }

    fun place(id: String, uid: String, data: Map<String, Any?>): SavedPlace? {
        if (data.keys != setOf(UID, LABEL, AREA)) return null
        val expectedLabel = when (id) {
            "home" -> "Home"
            "work" -> "Work"
            else -> return null
        }
        val area = data[AREA] as? String ?: return null
        return SavedPlace(expectedLabel, area).takeIf {
            data[UID] == uid && data[LABEL] == expectedLabel && SavedPlacePolicy.isBroadDisplayArea(area)
        }
    }
}
