package uk.rydeapp.ryde.ui.find

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.rydeapp.ryde.data.connected.ConnectedJourney
import uk.rydeapp.ryde.data.connected.ConnectedRequestStatus
import uk.rydeapp.ryde.data.connected.ConnectedSeatRequest
import uk.rydeapp.ryde.domain.PlaceMatch
import uk.rydeapp.ryde.domain.model.GeographicCoordinate
import uk.rydeapp.ryde.ui.home.ConnectedHomeJourney
import uk.rydeapp.ryde.ui.place.BroadAreaEndpoint
import uk.rydeapp.ryde.ui.place.BroadAreaPlaceSelectionPrompt
import uk.rydeapp.ryde.ui.theme.RydeTheme

class ConnectedFindScreenUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun routeFiltersSwapAndClearUpdateMatchingCount() {
        val items = listOf(item("out", "Mansfield", "Nottingham"), item("back", "Nottingham", "Mansfield"))
        compose.setContent { RydeTheme { ConnectedFindScreen(items, false, true, null, {}, {}, {}) } }
        scrollTo("2 upcoming journeys").assertIsDisplayed()
        origin().performScrollTo().performTextInput("  MANS  ")
        destination().performScrollTo().performTextInput("nott")
        scrollTo("1 matching journey").assertIsDisplayed()
        scrollTo("Mansfield → Nottingham").assertIsDisplayed()
        scrollTo("Swap origin and destination").performClick()
        origin().performScrollTo().assert(hasText("nott"))
        destination().performScrollTo().assert(hasText("  MANS  "))
        scrollTo("Nottingham → Mansfield").assertIsDisplayed()
        scrollTo("Clear filters").performClick()
        origin().performScrollTo().assert(hasText(""))
        destination().performScrollTo().assert(hasText(""))
        scrollTo("2 upcoming journeys").assertIsDisplayed()
        scrollTo("Clear filters").assertIsNotEnabled()
    }

    @Test fun noUpcomingJourneysAndNoFilterMatchesHaveDifferentEmptyStates() {
        val items = mutableStateOf(emptyList<ConnectedHomeJourney>())
        compose.setContent { RydeTheme { ConnectedFindScreen(items.value, false, true, null, {}, {}, {}) } }
        origin().performTextInput("York")
        scrollTo("No journeys currently available").assertIsDisplayed()
        compose.onAllNodesWithText("No journeys match your filters").assertCountEquals(0)
        compose.runOnIdle { items.value = listOf(item("out", "Mansfield", "Nottingham")) }
        scrollTo("No journeys match your filters").assertIsDisplayed()
        scrollTo("0 matching journeys").assertIsDisplayed()
        compose.onAllNodesWithText("No journeys currently available").assertCountEquals(0)
        // The no-match state has its own accessible reset action.
        compose.onAllNodesWithText("Clear filters").onLast().performScrollTo().performClick()
        scrollTo("Mansfield → Nottingham").assertIsDisplayed()
    }

    @Test fun calendarDateFiltersAndRestoresWithRouteDraftAndAnyDateClearsOnlyDate() {
        val today = LocalDate.now()
        val items = listOf(item("today", "Mansfield", "Nottingham", today),
            item("tomorrow", "Mansfield", "Derby", today.plusDays(1)))
        val restoration = StateRestorationTester(compose)
        restoration.setContent { RydeTheme { ConnectedFindScreen(items, false, true, null, {}, {}, {}) } }
        scrollTo("Any date").assertIsDisplayed()
        origin().performScrollTo().performTextInput("mans")
        scrollTo("Choose date").performClick()
        compose.onNodeWithText("Cancel").performClick()
        scrollTo("2 matching journeys").assertIsDisplayed()
        scrollTo("Choose date").performClick()
        compose.onNodeWithText("OK").performClick()
        scrollTo("1 matching journey").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        origin().performScrollTo().assert(hasText("mans"))
        field("connected-find-date").performScrollTo()
            .assert(hasText(today.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.UK))))
        scrollTo("1 matching journey").assertIsDisplayed()
        scrollTo("Any date").performClick()
        scrollTo("2 matching journeys").assertIsDisplayed()
        origin().performScrollTo().assert(hasText("mans"))
        scrollTo("Choose date").performClick()
        compose.onNodeWithText("OK").performClick()
        scrollTo("Clear filters").performClick()
        scrollTo("Any date").assertIsDisplayed()
        scrollTo("2 upcoming journeys").assertIsDisplayed()
    }

    @Test fun filteredCardsPreserveRequestStatusCallbacksAndBusyGuards() {
        val available = item("available", "Mansfield", "Nottingham")
        val pending = item("pending", "Derby", "Nottingham").copy(
            request = ConnectedSeatRequest("pending_rider", "pending", "driver", "rider", ConnectedRequestStatus.PENDING),
            canRequest = false,
        )
        val busy = mutableStateOf(false)
        val enabled = mutableStateOf(true)
        val requested = mutableListOf<String>()
        var managed = 0
        compose.setContent { RydeTheme {
            ConnectedFindScreen(listOf(available, pending), busy.value, enabled.value, null, {}, { requested += it }, { managed++ })
        } }
        origin().performTextInput("mans")
        scrollTo("Request one seat").performClick()
        compose.runOnIdle { assertEquals(listOf("available"), requested); busy.value = true }
        scrollTo("Request one seat").assertIsNotEnabled()
        scrollTo("Refresh").assertIsNotEnabled()
        compose.runOnIdle { busy.value = false; enabled.value = false }
        scrollTo("Request one seat").assertIsNotEnabled()
        origin().performScrollTo().performTextReplacement("derby")
        scrollTo("Your request is pending").assertIsDisplayed()
        scrollTo("Manage requests").performClick()
        compose.runOnIdle { assertEquals(1, managed); assertEquals(1, requested.size) }
    }

    @Test fun resultCardGroupsJourneySummaryAndExistingActions() {
        val opened = mutableListOf<String>()
        val requested = mutableListOf<String>()
        compose.setContent { RydeTheme {
            ConnectedFindScreen(
                journeys = listOf(item("available", "Mansfield", "Nottingham")),
                busy = false,
                requestsEnabled = true,
                message = null,
                onRefresh = {},
                onRequestSeat = { requested += it },
                onManageRequests = {},
                onOpenJourney = { opened += it },
            )
        } }

        val card = compose.onNodeWithTag("connected-find-result-available")
        card.performScrollTo().assert(
            hasAnyDescendant(hasText("Mansfield", substring = true)) and
                hasAnyDescendant(hasText("at 12:00", substring = true)) and
                hasAnyDescendant(hasText("seat", substring = true)) and
                hasAnyDescendant(hasText("Request one seat")) and
                hasAnyDescendant(hasText("View trip details")),
        )
        compose.onNodeWithText("View trip details").performClick()
        compose.onNodeWithText("Request one seat").performClick()

        compose.runOnIdle {
            assertEquals(listOf("available"), opened)
            assertEquals(listOf("available"), requested)
        }
    }

    @Test fun searchActionCarriesBothTypedAreasToResolutionBoundary() {
        var submitted: ConnectedFindCriteria? = null
        compose.setContent { RydeTheme {
            ConnectedFindScreen(
                journeys = listOf(item("out", "Mansfield", "Nottingham")),
                busy = false,
                requestsEnabled = true,
                message = null,
                onRefresh = {},
                onRequestSeat = {},
                onManageRequests = {},
                onResolveCriteria = { submitted = it },
            )
        } }

        origin().performTextInput("Mansfield")
        destination().performTextInput("Nottingham")
        field("connected-find-search").performScrollTo().performClick()

        compose.runOnIdle {
            assertEquals("Mansfield", submitted?.origin)
            assertEquals("Nottingham", submitted?.destination)
        }
    }

    @Test fun resolvedSearchRendersGeographicallyCloserJourneyFirst() {
        val riderOrigin = GeographicCoordinate(53.1432, -1.1984)
        val riderDestination = GeographicCoordinate(52.9548, -1.1581)
        val farther = item("farther", "Farther origin", "Farther destination").let { source ->
            source.copy(journey = source.journey.copy(
                originCoordinate = GeographicCoordinate(53.2232, -1.1984),
                destinationCoordinate = GeographicCoordinate(53.0448, -1.1581),
            ))
        }
        val closer = item("closer", "Closer origin", "Closer destination").let { source ->
            source.copy(journey = source.journey.copy(
                originCoordinate = GeographicCoordinate(53.1702, -1.1984),
                destinationCoordinate = GeographicCoordinate(52.9998, -1.1581),
            ))
        }
        val resolved = mutableStateOf<ConnectedFindCriteria?>(null)
        compose.setContent { RydeTheme {
            ConnectedFindScreen(
                journeys = listOf(farther, closer),
                busy = false,
                requestsEnabled = true,
                message = null,
                onRefresh = {},
                onRequestSeat = {},
                onManageRequests = {},
                resolvedCriteria = resolved.value,
                onResolveCriteria = { criteria ->
                    resolved.value = criteria.copy(
                        originCoordinate = riderOrigin,
                        destinationCoordinate = riderDestination,
                    )
                },
            )
        } }

        origin().performTextInput("Mansfield")
        destination().performTextInput("Nottingham")
        field("connected-find-search").performScrollTo().performClick()
        compose.onNodeWithTag("connected-find-list").performScrollToIndex(6)

        val closerTop = compose.onNodeWithText("Closer origin", substring = true)
            .fetchSemanticsNode().boundsInRoot.top
        val fartherTop = compose.onNodeWithText("Farther origin", substring = true)
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue(closerTop < fartherTop)
        compose.onNodeWithText(
            "Close area match · pickup ~3 km away · drop-off ~5 km away",
        ).assertIsDisplayed()
    }

    @Test fun legacyTextOnlyResultDoesNotInventGeographicMatchInformation() {
        compose.setContent { RydeTheme {
            ConnectedFindScreen(
                journeys = listOf(item("legacy", "Mansfield", "Nottingham")),
                busy = false,
                requestsEnabled = true,
                message = null,
                onRefresh = {},
                onRequestSeat = {},
                onManageRequests = {},
            )
        } }

        scrollTo("Request one seat").assertIsDisplayed()
        compose.onAllNodes(hasText("Close area match", substring = true)).assertCountEquals(0)
    }

    @Test fun ambiguousFromChoiceIsExplicitAndPreservesToDraft() {
        val london = PlaceMatch("Richmond — Greater London", GeographicCoordinate(51.4613, -0.3037))
        val yorkshire = PlaceMatch("Richmond — North Yorkshire", GeographicCoordinate(54.4037, -1.7375))
        val prompt = mutableStateOf<BroadAreaPlaceSelectionPrompt?>(null)
        var selected: PlaceMatch? = null
        compose.setContent { RydeTheme {
            ConnectedFindScreen(
                journeys = emptyList(),
                busy = false,
                requestsEnabled = true,
                message = null,
                onRefresh = {},
                onRequestSeat = {},
                onManageRequests = {},
                placeSelectionPrompt = prompt.value,
                onPlaceSelected = { endpoint, match ->
                    assertEquals(BroadAreaEndpoint.FROM, endpoint)
                    selected = match
                    prompt.value = null
                },
            )
        } }
        origin().performTextInput("Richmond")
        destination().performTextInput("Nottingham")
        compose.runOnIdle {
            prompt.value = BroadAreaPlaceSelectionPrompt(
                BroadAreaEndpoint.FROM,
                "Richmond",
                listOf(london, yorkshire),
            )
        }

        compose.onNodeWithText("Which From area did you mean by “Richmond”?").assertIsDisplayed()
        compose.onNodeWithText("Richmond — North Yorkshire").performClick()
        destination().performScrollTo().assert(hasText("Nottingham"))
        compose.runOnIdle { assertEquals(yorkshire, selected) }
    }

    private fun origin() = field("connected-find-origin")
    private fun destination() = field("connected-find-destination")
    private fun field(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("connected-find-list").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }
    private fun scrollTo(text: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("connected-find-list").performScrollToNode(hasText(text))
        return compose.onNodeWithText(text)
    }
    private fun item(id: String, origin: String, destination: String, date: LocalDate = LocalDate.now()) = ConnectedHomeJourney(
        ConnectedJourney(id, "driver", origin, destination,
            date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), 2, 1),
        request = null, canRequest = true,
    )
}
