package uk.rydeapp.ryde.ui.trips

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import uk.rydeapp.ryde.data.connected.ConnectedConfirmedTrip
import uk.rydeapp.ryde.data.connected.ConnectedConversation
import uk.rydeapp.ryde.data.connected.ConnectedConversationReadOnlyReason
import uk.rydeapp.ryde.data.connected.ConnectedConversationState
import uk.rydeapp.ryde.data.connected.ConnectedMessage
import uk.rydeapp.ryde.data.connected.ConnectedMessagePolicy
import uk.rydeapp.ryde.data.connected.ConnectedJourneyPlan
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

        compose.onNodeWithText("Coordinate with Morgan").assertExists()
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

    @Test
    fun noPlanShowsNotSetAndDriverCanProposeNormalizedFields() {
        var pickup by mutableStateOf("")
        var dropOff by mutableStateOf("")
        var saves = 0
        compose.setContent {
            MaterialTheme {
                ConnectedConversationScreen(
                    accountId = "driver",
                    otherDisplayName = "Riley",
                    state = data(canSend = true, canPropose = true),
                    draft = "", sending = false, sendError = null,
                    onDraftChanged = {}, onSend = {}, onRetry = {}, onBack = {},
                    pickupDraft = pickup,
                    dropOffDraft = dropOff,
                    onPickupChanged = { pickup = it },
                    onDropOffChanged = { dropOff = it },
                    onSavePlan = { saves++ },
                )
            }
        }

        compose.onNodeWithTag("journey-plan-status").assertTextEquals("Not set")
        compose.onNodeWithTag("plan-save").assertIsNotEnabled()
        compose.onNodeWithTag("plan-pickup-input").performTextInput("Mansfield station taxi rank, meet 08:15")
        compose.onNodeWithTag("plan-dropoff-input").performTextInput("Nottingham station main entrance")
        compose.onNodeWithTag("plan-save").assertIsEnabled().performClick()
        assertEquals(1, saves)
    }

    @Test
    fun riderSeesProposalAndAgreesCurrentRevision() {
        var agreedRevision = 0
        val plan = ConnectedJourneyPlan("Mansfield station", "Nottingham station", 3, 1, 0, null)
        compose.setContent {
            MaterialTheme {
                ConnectedConversationScreen(
                    accountId = "rider",
                    otherDisplayName = "Morgan",
                    state = data(canSend = true, plan = plan, canAgree = true),
                    draft = "", sending = false, sendError = null,
                    onDraftChanged = {}, onSend = {}, onRetry = {}, onBack = {},
                    onAgreePlan = { agreedRevision = it },
                )
            }
        }

        compose.onNodeWithTag("journey-plan-status").assertTextEquals("Waiting for rider agreement")
        compose.onNodeWithTag("plan-pickup").assertTextEquals("Mansfield station")
        compose.onNodeWithTag("plan-dropoff").assertTextEquals("Nottingham station")
        compose.onNodeWithTag("plan-agree").performClick()
        assertEquals(3, agreedRevision)
        compose.onNodeWithTag("plan-pickup-input").assertDoesNotExist()
    }

    @Test
    fun agreedPlanHasNoAgreeActionAndClosedDriverPlanIsReadOnly() {
        val agreed = ConnectedJourneyPlan("Pickup", "Drop-off", 2, 1, 2, 2)
        var closed by mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                ConnectedConversationScreen(
                    accountId = if (closed) "driver" else "rider",
                    otherDisplayName = null,
                    state = data(
                        canSend = !closed,
                        reason = ConnectedConversationReadOnlyReason.COMPLETED.takeIf { closed },
                        plan = agreed,
                        canPropose = !closed,
                    ),
                    draft = "", sending = false, sendError = null,
                    onDraftChanged = {}, onSend = {}, onRetry = {}, onBack = {},
                )
            }
        }

        compose.onNodeWithTag("journey-plan-status").assertTextEquals("Agreed")
        compose.onNodeWithTag("plan-agree").assertDoesNotExist()
        closed = true
        compose.onNodeWithTag("plan-pickup").assertExists()
        compose.onNodeWithTag("plan-pickup-input").assertDoesNotExist()
        compose.onNodeWithTag("messages-read-only").assertExists()
    }

    @Test
    fun driverEditOfAgreedPlanReturnsRealtimeStateToWaiting() {
        var plan by mutableStateOf(ConnectedJourneyPlan("Pickup", "Drop-off", 1, 1, 1, 2))
        var pickup by mutableStateOf(plan.pickupDetails)
        var dropOff by mutableStateOf(plan.dropOffDetails)
        compose.setContent {
            MaterialTheme {
                ConnectedConversationScreen(
                    accountId = "driver",
                    otherDisplayName = "Riley",
                    state = data(canSend = true, plan = plan, canPropose = true),
                    draft = "", sending = false, sendError = null,
                    onDraftChanged = {}, onSend = {}, onRetry = {}, onBack = {},
                    pickupDraft = pickup,
                    dropOffDraft = dropOff,
                    onPickupChanged = { pickup = it },
                    onDropOffChanged = { dropOff = it },
                    onSavePlan = {
                        plan = ConnectedJourneyPlan(pickup, dropOff, 2, 3, 0, null)
                    },
                )
            }
        }

        compose.onNodeWithTag("journey-plan-status").assertTextEquals("Agreed")
        compose.onNodeWithTag("plan-pickup-input").performTextReplacement("New pickup")
        compose.onNodeWithTag("plan-save").performClick()
        compose.onNodeWithTag("journey-plan-status").assertTextEquals("Waiting for rider agreement")
    }

    @Test
    fun constrainedHeightPlacesPlanAndComposerInOneScrollableSurface() {
        var draft by mutableStateOf("08:10 works for me")
        val plan = ConnectedJourneyPlan(
            "Mansfield station taxi rank, meet 08:10",
            "Nottingham station main entrance",
            2,
            1,
            0,
            null,
        )
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(360.dp).height(500.dp)) {
                    ConnectedConversationScreen(
                        accountId = "rider",
                        otherDisplayName = "Morgan",
                        state = data(
                            canSend = true,
                            plan = plan,
                            canAgree = true,
                        ),
                        draft = draft,
                        sending = false,
                        sendError = null,
                        onDraftChanged = { draft = it },
                        onSend = {},
                        onRetry = {},
                        onBack = {},
                    )
                }
            }
        }

        val scrollSurface = compose.onNodeWithTag("coordinate-scroll-surface").assert(hasScrollAction())
        compose.onNodeWithTag("journey-plan").assertExists()
        scrollSurface.performScrollToIndex(4)
        compose.onNodeWithTag("message-compose").assertTextContains("08:10 works for me")
            .assertIsDisplayed()
        compose.onNodeWithTag("message-send").assertIsEnabled()
            .assertIsDisplayed()
    }

    private fun data(
        canSend: Boolean,
        reason: ConnectedConversationReadOnlyReason? = null,
        messages: List<ConnectedMessage> = emptyList(),
        plan: ConnectedJourneyPlan? = null,
        canPropose: Boolean = false,
        canAgree: Boolean = false,
    ): ConnectedConversationState = ConnectedConversationState.Data(
        ConnectedConversation(trip, null, messages, canSend, reason, plan, canPropose, canAgree),
    )
}
