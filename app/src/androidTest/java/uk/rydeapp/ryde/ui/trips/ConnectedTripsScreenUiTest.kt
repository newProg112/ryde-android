package uk.rydeapp.ryde.ui.trips

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import uk.rydeapp.ryde.data.connected.*
import uk.rydeapp.ryde.ui.theme.RydeTheme

class ConnectedTripsScreenUiTest {
    @get:Rule val compose = createComposeRule()
    private val journey = ConnectedJourney("private-journey-id", "private-driver-uid", "York", "Leeds", 4_070_908_800_000L, 2, 1)
    private val request = ConnectedSeatRequest("private-request-id", journey.id, journey.driverUid,
        "private-rider-uid", ConnectedRequestStatus.PENDING, "Riley Rider")
    private val trip = ConnectedConfirmedTrip("private-trip-id", journey.id, request.id, journey.driverUid,
        request.riderUid, journey.originArea, journey.destinationArea, journey.departureEpochMillis, ConnectedTripStatus.CONFIRMED)

    @Test fun driverLifecycleChangeClosesCancellationAndDecisionControls() {
        val current = mutableStateOf(journey)
        compose.setContent { RydeTheme {
            ConnectedTripsScreen(connectedTripsContent(ConnectedJourneySnapshot(listOf(current.value), listOf(request)),
                journey.driverUid, 0), false, true, null, {}, {},
                onDecideRequest = { _, _ -> error("No valid decision") },
                onCancelJourney = { error("No valid cancellation") })
        } }
        compose.onNodeWithText("Cancel journey").performScrollTo().performClick()
        compose.onNodeWithText("Rider: Riley Rider").assertIsDisplayed()
        compose.onNodeWithText("Confirm journey cancellation").assertIsDisplayed()
        compose.runOnIdle { current.value = journey.copy(status = ConnectedJourneyStatus.CANCELLED) }
        compose.onAllNodesWithText("Confirm journey cancellation").assertCountEquals(0)
        compose.onAllNodesWithText("Accept").assertCountEquals(0)
        compose.onAllNodesWithText("Decline").assertCountEquals(0)
        compose.onAllNodesWithText("Cancel journey").assertCountEquals(0)
        assertSafe()
    }

    @Test fun driverSeesSafeSnapshotForTerminalHistoryAndRiderFallbackForLegacyRequests() {
        val current = mutableStateOf(request.copy(status = ConnectedRequestStatus.DECLINED))
        compose.setContent { RydeTheme {
            ConnectedTripsScreen(
                connectedTripsContent(ConnectedJourneySnapshot(listOf(journey), listOf(current.value)), journey.driverUid, 0),
                false, true, null, {}, {},
            )
        } }
        compose.onNodeWithText("Rider: Riley Rider").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Request declined").assertIsDisplayed()
        compose.runOnIdle { current.value = current.value.copy(riderDisplayName = null) }
        compose.onNodeWithText("Rider").assertIsDisplayed()
        compose.onAllNodesWithText("Rider: Riley Rider").assertCountEquals(0)
        assertSafe()
    }

    @Test fun genuineTerminalRequestsRetainSafeFieldsWithoutUnsupportedActions() {
        val current = mutableStateOf(request)
        compose.setContent { RydeTheme {
            ConnectedTripsScreen(connectedTripsContent(ConnectedJourneySnapshot(listOf(journey), listOf(current.value)),
                request.riderUid, 0), false, true, null, {}, { error("No confirmed booking") })
        } }
        mapOf(
            ConnectedRequestStatus.PENDING to "Your request is pending",
            ConnectedRequestStatus.DECLINED to "Your request was declined",
            ConnectedRequestStatus.CANCELLED to "Your request was cancelled",
            ConnectedRequestStatus.CANCELLED_AFTER_ACCEPTANCE to "Your confirmed seat was cancelled",
        ).forEach { (status, label) ->
            compose.runOnIdle { current.value = request.copy(status = status) }
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("York → Leeds").assertIsDisplayed()
            compose.onNodeWithText("2099", substring = true).assertIsDisplayed()
            compose.onAllNodesWithText("Cancel my seat").assertCountEquals(0)
            compose.onAllNodesWithText("Withdraw request").assertCountEquals(
                if (status == ConnectedRequestStatus.PENDING) 1 else 0,
            )
            assertSafe()
        }
    }

    @Test fun pendingWithdrawalRequiresConfirmationUsesRequestIdOnceAndIsNotRestoredArmed() {
        val busy = mutableStateOf(false)
        val current = mutableStateOf(request)
        val calls = mutableListOf<String>()
        val restoration = StateRestorationTester(compose)
        restoration.setContent { RydeTheme {
            ConnectedTripsScreen(
                connectedTripsContent(ConnectedJourneySnapshot(listOf(journey), listOf(current.value)), request.riderUid, 0),
                busy.value, true, null, {}, {},
                onWithdrawRequest = { calls += it; busy.value = true },
            )
        } }
        compose.onNodeWithText("Withdraw request").performScrollTo().performClick()
        compose.onNodeWithText("Keep request").performClick()
        compose.runOnIdle { assertEquals(emptyList<String>(), calls) }
        compose.onNodeWithText("Withdraw request").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onAllNodesWithText("Confirm withdrawal").assertCountEquals(0)
        compose.onNodeWithText("Withdraw request").performScrollTo().performClick()
        compose.onNodeWithText("Confirm withdrawal").performClick()
        compose.onNodeWithText("Withdraw request").assertIsNotEnabled().performClick()
        compose.runOnIdle {
            assertEquals(listOf(request.id), calls)
            busy.value = false
            current.value = request.copy(status = ConnectedRequestStatus.CANCELLED)
        }
        compose.onNodeWithText("Your request was cancelled").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Withdraw request").assertCountEquals(0)
        assertSafe()
    }

