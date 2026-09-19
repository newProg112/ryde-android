package uk.rydeapp.ryde.ui.trips

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import uk.rydeapp.ryde.data.connected.*
import uk.rydeapp.ryde.ui.home.connectedHomeJourneys
import uk.rydeapp.ryde.ui.theme.RydeTheme

class ConnectedTripDetailsScreenUiTest {
    @get:Rule val compose = createComposeRule()
    private val journey = ConnectedJourney("journey-id", "driver-uid", "York", "Leeds", 4_070_908_800_000L, 3, 2)
    private val request = ConnectedSeatRequest("request-id", journey.id, journey.driverUid, "rider-uid", ConnectedRequestStatus.PENDING)
    private val trip = ConnectedConfirmedTrip("trip-id", journey.id, request.id, journey.driverUid, request.riderUid,
        journey.originArea, journey.destinationArea, journey.departureEpochMillis, ConnectedTripStatus.CONFIRMED)
    private fun content(snapshot: ConnectedJourneySnapshot, uid: String = request.riderUid) =
        connectedTripDetailsContent(snapshot, uid, journey.id, connectedHomeJourneys(snapshot, uid, 0), connectedTripsContent(snapshot, uid, 0))
    private fun text(label: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("connected-trip-details-list").performScrollToNode(hasText(label))
        return compose.onNodeWithText(label)
    }

