package uk.rydeapp.ryde.data

import uk.rydeapp.ryde.domain.model.HomeContent

interface RydeRepository {
    fun getHomeContent(): HomeContent
}
