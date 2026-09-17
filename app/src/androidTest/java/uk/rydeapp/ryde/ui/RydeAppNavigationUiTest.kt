package uk.rydeapp.ryde.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.rydeapp.ryde.data.AppMode
import uk.rydeapp.ryde.data.FakeRydeRepository
import uk.rydeapp.ryde.data.RydeRepository
import uk.rydeapp.ryde.data.connected.*
import uk.rydeapp.ryde.domain.model.FindRideCriteria
import uk.rydeapp.ryde.domain.model.OfferRideCriteria
import uk.rydeapp.ryde.domain.model.SavedPlace
import uk.rydeapp.ryde.ui.theme.RydeTheme

/** Uses the real connected repository with in-memory gateway doubles; no Firebase infrastructure. */
class RydeAppNavigationUiTest {
    @get:Rule val compose = createComposeRule()
    private val auth = TestAuth()
    private val store = TestJourneys()
    private var legacyCommands = 0
    private val legacy = object : RydeRepository by FakeRydeRepository() {
        override fun findRides(criteria: FindRideCriteria): Nothing = legacyCalled()
        override suspend fun createSeatRequest(matchId: String, criteria: FindRideCriteria): Nothing = legacyCalled()
        override suspend fun createOfferedJourney(criteria: OfferRideCriteria): Nothing = legacyCalled()
        private fun legacyCalled(): Nothing {
            legacyCommands++
            error("Connected UI invoked a legacy command")
        }
    }
    private val repository = ConnectedRydeRepository(auth, TestProfiles(), legacyCapabilities = legacy, journeys = store)

    private fun launchConnected() {
        compose.setContent { RydeTheme { RydeApp(repository, AppMode.CONNECTED) } }
    }

    @Test
    fun connectedLaunchShowsOrderedShellAndOnlyGenuineHomeData() {
        launchConnected()
        assertShell()
        compose.onNodeWithText("Hello, Taylor").assertIsDisplayed()
        compose.onNodeWithText("Your saved areas").assertIsDisplayed()
        compose.onNodeWithText("York").assertIsDisplayed()
        compose.onNodeWithText("Wakefield").assertIsDisplayed()
        compose.onNodeWithText("Sheffield → Leeds").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Seats remaining: 1/2").assertIsDisplayed()
        assertNoFictionalContent()
        compose.onAllNodesWithText("Ryde journey lab").assertCountEquals(0)
    }

    @Test
    fun homeRequestDelegatesCorrectIdToConnectedStoreAndPublishesRequestState() {
        launchConnected()
        compose.onNodeWithText("Request one seat").performScrollTo().performClick()
        compose.onNodeWithText("Your request is pending").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Request one seat").assertCountEquals(0)
        compose.runOnIdle {
            assertEquals(listOf("rider-private-uid" to "connected-offer"), store.requestCalls)
            assertEquals(0, legacyCommands)
            assertEquals(ConnectedRequestStatus.PENDING, repository.journeyState.value.requests.single().status)
        }
        compose.onNodeWithText("Manage requests").performClick()
        tab("Trips").assertIsSelected()
        compose.onNodeWithText("Your request is pending").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Ryde journey lab").assertCountEquals(0)
    }

