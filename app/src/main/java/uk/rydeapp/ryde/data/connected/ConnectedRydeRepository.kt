package uk.rydeapp.ryde.data.connected

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import uk.rydeapp.ryde.data.AccountCommandResult
import uk.rydeapp.ryde.data.AccountSession
import uk.rydeapp.ryde.data.AsyncState
import uk.rydeapp.ryde.data.FakeRydeRepository
import uk.rydeapp.ryde.data.RydeRepository
import uk.rydeapp.ryde.data.RydeSnapshot
import uk.rydeapp.ryde.domain.model.ProfileContent
import uk.rydeapp.ryde.domain.model.SavePlaceResult
import uk.rydeapp.ryde.domain.model.SavedPlace

/** Firebase-emulator repository. Connected capabilities are explicit; demo data is never rendered in connected UI. */
class ConnectedRydeRepository(
    private val auth: ConnectedAuthGateway,
    private val profiles: ConnectedProfileStore,
    private val legacyCapabilities: RydeRepository = FakeRydeRepository(),
    private val firebaseOperationTimeoutMillis: Long = FIREBASE_OPERATION_TIMEOUT_MILLIS,
    private val journeys: ConnectedJourneyStore? = null,
    private val coordination: ConnectedCoordinationStore? = null,
) : RydeRepository by legacyCapabilities {
    private val mutableSessionState = MutableStateFlow<AccountSession>(
        if (auth.currentUserId == null) AccountSession.SignedOut else AccountSession.Checking,
    )
    override val sessionState: StateFlow<AccountSession> = mutableSessionState.asStateFlow()

    private val mutableAppState = MutableStateFlow<AsyncState<RydeSnapshot>>(AsyncState.Empty)
    override val appState: StateFlow<AsyncState<RydeSnapshot>> = mutableAppState.asStateFlow()

    private var connectedProfile: ConnectedProfile? = null
    private val mutableJourneyState = MutableStateFlow(ConnectedJourneySnapshot())
    val journeyState: StateFlow<ConnectedJourneySnapshot> = mutableJourneyState.asStateFlow()

    override suspend fun register(email: String, password: String, displayName: String): AccountCommandResult {
        val input = when (val result = ConnectedAccountValidator.registration(email, password, displayName)) {
            is ValidationResult.Invalid -> return AccountCommandResult.InvalidInput(result.userMessage)
            is ValidationResult.Valid -> result.value
        }
        return safely {
            val uid = firebaseCall { auth.register(input.email, input.password) }
            try {
                firebaseCall { profiles.create(ConnectedUserProfile(uid, checkNotNull(input.displayName))) }
                refresh()
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                try {
                    firebaseCall { auth.signOut() }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    // The original safe failure is more useful than a cleanup failure.
                } finally {
                    clearSignedOut()
                }
                throw failure
            }
        }
    }

    override suspend fun signIn(email: String, password: String): AccountCommandResult {
        val input = when (val result = ConnectedAccountValidator.credentials(email, password)) {
            is ValidationResult.Invalid -> return AccountCommandResult.InvalidInput(result.userMessage)
            is ValidationResult.Valid -> result.value
        }
        return safely {
            firebaseCall { auth.signIn(input.email, input.password) }
            try {
                refresh()
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                try {
                    firebaseCall { auth.signOut() }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    // The original safe failure is more useful than a cleanup failure.
                } finally {
                    clearSignedOut()
                }
                throw failure
            }
        }
    }

    override suspend fun signOut(): AccountCommandResult = try {
        firebaseCall { auth.signOut() }
        AccountCommandResult.Success
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        AccountCommandResult.Failure(SAFE_ACCOUNT_ERROR)
    } finally {
        clearSignedOut()
    }

    override suspend fun updateConnectedProfile(
        displayName: String,
        homeArea: String,
        workArea: String,
    ): AccountCommandResult {
        val draft = when (val result = ConnectedAccountValidator.profile(displayName, homeArea, workArea)) {
            is ValidationResult.Invalid -> return AccountCommandResult.InvalidInput(result.userMessage)
            is ValidationResult.Valid -> result.value
        }
        return safely {
            val uid = requireNotNull(auth.currentUserId)
            firebaseCall { profiles.save(uid, draft) }
            refresh()
        }
    }

    suspend fun createConnectedJourney(
        originArea: String,
        destinationArea: String,
        departure: String,
        seats: String,
    ): ConnectedJourneyCommandResult {
        val draft = when (val validated = ConnectedJourneyValidator.offer(originArea, destinationArea, departure, seats)) {
            is ValidationResult.Invalid -> return ConnectedJourneyCommandResult.InvalidInput(validated.userMessage)
            is ValidationResult.Valid -> validated.value
        }
        return journeyCommand { store, uid -> store.create(uid, draft) }
    }

    suspend fun requestConnectedSeat(journeyId: String): ConnectedJourneyCommandResult =
        journeyCommand { store, uid -> store.requestSeat(uid, journeyId) }

    suspend fun cancelConnectedRequest(requestId: String): ConnectedJourneyCommandResult =
        journeyCommand { store, uid -> store.cancelRequest(uid, requestId) }

    suspend fun cancelConnectedConfirmedSeat(tripId: String): ConnectedJourneyCommandResult =
        journeyCommand { store, uid -> store.cancelConfirmedSeat(uid, tripId) }

    suspend fun cancelConnectedJourney(journeyId: String): ConnectedJourneyCommandResult =
        journeyCommand { store, uid -> store.cancelJourney(uid, journeyId) }

    suspend fun completeConnectedJourney(journeyId: String): ConnectedJourneyCommandResult =
        journeyCommand { store, uid -> store.completeJourney(uid, journeyId) }

    suspend fun decideConnectedRequest(requestId: String, accept: Boolean): ConnectedJourneyCommandResult =
        journeyCommand { store, uid -> store.decide(uid, requestId, accept) }

    /** Keeps only remote journey closure authoritative between explicit full refreshes. */
    suspend fun synchronizeConnectedJourneyLifecycles() {
        val store = journeys ?: return
        val uid = auth.currentUserId ?: return
        try {
            combine(store.observeJourneys(uid), sessionState) { observed, session -> observed to session }
                .takeWhile { (_, session) ->
                    auth.currentUserId == uid &&
                        session is AccountSession.Authenticated && session.accountId == uid
                }
                .collect { (observed, _) ->
                    if (auth.currentUserId != uid) return@collect
                    mutableJourneyState.update { current ->
                        current.reconcileRemoteJourneyClosures(observed)
                    }
                }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // Explicit Refresh remains the safe fallback if the scoped listener fails.
        }
    }

    fun observeConnectedConversation(tripId: String): Flow<ConnectedConversationState> = flow {
        val store = coordination
        val uid = auth.currentUserId
        if (store == null || uid == null || !ConnectedMessagePolicy.validMessageId(tripId)) {
            emit(ConnectedConversationState.Error(SAFE_COORDINATION_LOAD_ERROR))
            return@flow
        }
        var previous: ConnectedConversation? = null
        emit(ConnectedConversationState.Loading)
        try {
            store.observeConversation(uid, tripId).collect { snapshot ->
                if (auth.currentUserId != uid) throw ConnectedCoordinationUnavailableException()
                val canSend = ConnectedJourneyLifecycle.canSendMessages(snapshot.trip, snapshot.journey, uid)
                val conversation = ConnectedConversation(
                    trip = snapshot.trip,
                    journey = snapshot.journey,
                    messages = snapshot.messages,
                    canSendMessages = canSend,
                    readOnlyReason = if (canSend) null else snapshot.readOnlyReason(),
                )
                previous = conversation
                emit(ConnectedConversationState.Data(conversation))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            emit(ConnectedConversationState.Error(SAFE_COORDINATION_LOAD_ERROR, previous))
        }
    }

    suspend fun sendConnectedMessage(
        tripId: String,
        messageId: String,
        body: String,
    ): ConnectedMessageCommandResult {
        val validated = when (val result = ConnectedMessagePolicy.validate(body)) {
            is ConnectedMessageValidationResult.Invalid -> return ConnectedMessageCommandResult.InvalidInput(result.userMessage)
            is ConnectedMessageValidationResult.Valid -> result.body
        }
        if (!ConnectedMessagePolicy.validMessageId(messageId)) {
            return ConnectedMessageCommandResult.Failure(SAFE_COORDINATION_SEND_ERROR)
        }
        val store = coordination ?: return ConnectedMessageCommandResult.Failure(SAFE_COORDINATION_SEND_ERROR)
        val uid = auth.currentUserId ?: return ConnectedMessageCommandResult.ReadOnly(SAFE_COORDINATION_READ_ONLY)
        return try {
            firebaseCall { store.sendMessage(uid, tripId, messageId, validated) }
            if (auth.currentUserId != uid) {
                ConnectedMessageCommandResult.ReadOnly(SAFE_COORDINATION_READ_ONLY)
            } else {
                ConnectedMessageCommandResult.Success(messageId)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: ConnectedCoordinationUnavailableException) {
            ConnectedMessageCommandResult.ReadOnly(SAFE_COORDINATION_READ_ONLY)
        } catch (_: Throwable) {
            ConnectedMessageCommandResult.Failure(SAFE_COORDINATION_SEND_ERROR)
        }
    }

    override suspend fun refresh() {
        val uid = auth.currentUserId
        if (uid == null) {
            clearSignedOut()
            return
        }
        val previousAppState = mutableAppState.value
        val previousSessionState = mutableSessionState.value
        // Keep an already rendered profile visible during refresh. Emitting Loading here
        // would dispose its Compose command scope and cancel this very refresh.
        if (previousAppState !is AsyncState.Data) {
            mutableAppState.value = AsyncState.Loading
        }
        try {
            val profile = requireNotNull(firebaseCall { profiles.load(uid) })
            val connectedJourneys = journeys?.let { firebaseCall { it.load(uid) } } ?: ConnectedJourneySnapshot()
            legacyCapabilities.refresh()
            val base = (legacyCapabilities.appState.value as? AsyncState.Data)?.value
                ?: error("Phase 9A fallback capabilities did not provide a snapshot")
            val displayName = profile.user.displayName
            val places = profile.savedPlaces
            val connectedSnapshot = base.copy(
                homeContent = base.homeContent.copy(
                    currentUser = base.homeContent.currentUser.copy(
                        firstName = displayName,
                        savedPlaces = places,
                    ),
                ),
                profileContent = base.profileContent.copy(
                    identity = base.profileContent.identity.copy(
                        firstName = displayName,
                        initials = initials(displayName),
                    ),
                    savedPlaces = places,
                    people = emptyList(),
                    completedJourneys = emptyList(),
                ),
            )
            connectedProfile = profile
            mutableJourneyState.value = connectedJourneys
            mutableSessionState.value = AccountSession.Authenticated(uid, displayName, isFictionalDemo = false)
            mutableAppState.value = AsyncState.Data(connectedSnapshot)
        } catch (cancelled: CancellationException) {
            mutableAppState.value = previousAppState
            mutableSessionState.value = previousSessionState
            throw cancelled
        } catch (failure: Throwable) {
            mutableAppState.value = if (previousAppState is AsyncState.Data) {
                previousAppState
            } else {
                AsyncState.Error(SAFE_ACCOUNT_ERROR)
            }
            if (previousAppState !is AsyncState.Data) {
                mutableSessionState.value = AccountSession.Failure(SAFE_ACCOUNT_ERROR)
            }
            throw failure
        }
    }

    override fun getProfileContent(): ProfileContent =
        (mutableAppState.value as? AsyncState.Data)?.value?.profileContent
            ?: legacyCapabilities.getProfileContent().copy(savedPlaces = emptyList(), people = emptyList(), completedJourneys = emptyList())

    override fun getSavedPlaces(): List<SavedPlace> = connectedProfile?.savedPlaces.orEmpty()

    override suspend fun savePlace(place: SavedPlace): SavePlaceResult {
        val current = connectedProfile ?: return SavePlaceResult.PrivateOrInvalidArea
        if (place.label !in setOf("Home", "Work")) return SavePlaceResult.PrivateOrInvalidArea
        val places = current.savedPlaces.associateBy { it.label }.toMutableMap().apply { put(place.label, place) }
        val home = places["Home"]?.area ?: return SavePlaceResult.PrivateOrInvalidArea
        val work = places["Work"]?.area ?: return SavePlaceResult.PrivateOrInvalidArea
        return when (updateConnectedProfile(current.user.displayName, home, work)) {
            AccountCommandResult.Success -> SavePlaceResult.Saved(place.copy(area = place.area.trim()))
            else -> SavePlaceResult.PrivateOrInvalidArea
        }
    }

    private suspend fun safely(action: suspend () -> Unit): AccountCommandResult = try {
        action()
        AccountCommandResult.Success
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        if (auth.currentUserId == null) clearSignedOut()
        AccountCommandResult.Failure(SAFE_ACCOUNT_ERROR)
    }

    private suspend fun journeyCommand(
        action: suspend (ConnectedJourneyStore, String) -> Unit,
    ): ConnectedJourneyCommandResult {
        val store = journeys ?: return ConnectedJourneyCommandResult.Failure(SAFE_JOURNEY_ERROR)
        val uid = auth.currentUserId ?: return ConnectedJourneyCommandResult.Failure(SAFE_JOURNEY_ERROR)
        return try {
            firebaseCall { action(store, uid) }
            refresh()
            ConnectedJourneyCommandResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            ConnectedJourneyCommandResult.Failure(SAFE_JOURNEY_ERROR)
        }
    }

    private suspend fun <T> firebaseCall(action: suspend () -> T): T = try {
        withTimeout(firebaseOperationTimeoutMillis) { action() }
    } catch (_: TimeoutCancellationException) {
        throw FirebaseOperationTimedOutException()
    }

    private fun clearSignedOut() {
        connectedProfile = null
        mutableJourneyState.value = ConnectedJourneySnapshot()
        mutableAppState.value = AsyncState.Empty
        mutableSessionState.value = AccountSession.SignedOut
    }

    private fun initials(name: String): String = name.split(Regex("\\s+"))
        .filter(String::isNotBlank)
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { "R" }

    companion object {
        const val FIREBASE_OPERATION_TIMEOUT_MILLIS = 15_000L
        const val SAFE_ACCOUNT_ERROR = "Ryde couldn't complete that account request. Check the local emulators and try again."
        const val SAFE_JOURNEY_ERROR = "Ryde couldn't complete that journey request. Refresh and check the local emulators."
        const val SAFE_COORDINATION_LOAD_ERROR = "Messages are unavailable right now. Try again."
        const val SAFE_COORDINATION_SEND_ERROR = "Ryde couldn't send that message. Try again."
        const val SAFE_COORDINATION_READ_ONLY = "This conversation is now read-only."
    }
}

private fun ConnectedConversationSnapshot.readOnlyReason(): ConnectedConversationReadOnlyReason = when {
    trip.status == ConnectedTripStatus.CANCELLED_BY_RIDER -> ConnectedConversationReadOnlyReason.CANCELLED_BY_RIDER
    journey?.status == ConnectedJourneyStatus.CANCELLED -> ConnectedConversationReadOnlyReason.CANCELLED_BY_DRIVER
    journey?.status == ConnectedJourneyStatus.COMPLETED -> ConnectedConversationReadOnlyReason.COMPLETED
    else -> ConnectedConversationReadOnlyReason.UNAVAILABLE
}

internal fun ConnectedJourneySnapshot.reconcileRemoteJourneyClosures(
    observed: List<ConnectedJourney>,
): ConnectedJourneySnapshot {
    val observedById = observed.associateBy(ConnectedJourney::id)
    val reconciled = journeys.map { current ->
        val remote = observedById[current.id]
        if (
            current.status == ConnectedJourneyStatus.OPEN &&
            remote != null &&
            remote.status in setOf(ConnectedJourneyStatus.CANCELLED, ConnectedJourneyStatus.COMPLETED) &&
            current.sameImmutableJourney(remote)
        ) remote else current
    }
    return if (reconciled == journeys) this else copy(journeys = reconciled)
}

private fun ConnectedJourney.sameImmutableJourney(other: ConnectedJourney): Boolean =
    id == other.id && driverUid == other.driverUid &&
        originArea == other.originArea && destinationArea == other.destinationArea &&
        departureEpochMillis == other.departureEpochMillis && seatCapacity == other.seatCapacity

private class FirebaseOperationTimedOutException : Exception()
