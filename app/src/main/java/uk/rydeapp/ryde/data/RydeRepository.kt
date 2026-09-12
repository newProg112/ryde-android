package uk.rydeapp.ryde.data

import kotlinx.coroutines.flow.StateFlow
import uk.rydeapp.ryde.domain.model.*

interface HomeContentRepository {
    fun getHomeContent(): HomeContent
}

interface AccountSessionRepository {
    val sessionState: StateFlow<AccountSession>
}

interface ProfileRepository {
    fun getProfileContent(): ProfileContent
    fun getSavedPlaces(): List<SavedPlace>
    suspend fun savePlace(place: SavedPlace): SavePlaceResult
    fun getCompletedJourneyHistory(): List<CompletedJourneyHistory>
    fun getTrustedPeople(): List<TrustedPerson>
    suspend fun setPersonTrusted(personId: String, trusted: Boolean): UpdateTrustedPersonResult
    fun prepareRepeatJourney(
        completedTripId: String,
        personId: String,
        mode: RepeatJourneyMode = RepeatJourneyMode.FIND,
    ): RepeatJourneyPrefillResult
}

interface RideDiscoveryRepository {
    fun getFindRideContent(): FindRideContent
    fun findRides(criteria: FindRideCriteria): FindRideSearchResult
    fun getSeatRequests(): List<SeatRequest>
    fun getSeatRequestForMatch(matchId: String, circleId: String? = null): SeatRequest?
    suspend fun createSeatRequest(matchId: String, criteria: FindRideCriteria): CreateSeatRequestResult
    suspend fun cancelSeatRequest(requestId: String): CancelSeatRequestResult
}

interface OfferedJourneyRepository {
    fun getOfferRideContent(): OfferRideContent
    fun getOfferedJourneys(): List<OfferedJourney>
    suspend fun createOfferedJourney(criteria: OfferRideCriteria): CreateOfferedJourneyResult
    suspend fun cancelOfferedJourney(journeyId: String): CancelOfferedJourneyResult
    fun getIncomingSeatRequests(): List<IncomingSeatRequest>
    fun getIncomingSeatRequestForJourney(journeyId: String): IncomingSeatRequest?
    suspend fun decideIncomingSeatRequest(
        requestId: String,
        decision: IncomingRequestDecision,
    ): DecideIncomingRequestResult
}

interface TripRepository {
    fun getConfirmedSharedTrips(): List<ConfirmedSharedTrip>
    fun getCompletedJourneyHistory(): List<CompletedJourneyHistory>
    suspend fun updateConfirmedJourneyStatus(
        confirmedTripId: String,
        status: JourneyLifecycleStatus,
    ): JourneyStatusUpdateResult
    suspend fun completeJourney(confirmedTripId: String): CompleteJourneyResult
}

interface CircleRepository {
    fun getCircleMembership(circleId: String): CircleMembership?
    suspend fun joinCircle(circleId: String): JoinCircleResult
    suspend fun leaveCircle(circleId: String): LeaveCircleResult
}

interface CoordinationRepository {
    fun getConversationForConfirmedTrip(confirmedTripId: String): GetConversationResult
    suspend fun sendMessage(conversationId: ConversationId, body: String): SendMessageResult
    suspend fun markConversationRead(conversationId: ConversationId): GetConversationResult
    fun getCoordinationUnreadCounts(): CoordinationUnreadCounts
    fun getCoordinationActivityItems(): List<CoordinationActivityItem>
    suspend fun markCoordinationActivityRead(activityId: String): Boolean
}

interface ObservableRydeRepository {
    val appState: StateFlow<AsyncState<RydeSnapshot>>
    suspend fun refresh()
}

interface RydeRepository :
    HomeContentRepository,
    AccountSessionRepository,
    ProfileRepository,
    RideDiscoveryRepository,
    OfferedJourneyRepository,
    TripRepository,
    CircleRepository,
    CoordinationRepository,
    ObservableRydeRepository
