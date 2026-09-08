package uk.rydeapp.ryde.domain

object ContributionCalculator {
    fun calculatePence(
        sharedMiles: Int,
        ratePencePerMile: Int = 20,
        roundingIncrementPence: Int = 50,
    ): Int {
        require(sharedMiles >= 0)
        require(ratePencePerMile >= 0)
        require(roundingIncrementPence > 0)

        val unroundedPence = Math.multiplyExact(sharedMiles, ratePencePerMile)
        if (unroundedPence == 0) return 0

        val roundedPence = Math.multiplyExact(
            (unroundedPence + roundingIncrementPence / 2) / roundingIncrementPence,
            roundingIncrementPence,
        )
        return maxOf(100, roundedPence)
    }
}
