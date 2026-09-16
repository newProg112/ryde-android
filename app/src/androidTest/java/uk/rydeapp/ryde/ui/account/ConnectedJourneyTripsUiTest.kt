package uk.rydeapp.ryde.ui.account

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import uk.rydeapp.ryde.data.connected.ConnectedConfirmedTrip
import uk.rydeapp.ryde.data.connected.ConnectedTripStatus
import uk.rydeapp.ryde.ui.theme.RydeTheme

class ConnectedJourneyTripsUiTest {
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
                ConnectedTripsSection(listOf(trip()), viewerUid = "driver-private-uid")
            }
        }

        composeRule.onNodeWithText("Mansfield → Nottingham").assertIsDisplayed()
        composeRule.onNodeWithText("Status: CONFIRMED").assertIsDisplayed()
        composeRule.onNodeWithText("You're driving").assertIsDisplayed()
        composeRule.onAllNodesWithText("driver-private-uid").assertCountEquals(0)
        composeRule.onAllNodesWithText("rider-private-uid").assertCountEquals(0)
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
