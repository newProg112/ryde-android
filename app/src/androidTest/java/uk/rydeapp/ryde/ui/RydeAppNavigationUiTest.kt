package uk.rydeapp.ryde.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.espresso.Espresso
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
    fun normalTabsAndProfileLabEntryStayConnectedAndReturnToTheirTab() {
        launchConnected()
        tab("Find").performClick()
        compose.onNodeWithText("Find your next Ryde").assertIsDisplayed()
        scrollFindTo("Sheffield → Leeds")
        compose.onNodeWithText("Sheffield → Leeds").performScrollTo().assertIsDisplayed()
        assertNoFictionalContent()
        tab("Home").performClick()
        tab("Home").assertIsSelected()
        tab("Offer").performClick()
        assertNoFictionalContent()
        compose.onNodeWithText("Offer journey").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Ryde journey lab").assertCountEquals(0)
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
        compose.onNodeWithText("From town or district").performTextInput("York")
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
        tab("Profile").performClick()
        compose.onNodeWithText("Journey Lab").performClick()
        compose.onNode(isSelectable() and hasText("Offer a journey")).performClick()
        compose.onNodeWithText("Origin broad area").performTextInput("Sheffield")
        compose.onNodeWithText("Destination broad area").performTextInput("Leeds")
        compose.onNodeWithText("Create emulator offer").performScrollTo().performClick()
        compose.onNodeWithText("Back to Ryde").assertIsNotEnabled()
        compose.runOnIdle { store.createGate!!.complete(Unit) }
        compose.onNodeWithText("Back to Ryde").assertIsEnabled().performClick()
        tab("Profile").assertIsSelected()
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
        findText("Find your next Ryde").assertIsDisplayed()
        scrollFindTo("1 upcoming journey")
        findText("1 upcoming journey").performScrollTo().assertIsDisplayed()
        scrollFindTo("Sheffield → Leeds")
        findText("Sheffield → Leeds").performScrollTo().assertIsDisplayed()
        findText("Seats remaining: 1/2").performScrollTo().assertIsDisplayed()
        findText("2099", substring = true).performScrollTo().assertIsDisplayed()
        findText("Request one seat").performScrollTo().assertIsEnabled()
        findText("Only broad town or district areas are shown. No exact address or live location is shared here.")
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
        scrollFindTo("No journeys currently available")
        findText("No journeys currently available").assertIsDisplayed()
        scrollFindTo("0 upcoming journeys")
        findText("0 upcoming journeys").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Request one seat").assertCountEquals(0)
        assertNoFictionalContent()
        compose.runOnIdle { store.journeys = listOf(offer) }
        findText("Refresh").performClick()
        scrollFindTo("Sheffield → Leeds")
        findText("Sheffield → Leeds").performScrollTo().assertIsDisplayed()
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
        scrollFindTo("5 upcoming journeys")
        findText("5 upcoming journeys").performScrollTo().assertIsDisplayed()
        scrollFindTo("Area D → Leeds")
        findText("Area D → Leeds").assertIsDisplayed()
        scrollFindTo("Full area → Leeds")
        findText("No seats available").performScrollTo().assertIsDisplayed()
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
        scrollFindTo("Request one seat")
        findText("Request one seat").performScrollTo().performClick()
        findText("Request one seat").assertIsNotEnabled()
        findText("Refresh").performScrollTo().assertIsNotEnabled()
        compose.onAllNodesWithText("Your request is pending").assertCountEquals(0)
        tab("Home").performClick()
        compose.onNodeWithText("Request one seat").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { store.requestGate!!.complete(Unit) }
        tab("Find").performClick()
        findText("Your request is pending").performScrollTo().assertIsDisplayed()
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
        scrollFindTo("Request one seat")
        findText("Request one seat").performScrollTo().performClick()
        findText("Request one seat").assertIsNotEnabled()
        compose.onAllNodesWithText("Your request is pending").assertCountEquals(0)
        findText(ConnectedRydeRepository.SAFE_JOURNEY_ERROR).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { store.failNextLoad = true }
        findText("Refresh").performScrollTo().performClick()
        findText("Ryde could not refresh. Please try again.").assertIsDisplayed()
        findText("Request one seat").performScrollTo().assertIsNotEnabled()
        findText("Refresh").performScrollTo().performClick()
        findText("Request one seat").performScrollTo().assertIsEnabled().performClick()
        findText("Your request is pending").performScrollTo().assertIsDisplayed()
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
        scrollFindTo("Your request was cancelled")
        findText("Your request was cancelled").performScrollTo().assertIsDisplayed()
        findText("Request one seat").performScrollTo().performClick()
        findText("Your request is pending").performScrollTo().assertIsDisplayed()
        val terminalLabels = mapOf(
            ConnectedRequestStatus.ACCEPTED to "Your seat is confirmed",
            ConnectedRequestStatus.DECLINED to "Your request was declined",
            ConnectedRequestStatus.CANCELLED_AFTER_ACCEPTANCE to "Your confirmed seat was cancelled",
        )
        terminalLabels.forEach { (status, label) ->
            compose.runOnIdle { store.requests[0] = store.requests[0].copy(status = status) }
            findText("Refresh").performScrollTo().performClick()
            scrollFindTo(label)
            findText(label).performScrollTo().assertIsDisplayed()
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

    private fun findText(text: String, substring: Boolean = false): SemanticsNodeInteraction {
        compose.onNodeWithTag("connected-find-list").performScrollToNode(hasText(text, substring = substring))
        return compose.onNodeWithText(text, substring = substring)
    }

    private fun findField(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("connected-find-list").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }

    @Test
    fun filteredRequestUsesCorrectJourneyOnceAndHomeStaysUnfiltered() {
        val original = store.journeys.single()
        store.journeys = listOf(original, original.copy(id = "filtered-offer", originArea = "York", destinationArea = "Wakefield",
            departureEpochMillis = original.departureEpochMillis + 60_000))
        launchConnected()
        tab("Find").performClick()
        findField("connected-find-origin").performTextInput("  YORK  ")
        findField("connected-find-destination").performScrollTo().performTextInput("wake")
        scrollFindTo("1 matching journey")
        compose.onNodeWithText("1 matching journey").assertIsDisplayed()
        scrollFindTo("Refresh")
        compose.onNodeWithText("Refresh").performClick()
        findField("connected-find-origin").performScrollTo().assert(hasText("  YORK  "))
        findField("connected-find-destination").performScrollTo().assert(hasText("wake"))
        store.requestGate = CompletableDeferred()
        scrollFindTo("Request one seat")
        compose.onNodeWithText("Request one seat").performClick().assertIsNotEnabled()
        tab("Home").performClick()
        compose.onNodeWithText("Sheffield → Leeds").performScrollTo().assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("York → Wakefield"))
        compose.onNodeWithText("York → Wakefield").assertIsDisplayed()
        compose.runOnIdle { store.requestGate!!.complete(Unit) }
        tab("Find").performClick()
        findField("connected-find-origin").performScrollTo().assert(hasText("  YORK  "))
        findField("connected-find-destination").performScrollTo().assert(hasText("wake"))
        scrollFindTo("Your request is pending")
        compose.onNodeWithText("Your request is pending").assertIsDisplayed()
        scrollFindTo("Manage requests")
        compose.onNodeWithText("Manage requests").performClick()
        tab("Trips").assertIsSelected()
        compose.onNodeWithText("Your request is pending").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("York → Wakefield").assertIsDisplayed()
        compose.onAllNodesWithText("Ryde journey lab").assertCountEquals(0)
        compose.runOnIdle {
            assertEquals(listOf("rider-private-uid" to "filtered-offer"), store.requestCalls)
            assertEquals(0, legacyCommands)
        }
    }

    @Test
    fun findFiltersRestoreForSameAccountAndResetWhenAccountChanges() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { RydeTheme { RydeApp(repository, AppMode.CONNECTED) } }
        tab("Find").performClick()
        findField("connected-find-origin").performTextInput("sheff")
        findField("connected-find-destination").performScrollTo().performTextInput("leed")
        scrollFindTo("Choose date")
        compose.onNodeWithText("Choose date").performClick()
        compose.onNodeWithText("OK").performClick()
        val selectedDate = findField("connected-find-date").performScrollTo().fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.Text].single().text
        tab("Home").performClick()
        tab("Find").performClick()
        findField("connected-find-date").performScrollTo().assert(hasText(selectedDate))
        restoration.emulateSavedInstanceStateRestore()
        tab("Find").assertIsSelected()
        findField("connected-find-origin").performScrollTo().assert(hasText("sheff"))
        findField("connected-find-destination").performScrollTo().assert(hasText("leed"))
        findField("connected-find-date").performScrollTo().assert(hasText(selectedDate))
        scrollFindTo("Refresh")
        compose.onNodeWithText("Refresh").performClick()
        findField("connected-find-date").performScrollTo().assert(hasText(selectedDate))
        compose.runOnIdle { runBlocking { auth.uid = "second-private-uid"; repository.refresh() } }
        tab("Home").assertIsSelected()
        tab("Find").performClick()
        findField("connected-find-origin").assert(hasText(""))
        findField("connected-find-destination").performScrollTo().assert(hasText(""))
        findField("connected-find-date").performScrollTo().assert(hasText("Any date"))
        scrollFindTo("1 upcoming journey")
        compose.onNodeWithText("1 upcoming journey").assertIsDisplayed()
    }

    @Test
    fun tripsShowsPendingThenConfirmedWithGenuineFieldsAndPreservesFindStatus() {
        launchConnected()
        tab("Find").performClick()
        scrollFindTo("Request one seat")
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
    @Test
    fun normalOfferValidatesPrivacyAndCreatesThroughConnectedStoreOnceAcrossTabs() {
        auth.uid = "driver-private-uid"
        launchConnected()
        tab("Offer").performClick()
        compose.onNodeWithText("From town or district").performTextInput("12 Street")
        compose.onNodeWithText("To town or district").performTextInput("Leeds")
        compose.onNodeWithText("Offer journey").performScrollTo().performClick()
        compose.onNodeWithText("Use broad town or district names only, without digits or commas.").assertIsDisplayed()
        compose.runOnIdle { assertTrue(store.createCalls.isEmpty()); store.createGate = CompletableDeferred() }
        compose.onNodeWithText("From town or district").performScrollTo().performTextReplacement("York")
        compose.onNodeWithText("Offer journey").performScrollTo().performClick()
        compose.onNodeWithText("Offer journey").assertIsNotEnabled().performClick()
        tab("Find").performClick()
        tab("Offer").performClick()
        compose.onNodeWithText("Offer journey").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { store.createGate!!.complete(Unit) }
        tab("Trips").assertIsSelected()
        compose.onNodeWithText("Journey offered. Manage your requests here.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Seats remaining: 1/1").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, store.createCalls.size)
            val (uid, draft) = store.createCalls.single()
            assertEquals("driver-private-uid", uid)
            assertEquals("York", draft.originArea)
            assertEquals("Leeds", draft.destinationArea)
            assertEquals(1, draft.seats)
            assertTrue(draft.departureEpochMillis > System.currentTimeMillis())
            assertEquals(0, legacyCommands)
        }
        tab("Offer").performClick()
        compose.onNodeWithText("From town or district").assert(hasText("York").not())
        assertNoFictionalContent()
    }

    private fun pendingForDriver() {
        auth.uid = "driver-private-uid"
        val journey = store.journeys.single()
        store.requests += ConnectedSeatRequest("${journey.id}_rider-private-uid", journey.id,
            journey.driverUid, "rider-private-uid", ConnectedRequestStatus.PENDING, "Taylor")
    }

    @Test
    fun driverAcceptsOneGenuinePendingRequestAndSeesConfirmedState() {
        pendingForDriver()
        store.decisionGate = CompletableDeferred()
        launchConnected()
        tab("Trips").performClick()
        compose.onNodeWithText("Pending request for one seat").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Rider: Taylor").assertIsDisplayed()
        compose.onNodeWithText("Accept").performScrollTo().performClick()
        compose.onNodeWithText("Decline").assertIsNotEnabled().performClick()
        tab("Offer").performClick()
        compose.onNodeWithText("Offer journey").performScrollTo().assertIsNotEnabled()
        tab("Trips").performClick()
        compose.onNodeWithText("Accept").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { store.decisionGate!!.complete(Unit) }
        compose.onNodeWithText("Accepted — rider seat confirmed").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Seats remaining: 0/2").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Accept").assertCountEquals(0)
        compose.onAllNodesWithText("Decline").assertCountEquals(0)
        compose.runOnIdle {
            assertEquals(listOf(Triple("driver-private-uid", "connected-offer_rider-private-uid", true)), store.decisionCalls)
            assertEquals(1, repository.journeyState.value.confirmedTrips.size)
            assertEquals(0, legacyCommands)
        }
        assertNoFictionalContent()
    }

    @Test
    fun driverCanDeclineFullJourneyWithoutAcceptanceOrCapacityChange() {
        pendingForDriver()
        store.journeys = store.journeys.map { it.copy(seatsRemaining = 0) }
        launchConnected()
        tab("Trips").performClick()
        compose.onNodeWithText("Journey full").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Accept").assertCountEquals(0)
        compose.onNodeWithText("Decline").performScrollTo().performClick()
        compose.onNodeWithText("Request declined").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Decline").assertCountEquals(0)
        compose.runOnIdle {
            assertEquals(listOf(Triple("driver-private-uid", "connected-offer_rider-private-uid", false)), store.decisionCalls)
            assertEquals(0, repository.journeyState.value.journeys.single().seatsRemaining)
            assertTrue(repository.journeyState.value.confirmedTrips.isEmpty())
        }
    }

    @Test
    fun committedDecisionRefreshFailureBlocksWritesUntilRefresh() {
        pendingForDriver()
        launchConnected()
        tab("Trips").performClick()
        compose.runOnIdle { store.failNextLoad = true }
        compose.onNodeWithText("Accept").performScrollTo().performClick()
        compose.onNodeWithText("Accept").assertIsNotEnabled()
        compose.onNodeWithText("Decline").assertIsNotEnabled()
        compose.onNodeWithText("Cancel journey").assertIsNotEnabled()
        compose.onNodeWithText("Refresh").performScrollTo().performClick()
        compose.onNodeWithText("Accepted — rider seat confirmed").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Accept").assertCountEquals(0)
        compose.runOnIdle { assertEquals(1, store.decisionCalls.size) }
    }

    @Test
    fun committedOfferRefreshFailureRecoversWithoutSecondCreation() {
        auth.uid = "driver-private-uid"
        launchConnected()
        tab("Offer").performClick()
        compose.onNodeWithText("From town or district").performTextInput("York")
        compose.onNodeWithText("To town or district").performTextInput("Leeds")
        compose.runOnIdle { store.failNextLoad = true }
        compose.onNodeWithText("Offer journey").performScrollTo().performClick()
        compose.onNodeWithText("Offer journey").assertIsNotEnabled()
        compose.onNodeWithText("Refresh").performScrollTo().performClick()
        compose.onNodeWithText("Manage offers in Trips").performScrollTo().performClick()
        compose.onNodeWithText("Seats remaining: 1/1").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, store.createCalls.size) }
    }

    @Test
    fun driverCancellationRequiresConfirmationAndClosesLinkedRequests() {
        pendingForDriver()
        launchConnected()
        tab("Trips").performClick()
        compose.onNodeWithText("Cancel journey").performScrollTo().performClick()
        compose.onNodeWithText("Keep journey").performClick()
        compose.runOnIdle { assertTrue(store.journeyCancelCalls.isEmpty()) }
        compose.onNodeWithText("Cancel journey").performClick()
        compose.runOnIdle { store.journeyCancelGate = CompletableDeferred(); store.failNextLoad = true }
        compose.onNodeWithText("Confirm journey cancellation").performClick()
        compose.onNodeWithText("Cancel journey").assertIsNotEnabled()
        compose.runOnIdle { store.journeyCancelGate!!.complete(Unit) }
        compose.onNodeWithText("Cancel journey").assertIsNotEnabled()
        compose.onNodeWithText("Refresh").performScrollTo().performClick()
        compose.onAllNodesWithText("Cancel journey").assertCountEquals(0)
        compose.onAllNodesWithText("Accept").assertCountEquals(0)
        compose.onAllNodesWithText("Decline").assertCountEquals(0)
        compose.onAllNodesWithText("Journey cancelled").assertCountEquals(2)
        compose.runOnIdle { assertEquals(listOf("driver-private-uid" to "connected-offer"), store.journeyCancelCalls) }
    }

    private fun tab(label: String) = compose.onNode(isSelectable() and hasText(label))

    private fun detailsText(label: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("connected-trip-details-list").performScrollToNode(hasText(label))
        return compose.onNodeWithText(label)
    }

    private fun openDetails() {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("View trip details"))
        compose.onNodeWithText("View trip details").performClick()
        compose.onNodeWithText("Trip details").assertIsDisplayed()
    }

    @Test
    fun homeDetailsKeepFiveTabsToolbarAndSystemBackReturnToHomeAndTabSelectionClosesDetails() {
        launchConnected()
        openDetails()
        assertShell()
        tab("Home").assertIsSelected()
        detailsText("Request one seat").assertIsEnabled()
        detailsText("Back").performClick()
        tab("Home").assertIsSelected()
        compose.onAllNodesWithText("Trip details").assertCountEquals(0)
        openDetails()
        Espresso.pressBack()
        tab("Home").assertIsSelected()
        compose.onAllNodesWithText("Trip details").assertCountEquals(0)
        openDetails()
        tab("Offer").performClick()
        tab("Offer").assertIsSelected()
        compose.onAllNodesWithText("Trip details").assertCountEquals(0)
        compose.onNodeWithText("From town or district").assertIsDisplayed()
    }

    @Test
    fun filteredFindDetailsPreserveOriginatingScrollAndFiltersAndRequestCorrectJourneyOnce() {
        val original = store.journeys.single()
        store.journeys = listOf(original, original.copy(id = "selected-offer", originArea = "York", destinationArea = "Wakefield"))
        launchConnected()
        tab("Find").performClick()
        findField("connected-find-origin").performTextInput("York")
        findField("connected-find-destination").performTextInput("wake")
        scrollFindTo("View trip details")
        val scrollBefore = compose.onNodeWithTag("connected-find-list").fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
        compose.onNodeWithText("View trip details").performClick()
        tab("Find").assertIsSelected()
        detailsText("Back").performClick()
        compose.onNodeWithText("View trip details").assertIsDisplayed()
        val scrollAfter = compose.onNodeWithTag("connected-find-list").fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertEquals(scrollBefore, scrollAfter, 0.01f)
        compose.onNodeWithText("View trip details").performClick()
        store.requestGate = CompletableDeferred()
        detailsText("Request one seat").performClick().assertIsNotEnabled().performClick()
        compose.runOnIdle { store.requestGate!!.complete(Unit) }
        detailsText("Your request is pending").assertIsDisplayed()
        detailsText("Refresh").performClick()
        detailsText("Back").performClick()
        findField("connected-find-origin").assert(hasText("York"))
        findField("connected-find-destination").assert(hasText("wake"))
        tab("Home").performClick()
        compose.onNodeWithText("Sheffield → Leeds").performScrollTo().assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("York → Wakefield"))
        compose.onNodeWithText("York → Wakefield").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf("rider-private-uid" to "selected-offer"), store.requestCalls)
            assertEquals(0, legacyCommands)
        }
    }

    @Test
    fun detailsRestoreForSameAccountButAccountChangeClearsSelectionAndFindFilters() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { RydeTheme { RydeApp(repository, AppMode.CONNECTED) } }
        tab("Find").performClick()
        findField("connected-find-origin").performTextInput("Sheffield")
        openDetails()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Trip details").assertIsDisplayed()
        tab("Find").assertIsSelected()
        detailsText("Back").performClick()
        findField("connected-find-origin").assert(hasText("Sheffield"))
        openDetails()
        compose.runOnIdle { runBlocking { auth.uid = "second-private-uid"; repository.refresh() } }
        tab("Home").assertIsSelected()
        compose.onAllNodesWithText("Trip details").assertCountEquals(0)
        tab("Find").performClick()
        findField("connected-find-origin").assert(hasText("Sheffield").not())
    }

    @Test
    fun signOutClearsDetailsAndSigningBackIntoSameAccountStartsAtHome() {
        launchConnected()
        tab("Find").performClick()
        openDetails()
        compose.runOnIdle { runBlocking { repository.signOut() } }
        compose.onAllNodesWithText("Trip details").assertCountEquals(0)
        compose.onAllNodes(isSelectable()).assertCountEquals(0)
        compose.runOnIdle { runBlocking { auth.uid = "rider-private-uid"; repository.refresh() } }
        tab("Home").assertIsSelected()
        compose.onAllNodesWithText("Trip details").assertCountEquals(0)
    }

    @Test
    fun riderTripsDetailsCancelByTripIdRetainDestinationAndReturnToRealHistory() {
        store.confirmSeat()
        launchConnected()
        tab("Trips").performClick()
        openDetails()
        detailsText("Your seat is confirmed").assertIsDisplayed()
        detailsText("Cancel my seat").performClick()
        compose.onNodeWithText("Keep my seat").performClick()
        compose.runOnIdle { assertTrue(store.cancelCalls.isEmpty()) }
        detailsText("Cancel my seat").performClick()
        compose.onNodeWithText("Confirm cancellation").performClick()
        detailsText("Your seat was cancelled").assertIsDisplayed()
        compose.onAllNodesWithText("Cancel my seat").assertCountEquals(0)
        detailsText("Back").performClick()
        tab("Trips").assertIsSelected()
        compose.onNodeWithText("Your seat was cancelled").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf("rider-private-uid" to "connected-offer_rider-private-uid"), store.cancelCalls)
            assertEquals(0, legacyCommands)
        }
    }

    @Test
    fun driverTripsDetailsReuseDecisionsAndCancellationAndStayOpenAfterMutations() {
        pendingForDriver()
        val first = store.requests.single()
        val second = first.copy(id = "second-request-id", riderUid = "second-private-uid")
        store.requests += second
        launchConnected()
        tab("Trips").performClick()
        openDetails()
        compose.onAllNodesWithText("Rider: Taylor").assertCountEquals(2)
        val list = compose.onNodeWithTag("connected-trip-details-list")
        list.performScrollToNode(hasTestTag("details-incoming:${first.id}"))
        compose.onNode(hasText("Accept") and hasAnyAncestor(hasTestTag("details-incoming:${first.id}"))).performClick()
        list.performScrollToNode(hasTestTag("details-incoming:${second.id}"))
        compose.onNode(hasText("Decline") and hasAnyAncestor(hasTestTag("details-incoming:${second.id}"))).performClick()
        detailsText("Cancel journey").performClick()
        compose.onNodeWithText("Keep journey").performClick()
        compose.runOnIdle { assertTrue(store.journeyCancelCalls.isEmpty()) }
        detailsText("Cancel journey").performClick()
        compose.onNodeWithText("Confirm journey cancellation").performClick()
        compose.onNodeWithText("Trip details").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Accept").assertCountEquals(0)
        compose.onAllNodesWithText("Decline").assertCountEquals(0)
        compose.onAllNodesWithText("Cancel journey").assertCountEquals(0)
        detailsText("Back").performClick()
        tab("Trips").assertIsSelected()
        compose.runOnIdle {
            assertEquals(listOf(Triple("driver-private-uid", first.id, true), Triple("driver-private-uid", second.id, false)), store.decisionCalls)
            assertEquals(listOf("driver-private-uid" to "connected-offer"), store.journeyCancelCalls)
            assertEquals(0, legacyCommands)
        }
    }

    @Test
    fun detailsRefreshFailsClosedWhenSelectedJourneyDisappearsAndLabRemainsIndependent() {
        launchConnected()
        openDetails()
        compose.runOnIdle { store.journeys = emptyList() }
        detailsText("Refresh").performClick()
        detailsText("Journey details unavailable").assertIsDisplayed()
        compose.onAllNodesWithText("Request one seat").assertCountEquals(0)
        compose.onNodeWithText("Trip details").assertIsDisplayed()
        tab("Profile").performClick()
        compose.onNodeWithText("Journey Lab").performClick()
        compose.onNodeWithText("Ryde journey lab").assertIsDisplayed()
        compose.onAllNodesWithText("Trip details").assertCountEquals(0)
        compose.onNodeWithText("Back to Ryde").performClick()
        tab("Profile").assertIsSelected()
        compose.onAllNodesWithText("Trip details").assertCountEquals(0)
    }

    @Test
    fun pendingRequestWithdrawsFromTripsOnceAcrossTabsAndKeepsHistory() {
        val offer = store.journeys.single()
        val pending = ConnectedSeatRequest("connected-offer_rider-private-uid", offer.id, offer.driverUid,
            "rider-private-uid", ConnectedRequestStatus.PENDING)
        store.requests += pending
        store.requestCancelGate = CompletableDeferred()
        launchConnected()
        tab("Trips").performClick()
        compose.onNodeWithText("Withdraw request").performScrollTo().performClick()
        compose.onNodeWithText("Keep request").performClick()
        compose.runOnIdle { assertTrue(store.requestCancelCalls.isEmpty()) }
        compose.onNodeWithText("Withdraw request").performClick()
        compose.onNodeWithText("Confirm withdrawal").performClick()
        compose.onNodeWithText("Withdraw request").assertIsNotEnabled().performClick()
        tab("Find").performClick()
        tab("Trips").performClick()
        compose.onNodeWithText("Withdraw request").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { store.requestCancelGate!!.complete(Unit) }
        compose.onNodeWithText("Your request was cancelled").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Withdraw request").assertCountEquals(0)
        compose.onNodeWithText("Sheffield → Leeds").performScrollTo().assertIsDisplayed()
        tab("Find").performClick()
        findText("Request one seat").performScrollTo().assertIsEnabled()
        compose.runOnIdle {
            assertEquals(listOf("rider-private-uid" to pending.id), store.requestCancelCalls)
            assertEquals(ConnectedRequestStatus.CANCELLED, repository.journeyState.value.requests.single().status)
            assertEquals(0, legacyCommands)
        }
    }

    @Test
    fun pendingDetailsWithdrawalCommittedRefreshFailureRequiresRefreshAndDoesNotDuplicate() {
        val offer = store.journeys.single()
        val pending = ConnectedSeatRequest("connected-offer_rider-private-uid", offer.id, offer.driverUid,
            "rider-private-uid", ConnectedRequestStatus.PENDING)
        store.requests += pending
        launchConnected()
        tab("Trips").performClick()
        openDetails()
        compose.runOnIdle { store.failNextLoad = true }
        detailsText("Withdraw request").performClick()
        compose.onNodeWithText("Confirm withdrawal").performClick()
        detailsText("Withdraw request").assertIsNotEnabled().performClick()
        detailsText(ConnectedRydeRepository.SAFE_JOURNEY_ERROR).assertIsDisplayed()
        detailsText("Refresh").performClick()
        detailsText("Your request was cancelled").assertIsDisplayed()
        compose.onAllNodesWithText("Withdraw request").assertCountEquals(0)
        detailsText("Back").performClick()
        tab("Trips").assertIsSelected()
        compose.onNodeWithText("Your request was cancelled").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf("rider-private-uid" to pending.id), store.requestCancelCalls)
            assertEquals(0, legacyCommands)
        }
    }

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
        val createCalls = mutableListOf<Pair<String, ConnectedJourneyDraft>>()
        val decisionCalls = mutableListOf<Triple<String, String, Boolean>>()
        val journeyCancelCalls = mutableListOf<Pair<String, String>>()
        var decisionGate: CompletableDeferred<Unit>? = null
        var journeyCancelGate: CompletableDeferred<Unit>? = null
        val requestCancelCalls = mutableListOf<Pair<String, String>>()
        var requestCancelGate: CompletableDeferred<Unit>? = null
        val cancelCalls = mutableListOf<Pair<String, String>>()
        var cancelGate: CompletableDeferred<Unit>? = null
        var failNextCancel = false
        val trips = mutableListOf<ConnectedConfirmedTrip>()
        fun confirmSeat() {
            val journey = journeys.single()
            val id = "${journey.id}_rider-private-uid"
            requests.clear()
            requests += ConnectedSeatRequest(id, journey.id, journey.driverUid, "rider-private-uid",
                ConnectedRequestStatus.ACCEPTED, "Taylor")
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
            return ConnectedJourneySnapshot(journeys, requests.filter { it.riderUid == uid || it.driverUid == uid }, trips.toList())
        }
        override suspend fun create(uid: String, draft: ConnectedJourneyDraft) {
            createCalls += uid to draft
            createGate?.await()
            journeys = journeys + ConnectedJourney("created-offer-${createCalls.size}", uid,
                draft.originArea, draft.destinationArea, draft.departureEpochMillis, draft.seats, draft.seats)
        }
        override suspend fun requestSeat(uid: String, journeyId: String) {
            requestCalls += uid to journeyId
            requestGate?.await()
            if (failNextRequest) {
                failNextRequest = false
                error("Request failed before commit")
            }
            val journey = journeys.single { it.id == journeyId }
            requests.removeAll { it.journeyId == journeyId && it.riderUid == uid }
            requests += ConnectedSeatRequest("${journeyId}_$uid", journeyId, journey.driverUid, uid,
                ConnectedRequestStatus.PENDING, if (uid == "rider-private-uid") "Taylor" else "Morgan")
        }
        override suspend fun cancelRequest(uid: String, requestId: String) {
            requestCancelCalls += uid to requestId
            requestCancelGate?.await()
            val index = requests.indexOfFirst { it.id == requestId }
            check(index >= 0 && requests[index].riderUid == uid && requests[index].status == ConnectedRequestStatus.PENDING)
            requests[index] = requests[index].copy(status = ConnectedRequestStatus.CANCELLED)
        }
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
        override suspend fun cancelJourney(uid: String, journeyId: String) {
            journeyCancelCalls += uid to journeyId
            journeyCancelGate?.await()
            journeys = journeys.map { if (it.id == journeyId) it.copy(status = ConnectedJourneyStatus.CANCELLED) else it }
        }
        override suspend fun decide(uid: String, requestId: String, accept: Boolean) {
            decisionCalls += Triple(uid, requestId, accept)
            decisionGate?.await()
            val index = requests.indexOfFirst { it.id == requestId }
            requests[index] = requests[index].copy(status = if (accept) ConnectedRequestStatus.ACCEPTED else ConnectedRequestStatus.DECLINED)
            if (accept) {
                val journey = journeys.single { it.id == requests[index].journeyId }
                journeys = journeys.map { if (it.id == journey.id) it.copy(seatsRemaining = it.seatsRemaining - 1) else it }
                trips += ConnectedConfirmedTrip(requestId, journey.id, requestId, uid, requests[index].riderUid,
                    journey.originArea, journey.destinationArea, journey.departureEpochMillis, ConnectedTripStatus.CONFIRMED)
            }
        }
    }
}
