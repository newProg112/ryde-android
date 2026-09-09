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
}
