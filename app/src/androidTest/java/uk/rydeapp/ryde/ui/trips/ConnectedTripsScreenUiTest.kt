package uk.rydeapp.ryde.ui.trips

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import uk.rydeapp.ryde.data.connected.*
import uk.rydeapp.ryde.ui.theme.RydeTheme

class ConnectedTripsScreenUiTest {
    @get:Rule val compose = createComposeRule()
    private val journey = ConnectedJourney("private-journey-id", "private-driver-uid", "York", "Leeds", 4_070_908_800_000L, 2, 1)
    private val request = ConnectedSeatRequest("private-request-id", journey.id, journey.driverUid,
        "private-rider-uid", ConnectedRequestStatus.PENDING)
    private val trip = ConnectedConfirmedTrip("private-trip-id", journey.id, request.id, journey.driverUid,
        request.riderUid, journey.originArea, journey.destinationArea, journey.departureEpochMillis, ConnectedTripStatus.CONFIRMED)

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
            assertSafe()
        }
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

    private fun assertSafe() {
        listOf("private-driver-uid", "private-rider-uid", "private-request-id", "private-trip-id", "private-journey-id",
            "Demo", "Alex", "£", "Circle", "Withdraw request", "Accept", "Decline").forEach {
            compose.onAllNodesWithText(it, substring = true).assertCountEquals(0)
        }
    }
}
