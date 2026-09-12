package uk.rydeapp.ryde.data

import kotlinx.coroutines.runBlocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.rydeapp.ryde.domain.model.LocationSharingAction
import uk.rydeapp.ryde.domain.model.LocationSharingPolicy
import uk.rydeapp.ryde.domain.model.LocationSharingState
import uk.rydeapp.ryde.domain.model.PersonalSafetyStatus
import uk.rydeapp.ryde.domain.model.RepeatJourneyPrefillResult
import uk.rydeapp.ryde.domain.model.SavePlaceResult
import uk.rydeapp.ryde.domain.model.SavedPlace
import uk.rydeapp.ryde.domain.model.SavedPlacePolicy
import uk.rydeapp.ryde.domain.model.TrustedPerson
import uk.rydeapp.ryde.domain.model.UpdateTrustedPersonResult

class FakeRydeRepositoryTrustTest {
    @Test
    fun `only a person from a completed shared trip can become trusted`() = runBlocking {
        val repository = FakeRydeRepository()

        val eligible = repository.setPersonTrusted("jamie-demo", true)
        val ineligible = repository.setPersonTrusted("casey-demo", true)

        assertTrue(eligible is UpdateTrustedPersonResult.Updated)
        assertEquals("Jamie", repository.getTrustedPeople().single().firstName)
        assertTrue(ineligible is UpdateTrustedPersonResult.NotEligible)
    }

    @Test
    fun `blocked or reported status overrides a personal trusted choice`() = runBlocking {
        fun person(status: PersonalSafetyStatus) = TrustedPerson(
            id = "demo",
            firstName = "Jamie",
            initials = "JM",
            rating = 4.8,
            completedTripIds = setOf("completed-trip"),
            personallyTrusted = true,
            safetyStatus = status,
        )

        assertFalse(person(PersonalSafetyStatus.BLOCKED).isTrusted)
        assertFalse(person(PersonalSafetyStatus.REPORTED).isTrusted)
        assertTrue(person(PersonalSafetyStatus.CLEAR).isTrusted)
    }

    @Test
    fun `saved places expose broad areas and reject private-looking addresses`() = runBlocking {
        val repository = FakeRydeRepository()

        assertEquals(listOf("Sutton-in-Ashfield", "Nottingham"), repository.getSavedPlaces().map { it.displayArea })
        assertTrue(repository.getSavedPlaces().all { SavedPlacePolicy.isBroadDisplayArea(it.displayArea) })
        assertTrue(
            repository.savePlace(SavedPlace("Private", "14 Example Street, NG1 1AA"))
                is SavePlaceResult.PrivateOrInvalidArea,
        )
    }

    @Test
    fun `repeat journey prefill uses selected trip and person without creating a trip`() = runBlocking {
        val repository = FakeRydeRepository()
        val trip = repository.getCompletedJourneyHistory().single()
        val before = Triple(
            repository.getSeatRequests().size,
            repository.getOfferedJourneys().size,
            repository.getConfirmedSharedTrips().size,
        )

        val result = repository.prepareRepeatJourney(trip.id, trip.personId)

        assertTrue(result is RepeatJourneyPrefillResult.Ready)
        val prefill = (result as RepeatJourneyPrefillResult.Ready).prefill
        assertEquals(trip.id, prefill.sourceCompletedTripId)
        assertEquals(trip.personId, prefill.preferredPersonId)
        assertEquals(trip.originArea, prefill.originArea)
        assertEquals(trip.destinationArea, prefill.destinationArea)
        assertEquals(before, Triple(repository.getSeatRequests().size, repository.getOfferedJourneys().size, repository.getConfirmedSharedTrips().size))
    }

    @Test
    fun `location sharing cannot start without explicit consent`() = runBlocking {
        assertEquals(
            LocationSharingState.CONSENT_REQUIRED,
            LocationSharingPolicy.transition(
                current = LocationSharingState.NOT_SHARED,
                action = LocationSharingAction.START,
                hasExplicitConsent = false,
            ),
        )
        assertEquals(
            LocationSharingState.SHARING,
            LocationSharingPolicy.transition(
                current = LocationSharingState.CONSENT_REQUIRED,
                action = LocationSharingAction.GIVE_CONSENT_AND_START,
                hasExplicitConsent = true,
            ),
        )
    }
}