    @Test
    fun transitionalTabsAndLabEntriesStayConnectedAndReturnToTheirTab() {
        launchConnected()
        tab("Find").performClick()
        compose.onNodeWithText("Find your next Ryde").assertIsDisplayed()
        compose.onNodeWithText("Sheffield → Leeds").performScrollTo().assertIsDisplayed()
        assertNoFictionalContent()
        tab("Home").performClick()
        tab("Home").assertIsSelected()
        tab("Offer").performClick()
        assertNoFictionalContent()
        compose.onNodeWithText("Offer in Journey Lab").performClick()
        compose.onNodeWithText("Ryde journey lab").assertIsDisplayed()
        compose.onNode(isSelectable() and hasText("Offer a journey")).assertIsSelected()
        compose.onNodeWithText("Create emulator offer").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Back to Ryde").performClick()
        tab("Offer").assertIsSelected()
        tab("Trips").performClick()
        assertNoFictionalContent()
        compose.onNodeWithText("No trips yet").assertIsDisplayed()
        compose.onNodeWithText("Refresh").assertIsEnabled()
        compose.onAllNodesWithText("Ryde journey lab").assertCountEquals(0)
        tab("Trips").assertIsSelected()
        tab("Profile").performClick()
        assertNoFictionalContent()
        compose.onNodeWithText("Journey Lab").performClick()
        compose.onNodeWithText("Ryde journey lab").assertIsDisplayed()
        compose.onAllNodes(isSelectable()).assertCountEquals(7)
        compose.onNodeWithText("Back to Ryde").performClick()
        tab("Profile").assertIsSelected()
        compose.runOnIdle { assertEquals(0, legacyCommands) }
    }

