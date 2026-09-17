package uk.rydeapp.ryde.ui.offer

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import uk.rydeapp.ryde.ui.theme.RydeTheme

class ConnectedOfferScreenUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun formRestoresDraftAndUsesDateAndTimePickersWithoutSubmitting() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { RydeTheme {
            ConnectedOfferScreen(false, true, null, 0, { _, _, _, _ -> error("No submission") }, {}, {})
        } }
        compose.onNodeWithText("From town or district").performTextInput("York")
        compose.onNodeWithText("To town or district").performTextInput("Leeds")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("From town or district").assert(hasText("York"))
        compose.onNodeWithText("To town or district").assert(hasText("Leeds"))
        compose.onNodeWithText("Choose date").performScrollTo().performClick()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("Choose time").performClick()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("Offer journey").performScrollTo().assertIsEnabled()
    }

    @Test fun invalidSeatCountStaysInFormAndRefreshRequiredDisablesSubmission() {
        val enabled = mutableStateOf(true)
        var calls = 0
        compose.setContent { RydeTheme {
            ConnectedOfferScreen(false, enabled.value, null, 0, { _, _, _, _ -> calls++ }, {}, {})
        } }
        compose.onNodeWithText("From town or district").performTextInput("York")
        compose.onNodeWithText("To town or district").performTextInput("Leeds")
        compose.onNodeWithText("Spare seats (1–8)").performScrollTo().performTextReplacement("9")
        compose.onNodeWithText("Offer journey").performScrollTo().performClick()
        compose.onNodeWithText("Enter between 1 and 8 seats.").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, calls); enabled.value = false }
        compose.onNodeWithText("Offer journey").assertIsNotEnabled()
        compose.onNodeWithText("Refresh").performScrollTo().assertIsEnabled()
    }
}
