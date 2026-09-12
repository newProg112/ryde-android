package uk.rydeapp.ryde.data.connected

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
