package uk.rydeapp.ryde.data.connected

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.rydeapp.ryde.domain.model.SavedPlace

class ConnectedAccountValidatorTest {
    @Test
    fun `registration normalises safe fields`() {
        val result = ConnectedAccountValidator.registration("  Person@Example.COM ", "password-123", "  Alex Rider  ")
            as ValidationResult.Valid
        assertEquals("person@example.com", result.value.email)
        assertEquals("Alex Rider", result.value.displayName)
    }

    @Test
    fun `registration rejects malformed email short password and missing name`() {
        assertTrue(ConnectedAccountValidator.registration("bad", "password-123", "Alex") is ValidationResult.Invalid)
        assertTrue(ConnectedAccountValidator.registration("a@b.test", "short", "Alex") is ValidationResult.Invalid)
        assertTrue(ConnectedAccountValidator.registration("a@b.test", "password-123", " ") is ValidationResult.Invalid)
    }

    @Test
    fun `profile accepts broad areas and rejects private-looking values`() {
        assertTrue(ConnectedAccountValidator.profile("Alex", "Sutton-in-Ashfield", "Nottingham") is ValidationResult.Valid)
        assertTrue(ConnectedAccountValidator.profile("Alex", "12 High Street", "Nottingham") is ValidationResult.Invalid)
        assertTrue(ConnectedAccountValidator.profile("Alex", "Nottingham, UK", "Derby") is ValidationResult.Invalid)
    }

    @Test
    fun `firestore mapper round trips only recognised valid schema`() {
        val user = ConnectedUserProfile("uid-one", "Alex")
        assertEquals(user, FirestoreProfileMapper.user("uid-one", FirestoreProfileMapper.userData(user)))
        val home = SavedPlace("Home", "Nottingham")
        assertEquals(home, FirestoreProfileMapper.place("home", "uid-one", FirestoreProfileMapper.placeData("uid-one", home)))
        assertEquals(null, FirestoreProfileMapper.user("uid-two", FirestoreProfileMapper.userData(user)))
        assertEquals(null, FirestoreProfileMapper.place("gym", "uid-one", FirestoreProfileMapper.placeData("uid-one", home)))
    }
}
