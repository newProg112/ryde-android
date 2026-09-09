package uk.rydeapp.ryde.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.rydeapp.ryde.domain.model.DemoTravelDate
import uk.rydeapp.ryde.domain.model.DriverProfile
import uk.rydeapp.ryde.domain.model.FindRideCriteria
import uk.rydeapp.ryde.domain.model.FindRideField
import uk.rydeapp.ryde.domain.model.Flexibility
import uk.rydeapp.ryde.domain.model.Journey
import uk.rydeapp.ryde.domain.model.RouteMatch

class FindRideMatcherTest {
    private val criteria = FindRideCriteria(
        origin = "Sutton-in-Ashfield",
        destination = "Nottingham",
        travelDate = DemoTravelDate.TODAY,
        departureMinutes = 485,
        flexibility = Flexibility.THIRTY,
        seatsRequired = 1,
    )

    @Test
    fun `valid default criteria have no errors`() {
        assertTrue(FindRideMatcher.validate(criteria).isEmpty())
    }

    @Test
    fun `blank endpoints are rejected`() {
        val errors = FindRideMatcher.validate(criteria.copy(origin = "  ", destination = ""))
        assertEquals(setOf(FindRideField.ORIGIN, FindRideField.DESTINATION), errors.map { it.field }.toSet())
    }

    @Test
    fun `identical endpoints are rejected ignoring case and whitespace`() {
        val errors = FindRideMatcher.validate(criteria.copy(origin = " Nottingham "))
        assertTrue(errors.any { it.field == FindRideField.ENDPOINTS })
    }

    @Test
    fun `seat bounds allow one to four and reject outside values`() {
        assertTrue(FindRideMatcher.validate(criteria.copy(seatsRequired = 1)).isEmpty())
        assertTrue(FindRideMatcher.validate(criteria.copy(seatsRequired = 4)).isEmpty())
        assertTrue(FindRideMatcher.validate(criteria.copy(seatsRequired = 0)).any { it.field == FindRideField.SEATS })
        assertTrue(FindRideMatcher.validate(criteria.copy(seatsRequired = 5)).any { it.field == FindRideField.SEATS })
    }

    @Test
    fun `time window includes boundary and excludes minute beyond it`() {
        assertTrue(FindRideMatcher.isWithinTimeWindow(criteria, candidateMinutes = 455))
        assertTrue(FindRideMatcher.isWithinTimeWindow(criteria, candidateMinutes = 515))
        assertFalse(FindRideMatcher.isWithinTimeWindow(criteria, candidateMinutes = 516))
    }

    @Test
    fun `candidate with insufficient seats is rejected`() {
        val matches = FindRideMatcher.rankCompatible(criteria.copy(seatsRequired = 2), listOf(match("one", 99, seats = 1)))
        assertTrue(matches.isEmpty())
    }

    @Test
    fun `compatible candidates are ranked deterministically by score`() {
        val matches = FindRideMatcher.rankCompatible(
            criteria,
            listOf(match("lower", 72), match("best", 92), match("middle", 84)),
        )
        assertEquals(listOf("best", "middle", "lower"), matches.map { it.id })
    }

    private fun match(id: String, score: Int, seats: Int = 2) = RouteMatch(
        id = id,
        driver = DriverProfile(id, 4.8, true),
        driverJourney = Journey("Mansfield", "Nottingham", "08:00"),
        compatibleOrigin = criteria.origin,
        compatibleDestination = criteria.destination,
        travelDate = criteria.travelDate,
        pickupArea = "Public pickup area",
        pickupMinutes = criteria.departureMinutes,
        walkMinutes = 5,
        sharedMiles = 10,
        detourMiles = 1.0,
        availableSeats = seats,
        matchScore = score,
        contributionPence = ContributionCalculator.calculatePence(10),
        serviceFeePence = 50,
    )
}
