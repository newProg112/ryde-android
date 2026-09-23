package uk.rydeapp.ryde.data.connected

import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

/** Opt-in only: demo namespace and ports never overlap the manual Firebase emulators. */
class FirestoreConnectedCancellationEmulatorTest {
    @Test
    fun actualGatewayReleasesOneSeatWithoutGuardReadsAndRetainsHistory() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("rydeRulesEmulator") == "true")
        val apps = mutableListOf<FirebaseApp>()
        try {
            suspend fun account(displayName: String): Triple<String, FirebaseFirestore, FirestoreConnectedJourneyStore> {
                val unique = UUID.randomUUID().toString()
                val app = FirebaseApp.initializeApp(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    FirebaseOptions.Builder()
                        .setProjectId("demo-ryde-rules-test")
                        .setApplicationId("1:1234567890:android:abcdef0123456789")
                        .setApiKey("local-emulator-only")
                        .build(),
                    "cancellation-$unique",
                )
                apps += app
                val auth = FirebaseAuth.getInstance(app).apply { useEmulator("10.0.2.2", 9199) }
                val firestore = FirebaseFirestore.getInstance(app).apply { useEmulator("10.0.2.2", 8180) }
                val uid = checkNotNull(auth.createUserWithEmailAndPassword("$unique@example.test", "password-123").await().user?.uid)
                FirestoreConnectedProfileStore(firestore).create(ConnectedUserProfile(uid, displayName))
                return Triple(uid, firestore, FirestoreConnectedJourneyStore(firestore))
            }
            val (driverUid, driverDb, driver) = account("Driver Label")
            val (riderUid, riderDb, rider) = account("Shared Rider Label")
            val (otherUid, otherDb, other) = account("Shared Rider Label")
            driver.create(driverUid, ConnectedJourneyDraft("Mansfield", "Nottingham", System.currentTimeMillis() + 86_400_000, 1))
            val journey = driver.load(driverUid).journeys.single { it.driverUid == driverUid }
            rider.requestSeat(riderUid, journey.id)
            val requestId = "${journey.id}_$riderUid"
            val driverRequest = driver.load(driverUid).requests.single { it.id == requestId }
            assertEquals("Shared Rider Label", driverRequest.riderDisplayName)
            assertEquals(driverRequest, rider.load(riderUid).requests.single { it.id == requestId })
            assertFalse(runCatching {
                driverDb.collection("users").document(riderUid).get(Source.SERVER).await()
            }.isSuccess)
            driver.decide(driverUid, requestId, true)
            val accepted = rider.load(riderUid).confirmedTrips.single()
            assertEquals("Driver Label", accepted.driverDisplayName)
            val driverCoordination = FirestoreConnectedCoordinationStore(driverDb)
            val riderCoordination = FirestoreConnectedCoordinationStore(riderDb)
            driverCoordination.sendMessage(
                driverUid, accepted.id, "driver-message", "I'm outside the station.",
            )
            val riderConversation = withTimeout(10_000) {
                riderCoordination.observeConversation(riderUid, accepted.id)
                    .first { it.messages.any { message -> message.id == "driver-message" } }
            }
            assertEquals("I'm outside the station.", riderConversation.messages.single().body)
            riderCoordination.sendMessage(riderUid, accepted.id, "rider-message", "I'm here.")
            val driverConversation = withTimeout(10_000) {
                driverCoordination.observeConversation(driverUid, accepted.id).first { it.messages.size == 2 }
            }
            assertEquals(listOf("driver-message", "rider-message"), driverConversation.messages.map { it.id })
            assertFalse(runCatching {
                withTimeout(5_000) {
                    FirestoreConnectedCoordinationStore(otherDb)
                        .observeConversation(otherUid, accepted.id).first()
                }
            }.isSuccess)
            assertFalse(runCatching {
                riderDb.collection("users").document(driverUid).get(Source.SERVER).await()
            }.isSuccess)
            FirestoreConnectedProfileStore(driverDb).save(
                driverUid,
                ConnectedProfileDraft("Renamed Driver", "Mansfield", "Nottingham"),
            )
            assertEquals("Driver Label", rider.load(riderUid).confirmedTrips.single().driverDisplayName)
            val guardRef = riderDb.collection("journeyAcceptanceGuards").document(journey.id)
            val denied = runCatching { guardRef.get(Source.SERVER).await() }.exceptionOrNull()
            assertTrue(denied is FirebaseFirestoreException)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, (denied as FirebaseFirestoreException).code)
            assertFalse(runCatching { driver.cancelConfirmedSeat(driverUid, requestId) }.isSuccess)
            rider.cancelConfirmedSeat(riderUid, requestId)
            val cancelled = rider.load(riderUid)
            val trip = cancelled.confirmedTrips.single()
            assertEquals(ConnectedTripStatus.CANCELLED_BY_RIDER, trip.status)
            assertEquals("Driver Label", trip.driverDisplayName)
            assertTrue(trip.cancelledAtEpochMillis != null)
            assertEquals(ConnectedRequestStatus.CANCELLED_AFTER_ACCEPTANCE, cancelled.requests.single().status)
            val retainedConversation = withTimeout(10_000) {
                riderCoordination.observeConversation(riderUid, trip.id).first { snapshot ->
                    snapshot.trip.status == ConnectedTripStatus.CANCELLED_BY_RIDER && snapshot.messages.size == 2
                }
            }
            assertTrue(ConnectedJourneyLifecycle.canReadMessages(retainedConversation.trip, riderUid))
            assertFalse(ConnectedJourneyLifecycle.canSendMessages(
                retainedConversation.trip, retainedConversation.journey, riderUid,
            ))
            assertFalse(runCatching {
                riderCoordination.sendMessage(riderUid, trip.id, "after-cancel", "Still here")
            }.isSuccess)
            assertEquals(1, cancelled.journeys.single { it.id == journey.id }.seatsRemaining)
            assertEquals(trip, driver.load(driverUid).confirmedTrips.single())
            val driverGuard = driverDb.collection("journeyAcceptanceGuards").document(journey.id)
            assertEquals(0L, driverGuard.get(Source.SERVER).await().getLong("acceptanceCount"))
            assertFalse(runCatching { rider.cancelConfirmedSeat(riderUid, requestId) }.isSuccess)
            assertFalse(runCatching { rider.requestSeat(riderUid, journey.id) }.isSuccess)
            assertEquals(0L, driverGuard.get(Source.SERVER).await().getLong("acceptanceCount"))
            assertTrue(other.load(otherUid).confirmedTrips.isEmpty())
            other.requestSeat(otherUid, journey.id)
            assertEquals(
                "Shared Rider Label",
                driver.load(driverUid).requests.single { it.riderUid == otherUid }.riderDisplayName,
            )
            driver.decide(driverUid, "${journey.id}_$otherUid", true)
            assertEquals(1L, driverGuard.get(Source.SERVER).await().getLong("acceptanceCount"))
            assertEquals(0, driver.load(driverUid).journeys.single { it.id == journey.id }.seatsRemaining)
            assertEquals(trip, rider.load(riderUid).confirmedTrips.single())
            assertFalse(runCatching { guardRef.get(Source.SERVER).await() }.isSuccess)
            // Closing the offer preserves both the earlier rider withdrawal and
            // the other rider's confirmation as source history.
            val guardBeforeClosure = driverGuard.get(Source.SERVER).await().data
            assertFalse(runCatching { other.cancelJourney(otherUid, journey.id) }.isSuccess)
            driver.cancelJourney(driverUid, journey.id)
            val closed = driver.load(driverUid).journeys.single { it.id == journey.id }
            assertEquals(ConnectedJourneyStatus.CANCELLED, closed.status)
            assertTrue(closed.cancelledAtEpochMillis != null)
            assertEquals(0, closed.seatsRemaining)
            assertEquals(guardBeforeClosure, driverGuard.get(Source.SERVER).await().data)
            assertEquals(trip, rider.load(riderUid).confirmedTrips.single())
            assertEquals(ConnectedTripLifecycle.CANCELLED_BY_RIDER, ConnectedJourneyLifecycle.trip(trip, closed))
            val otherConfirmed = other.load(otherUid).confirmedTrips.single()
            assertEquals("Renamed Driver", otherConfirmed.driverDisplayName)
            assertEquals(ConnectedTripStatus.CONFIRMED, otherConfirmed.status)
            assertEquals(ConnectedTripLifecycle.CANCELLED_BY_DRIVER, ConnectedJourneyLifecycle.trip(otherConfirmed, closed))
            assertFalse(runCatching { other.cancelConfirmedSeat(otherUid, otherConfirmed.id) }.isSuccess)
            assertFalse(runCatching { driver.cancelJourney(driverUid, journey.id) }.isSuccess)
            assertEquals(guardBeforeClosure, driverGuard.get(Source.SERVER).await().data)

            driver.create(driverUid, ConnectedJourneyDraft("Mansfield", "Nottingham", System.currentTimeMillis() + 86_400_000, 2))
            val pendingJourney = driver.load(driverUid).journeys.single { it.driverUid == driverUid && it.status == ConnectedJourneyStatus.OPEN }
            rider.requestSeat(riderUid, pendingJourney.id)
            driver.cancelJourney(driverUid, pendingJourney.id)
            val pendingId = "${pendingJourney.id}_$riderUid"
            val pendingHistory = rider.load(riderUid).requests.single { it.id == pendingId }
            val pendingClosed = driver.load(driverUid).journeys.single { it.id == pendingJourney.id }
            assertEquals(ConnectedRequestStatus.PENDING, pendingHistory.status)
            assertTrue(ConnectedJourneyLifecycle.requestCancelledByDriver(pendingHistory, pendingClosed))
            assertFalse(runCatching { driver.decide(driverUid, pendingId, true) }.isSuccess)
            assertFalse(runCatching { driver.decide(driverUid, pendingId, false) }.isSuccess)
            assertFalse(runCatching { rider.cancelRequest(riderUid, pendingId) }.isSuccess)
            assertFalse(runCatching { other.requestSeat(otherUid, pendingJourney.id) }.isSuccess)
            assertEquals(2, pendingClosed.seatsRemaining)

            val departure = System.currentTimeMillis() + 3_000
            driver.create(driverUid, ConnectedJourneyDraft("Derby", "Leicester", departure, 1))
            val completing = driver.load(driverUid).journeys.single {
                it.driverUid == driverUid && it.status == ConnectedJourneyStatus.OPEN
            }
            rider.requestSeat(riderUid, completing.id)
            val completingRequestId = "${completing.id}_$riderUid"
            driver.decide(driverUid, completingRequestId, true)
            assertFalse(runCatching { rider.completeJourney(riderUid, completing.id) }.isSuccess)
            delay(3_500)
            driver.completeJourney(driverUid, completing.id)
            val completed = driver.load(driverUid).journeys.single { it.id == completing.id }
            assertEquals(ConnectedJourneyStatus.COMPLETED, completed.status)
            assertTrue(completed.completedAtEpochMillis != null)
            assertEquals(0, completed.seatsRemaining)
            val riderCompleted = rider.load(riderUid)
            val riderCompletedJourney = riderCompleted.journeys.single { it.id == completing.id }
            val riderCompletedTrip = riderCompleted.confirmedTrips.single { it.id == completingRequestId }
            assertEquals(ConnectedJourneyStatus.COMPLETED, riderCompletedJourney.status)
            assertEquals(
                ConnectedTripLifecycle.COMPLETED,
                ConnectedJourneyLifecycle.trip(riderCompletedTrip, riderCompletedJourney),
            )
            assertFalse(runCatching { driver.completeJourney(driverUid, completing.id) }.isSuccess)
        } finally {
            apps.forEach { it.delete() }
        }
    }
}