    @Test
    fun selectedTabRestoresForSameAccountButResetsWhenAccountChanges() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { RydeTheme { RydeApp(repository, AppMode.CONNECTED) } }
        tab("Find").performClick()
        restoration.emulateSavedInstanceStateRestore()
        tab("Find").assertIsSelected()
        tab("Offer").performClick()
        compose.onNodeWithText("Offer in Journey Lab").performClick()
        compose.runOnIdle {
            runBlocking {
                auth.uid = "second-private-uid"
                repository.refresh()
            }
        }
        tab("Home").assertIsSelected()
        compose.onNodeWithText("Hello, Morgan").assertIsDisplayed()
        compose.onAllNodesWithText("Hello, Taylor").assertCountEquals(0)
        compose.onAllNodesWithText("Ryde journey lab").assertCountEquals(0)
    }

    @Test
    fun labCannotBeDismissedWhileItsOfferCommandIsRunning() {
        launchConnected()
        store.createGate = CompletableDeferred()
        tab("Offer").performClick()
        compose.onNodeWithText("Offer in Journey Lab").performClick()
        compose.onNodeWithText("Origin broad area").performTextInput("Sheffield")
        compose.onNodeWithText("Destination broad area").performTextInput("Leeds")
        compose.onNodeWithText("Create emulator offer").performScrollTo().performClick()
        compose.onNodeWithText("Back to Ryde").assertIsNotEnabled()
        compose.runOnIdle { store.createGate!!.complete(Unit) }
        compose.onNodeWithText("Back to Ryde").assertIsEnabled().performClick()
        tab("Offer").assertIsSelected()
    }

    @Test
    fun requestSurvivesTabChangeAndRefreshFailureCanRecoverWithoutDuplicateRequest() {
        launchConnected()
        store.requestGate = CompletableDeferred()
        compose.onNodeWithText("Request one seat").performScrollTo().performClick()
        tab("Find").performClick()
        compose.runOnIdle {
            store.failNextLoad = true
            store.requestGate!!.complete(Unit)
        }
        compose.waitForIdle()
        tab("Home").performClick()
        compose.onNodeWithText(ConnectedRydeRepository.SAFE_JOURNEY_ERROR).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Request one seat").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Refresh").performScrollTo()
        compose.onNodeWithText("Refresh").performClick()
        compose.onNodeWithText("Your request is pending").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, store.requestCalls.size) }
    }

    @Test
    fun localDemoStillShowsItsExistingHomeAndFindWorkflow() {
        compose.setContent { RydeTheme { RydeApp() } }
        assertShell()
        compose.onNodeWithText("Hello, Sam").assertIsDisplayed()
        compose.onNodeWithText("Demo").assertIsDisplayed()
        compose.onNodeWithText("Suggested route match").performScrollTo().assertIsDisplayed()
        tab("Find").performClick()
        compose.onNodeWithText("Origin area").assertIsDisplayed()
        compose.onAllNodesWithText("Find your next Ryde").assertCountEquals(0)
    }

    @Test
    fun homeShortcutOpensGenuineFindWithSafeDetailsAndNoLab() {
        launchConnected()
        compose.onNodeWithText("Find a Ryde").performClick()
        tab("Find").assertIsSelected()
        compose.onNodeWithText("Find your next Ryde").assertIsDisplayed()
        compose.onNodeWithText("1 upcoming journey").assertIsDisplayed()
        compose.onNodeWithText("Sheffield → Leeds").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Seats remaining: 1/2").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2099", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Request one seat").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Only broad town or district areas are shown. No exact address or live location is shared here.")
            .performScrollTo().assertIsDisplayed()
        assertNoFictionalContent()
        compose.onAllNodesWithText("Ryde journey lab").assertCountEquals(0)
        compose.onAllNodesWithText("Manage requests").assertCountEquals(0)
        compose.runOnIdle { assertEquals(0, legacyCommands) }
    }

    @Test
    fun emptyFindRefreshesIntoGenuineAvailableState() {
        val offer = store.journeys.single()
        store.journeys = emptyList()
        launchConnected()
        tab("Find").performClick()
        compose.onNodeWithText("No journeys currently available").assertIsDisplayed()
        compose.onNodeWithText("0 upcoming journeys").assertIsDisplayed()
        compose.onAllNodesWithText("Request one seat").assertCountEquals(0)
        assertNoFictionalContent()
        compose.runOnIdle { store.journeys = listOf(offer) }
        compose.onNodeWithText("Refresh").performClick()
        compose.onNodeWithText("Sheffield → Leeds").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertTrue(store.loadCalls >= 2) }
    }

    @Test
    fun findShowsFullListAndSuppressesClosedDepartedOwnAndFullJourneyActions() {
        val offer = store.journeys.single()
        store.journeys = (1..4).map {
            offer.copy(id = "offer-$it", originArea = "Area ${('A'.code + it - 1).toChar()}", departureEpochMillis = offer.departureEpochMillis + it)
        } + listOf(
            offer.copy(id = "closed", originArea = "Closed area", status = ConnectedJourneyStatus.CANCELLED),
            offer.copy(id = "departed", originArea = "Departed area", departureEpochMillis = 1),
            offer.copy(id = "own", originArea = "Own area", driverUid = "rider-private-uid"),
            offer.copy(id = "full", originArea = "Full area", seatsRemaining = 0),
        )
        launchConnected()
        compose.onNode(hasScrollToIndexAction())
            .performScrollToNode(hasText("View all journeys in Find"))
        compose.onNodeWithText("View all journeys in Find").assertIsDisplayed().performClick()
        tab("Find").assertIsSelected()
        compose.onNodeWithText("5 upcoming journeys").assertIsDisplayed()
        scrollFindTo("Area D → Leeds")
        compose.onNodeWithText("Area D → Leeds").assertIsDisplayed()
        scrollFindTo("Full area → Leeds")
        compose.onNodeWithText("No seats available").performScrollTo().assertIsDisplayed()
        listOf("Closed area", "Departed area", "Own area").forEach {
            compose.onAllNodesWithText(it, substring = true).assertCountEquals(0)
        }
        assertNoFictionalContent()
    }

    @Test
    fun findRequestsExactlyOnceAndInFlightStateSurvivesTabChanges() {
        launchConnected()
        store.requestGate = CompletableDeferred()
        tab("Find").performClick()
        compose.onNodeWithText("Request one seat").performScrollTo().performClick()
        compose.onNodeWithText("Request one seat").assertIsNotEnabled()
        compose.onNodeWithText("Refresh").performScrollTo().assertIsNotEnabled()
        compose.onAllNodesWithText("Your request is pending").assertCountEquals(0)
        tab("Home").performClick()
        compose.onNodeWithText("Request one seat").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { store.requestGate!!.complete(Unit) }
        tab("Find").performClick()
        compose.onNodeWithText("Your request is pending").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Request one seat").assertCountEquals(0)
        compose.onAllNodesWithText("Ryde journey lab").assertCountEquals(0)
        compose.runOnIdle {
            assertEquals(listOf("rider-private-uid" to "connected-offer"), store.requestCalls)
            assertEquals(0, legacyCommands)
        }
    }

    @Test
    fun findRequestAndRefreshFailuresRecoverFromRepositoryTruth() {
        launchConnected()
        store.failNextRequest = true
        tab("Find").performClick()
        compose.onNodeWithText("Request one seat").performScrollTo().performClick()
        compose.onNodeWithText("Request one seat").assertIsNotEnabled()
        compose.onAllNodesWithText("Your request is pending").assertCountEquals(0)
        compose.onNodeWithText(ConnectedRydeRepository.SAFE_JOURNEY_ERROR).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { store.failNextLoad = true }
        compose.onNodeWithText("Refresh").performScrollTo().performClick()
        compose.onNodeWithText("Ryde could not refresh. Please try again.").assertIsDisplayed()
        compose.onNodeWithText("Request one seat").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Refresh").performScrollTo().performClick()
        compose.onNodeWithText("Request one seat").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("Your request is pending").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Request one seat").assertCountEquals(0)
        compose.runOnIdle {
            assertEquals(2, store.requestCalls.size)
            assertEquals(1, repository.journeyState.value.requests.size)
            assertEquals(0, legacyCommands)
        }
    }

    @Test
    fun findOffersEligibleRerequestAndPreservesEveryTerminalRequestState() {
        val offer = store.journeys.single()
        store.requests += ConnectedSeatRequest("connected-offer_rider-private-uid", offer.id, offer.driverUid,
            "rider-private-uid", ConnectedRequestStatus.CANCELLED)
        launchConnected()
        tab("Find").performClick()
        compose.onNodeWithText("Your request was cancelled").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Request one seat").performScrollTo().performClick()
        compose.onNodeWithText("Your request is pending").performScrollTo().assertIsDisplayed()
        val terminalLabels = mapOf(
            ConnectedRequestStatus.ACCEPTED to "Your seat is confirmed",
            ConnectedRequestStatus.DECLINED to "Your request was declined",
            ConnectedRequestStatus.CANCELLED_AFTER_ACCEPTANCE to "Your confirmed seat was cancelled",
        )
        terminalLabels.forEach { (status, label) ->
            compose.runOnIdle { store.requests[0] = store.requests[0].copy(status = status) }
            compose.onNodeWithText("Refresh").performScrollTo().performClick()
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
            compose.onAllNodesWithText("Request one seat").assertCountEquals(0)
            assertNoFictionalContent()
        }
        compose.runOnIdle {
            assertEquals(1, store.requestCalls.size)
            assertEquals(0, legacyCommands)
        }
    }

    private fun scrollFindTo(text: String) {
        compose.onNodeWithTag("connected-find-list").performScrollToNode(hasText(text))
    }

    @Test
    fun tripsShowsPendingThenConfirmedWithGenuineFieldsAndPreservesFindStatus() {
        launchConnected()
        tab("Find").performClick()
        compose.onNodeWithText("Request one seat").performScrollTo().performClick()
        tab("Trips").performClick()
        compose.onNodeWithText("Your request is pending").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sheffield → Leeds").assertIsDisplayed()
        compose.onNodeWithText("2099", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Rider").assertIsDisplayed()
        compose.onAllNodesWithText("Cancel my seat").assertCountEquals(0)
        assertNoFictionalContent()
        compose.runOnIdle { store.confirmSeat() }
        compose.onNodeWithText("Refresh").performScrollTo().performClick()
        compose.onNodeWithText("Your seat is confirmed").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Your request is pending").assertCountEquals(0)
        compose.onNodeWithText("Cancel my seat").assertIsEnabled()
        assertNoFictionalContent()
        tab("Find").performClick()
        compose.onNodeWithText("Your seat is confirmed").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, legacyCommands) }
    }

    @Test
    fun tripsCancellationIsSingleInFlightCommandAcrossTabsAndRetainsRealHistory() {
        store.confirmSeat()
        store.cancelGate = CompletableDeferred()
        launchConnected()
        tab("Trips").performClick()
        compose.onNodeWithText("Cancel my seat").performScrollTo().performClick()
        compose.onNodeWithText("Keep my seat").performClick()
        compose.runOnIdle { assertTrue(store.cancelCalls.isEmpty()) }
        compose.onNodeWithText("Cancel my seat").performClick()
        compose.onNodeWithText("Confirm cancellation").performClick()
        compose.onNodeWithText("Cancel my seat").assertIsNotEnabled().performClick()
        compose.onNodeWithText("Refresh").performScrollTo().assertIsNotEnabled()
        tab("Find").performClick()
        tab("Trips").performClick()
        compose.onNodeWithText("Cancel my seat").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { store.cancelGate!!.complete(Unit) }
        compose.onNodeWithText("Your seat was cancelled").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Cancel my seat").assertCountEquals(0)
        compose.onNodeWithText("Sheffield → Leeds").assertIsDisplayed()
        assertNoFictionalContent()
        compose.runOnIdle {
            assertEquals(listOf("rider-private-uid" to "connected-offer_rider-private-uid"), store.cancelCalls)
            assertEquals(ConnectedTripStatus.CANCELLED_BY_RIDER, repository.journeyState.value.confirmedTrips.single().status)
            assertEquals(0, legacyCommands)
        }
    }

    @Test
    fun tripsCancellationAndRefreshFailuresRecoverBeforeRetry() {
        store.confirmSeat()
        store.failNextCancel = true
        launchConnected()
        tab("Trips").performClick()
        compose.onNodeWithText("Cancel my seat").performScrollTo().performClick()
        compose.onNodeWithText("Confirm cancellation").performClick()
        compose.onNodeWithText("Cancel my seat").assertIsNotEnabled()
        compose.onNodeWithText(ConnectedRydeRepository.SAFE_JOURNEY_ERROR).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { store.failNextLoad = true }
        compose.onNodeWithText("Refresh").performClick()
        compose.onNodeWithText("Ryde could not refresh. Please try again.").assertIsDisplayed()
        compose.onNodeWithText("Cancel my seat").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Refresh").performScrollTo().performClick()
        compose.onNodeWithText("Cancel my seat").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("Confirm cancellation").performClick()
        compose.onNodeWithText("Your seat was cancelled").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, store.cancelCalls.size) }
    }

    @Test
    fun tripsRefreshAfterCommittedCancellationFailurePreventsSecondWrite() {
        store.confirmSeat()
        launchConnected()
        tab("Trips").performClick()
        compose.runOnIdle { store.failNextLoad = true }
        compose.onNodeWithText("Cancel my seat").performScrollTo().performClick()
        compose.onNodeWithText("Confirm cancellation").performClick()
        compose.onNodeWithText("Cancel my seat").assertIsNotEnabled()
        compose.onNodeWithText("Refresh").performScrollTo().performClick()
        compose.onNodeWithText("Your seat was cancelled").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Cancel my seat").assertCountEquals(0)
        compose.runOnIdle { assertEquals(1, store.cancelCalls.size) }
    }

    // Match the selectable tab, not the icon child hidden/replaced by Material navigation semantics.
    private fun tab(label: String) = compose.onNode(isSelectable() and hasText(label))

    private fun assertShell() {
        val labels = listOf("Home", "Find", "Offer", "Trips", "Profile")
        compose.onAllNodes(isSelectable()).assertCountEquals(5)
        val positions = labels.map { label ->
            tab(label).assertIsDisplayed().fetchSemanticsNode().boundsInRoot.left
        }
        assertTrue(positions.zipWithNext().all { (left, right) -> left < right })
        labels.forEach { label -> tab(label).assert(hasText(label)) }
    }

    private fun assertNoFictionalContent() {
        listOf(
            "Alex", "Suggested route match", "Nottingham Live", "Demo", "★", "Demo verified", "£", "Circle",
            "driver-private-uid", "rider-private-uid", "second-private-uid",
        ).forEach { compose.onAllNodesWithText(it, substring = true).assertCountEquals(0) }
    }

    private class TestAuth : ConnectedAuthGateway {
        var uid: String? = "rider-private-uid"
        override val currentUserId: String? get() = uid
        override suspend fun register(email: String, password: String) = checkNotNull(uid)
        override suspend fun signIn(email: String, password: String) = checkNotNull(uid)
        override suspend fun signOut() { uid = null }
    }

    private class TestProfiles : ConnectedProfileStore {
        override suspend fun create(profile: ConnectedUserProfile) = Unit
        override suspend fun load(uid: String) = ConnectedProfile(
            ConnectedUserProfile(uid, if (uid == "rider-private-uid") "Taylor" else "Morgan"),
            listOf(SavedPlace("Home", "York"), SavedPlace("Work", "Wakefield")),
        )
        override suspend fun save(uid: String, draft: ConnectedProfileDraft) = Unit
    }

    private class TestJourneys : ConnectedJourneyStore {
        val cancelCalls = mutableListOf<Pair<String, String>>()
        var cancelGate: CompletableDeferred<Unit>? = null
        var failNextCancel = false
        val trips = mutableListOf<ConnectedConfirmedTrip>()
        fun confirmSeat() {
            val journey = journeys.single()
            val id = "${journey.id}_rider-private-uid"
            requests.clear()
            requests += ConnectedSeatRequest(id, journey.id, journey.driverUid, "rider-private-uid", ConnectedRequestStatus.ACCEPTED)
            trips += ConnectedConfirmedTrip(id, journey.id, id, journey.driverUid, "rider-private-uid",
                journey.originArea, journey.destinationArea, journey.departureEpochMillis, ConnectedTripStatus.CONFIRMED)
        }
        val requestCalls = mutableListOf<Pair<String, String>>()
        var requestGate: CompletableDeferred<Unit>? = null
        var createGate: CompletableDeferred<Unit>? = null
        var failNextLoad = false
        var failNextRequest = false
        var loadCalls = 0
        var journeys = listOf(ConnectedJourney("connected-offer", "driver-private-uid", "Sheffield", "Leeds", 4_070_908_800_000L, 2, 1))
        val requests = mutableListOf<ConnectedSeatRequest>()
        override suspend fun load(uid: String): ConnectedJourneySnapshot {
            loadCalls++
            if (failNextLoad) {
                failNextLoad = false
                error("Load failed after commit")
            }
            return ConnectedJourneySnapshot(journeys, requests.filter { it.riderUid == uid }, trips.toList())
        }
        override suspend fun create(uid: String, draft: ConnectedJourneyDraft) { createGate?.await() }
        override suspend fun requestSeat(uid: String, journeyId: String) {
            requestCalls += uid to journeyId
            requestGate?.await()
            if (failNextRequest) {
                failNextRequest = false
                error("Request failed before commit")
            }
            val journey = journeys.single { it.id == journeyId }
            requests.removeAll { it.journeyId == journeyId && it.riderUid == uid }
            requests += ConnectedSeatRequest("${journeyId}_$uid", journeyId, journey.driverUid, uid, ConnectedRequestStatus.PENDING)
        }
        override suspend fun cancelRequest(uid: String, requestId: String) = Unit
        override suspend fun cancelConfirmedSeat(uid: String, tripId: String) {
            cancelCalls += uid to tripId
            cancelGate?.await()
            if (failNextCancel) {
                failNextCancel = false
                error("Cancellation failed")
            }
            val index = trips.indexOfFirst { it.id == tripId }
            trips[index] = trips[index].copy(status = ConnectedTripStatus.CANCELLED_BY_RIDER, cancelledAtEpochMillis = 1)
            val requestIndex = requests.indexOfFirst { it.id == trips[index].acceptedRequestId }
            requests[requestIndex] = requests[requestIndex].copy(status = ConnectedRequestStatus.CANCELLED_AFTER_ACCEPTANCE)
        }
        override suspend fun cancelJourney(uid: String, journeyId: String) = Unit
        override suspend fun decide(uid: String, requestId: String, accept: Boolean) = Unit
    }
}
