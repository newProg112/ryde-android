package uk.rydeapp.ryde.ui.account

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import uk.rydeapp.ryde.data.connected.ConnectedConfirmedTrip
import uk.rydeapp.ryde.data.connected.ConnectedTripStatus
import uk.rydeapp.ryde.ui.theme.RydeTheme

class ConnectedJourneyTripsUiTest {
    @Test
    fun riderConfirmsCancellationAndRetainsTerminalHistory() {
        val current = mutableStateOf(trip())
        var calls = 0
        composeRule.setContent {
            RydeTheme {
                ConnectedTripsSection(listOf(current.value), viewerUid = "rider-private-uid", onCancelSeat = { id ->
                    assertEquals(trip().id, id)
                    calls++
                    current.value = current.value.copy(status = ConnectedTripStatus.CANCELLED_BY_RIDER, cancelledAtEpochMillis = 1)
                })
            }
        }
        composeRule.onNodeWithText("Cancel my seat").performClick()
        composeRule.onNodeWithText("Your seat will be returned to the journey. This booking cannot be reopened.").assertIsDisplayed()
        composeRule.onNodeWithText("Keep my seat").performClick()
        composeRule.runOnIdle { assertEquals(0, calls) }
        composeRule.onNodeWithText("Cancel my seat").performClick()
        composeRule.onNodeWithText("Confirm cancellation").performClick()
        composeRule.onNodeWithText("Status: CANCELLED_BY_RIDER · Cancelled by rider").assertIsDisplayed()
        composeRule.onNodeWithText("Rider").assertIsDisplayed()
        composeRule.onAllNodesWithText("Cancel my seat").assertCountEquals(0)
        composeRule.onAllNodesWithText("Re-request seat").assertCountEquals(0)
        composeRule.runOnIdle { assertEquals(1, calls) }
    }

    @Test
    fun driverSeesCancelledHistoryWithoutRiderAction() {
        composeRule.setContent {
            RydeTheme {
                ConnectedTripsSection(listOf(trip().copy(status = ConnectedTripStatus.CANCELLED_BY_RIDER, cancelledAtEpochMillis = 1)),
                    viewerUid = "driver-private-uid", onCancelSeat = { throw AssertionError("Driver cannot cancel rider seat") })
            }
        }
        composeRule.onNodeWithText("Status: CANCELLED_BY_RIDER · Cancelled by rider").assertIsDisplayed()
        composeRule.onNodeWithText("Driver").assertIsDisplayed()
        composeRule.onAllNodesWithText("Cancel my seat").assertCountEquals(0)
    }

    @Test
    fun cancellationActionIsDisabledWhileBusy() {
        composeRule.setContent {
            RydeTheme {
                ConnectedTripsSection(listOf(trip()), viewerUid = "rider-private-uid", busy = true, onCancelSeat = {})
            }
        }
        composeRule.onNodeWithText("Cancel my seat").assertIsNotEnabled()
    }

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyStateIsShownWithoutFirebase() {
        composeRule.setContent {
            RydeTheme {
                ConnectedTripsSection(emptyList(), viewerUid = "driver")
            }
        }

        composeRule.onNodeWithText(CONNECTED_TRIPS_EMPTY_STATE).assertIsDisplayed()
    }

    @Test
    fun driverSeesOnlySafeConfirmedTripFieldsAndDrivingRole() {
        composeRule.setContent {
            RydeTheme {
                ConnectedTripsSection(listOf(trip()), viewerUid = "driver-private-uid", onCancelSeat = {})
            }
        }

        composeRule.onNodeWithText("Mansfield → Nottingham").assertIsDisplayed()
        composeRule.onNodeWithText("Status: CONFIRMED").assertIsDisplayed()
        composeRule.onNodeWithText("You're driving").assertIsDisplayed()
        composeRule.onAllNodesWithText("driver-private-uid").assertCountEquals(0)
        composeRule.onAllNodesWithText("rider-private-uid").assertCountEquals(0)
        composeRule.onAllNodesWithText("Cancel my seat").assertCountEquals(0)
    }

    @Test
    fun riderSeesConfirmedTripWithRidingRole() {
        composeRule.setContent {
            RydeTheme {
                ConnectedTripsSection(listOf(trip()), viewerUid = "rider-private-uid")
            }
        }

        composeRule.onNodeWithText("Mansfield → Nottingham").assertIsDisplayed()
        composeRule.onNodeWithText("Status: CONFIRMED").assertIsDisplayed()
        composeRule.onNodeWithText("You're riding").assertIsDisplayed()
    }

    private fun trip() = ConnectedConfirmedTrip(
        id = "journey-1_rider-private-uid",
        journeyId = "journey-1",
        acceptedRequestId = "journey-1_rider-private-uid",
        driverUid = "driver-private-uid",
        riderUid = "rider-private-uid",
        originArea = "Mansfield",
        destinationArea = "Nottingham",
        departureEpochMillis = 4_070_908_800_000L,
        status = ConnectedTripStatus.CONFIRMED,
    )
}
