package uk.rydeapp.ryde.ui.find

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test
import uk.rydeapp.ryde.data.connected.ConnectedJourney
import uk.rydeapp.ryde.data.connected.ConnectedRequestStatus
import uk.rydeapp.ryde.data.connected.ConnectedSeatRequest
import uk.rydeapp.ryde.domain.GeographicJourneyMatch
import uk.rydeapp.ryde.domain.model.GeographicCoordinate
import uk.rydeapp.ryde.ui.home.ConnectedHomeJourney
import uk.rydeapp.ryde.ui.place.BroadAreaCoordinates

class ConnectedFindUiTest {
    private val first = item("first", "Mansfield Woodhouse", "Nottingham", "2026-09-18T08:00:00Z")
    private val second = item("second", "Mansfield", "Derby", "2026-09-18T09:00:00Z")
    private val third = item("third", "Derby", "Nottingham", "2026-09-19T09:00:00Z")
    private val journeys = listOf(first, second, third)

    @Test fun blankFiltersKeepAllSuppliedItemsAndHaveNoActiveFilters() {
        val criteria = ConnectedFindCriteria("  ", "\t")
        assertFalse(criteria.hasFilters)
        assertEquals(journeys, filterConnectedFindJourneys(journeys, criteria))
        assertTrue(filterConnectedFindJourneys(emptyList(), criteria).isEmpty())
    }

    @Test fun originAndDestinationCanFilterIndependentlyOrTogether() {
        assertEquals(listOf(first, second), filterConnectedFindJourneys(journeys, ConnectedFindCriteria(origin = "Mansfield")))
        assertEquals(listOf(first, third), filterConnectedFindJourneys(journeys, ConnectedFindCriteria(destination = "Nottingham")))
        assertEquals(listOf(first), filterConnectedFindJourneys(journeys, ConnectedFindCriteria("Mansfield", "Nottingham")))
        assertTrue(filterConnectedFindJourneys(journeys, ConnectedFindCriteria("York")).isEmpty())
    }

    @Test fun matchingTrimsFiltersIgnoresCaseAndUsesSubstrings() {
        assertEquals(listOf(first), filterConnectedFindJourneys(journeys, ConnectedFindCriteria("  wOoDhOuSe  ", "  NOTT  ")))
        assertTrue(ConnectedFindCriteria("man").hasFilters)
    }

    @Test fun filteringPreservesInputDepartureOrderWithoutMutatingOrRebuildingItems() {
        val matches = filterConnectedFindJourneys(journeys, ConnectedFindCriteria(destination = "Nottingham"))
        assertEquals(listOf("first", "third"), matches.map { it.journey.id })
        assertSame(first, matches[0])
        assertSame(third, matches[1])
        assertEquals(listOf(first, second, third), journeys)
        // Filtering does not introduce its own sort, even when supplied a different order.
        assertEquals(listOf(third, first), filterConnectedFindJourneys(listOf(third, first), ConnectedFindCriteria()))
    }

    @Test fun dateAndRouteFiltersCombineAndAnyDateIncludesEveryDeparture() {
        val criteria = ConnectedFindCriteria(destination = "nott", departureDate = LocalDate.of(2026, 9, 19))
        assertTrue(criteria.hasFilters)
        assertEquals(listOf(third), filterConnectedFindJourneys(journeys, criteria, ZoneId.of("Europe/London")))
        assertEquals(listOf(first, third), filterConnectedFindJourneys(journeys, criteria.copy(departureDate = null)))
    }

    @Test fun matchingUsesLocalCalendarDateAcrossMidnightAndDaylightSavingBoundaries() {
        val boundary = item("boundary", "Mansfield", "Nottingham", "2026-09-18T23:30:00Z")
        val september18 = ConnectedFindCriteria(departureDate = LocalDate.of(2026, 9, 18))
        val september19 = september18.copy(departureDate = LocalDate.of(2026, 9, 19))
        assertEquals(listOf(boundary), filterConnectedFindJourneys(listOf(boundary), september19, ZoneId.of("Europe/London")))
        assertTrue(filterConnectedFindJourneys(listOf(boundary), september18, ZoneId.of("Europe/London")).isEmpty())
        assertEquals(listOf(boundary), filterConnectedFindJourneys(listOf(boundary), september18, ZoneId.of("UTC")))
        val west = item("west", "Mansfield", "Nottingham", "2026-09-19T00:30:00Z")
        assertEquals(listOf(west), filterConnectedFindJourneys(listOf(west), september18, ZoneId.of("America/Los_Angeles")))
        val winter = item("winter", "Mansfield", "Nottingham", "2026-12-18T23:30:00Z")
        assertEquals(listOf(winter), filterConnectedFindJourneys(listOf(winter),
            ConnectedFindCriteria(departureDate = LocalDate.of(2026, 12, 18)), ZoneId.of("Europe/London")))
    }

