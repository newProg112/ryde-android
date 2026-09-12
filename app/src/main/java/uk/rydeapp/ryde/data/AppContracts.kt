package uk.rydeapp.ryde.data

import uk.rydeapp.ryde.domain.model.*

enum class AppMode { LOCAL_DEMO, CONNECTED }

sealed interface AccountCommandResult {
    data object Success : AccountCommandResult
    data class InvalidInput(val userMessage: String) : AccountCommandResult
    data class Failure(val userMessage: String) : AccountCommandResult
}

sealed interface AccountSession {
    data object Checking : AccountSession
    data object SignedOut : AccountSession
    data class Authenticated(
        val accountId: String,
        val displayName: String,
        val isFictionalDemo: Boolean,
    ) : AccountSession
    data class Failure(val userMessage: String) : AccountSession
}

sealed interface AsyncState<out T> {
    data object Loading : AsyncState<Nothing>
    data class Data<T>(val value: T) : AsyncState<T>
    data object Empty : AsyncState<Nothing>
    data class Error(val userMessage: String) : AsyncState<Nothing>
}

data class RydeSnapshot(
    val homeContent: HomeContent,
    val findRideContent: FindRideContent,
    val offerRideContent: OfferRideContent,
    val circleMembership: CircleMembership,
    val seatRequests: List<SeatRequest>,
    val offeredJourneys: List<OfferedJourney>,
    val incomingRequests: List<IncomingSeatRequest>,
    val confirmedTrips: List<ConfirmedSharedTrip>,
    val completedJourneyHistory: List<CompletedJourneyHistory>,
    val coordinationActivities: List<CoordinationActivityItem>,
    val coordinationUnreadCounts: CoordinationUnreadCounts,
    val profileContent: ProfileContent,
)

object RydeAppComposition {
    fun repository(mode: AppMode, connectedRepository: RydeRepository? = null): RydeRepository =
        when (mode) {
            AppMode.LOCAL_DEMO -> connectedRepository ?: FakeRydeRepository()
            AppMode.CONNECTED -> requireNotNull(connectedRepository) {
                "CONNECTED mode requires an explicitly configured connected repository"
            }
        }
}
