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

enum class ConnectedRequestStatus { PENDING, ACCEPTED, DECLINED, CANCELLED, CANCELLED_AFTER_ACCEPTANCE }

enum class ConnectedTripStatus { CONFIRMED, CANCELLED_BY_RIDER }

enum class ConnectedJourneyStatus { OPEN, CANCELLED }

data class ConnectedJourney(
    val id: String,
    val driverUid: String,
    val originArea: String,
    val destinationArea: String,
    val departureEpochMillis: Long,
    val seatCapacity: Int,
    val seatsRemaining: Int,
    val status: ConnectedJourneyStatus = ConnectedJourneyStatus.OPEN,
    val cancelledAtEpochMillis: Long? = null,
)

data class ConnectedSeatRequest(
    val id: String,
    val journeyId: String,
    val driverUid: String,
    val riderUid: String,
    val status: ConnectedRequestStatus,
    /** Immutable for one request cycle; null only for legacy records. */
    val riderDisplayName: String? = null,
)

data class ConnectedConfirmedTrip(
    val id: String,
    val journeyId: String,
    val acceptedRequestId: String,
    val driverUid: String,
    val riderUid: String,
    val originArea: String,
    val destinationArea: String,
    val departureEpochMillis: Long,
    val status: ConnectedTripStatus,
    val cancelledAtEpochMillis: Long? = null,
    /** Immutable acceptance-time snapshot; null only for legacy records. */
    val driverDisplayName: String? = null,
)

data class ConnectedJourneyAcceptanceGuard(
    val driverUid: String,
    val acceptanceCount: Int,
    val lastAcceptedRequestId: String?,
    val lastCancelledRequestId: String? = null,
)

