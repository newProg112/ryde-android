package uk.rydeapp.ryde.ui.account

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import uk.rydeapp.ryde.data.connected.ConnectedJourney
import uk.rydeapp.ryde.data.connected.ConnectedJourneyStatus
import uk.rydeapp.ryde.ui.theme.RydeTheme

class ConnectedJourneyOffersUiTest {
    @get:Rule val composeRule = createComposeRule()
    private val offer = ConnectedJourney("j", "driver", "Mansfield", "Nottingham", 4_070_908_800_000L, 2, 1)

    @Test
    fun driverConfirmsWholeJourneyCancellationAndRetainsHistory() {
        val current = mutableStateOf(offer)
        var calls = 0
        composeRule.setContent {
            RydeTheme {
                ConnectedOffersSection(listOf(current.value), "driver", onCancelJourney = {
                    assertEquals(offer.id, it)
                    calls++
                    current.value = offer.copy(status = ConnectedJourneyStatus.CANCELLED, cancelledAtEpochMillis = 1)
                })
            }
        }
        composeRule.onNodeWithText("Cancel journey").performClick()
        composeRule.onNodeWithText("This cancels the whole journey for all confirmed riders and pending requests. It cannot be undone.").assertIsDisplayed()
        composeRule.onNodeWithText("Keep journey").performClick()
        composeRule.runOnIdle { assertEquals(0, calls) }
        composeRule.onNodeWithText("Cancel journey").performClick()
        composeRule.onNodeWithText("Confirm journey cancellation").performClick()
        composeRule.onNodeWithText("Status: CANCELLED").assertIsDisplayed()
        composeRule.onNodeWithText("Historical capacity: 1/2 · Unavailable for booking").assertIsDisplayed()
        composeRule.onAllNodesWithText("Cancel journey").assertCountEquals(0)
        composeRule.runOnIdle { assertEquals(1, calls) }
    }

    @Test
    fun busyDriverCannotStartCancellation() {
        composeRule.setContent { RydeTheme { ConnectedOffersSection(listOf(offer), "driver", busy = true, onCancelJourney = {}) } }
        composeRule.onNodeWithText("Cancel journey").assertIsNotEnabled()
    }

    @Test
    fun anotherAccountCannotSeeOrCancelDriversOfferInYourOffers() {
        composeRule.setContent { RydeTheme { ConnectedOffersSection(listOf(offer), "rider", onCancelJourney = {}) } }
        composeRule.onAllNodesWithText("Cancel journey").assertCountEquals(0)
        composeRule.onNodeWithText("No connected offers yet.").assertIsDisplayed()
    }
}
