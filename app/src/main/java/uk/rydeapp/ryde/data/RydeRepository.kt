package uk.rydeapp.ryde.data

import uk.rydeapp.ryde.domain.model.HomeContent
import uk.rydeapp.ryde.domain.model.FindRideContent
import uk.rydeapp.ryde.domain.model.FindRideCriteria
import uk.rydeapp.ryde.domain.model.FindRideSearchResult

interface RydeRepository {
    fun getHomeContent(): HomeContent
    fun getFindRideContent(): FindRideContent
    fun findRides(criteria: FindRideCriteria): FindRideSearchResult
}
