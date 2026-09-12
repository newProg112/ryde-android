package uk.rydeapp.ryde.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import uk.rydeapp.ryde.data.AccountSession
import uk.rydeapp.ryde.data.AppMode
import uk.rydeapp.ryde.data.AsyncState
import uk.rydeapp.ryde.data.RydeRepository
import uk.rydeapp.ryde.data.RydeSnapshot
import uk.rydeapp.ryde.domain.model.*

sealed interface RydeAppUiState {
    data object Loading : RydeAppUiState
    data object SignedOut : RydeAppUiState
    data class Ready(
        val mode: AppMode,
        val session: AccountSession,
        val snapshot: RydeSnapshot,
        val repeatJourneyPrefill: RepeatJourneyPrefill? = null,
    ) : RydeAppUiState
    data class Error(val userMessage: String) : RydeAppUiState
}

/** Compose- and Android-free coordinator for repository observation and commands. */
class RydeAppStateHolder(
    private val repository: RydeRepository,
    private val appMode: AppMode,
    private val scope: CoroutineScope,
) {
    private val mutableUiState = MutableStateFlow<RydeAppUiState>(RydeAppUiState.Loading)
    val uiState: StateFlow<RydeAppUiState> = mutableUiState.asStateFlow()

    init {
        scope.launch {
            combine(repository.sessionState, repository.appState) { session, appState ->
                session to appState
            }.collect { (session, appState) -> render(session, appState) }
        }
        retry()
    }

    fun retry() {
        mutableUiState.value = RydeAppUiState.Loading
        scope.launch { refreshSafely() }
    }

    fun findRides(criteria: FindRideCriteria): FindRideSearchResult = repository.findRides(criteria)

    fun seatRequestForMatch(matchId: String, circleId: String?): SeatRequest? =
        repository.getSeatRequestForMatch(matchId, circleId)

    fun prepareRepeatJourney(completedTripId: String, personId: String): RepeatJourneyPrefillResult =
        repository.prepareRepeatJourney(completedTripId, personId).also { result ->
            if (result is RepeatJourneyPrefillResult.Ready) {
                val current = mutableUiState.value as? RydeAppUiState.Ready
                if (current != null) mutableUiState.value = current.copy(repeatJourneyPrefill = result.prefill)
            }
        }

    suspend fun joinCircle(circleId: String) = command { repository.joinCircle(circleId) }
    suspend fun leaveCircle(circleId: String) = command { repository.leaveCircle(circleId) }
    suspend fun createSeatRequest(matchId: String, criteria: FindRideCriteria) =
        command { repository.createSeatRequest(matchId, criteria) }
    suspend fun cancelSeatRequest(requestId: String) = command { repository.cancelSeatRequest(requestId) }
    suspend fun createOfferedJourney(criteria: OfferRideCriteria) =
        command { repository.createOfferedJourney(criteria) }
    suspend fun cancelOfferedJourney(journeyId: String) = command { repository.cancelOfferedJourney(journeyId) }
    suspend fun decideIncomingSeatRequest(requestId: String, decision: IncomingRequestDecision) =
        command { repository.decideIncomingSeatRequest(requestId, decision) }
    suspend fun openConversation(confirmedTripId: String): GetConversationResult? = command {
        when (val result = repository.getConversationForConfirmedTrip(confirmedTripId)) {
            is GetConversationResult.Available -> repository.markConversationRead(result.conversation.id)
            is GetConversationResult.Unavailable -> result
        }
    }
    suspend fun sendMessage(conversationId: ConversationId, body: String) =
        command { repository.sendMessage(conversationId, body) }
    suspend fun markActivityRead(activityId: String) =
        command { repository.markCoordinationActivityRead(activityId) }
    suspend fun updateJourneyStatus(confirmedTripId: String, status: JourneyLifecycleStatus) =
        command { repository.updateConfirmedJourneyStatus(confirmedTripId, status) }
    suspend fun completeJourney(confirmedTripId: String) = command { repository.completeJourney(confirmedTripId) }
    suspend fun savePlace(place: SavedPlace) = command { repository.savePlace(place) }
    suspend fun setPersonTrusted(personId: String, trusted: Boolean) =
        command { repository.setPersonTrusted(personId, trusted) }

    private suspend fun <T> command(action: suspend () -> T): T? {
        return try {
            action().also { refreshSafely() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            mutableUiState.value = RydeAppUiState.Error(SAFE_LOAD_ERROR)
            null
        }
    }

    private suspend fun refreshSafely() {
        try {
            repository.refresh()
            render(repository.sessionState.value, repository.appState.value)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            mutableUiState.value = RydeAppUiState.Error(SAFE_LOAD_ERROR)
        }
    }

    private fun render(session: AccountSession, appState: AsyncState<RydeSnapshot>) {
        val incoming = when (session) {
            AccountSession.Checking -> RydeAppUiState.Loading
            AccountSession.SignedOut -> RydeAppUiState.SignedOut
            is AccountSession.Failure -> RydeAppUiState.Error(SAFE_LOAD_ERROR)
            is AccountSession.Authenticated -> when (appState) {
                AsyncState.Loading -> RydeAppUiState.Loading
                AsyncState.Empty -> RydeAppUiState.Error(SAFE_LOAD_ERROR)
                is AsyncState.Error -> RydeAppUiState.Error(SAFE_LOAD_ERROR)
                is AsyncState.Data -> RydeAppUiState.Ready(appMode, session, appState.value)
            }
        }
        val prefill = (mutableUiState.value as? RydeAppUiState.Ready)?.repeatJourneyPrefill
        mutableUiState.value = if (incoming is RydeAppUiState.Ready) {
            incoming.copy(repeatJourneyPrefill = prefill)
        } else {
            incoming
        }
    }

    companion object {
        const val SAFE_LOAD_ERROR = "Ryde couldn't load right now. Please try again."
    }
}
