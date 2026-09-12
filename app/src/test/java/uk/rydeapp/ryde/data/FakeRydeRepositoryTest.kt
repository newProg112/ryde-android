package uk.rydeapp.ryde.data

import kotlinx.coroutines.runBlocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeRydeRepositoryTest {
    private val repository = FakeRydeRepository()
    private val home = repository.getHomeContent()

    @Test
    fun `demo match exposes transparent contribution split`() = runBlocking {
        with(home.suggestedMatch) {
            assertEquals(17, sharedMiles)
            assertEquals(350, contributionPence)
            assertEquals(50, serviceFeePence)
            assertEquals(400, riderTotalPence)
            assertEquals(350, driverReceivesPence)
        }
    }

    @Test
    fun `hosted circle clearly covers service fee`() = runBlocking {
        assertTrue(home.hostedCircle.serviceFeeCoveredByHost)
    }

    @Test
    fun `default find search returns Alex first`() = runBlocking {
        val result = repository.findRides(repository.getFindRideContent().defaultCriteria)

        assertTrue(result.isValid)
        assertEquals("Alex", result.matches.first().driver.firstName)
        assertEquals(92, result.matches.first().matchScore)
    }

    @Test
    fun `Alex search match uses seventeen mile contribution breakdown`() = runBlocking {
        val alex = repository
            .findRides(repository.getFindRideContent().defaultCriteria)
            .matches
            .first()

        assertEquals(17, alex.sharedMiles)
        assertEquals(350, alex.contributionPence)
        assertEquals(50, alex.serviceFeePence)
        assertEquals(400, alex.riderTotalPence)
        assertEquals(350, alex.driverReceivesPence)
    }
}
