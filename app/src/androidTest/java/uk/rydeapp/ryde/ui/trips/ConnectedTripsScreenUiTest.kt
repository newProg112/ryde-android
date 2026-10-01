package uk.rydeapp.ryde.ui.trips

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import uk.rydeapp.ryde.data.connected.*
import uk.rydeapp.ryde.domain.model.GeographicCoordinate
import uk.rydeapp.ryde.ui.theme.RydeTheme

class ConnectedTripsScreenUiTest {
    @get:Rule val compose = createComposeRule()
    private val journey = ConnectedJourney("private-journey-id", "private-driver-uid", "York", "Leeds", 4_070_908_800_000L, 2, 1)
    private val request = ConnectedSeatRequest("private-request-id", journey.id, journey.driverUid,
        "private-rider-uid", ConnectedRequestStatus.PENDING, "Riley Rider")
    private val trip = ConnectedConfirmedTrip("private-trip-id", journey.id, request.id, journey.driverUid,
        request.riderUid, journey.originArea, journey.destinationArea, journey.departureEpochMillis,
        ConnectedTripStatus.CONFIRMED, driverDisplayName = "Morgan Driver")

    @Test fun requestedBroadAreaSegmentRendersSeparatelyForRiderAndDriver() {
        val segmented = request.copy(requestedBroadAreaSegment = ConnectedRequestedBroadAreaSegment(
            "Hucknall", "Nottingham",
        ))
        val viewer = mutableStateOf(request.riderUid)
        val accepted = mutableStateOf(false)
        compose.setContent { RydeTheme {
            ConnectedTripsScreen(
                connectedTripsContent(
                    ConnectedJourneySnapshot(
                        listOf(journey),
                        listOf(segmented.copy(status = if (accepted.value) {
                            ConnectedRequestStatus.ACCEPTED
                        } else ConnectedRequestStatus.PENDING)),
                        if (accepted.value) listOf(trip) else emptyList(),
                    ),
                    viewer.value,
                    0,
                ),
                false, true, null, {}, {},
            )
        } }

        compose.onNodeWithText("Rider requested: Hucknall → Nottingham").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Broad requested areas only — confirmed pairs coordinate pickup and drop-off privately.")
            .performScrollTo().assertIsDisplayed()
        compose.runOnIdle { viewer.value = journey.driverUid }
        compose.onNodeWithText("Rider requested: Hucknall → Nottingham").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            viewer.value = request.riderUid
            accepted.value = true
        }
        compose.onNodeWithText("Your seat is confirmed").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Rider requested: Hucknall → Nottingham").assertIsDisplayed()
    }

    @Test fun declaredViaRendersForDriverPendingRiderAndConfirmedRiderWhileNoViaStaysUnchanged() {
        val routed = journey.copy(
            originCoordinate = GeographicCoordinate(53.1432, -1.1984),
            destinationCoordinate = GeographicCoordinate(52.9548, -1.1581),
            routeWaypoints = listOf(ConnectedRouteWaypoint(
                "Hucknall", GeographicCoordinate(53.0380, -1.2034),
            )),
        )
        val currentJourney = mutableStateOf(routed)
        val viewer = mutableStateOf(journey.driverUid)
        val accepted = mutableStateOf(false)
        compose.setContent { RydeTheme {
            val snapshot = ConnectedJourneySnapshot(
                listOf(currentJourney.value),
                listOf(request.copy(status = if (accepted.value) ConnectedRequestStatus.ACCEPTED else ConnectedRequestStatus.PENDING)),
                if (accepted.value) listOf(trip) else emptyList(),
            )
            ConnectedTripsScreen(connectedTripsContent(snapshot, viewer.value, 0), false, true, null, {}, {})
        } }

        compose.onNodeWithText("Via Hucknall").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { viewer.value = request.riderUid }
        compose.onNodeWithText("Via Hucknall").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { accepted.value = true }
        compose.onNodeWithText("Your seat is confirmed").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Via Hucknall").assertIsDisplayed()

        compose.runOnIdle { currentJourney.value = journey }
        compose.onNodeWithText("Your seat is confirmed").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Via Hucknall").assertCountEquals(0)
    }

    @Test fun riderSeesPrivateDriverSnapshotThroughoutHistoryAndLegacyFallsBackSafely() {
        val currentJourney = mutableStateOf(journey)
        val currentTrip = mutableStateOf(trip)
        compose.setContent { RydeTheme {
            ConnectedTripsScreen(
                connectedTripsContent(
                    ConnectedJourneySnapshot(listOf(currentJourney.value), confirmedTrips = listOf(currentTrip.value)),
                    request.riderUid,
                    0,
                ),
                false, true, null, {}, {},
            )
        } }
        compose.onNodeWithText("Driver: Morgan Driver").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { currentTrip.value = trip.copy(status = ConnectedTripStatus.CANCELLED_BY_RIDER) }
        compose.onNodeWithText("Your seat was cancelled").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Driver: Morgan Driver").assertIsDisplayed()
        compose.runOnIdle {
            currentTrip.value = trip
            currentJourney.value = journey.copy(status = ConnectedJourneyStatus.CANCELLED)
        }
        compose.onNodeWithText("Journey cancelled by driver").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Driver: Morgan Driver").assertIsDisplayed()
        compose.runOnIdle { currentTrip.value = trip.copy(driverDisplayName = null) }
        compose.onAllNodesWithText("Driver: Morgan Driver").assertCountEquals(0)
        compose.onNodeWithText("Rider").assertIsDisplayed()
        assertSafe()
    }

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

    @Test fun departedOpenConfirmedSeatRemainsCurrentAwaitingCompletionAndCannotBeCancelled() {
        compose.setContent { RydeTheme {
            ConnectedTripsScreen(
                connectedTripsContent(
                    ConnectedJourneySnapshot(listOf(journey), confirmedTrips = listOf(trip)),
                    request.riderUid,
                    journey.departureEpochMillis,
                ),
                false, true, null, {}, { error("Past seat cannot be cancelled") },
            )
        } }
        compose.onNodeWithText("Current rides and requests").assertIsDisplayed()
        compose.onAllNodesWithText("Rider history").assertCountEquals(0)
        compose.onNodeWithText("Departure has passed — awaiting driver completion")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Driver: Morgan Driver").assertIsDisplayed()
        compose.onNodeWithText("${journey.originArea} \u2192 ${journey.destinationArea}")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2099", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("Your seat is confirmed").assertCountEquals(0)
        compose.onAllNodesWithText("Cancel my seat").assertCountEquals(0)
    }

    @Test fun currentAndHistoryHeadingsRenderOnlyWhenTheirLifecycleSectionHasContent() {
        val current = mutableStateOf(request)
        compose.setContent { RydeTheme {
            ConnectedTripsScreen(
                connectedTripsContent(
                    ConnectedJourneySnapshot(listOf(journey), listOf(current.value)),
                    request.riderUid,
                    0,
                ),
                false, true, null, {}, {},
            )
        } }

        compose.onNodeWithText("Current rides and requests").assertIsDisplayed()
        compose.onAllNodesWithText("Rider history").assertCountEquals(0)
        compose.onAllNodesWithText("Current offered journeys").assertCountEquals(0)
        compose.onAllNodesWithText("Offered journey history").assertCountEquals(0)

        compose.runOnIdle { current.value = request.copy(status = ConnectedRequestStatus.DECLINED) }
        compose.onNodeWithText("Rider history").assertIsDisplayed()
        compose.onAllNodesWithText("Current rides and requests").assertCountEquals(0)
        compose.onAllNodesWithText("Withdraw request").assertCountEquals(0)
    }

    @Test fun departedOpenDriverJourneyStaysCurrentAndKeepsCompletionConfirmation() {
        val current = mutableStateOf(journey)
        val completions = mutableListOf<String>()
        compose.setContent { RydeTheme {
            ConnectedTripsScreen(
                connectedTripsContent(
                    ConnectedJourneySnapshot(listOf(current.value)),
                    journey.driverUid,
                    journey.departureEpochMillis,
                ),
                false, true, null, {}, {},
                onCompleteJourney = { completions += it },
            )
        } }

        compose.onNodeWithText("Current offered journeys").assertIsDisplayed()
        compose.onAllNodesWithText("Offered journey history").assertCountEquals(0)
        compose.onNodeWithText("Departure has passed — ready to mark complete")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Mark journey complete").performClick()
        compose.onNodeWithText("Confirm journey completion").performClick()
        compose.runOnIdle { assertEquals(listOf(journey.id), completions) }

        compose.runOnIdle {
            current.value = journey.copy(
                status = ConnectedJourneyStatus.COMPLETED,
                completedAtEpochMillis = journey.departureEpochMillis,
            )
        }
        compose.onNodeWithText("Offered journey history").assertIsDisplayed()
        compose.onAllNodesWithText("Current offered journeys").assertCountEquals(0)
        compose.onNodeWithText("Journey completed").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Mark journey complete").assertCountEquals(0)
    }

    @Test fun passedPendingRequestKeepsOnlyAuthorisedCleanupByRequestId() {
        val calls = mutableListOf<String>()
        compose.setContent { RydeTheme {
            ConnectedTripsScreen(
                connectedTripsContent(
                    ConnectedJourneySnapshot(listOf(journey), listOf(request)),
                    request.riderUid,
                    journey.departureEpochMillis,
                ),
                false, true, null, {}, {}, onWithdrawRequest = { calls += it },
            )
        } }
        compose.onNodeWithText("Departure has passed - this request can no longer be accepted")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Withdraw request").performClick()
        compose.onNodeWithText("Confirm withdrawal").performClick()
        compose.runOnIdle { assertEquals(listOf(request.id), calls) }
    }

    @Test fun driverPastRowsKeepSafeNamesCloseAcceptanceAndRouteDeclineByRequestId() {
        val accepted = request.copy(id = "accepted-request", status = ConnectedRequestStatus.ACCEPTED)
        val pending = request.copy(id = "pending-request", riderDisplayName = null)
        val decisions = mutableListOf<Pair<String, Boolean>>()
        compose.setContent { RydeTheme {
            ConnectedTripsScreen(
                connectedTripsContent(
                    ConnectedJourneySnapshot(listOf(journey), listOf(accepted, pending)),
                    journey.driverUid,
                    journey.departureEpochMillis,
                ),
                false, true, null, {}, {}, onDecideRequest = { id, accept -> decisions += id to accept },
                onCancelJourney = { error("Past journey cannot be cancelled") },
            )
        } }
        compose.onNodeWithText("Rider: Riley Rider").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Departure has passed - rider had a confirmed seat").assertIsDisplayed()
        compose.onNodeWithText("Rider").assertIsDisplayed()
        compose.onNodeWithText("Departure has passed - this request can no longer be accepted").assertIsDisplayed()
        compose.onAllNodesWithText("Accept").assertCountEquals(0)
        compose.onAllNodesWithText("Cancel journey").assertCountEquals(0)
        compose.onNodeWithText("Decline").performClick()
        compose.runOnIdle { assertEquals(listOf(pending.id to false), decisions) }
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
