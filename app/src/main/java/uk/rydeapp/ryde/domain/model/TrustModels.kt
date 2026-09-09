package uk.rydeapp.ryde.domain.model

data class DemoProfileIdentity(
    val firstName: String,
    val initials: String,
    val memberSince: String,
    val completedSharedJourneys: Int,
    val reliabilityPercent: Int,
    val rating: Double,
    val vehicle: DemoVehicle,
)

data class DemoVehicle(
    val description: String,
    val colour: String,
)

enum class PersonalSafetyStatus { CLEAR, BLOCKED, REPORTED }

data class TrustedPerson(
    val id: String,
    val firstName: String,
    val initials: String,
    val rating: Double,
    val completedTripIds: Set<String>,
    val personallyTrusted: Boolean,
    val safetyStatus: PersonalSafetyStatus = PersonalSafetyStatus.CLEAR,
) {
    val isTrusted: Boolean
        get() = personallyTrusted && safetyStatus == PersonalSafetyStatus.CLEAR

    val isEligibleForTrust: Boolean
        get() = completedTripIds.isNotEmpty() && safetyStatus == PersonalSafetyStatus.CLEAR
}

data class CompletedJourneyHistory(
    val id: String,
    val personId: String,
    val personName: String,
    val originArea: String,
    val destinationArea: String,
    val completedLabel: String,
)

data class ProfileContent(
    val identity: DemoProfileIdentity,
    val savedPlaces: List<SavedPlace>,
    val people: List<TrustedPerson>,
    val completedJourneys: List<CompletedJourneyHistory>,
)

sealed interface UpdateTrustedPersonResult {
    data class Updated(val person: TrustedPerson) : UpdateTrustedPersonResult
    data class NotEligible(val person: TrustedPerson?) : UpdateTrustedPersonResult
}

enum class RepeatJourneyMode { FIND, OFFER }

data class RepeatJourneyPrefill(
    val requestId: String,
    val mode: RepeatJourneyMode,
    val sourceCompletedTripId: String,
    val preferredPersonId: String,
    val preferredPersonName: String,
    val originArea: String,
    val destinationArea: String,
)

sealed interface RepeatJourneyPrefillResult {
    data class Ready(val prefill: RepeatJourneyPrefill) : RepeatJourneyPrefillResult
    data object CompletedTripNotFound : RepeatJourneyPrefillResult
    data object PersonNotOnCompletedTrip : RepeatJourneyPrefillResult
}

enum class LocationSharingState { NOT_SHARED, CONSENT_REQUIRED, SHARING, STOPPED }

enum class LocationSharingAction { PREPARE, GIVE_CONSENT_AND_START, START, STOP }

object LocationSharingPolicy {
    fun transition(
        current: LocationSharingState,
        action: LocationSharingAction,
        hasExplicitConsent: Boolean = false,
    ): LocationSharingState = when (action) {
        LocationSharingAction.PREPARE -> LocationSharingState.CONSENT_REQUIRED
        LocationSharingAction.GIVE_CONSENT_AND_START ->
            if (hasExplicitConsent) LocationSharingState.SHARING else LocationSharingState.CONSENT_REQUIRED
        LocationSharingAction.START ->
            if (hasExplicitConsent) LocationSharingState.SHARING else LocationSharingState.CONSENT_REQUIRED
        LocationSharingAction.STOP -> LocationSharingState.STOPPED
    }
}

object SavedPlacePolicy {
    fun isBroadDisplayArea(value: String): Boolean {
        val area = value.trim()
        return area.isNotEmpty() && area.none(Char::isDigit) && ',' !in area && area.length <= 60
    }
}

sealed interface SavePlaceResult {
    data class Saved(val place: SavedPlace) : SavePlaceResult
    data object PrivateOrInvalidArea : SavePlaceResult
}
