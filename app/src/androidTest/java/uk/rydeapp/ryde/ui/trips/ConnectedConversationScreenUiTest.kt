package uk.rydeapp.ryde.ui.trips

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import uk.rydeapp.ryde.data.connected.ConnectedConfirmedTrip
import uk.rydeapp.ryde.data.connected.ConnectedConversation
import uk.rydeapp.ryde.data.connected.ConnectedConversationReadOnlyReason
import uk.rydeapp.ryde.data.connected.ConnectedConversationState
import uk.rydeapp.ryde.data.connected.ConnectedMessage
import uk.rydeapp.ryde.data.connected.ConnectedMessagePolicy
import uk.rydeapp.ryde.data.connected.ConnectedTripStatus

class ConnectedConversationScreenUiTest {
    @get:Rule val compose = createComposeRule()

    private val trip = ConnectedConfirmedTrip(
        "j_rider", "j", "j_rider", "driver", "rider", "Derby", "Nottingham", 1_000,
        ConnectedTripStatus.CONFIRMED,
    )

    @Test
    fun activeConversationShowsContextEmptyStateAndSendsOnceWhileBusy() {
        var draft by mutableStateOf("")
        var sending by mutableStateOf(false)
        var sends = 0
        compose.setContent {
            MaterialTheme {
                ConnectedConversationScreen(
                    accountId = "rider",
                    otherDisplayName = "Morgan",
                    state = data(canSend = true),
                    draft = draft,
                    sending = sending,
                    sendError = null,
                    onDraftChanged = { draft = it },
                    onSend = { sends++; sending = true },
                    onRetry = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("Messages with Morgan").assertExists()
        compose.onNodeWithText("Derby → Nottingham").assertExists()
        compose.onNodeWithText("No messages yet. Send a short update to coordinate this trip.").assertExists()
        compose.onNodeWithText("Use public pickup places. Do not share a home address, phone number, or live location.")
            .assertExists()
        compose.onNodeWithTag("message-compose").performTextInput("I'm here.")
        compose.onNodeWithTag("message-compose").assertIsDisplayed()
        compose.onNodeWithText("9/${ConnectedMessagePolicy.MAX_BODY_LENGTH}").assertIsDisplayed()
        compose.onNodeWithTag("message-send").assertIsDisplayed()
        compose.onNodeWithTag("message-send").performClick()
        compose.onNodeWithTag("message-send").assertIsNotEnabled()
        assertEquals(1, sends)
    }

    @Test
    fun readOnlyConversationRetainsHistoryAndRemovesComposer() {
        compose.setContent {
            MaterialTheme {
                ConnectedConversationScreen(
                    accountId = "rider",
                    otherDisplayName = null,
                    state = data(
                        canSend = false,
                        reason = ConnectedConversationReadOnlyReason.CANCELLED_BY_DRIVER,
                        messages = listOf(ConnectedMessage("m", "driver", "Earlier update", 10)),
                    ),
                    draft = "",
                    sending = false,
                    sendError = null,
                    onDraftChanged = {}, onSend = {}, onRetry = {}, onBack = {},
                )
            }
        }

        compose.onNodeWithText("Earlier update").assertExists()
        compose.onNodeWithTag("message-other:m").assertExists()
        compose.onNodeWithTag("messages-read-only").assertExists()
        compose.onNodeWithTag("message-compose").assertDoesNotExist()
        compose.onNodeWithTag("message-send").assertDoesNotExist()
    }

    @Test
    fun laterListenerFailureKeepsSameConversationReadOnlyAndOffersRetry() {
        var retries = 0
        val previous = (data(
            canSend = true,
            messages = listOf(ConnectedMessage("own", "rider", "Still visible", 20)),
        ) as ConnectedConversationState.Data).conversation
        compose.setContent {
            MaterialTheme {
                ConnectedConversationScreen(
                    accountId = "rider",
                    otherDisplayName = "Morgan",
                    state = ConnectedConversationState.Error("Messages are unavailable right now. Try again.", previous),
                    draft = "Retry me",
                    sending = false,
                    sendError = null,
                    onDraftChanged = {}, onSend = {}, onRetry = { retries++ }, onBack = {},
                )
            }
        }

        compose.onNodeWithTag("message-own:own").assertExists()
        compose.onNodeWithTag("message-compose").assertDoesNotExist()
        compose.onNodeWithTag("messages-retry").performClick()
        assertEquals(1, retries)
    }

    private fun data(
        canSend: Boolean,
        reason: ConnectedConversationReadOnlyReason? = null,
        messages: List<ConnectedMessage> = emptyList(),
    ): ConnectedConversationState = ConnectedConversationState.Data(
        ConnectedConversation(trip, null, messages, canSend, reason),
    )
}
