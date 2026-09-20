package uk.rydeapp.ryde.data.connected

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectedCoordinationTest {
    private val departure = 1_000L
    private val journey = ConnectedJourney("j", "driver", "Derby", "Nottingham", departure, 2, 1)
    private val trip = ConnectedConfirmedTrip(
        "j_rider", "j", "j_rider", "driver", "rider", "Derby", "Nottingham", departure,
        ConnectedTripStatus.CONFIRMED,
    )

    @Test
    fun `message policy trims and enforces structural content limits`() {
        assertEquals(
            ConnectedMessageValidationResult.Valid("I'm here."),
            ConnectedMessagePolicy.validate("  I'm here.  "),
        )
        assertTrue(ConnectedMessagePolicy.validate("   ") is ConnectedMessageValidationResult.Invalid)
        assertTrue(ConnectedMessagePolicy.validate("x".repeat(501)) is ConnectedMessageValidationResult.Invalid)
        assertTrue(ConnectedMessagePolicy.validate("hello\u0007") is ConnectedMessageValidationResult.Invalid)
        assertTrue(ConnectedMessagePolicy.validMessageId("opaque_id-123"))
        assertFalse(ConnectedMessagePolicy.validMessageId("not.opaque"))
    }

    @Test
    fun `participants retain read eligibility but send requires coherent confirmed open truth`() {
        assertTrue(ConnectedJourneyLifecycle.canReadMessages(trip, "driver"))
        assertTrue(ConnectedJourneyLifecycle.canReadMessages(trip, "rider"))
        assertFalse(ConnectedJourneyLifecycle.canReadMessages(trip, "stranger"))
        assertTrue(ConnectedJourneyLifecycle.canSendMessages(trip, journey, "driver"))
        assertTrue(ConnectedJourneyLifecycle.canSendMessages(trip, journey, "rider"))

        assertFalse(ConnectedJourneyLifecycle.canSendMessages(
            trip.copy(status = ConnectedTripStatus.CANCELLED_BY_RIDER, cancelledAtEpochMillis = 2), journey, "rider",
        ))
        assertFalse(ConnectedJourneyLifecycle.canSendMessages(
            trip, journey.copy(status = ConnectedJourneyStatus.CANCELLED, cancelledAtEpochMillis = 2), "driver",
        ))
        assertFalse(ConnectedJourneyLifecycle.canSendMessages(trip, null, "rider"))
        assertFalse(ConnectedJourneyLifecycle.canSendMessages(trip, journey.copy(originArea = "Mansfield"), "rider"))
        assertFalse(ConnectedJourneyLifecycle.canSendMessages(trip, journey, "stranger"))
    }

    @Test
    fun `departure time does not disable coordination`() {
        assertTrue(ConnectedJourneyLifecycle.canSendMessages(trip, journey, "rider"))
        assertEquals(ConnectedTripLifecycle.DEPARTURE_PASSED, ConnectedJourneyLifecycle.trip(trip, journey, departure))
        assertEquals(ConnectedTripLifecycle.DEPARTURE_PASSED, ConnectedJourneyLifecycle.trip(trip, journey, departure + 1))
    }

    @Test
    fun `message mapper requires exact committed server shape and deterministic identity`() {
        val valid = mapOf<String, Any?>(
            "senderUid" to "rider",
            "body" to "I'm here.",
            "sentAt" to Timestamp(10, 0),
        )
        assertEquals(10_000L, FirestoreConnectedMessageMapper.message("message-1", valid)?.sentAtEpochMillis)
        assertNull(FirestoreConnectedMessageMapper.message("message-1", valid + ("extra" to true)))
        assertEquals(" padded ", FirestoreConnectedMessageMapper.message("message-1", valid + ("body" to " padded "))?.body)
        assertNull(FirestoreConnectedMessageMapper.message("bad.id", valid))
        assertNull(FirestoreConnectedMessageMapper.message("message-1", valid + ("sentAt" to "pending")))
    }

    @Test
    fun `latest messages are bounded and equal timestamps use opaque id ordering`() {
        val messages = (0..101).map { index ->
            ConnectedMessage(index.toString().padStart(3, '0'), "rider", "Message $index", index / 2L)
        }.reversed()
        val ordered = orderedLatestConnectedMessages(messages)
        assertEquals(100, ordered.size)
        assertEquals("002", ordered.first().id)
        assertEquals("101", ordered.last().id)
        assertEquals(ordered.sortedWith(compareBy(ConnectedMessage::sentAtEpochMillis, ConnectedMessage::id)), ordered)
    }
}