data class ConnectedJourneySnapshot(
    val journeys: List<ConnectedJourney> = emptyList(),
    val requests: List<ConnectedSeatRequest> = emptyList(),
    val confirmedTrips: List<ConnectedConfirmedTrip> = emptyList(),
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
    private val legacyRequestFields = setOf("journeyId", "driverUid", "riderUid", "status")
    private val requestFields = legacyRequestFields + "riderDisplayName"
    private val acceptanceGuardFields = setOf("driverUid", "acceptanceCount", "lastAcceptedRequestId")
    private val legacyConfirmedTripFields = setOf(
        "journeyId",
        "acceptedRequestId",
        "driverUid",
        "riderUid",
        "originArea",
        "destinationArea",
        "departureAt",
        "status",
    )
    private val confirmedTripFields = legacyConfirmedTripFields + "driverDisplayName"

    fun journeyData(driverUid: String, draft: ConnectedJourneyDraft): Map<String, Any> = mapOf(
        "driverUid" to driverUid,
        "originArea" to draft.originArea,
        "destinationArea" to draft.destinationArea,
        "departureAt" to Timestamp(draft.departureEpochMillis / 1000, ((draft.departureEpochMillis % 1000) * 1_000_000).toInt()),
        "seatCapacity" to draft.seats,
        "seatsRemaining" to draft.seats,
        "status" to "OPEN",
    )

    fun requestData(
        journey: ConnectedJourney,
        riderUid: String,
        riderDisplayName: String,
    ): Map<String, Any> = mapOf(
        "journeyId" to journey.id,
        "driverUid" to journey.driverUid,
        "riderUid" to riderUid,
        "status" to ConnectedRequestStatus.PENDING.name,
        "riderDisplayName" to riderDisplayName,
    )

    fun initialAcceptanceGuardData(driverUid: String): Map<String, Any?> = mapOf(
        "driverUid" to driverUid,
        "acceptanceCount" to 0,
        "lastAcceptedRequestId" to null,
    )

    fun confirmedTripData(
        journey: ConnectedJourney,
        request: ConnectedSeatRequest,
        driverDisplayName: String,
    ): Map<String, Any> = mapOf(
        "journeyId" to journey.id,
        "acceptedRequestId" to request.id,
        "driverUid" to request.driverUid,
        "riderUid" to request.riderUid,
        "originArea" to journey.originArea,
        "destinationArea" to journey.destinationArea,
        "departureAt" to journey.departureEpochMillis.toTimestamp(),
        "status" to ConnectedTripStatus.CONFIRMED.name,
        "driverDisplayName" to driverDisplayName,
    )

    fun journey(id: String, data: Map<String, Any?>): ConnectedJourney? {
        val status = runCatching { ConnectedJourneyStatus.valueOf(data["status"] as? String ?: return null) }.getOrNull() ?: return null
        val expectedFields = if (status == ConnectedJourneyStatus.CANCELLED) journeyFields + "cancelledAt" else journeyFields
        if (data.keys != expectedFields) return null
        val cancelledAt = if (status == ConnectedJourneyStatus.CANCELLED) {
            (data["cancelledAt"] as? Timestamp)?.toDate()?.time ?: return null
        } else null
        val capacity = (data["seatCapacity"] as? Number)?.toInt() ?: return null
        val remaining = (data["seatsRemaining"] as? Number)?.toInt() ?: return null
        return ConnectedJourney(
            id, data["driverUid"] as? String ?: return null,
            data["originArea"] as? String ?: return null,
            data["destinationArea"] as? String ?: return null,
            (data["departureAt"] as? Timestamp)?.toDate()?.time ?: return null,
            capacity, remaining, status, cancelledAt,
        ).takeIf { isValidJourney(it) }
    }

    fun request(id: String, data: Map<String, Any?>): ConnectedSeatRequest? {
        if (data.keys != legacyRequestFields && data.keys != requestFields) return null
        val status = runCatching { ConnectedRequestStatus.valueOf(data["status"] as? String ?: return null) }.getOrNull() ?: return null
        val riderDisplayName = if ("riderDisplayName" in data) {
            (data["riderDisplayName"] as? String)?.takeIf(::isSafeDisplayName) ?: return null
        } else null
        return ConnectedSeatRequest(
            id, data["journeyId"] as? String ?: return null,
            data["driverUid"] as? String ?: return null,
            data["riderUid"] as? String ?: return null, status, riderDisplayName,
        ).takeIf { it.id == "${it.journeyId}_${it.riderUid}" && it.driverUid != it.riderUid }
    }

    fun acceptanceGuard(data: Map<String, Any?>): ConnectedJourneyAcceptanceGuard? {
        if (data.keys != acceptanceGuardFields && data.keys != acceptanceGuardFields + "lastCancelledRequestId") return null
        val count = (data["acceptanceCount"] as? Number)?.toInt() ?: return null
        if (data["lastAcceptedRequestId"] != null && data["lastAcceptedRequestId"] !is String) return null
        if ("lastCancelledRequestId" in data && (data["lastCancelledRequestId"] !is String || (data["lastCancelledRequestId"] as String).isBlank())) return null
        val lastRequestId = data["lastAcceptedRequestId"] as? String
        return ConnectedJourneyAcceptanceGuard(
            driverUid = data["driverUid"] as? String ?: return null,
            acceptanceCount = count,
            lastAcceptedRequestId = lastRequestId,
            lastCancelledRequestId = data["lastCancelledRequestId"] as? String,
        ).takeIf {
            it.driverUid.isNotBlank() && it.acceptanceCount in 0..8 &&
                ((it.acceptanceCount == 0 && it.lastAcceptedRequestId == null) ||
                    !it.lastAcceptedRequestId.isNullOrBlank())
        }
    }

    fun confirmedTrip(id: String, data: Map<String, Any?>): ConnectedConfirmedTrip? {
        val status = runCatching {
            ConnectedTripStatus.valueOf(data["status"] as? String ?: return null)
        }.getOrNull() ?: return null
        val baseFields = if ("driverDisplayName" in data) confirmedTripFields else legacyConfirmedTripFields
        val expectedFields = if (status == ConnectedTripStatus.CANCELLED_BY_RIDER) baseFields + "cancelledAt" else baseFields
        if (data.keys != expectedFields) return null
        val driverDisplayName = if ("driverDisplayName" in data) {
            (data["driverDisplayName"] as? String)?.takeIf(::isSafeDisplayName) ?: return null
        } else null
        val cancelledAt = if (status == ConnectedTripStatus.CANCELLED_BY_RIDER) {
            (data["cancelledAt"] as? Timestamp)?.toDate()?.time ?: return null
        } else null
        return ConnectedConfirmedTrip(
            id = id,
            journeyId = data["journeyId"] as? String ?: return null,
            acceptedRequestId = data["acceptedRequestId"] as? String ?: return null,
            driverUid = data["driverUid"] as? String ?: return null,
            riderUid = data["riderUid"] as? String ?: return null,
            originArea = data["originArea"] as? String ?: return null,
            destinationArea = data["destinationArea"] as? String ?: return null,
            departureEpochMillis = (data["departureAt"] as? Timestamp)?.toDate()?.time ?: return null,
            status = status,
            cancelledAtEpochMillis = cancelledAt,
            driverDisplayName = driverDisplayName,
        ).takeIf {
            it.id == it.acceptedRequestId &&
                it.acceptedRequestId == "${it.journeyId}_${it.riderUid}" &&
                it.driverUid.isNotBlank() && it.driverUid != it.riderUid &&
                ConnectedJourneyValidator.isBroadArea(it.originArea) &&
                ConnectedJourneyValidator.isBroadArea(it.destinationArea) &&
                !it.originArea.equals(it.destinationArea, true)
        }
    }

    private fun isValidJourney(journey: ConnectedJourney): Boolean =
        journey.driverUid.isNotBlank() && ConnectedJourneyValidator.isBroadArea(journey.originArea) &&
            ConnectedJourneyValidator.isBroadArea(journey.destinationArea) &&
            !journey.originArea.equals(journey.destinationArea, true) &&
            journey.seatCapacity in 1..8 && journey.seatsRemaining in 0..journey.seatCapacity

    private fun isSafeDisplayName(value: String): Boolean =
        value.isNotBlank() && value.length <= 60 && value.none(Char::isISOControl)

    private fun Long.toTimestamp(): Timestamp =
        Timestamp(this / 1000, ((this % 1000) * 1_000_000).toInt())
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
            storedUid == uid && displayName.isNotBlank() && displayName.length <= 60 &&
                displayName.none(Char::isISOControl)
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