    @Test fun defaultTimezoneIsTheDeviceTimezone() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))
            val boundary = item("boundary", "Mansfield", "Nottingham", "2026-09-18T23:30:00Z")
            assertEquals(listOf(boundary), filterConnectedFindJourneys(listOf(boundary),
                ConnectedFindCriteria(departureDate = LocalDate.of(2026, 9, 19))))
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test fun everyRequestStatusAndActionFlagIsPreservedIncludingFullJourneys() {
        ConnectedRequestStatus.entries.forEach { status ->
            val request = ConnectedSeatRequest("first_rider", "first", "driver", "rider", status)
            val source = first.copy(request = request, canRequest = false, canRerequest = status == ConnectedRequestStatus.CANCELLED)
            val match = filterConnectedFindJourneys(listOf(source), ConnectedFindCriteria("mans", "nott")).single()
            assertSame(source, match)
            assertSame(request, match.request)
            assertFalse(match.canRequest)
            assertEquals(source.canRerequest, match.canRerequest)
        }
        val full = first.copy(journey = first.journey.copy(seatsRemaining = 0), canRequest = false)
        assertSame(full, filterConnectedFindJourneys(listOf(full), ConnectedFindCriteria("mans")).single())
    }

    @Test fun resolvedCoordinatesReachSearchBoundaryWithoutChangingTextMatching() {
        val origin = GeographicCoordinate(53.1432, -1.1984)
        val destination = GeographicCoordinate(52.9548, -1.1581)
        val typed = ConnectedFindCriteria("Mansfield", "Nottingham")
        val resolved = typed.withResolvedBroadAreas(BroadAreaCoordinates(origin, destination))

        assertEquals(origin, resolved.originCoordinate)
        assertEquals(destination, resolved.destinationCoordinate)
        assertTrue(resolved.sameTypedAreasAs(typed.copy(departureDate = LocalDate.of(2027, 1, 1))))
        assertEquals(listOf(first), filterConnectedFindJourneys(journeys, resolved))
    }

    @Test fun independentlyUnresolvedEndpointReachesBoundaryAsNullWithoutChangingFilter() {
        val destination = GeographicCoordinate(52.9548, -1.1581)
        val resolved = ConnectedFindCriteria("Unknown", "Nottingham").withResolvedBroadAreas(
            BroadAreaCoordinates(from = null, to = destination),
        )

        assertNull(resolved.originCoordinate)
        assertEquals(destination, resolved.destinationCoordinate)
        assertTrue(filterConnectedFindJourneys(journeys, resolved).isEmpty())
    }

    @Test fun connectedFindExposesCompatibleGeographicStateWithoutChangingVisibleResult() {
        val origin = GeographicCoordinate(53.1432, -1.1984)
        val destination = GeographicCoordinate(52.9548, -1.1581)
        val located = first.copy(
            journey = first.journey.copy(
                originCoordinate = origin,
                destinationCoordinate = destination,
            ),
        )
        val criteria = ConnectedFindCriteria(
            origin = "Mansfield",
            destination = "Nottingham",
            originCoordinate = origin,
            destinationCoordinate = destination,
        )

        val result = assessConnectedFindJourneys(listOf(located), criteria).single()

        assertSame(located, result.item)
        assertTrue(result.geographicMatch is GeographicJourneyMatch.Compatible)
    }

    @Test fun geographicallyIncompatibleJourneyRemainsVisibleForMigrationSafety() {
        val riderOrigin = GeographicCoordinate(53.1432, -1.1984)
        val destination = GeographicCoordinate(52.9548, -1.1581)
        val locatedElsewhere = first.copy(
            journey = first.journey.copy(
                originCoordinate = GeographicCoordinate(51.4613, -0.3037),
                destinationCoordinate = destination,
            ),
        )
        val criteria = ConnectedFindCriteria(
            origin = "Mansfield",
            destination = "Nottingham",
            originCoordinate = riderOrigin,
            destinationCoordinate = destination,
        )

        val result = assessConnectedFindJourneys(listOf(locatedElsewhere), criteria).single()

        assertSame(locatedElsewhere, result.item)
        assertTrue(result.geographicMatch is GeographicJourneyMatch.Incompatible)
    }

    @Test fun legacyJourneyOrUnresolvedRiderHasInsufficientGeographicDataAndStaysVisible() {
        val coordinate = GeographicCoordinate(53.1432, -1.1984)
        val unresolvedRider = ConnectedFindCriteria("Mansfield", "Nottingham")
        val partiallyResolvedRider = unresolvedRider.copy(originCoordinate = coordinate)

        listOf(unresolvedRider, partiallyResolvedRider).forEach { criteria ->
            val result = assessConnectedFindJourneys(listOf(first), criteria).single()
            assertSame(first, result.item)
            assertEquals(GeographicJourneyMatch.InsufficientGeographicData, result.geographicMatch)
        }
    }

    private fun item(id: String, origin: String, destination: String, departure: String) = ConnectedHomeJourney(
        ConnectedJourney(id, "driver", origin, destination, Instant.parse(departure).toEpochMilli(), 2, 1),
        request = null, canRequest = true,
    )
}