    @Test fun requestAndRefreshForwardJourneyIdAndBusyBlocksRepeatActions() {
        val busy = mutableStateOf(false)
        val enabled = mutableStateOf(true)
        val calls = mutableListOf<String>()
        var refreshes = 0
        compose.setContent { RydeTheme {
            ConnectedTripDetailsScreen(content(ConnectedJourneySnapshot(listOf(journey))), busy.value, enabled.value, null,
                {}, { refreshes++ }, { calls += it; busy.value = true }, { _, _ -> error("No request") }, {}, {})
        } }
        text("Request one seat").performClick().assertIsNotEnabled().performClick()
        text("Refresh").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(listOf(journey.id), calls); busy.value = false; enabled.value = false }
        text("Request one seat").assertIsNotEnabled()
        text("Refresh").performClick()
        compose.runOnIdle { assertEquals(1, refreshes) }
    }

    @Test fun riderStatusesExposeOnlyValidPendingWithdrawalAndPreserveHistory() {
        val current = mutableStateOf(request)
        compose.setContent { RydeTheme {
            ConnectedTripDetailsScreen(content(ConnectedJourneySnapshot(listOf(journey), listOf(current.value))), false, true, null,
                {}, {}, {}, { _, _ -> error("Not driver") }, { error("Not confirmed") }, {})
        } }
        mapOf(ConnectedRequestStatus.PENDING to "Your request is pending",
            ConnectedRequestStatus.DECLINED to "Your request was declined",
            ConnectedRequestStatus.CANCELLED to "Your request was cancelled",
            ConnectedRequestStatus.CANCELLED_AFTER_ACCEPTANCE to "Your confirmed seat was cancelled").forEach { (status, label) ->
            compose.runOnIdle { current.value = request.copy(status = status) }
            text(label).assertIsDisplayed()
            compose.onAllNodesWithText("Cancel my seat").assertCountEquals(0)
            compose.onAllNodesWithText("Withdraw request").assertCountEquals(
                if (status == ConnectedRequestStatus.PENDING) 1 else 0,
            )
            if (status == ConnectedRequestStatus.CANCELLED) text("Request one seat").assertIsEnabled()
            else compose.onAllNodesWithText("Request one seat").assertCountEquals(0)
        }
    }

    @Test fun pendingWithdrawalRequiresConfirmationUsesRequestIdOnceAndIsNotRestoredArmed() {
        val restoration = StateRestorationTester(compose)
        val busy = mutableStateOf(false)
        val calls = mutableListOf<String>()
        restoration.setContent { RydeTheme {
            ConnectedTripDetailsScreen(
                content(ConnectedJourneySnapshot(listOf(journey), listOf(request))), busy.value, true, null,
                {}, {}, {}, { _, _ -> }, {}, {},
                onWithdrawRequest = { calls += it; busy.value = true },
            )
        } }
        text("Withdraw request").performClick()
        compose.onNodeWithText("Keep request").performClick()
        compose.runOnIdle { assertEquals(emptyList<String>(), calls) }
        text("Withdraw request").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onAllNodesWithText("Confirm withdrawal").assertCountEquals(0)
        text("Withdraw request").performClick()
        compose.onNodeWithText("Confirm withdrawal").performClick()
        text("Withdraw request").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(request.id), calls) }
    }

    @Test fun pendingWithdrawalConfirmationClosesWhenEligibilityChanges() {
        val current = mutableStateOf(journey)
        val calls = mutableListOf<String>()
        compose.setContent { RydeTheme {
            ConnectedTripDetailsScreen(
                content(ConnectedJourneySnapshot(listOf(current.value), listOf(request))), false, true, null,
                {}, {}, {}, { _, _ -> }, {}, {}, onWithdrawRequest = { calls += it },
            )
        } }
        text("Withdraw request").performClick()
        compose.onNodeWithText("Confirm withdrawal").assertIsDisplayed()
        compose.runOnIdle { current.value = journey.copy(status = ConnectedJourneyStatus.CANCELLED) }
        compose.onAllNodesWithText("Confirm withdrawal").assertCountEquals(0)
        compose.onAllNodesWithText("Withdraw request").assertCountEquals(0)
        compose.runOnIdle { assertEquals(emptyList<String>(), calls) }
    }

    @Test fun confirmedCancellationRequiresConfirmationUsesTripIdAndIsNotRestoredArmed() {
        val restoration = StateRestorationTester(compose)
        val calls = mutableListOf<String>()
        restoration.setContent { RydeTheme {
            ConnectedTripDetailsScreen(content(ConnectedJourneySnapshot(listOf(journey), confirmedTrips = listOf(trip))), false, true, null,
                {}, {}, {}, { _, _ -> }, { calls += it }, {})
        } }
        text("Cancel my seat").performClick()
        compose.onNodeWithText("Keep my seat").performClick()
        compose.runOnIdle { assertEquals(emptyList<String>(), calls) }
        text("Cancel my seat").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onAllNodesWithText("Confirm cancellation").assertCountEquals(0)
        text("Cancel my seat").performClick()
        compose.onNodeWithText("Confirm cancellation").performClick()
        compose.onAllNodesWithText("Confirm cancellation").assertCountEquals(0)
        compose.runOnIdle { assertEquals(listOf(trip.id), calls) }
    }

    @Test fun driverDecisionsUseRequestIdAndJourneyCancellationRequiresConfirmation() {
        val decisions = mutableListOf<Pair<String, Boolean>>()
        val cancellations = mutableListOf<String>()
        val restoration = StateRestorationTester(compose)
        restoration.setContent { RydeTheme {
            ConnectedTripDetailsScreen(content(ConnectedJourneySnapshot(listOf(journey), listOf(request)), journey.driverUid), false, true, null,
                {}, {}, {}, { id, accept -> decisions += id to accept }, {}, { cancellations += it })
        } }
        text("Accept").performClick()
        text("Decline").performClick()
        text("Cancel journey").performClick()
        compose.onNodeWithText("Keep journey").performClick()
        compose.runOnIdle { assertEquals(emptyList<String>(), cancellations) }
        text("Cancel journey").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onAllNodesWithText("Confirm journey cancellation").assertCountEquals(0)
        text("Cancel journey").performClick()
        compose.onNodeWithText("Confirm journey cancellation").performClick()
        compose.runOnIdle {
            assertEquals(listOf(request.id to true, request.id to false), decisions)
            assertEquals(listOf(journey.id), cancellations)
        }
    }

    @Test fun lifecycleChangeClosesArmedCancellationWithoutSendingCommand() {
        val current = mutableStateOf(journey)
        compose.setContent { RydeTheme {
            ConnectedTripDetailsScreen(content(ConnectedJourneySnapshot(listOf(current.value), confirmedTrips = listOf(trip))), false, true, null,
                {}, {}, {}, { _, _ -> }, { error("Closed journey") }, {})
        } }
        text("Cancel my seat").performClick()
        compose.runOnIdle { current.value = journey.copy(status = ConnectedJourneyStatus.CANCELLED) }
        compose.onAllNodesWithText("Confirm cancellation").assertCountEquals(0)
        compose.onAllNodesWithText("Cancel my seat").assertCountEquals(0)
        text("Journey cancelled by driver").assertIsDisplayed()
    }

    @Test fun unavailableAndMismatchedSelectionHasNoActionsButKeepsPersistedBookingHistory() {
        val snapshot = mutableStateOf(ConnectedJourneySnapshot())
        var backs = 0
        compose.setContent { RydeTheme {
            ConnectedTripDetailsScreen(content(snapshot.value), false, true, null,
                { backs++ }, {}, { error("Unavailable") }, { _, _ -> error("Unavailable") }, { error("Unavailable") }, { error("Unavailable") })
        } }
        text("Journey details unavailable").assertIsDisplayed()
        text("Back").performClick()
        compose.runOnIdle {
            assertEquals(1, backs)
            snapshot.value = ConnectedJourneySnapshot(listOf(journey.copy(driverUid = "other-driver")), confirmedTrips = listOf(trip))
        }
        text("York → Leeds").assertIsDisplayed()
        text("Journey unavailable").assertIsDisplayed()
        listOf("Request one seat", "Cancel my seat", "Cancel journey", "Accept", "Decline").forEach {
            compose.onAllNodesWithText(it).assertCountEquals(0)
        }
        listOf(journey.id, request.id, trip.id, journey.driverUid, request.riderUid).forEach {
            compose.onAllNodesWithText(it, substring = true).assertCountEquals(0)
        }
    }
}
