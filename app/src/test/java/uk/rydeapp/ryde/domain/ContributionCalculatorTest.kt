package uk.rydeapp.ryde.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ContributionCalculatorTest {
    @Test
    fun `zero shared miles has no contribution`() {
        assertEquals(0, ContributionCalculator.calculatePence(sharedMiles = 0))
    }

    @Test
    fun `positive contribution that would round below one pound uses minimum`() {
        assertEquals(100, ContributionCalculator.calculatePence(sharedMiles = 1))
    }

    @Test
    fun `sixteen shared miles rounds three pounds twenty down to three pounds`() {
        assertEquals(300, ContributionCalculator.calculatePence(sharedMiles = 16))
    }

    @Test
    fun `seventeen shared miles rounds three pounds forty up to three pounds fifty`() {
        assertEquals(350, ContributionCalculator.calculatePence(sharedMiles = 17))
    }

    @Test
    fun `half increment rounds upwards`() {
        assertEquals(
            150,
            ContributionCalculator.calculatePence(
                sharedMiles = 5,
                ratePencePerMile = 25,
                roundingIncrementPence = 50,
            ),
        )
    }

    @Test
    fun `negative shared miles are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ContributionCalculator.calculatePence(sharedMiles = -1)
        }
    }

    @Test
    fun `negative rate is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ContributionCalculator.calculatePence(
                sharedMiles = 1,
                ratePencePerMile = -1,
            )
        }
    }

    @Test
    fun `non-positive rounding increments are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ContributionCalculator.calculatePence(
                sharedMiles = 1,
                roundingIncrementPence = 0,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ContributionCalculator.calculatePence(
                sharedMiles = 1,
                roundingIncrementPence = -1,
            )
        }
    }
}
