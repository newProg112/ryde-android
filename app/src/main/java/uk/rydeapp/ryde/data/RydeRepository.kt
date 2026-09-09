package uk.rydeapp.ryde.data

import uk.rydeapp.ryde.domain.model.HomeContent
import uk.rydeapp.ryde.domain.model.FindRideContent
import uk.rydeapp.ryde.domain.model.FindRideCriteria
import uk.rydeapp.ryde.domain.model.FindRideSearchResult
import uk.rydeapp.ryde.domain.model.CancelSeatRequestResult
import uk.rydeapp.ryde.domain.model.CreateSeatRequestResult
import uk.rydeapp.ryde.domain.model.SeatRequest

interface RydeRepository {
    fun getHomeContent(): HomeContent
    fun getFindRideContent(): FindRideContent
    fun findRides(criteria: FindRideCriteria): FindRideSearchResult
    fun getSeatRequests(): List<SeatRequest>
    fun getSeatRequestForMatch(matchId: String): SeatRequest?
    fun createSeatRequest(matchId: String, criteria: FindRideCriteria): CreateSeatRequestResult
    fun cancelSeatRequest(requestId: String): CancelSeatRequestResult
}