    @Test fun pendingWithdrawalConfirmationClosesWhenJourneyEligibilityChanges() {
        val current = mutableStateOf(journey)
        val calls = mutableListOf<String>()
        compose.setContent { RydeTheme {
            ConnectedTripsScreen(
                connectedTripsContent(ConnectedJourneySnapshot(listOf(current.value), listOf(request)), request.riderUid, 0),
                false, true, null, {}, {}, onWithdrawRequest = { calls += it },
            )
        } }
        compose.onNodeWithText("Withdraw request").performScrollTo().performClick()
        compose.onNodeWithText("Confirm withdrawal").assertIsDisplayed()
        compose.runOnIdle { current.value = journey.copy(status = ConnectedJourneyStatus.CANCELLED) }
        compose.onAllNodesWithText("Confirm withdrawal").assertCountEquals(0)
        compose.onAllNodesWithText("Withdraw request").assertCountEquals(0)
        compose.runOnIdle { assertEquals(emptyList<String>(), calls) }
    }

    @Test fun lifecycleChangeClosesConfirmationWithoutCallingCancellation() {
        val current = mutableStateOf(journey)
        compose.setContent { RydeTheme {
            ConnectedTripsScreen(connectedTripsContent(ConnectedJourneySnapshot(listOf(current.value), listOf(request), listOf(trip)),
                request.riderUid, 0), false, true, null, {}, { error("Closed journey cannot cancel") })
        } }
        compose.onNodeWithText("Cancel my seat").performScrollTo().performClick()
        compose.onNodeWithText("Confirm cancellation").assertIsDisplayed()
        compose.runOnIdle { current.value = journey.copy(status = ConnectedJourneyStatus.CANCELLED) }
        compose.onNodeWithText("Journey cancelled by driver").assertIsDisplayed()
        compose.onAllNodesWithText("Confirm cancellation").assertCountEquals(0)
        compose.onAllNodesWithText("Cancel my seat").assertCountEquals(0)
        compose.onAllNodesWithText("Withdraw request").assertCountEquals(0)
        assertSafe()
    }

    @Test fun missingJourneyKeepsPendingRecordWithoutFabricatedDetails() {
        compose.setContent { RydeTheme {
            ConnectedTripsScreen(connectedTripsContent(ConnectedJourneySnapshot(requests = listOf(request)), request.riderUid, 0),
                false, true, null, {}, { error("No linked journey") })
        } }
        compose.onNodeWithText("Journey details unavailable").assertIsDisplayed()
        compose.onNodeWithText("Journey unavailable").assertIsDisplayed()
        compose.onAllNodesWithText("No trips yet").assertCountEquals(0)
        compose.onAllNodesWithText("York", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Cancel my seat").assertCountEquals(0)
        assertSafe()
    }

    @Test fun declinedRequestShowsSubsequentJourneyCancellationAlongsideHistoryAndKeepsOtherSeatConfirmed() {
        val offered = journey.copy(id = "declined-offer", originArea = "Mansfield", destinationArea = "Sheffield", seatsRemaining = 2)
        val current = mutableStateOf(offered)
        val declined = request.copy(id = "declined-request", journeyId = offered.id, status = ConnectedRequestStatus.DECLINED)
        val confirmed = journey.copy(originArea = "Mansfield", destinationArea = "Nottingham")
        val confirmedTrip = trip.copy(originArea = confirmed.originArea, destinationArea = confirmed.destinationArea)
        compose.setContent { RydeTheme {
            ConnectedTripsScreen(connectedTripsContent(
                ConnectedJourneySnapshot(listOf(confirmed, current.value), listOf(declined), listOf(confirmedTrip)),
                request.riderUid, 0), false, true, null, {}, { error("No cancellation expected") })
        } }
        compose.onNodeWithText("Your request was declined").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Journey cancelled by driver").assertCountEquals(0)
        compose.runOnIdle { current.value = offered.copy(status = ConnectedJourneyStatus.CANCELLED) }
        compose.onNodeWithText("Journey cancelled by driver").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Your request was declined").assertIsDisplayed()
        compose.onNodeWithText("Mansfield → Sheffield").assertIsDisplayed()
        compose.onNodeWithText("Your seat is confirmed").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Mansfield → Nottingham").assertIsDisplayed()
        compose.onAllNodesWithText("Cancel my seat").assertCountEquals(1)
        compose.onAllNodesWithText("Accept").assertCountEquals(0)
        compose.onAllNodesWithText("Decline").assertCountEquals(0)
    }

    private fun assertSafe() {
        listOf("private-driver-uid", "private-rider-uid", "private-request-id", "private-trip-id", "private-journey-id",
            "Demo", "Alex", "£", "Circle", "Accept", "Decline").forEach {
            compose.onAllNodesWithText(it, substring = true).assertCountEquals(0)
        }
    }
}
