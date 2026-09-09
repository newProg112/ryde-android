package uk.rydeapp.ryde.data

import uk.rydeapp.ryde.domain.model.HomeContent
import uk.rydeapp.ryde.domain.model.FindRideContent
import uk.rydeapp.ryde.domain.model.FindRideCriteria
import uk.rydeapp.ryde.domain.model.FindRideSearchResult
import uk.rydeapp.ryde.domain.model.CancelSeatRequestResult
import uk.rydeapp.ryde.domain.model.CreateSeatRequestResult
import uk.rydeapp.ryde.domain.model.SeatRequest
import uk.rydeapp.ryde.domain.model.CancelOfferedJourneyResult
import uk.rydeapp.ryde.domain.model.CreateOfferedJourneyResult
import uk.rydeapp.ryde.domain.model.OfferRideContent
import uk.rydeapp.ryde.domain.model.OfferRideCriteria
import uk.rydeapp.ryde.domain.model.OfferedJourney
import uk.rydeapp.ryde.domain.model.ConfirmedSharedTrip
import uk.rydeapp.ryde.domain.model.DecideIncomingRequestResult
import uk.rydeapp.ryde.domain.model.IncomingRequestDecision
import uk.rydeapp.ryde.domain.model.IncomingSeatRequest
import uk.rydeapp.ryde.domain.model.CircleMembership
import uk.rydeapp.ryde.domain.model.JoinCircleResult
import uk.rydeapp.ryde.domain.model.LeaveCircleResult
import uk.rydeapp.ryde.domain.model.CompletedJourneyHistory
import uk.rydeapp.ryde.domain.model.ProfileContent
import uk.rydeapp.ryde.domain.model.RepeatJourneyMode
import uk.rydeapp.ryde.domain.model.RepeatJourneyPrefillResult
import uk.rydeapp.ryde.domain.model.SavePlaceResult
import uk.rydeapp.ryde.domain.model.SavedPlace
import uk.rydeapp.ryde.domain.model.TrustedPerson
import uk.rydeapp.ryde.domain.model.UpdateTrustedPersonResult
import uk.rydeapp.ryde.domain.model.CompleteJourneyResult
import uk.rydeapp.ryde.domain.model.ConversationId
import uk.rydeapp.ryde.domain.model.CoordinationActivityItem
import uk.rydeapp.ryde.domain.model.CoordinationUnreadCounts
import uk.rydeapp.ryde.domain.model.GetConversationResult
import uk.rydeapp.ryde.domain.model.JourneyLifecycleStatus
import uk.rydeapp.ryde.domain.model.JourneyStatusUpdateResult
import uk.rydeapp.ryde.domain.model.SendMessageResult

interface RydeRepository {
    fun getHomeContent(): HomeContent
    fun getCircleMembership(circleId: String): CircleMembership?
    fun joinCircle(circleId: String): JoinCircleResult
    fun leaveCircle(circleId: String): LeaveCircleResult
    fun getFindRideContent(): FindRideContent
    fun findRides(criteria: FindRideCriteria): FindRideSearchResult
    fun getSeatRequests(): List<SeatRequest>
    fun getSeatRequestForMatch(matchId: String, circleId: String? = null): SeatRequest?
    fun createSeatRequest(matchId: String, criteria: FindRideCriteria): CreateSeatRequestResult
    fun cancelSeatRequest(requestId: String): CancelSeatRequestResult
    fun getOfferRideContent(): OfferRideContent
    fun getOfferedJourneys(): List<OfferedJourney>
    fun createOfferedJourney(criteria: OfferRideCriteria): CreateOfferedJourneyResult
    fun cancelOfferedJourney(journeyId: String): CancelOfferedJourneyResult
    fun getIncomingSeatRequests(): List<IncomingSeatRequest>
    fun getIncomingSeatRequestForJourney(journeyId: String): IncomingSeatRequest?
    fun decideIncomingSeatRequest(
        requestId: String,
        decision: IncomingRequestDecision,
    ): DecideIncomingRequestResult
    fun getConfirmedSharedTrips(): List<ConfirmedSharedTrip>
    fun getConversationForConfirmedTrip(confirmedTripId: String): GetConversationResult
    fun sendMessage(conversationId: ConversationId, body: String): SendMessageResult
    fun markConversationRead(conversationId: ConversationId): GetConversationResult
    fun getCoordinationUnreadCounts(): CoordinationUnreadCounts
    fun getCoordinationActivityItems(): List<CoordinationActivityItem>
    fun markCoordinationActivityRead(activityId: String): Boolean
    fun updateConfirmedJourneyStatus(
        confirmedTripId: String,
        status: JourneyLifecycleStatus,
    ): JourneyStatusUpdateResult
    fun completeJourney(confirmedTripId: String): CompleteJourneyResult
    fun getProfileContent(): ProfileContent
    fun getSavedPlaces(): List<SavedPlace>
    fun savePlace(place: SavedPlace): SavePlaceResult
    fun getCompletedJourneyHistory(): List<CompletedJourneyHistory>
    fun getTrustedPeople(): List<TrustedPerson>
    fun setPersonTrusted(personId: String, trusted: Boolean): UpdateTrustedPersonResult
    fun prepareRepeatJourney(
        completedTripId: String,
        personId: String,
        mode: RepeatJourneyMode = RepeatJourneyMode.FIND,
    ): RepeatJourneyPrefillResult
}
