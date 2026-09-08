package uk.rydeapp.ryde.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeRydeRepositoryTest {
    private val home = FakeRydeRepository.getHomeContent()

    @Test
    fun `demo match exposes transparent contribution split`() {
        with(home.suggestedMatch) {
            assertEquals(17, sharedMiles)
            assertEquals(350, contributionPence)
            assertEquals(50, serviceFeePence)
            assertEquals(400, riderTotalPence)
            assertEquals(350, driverReceivesPence)
        }
    }

    @Test
    fun `hosted circle clearly covers service fee`() {
        assertTrue(home.hostedCircle.serviceFeeCoveredByHost)
    }
}
