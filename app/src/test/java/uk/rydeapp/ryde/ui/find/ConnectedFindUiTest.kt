package uk.rydeapp.ryde.ui.find

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test
import uk.rydeapp.ryde.data.connected.ConnectedJourney
import uk.rydeapp.ryde.data.connected.ConnectedJourneySnapshot
import uk.rydeapp.ryde.data.connected.ConnectedJourneyStatus
import uk.rydeapp.ryde.data.connected.ConnectedRequestStatus
import uk.rydeapp.ryde.data.connected.ConnectedRouteWaypoint
import uk.rydeapp.ryde.data.connected.ConnectedSeatRequest
import uk.rydeapp.ryde.domain.BroadAreaJourneyMatchPolicy
import uk.rydeapp.ryde.domain.GeographicDistance
import uk.rydeapp.ryde.domain.GeographicDistanceCalculator
import uk.rydeapp.ryde.domain.GeographicJourneyMatch
import uk.rydeapp.ryde.domain.model.GeographicCoordinate
import uk.rydeapp.ryde.ui.home.ConnectedHomeJourney
import uk.rydeapp.ryde.ui.home.connectedHomeJourneys
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

    @Test fun anyTimePreservesExistingDateResults() {
        val criteria = ConnectedFindCriteria(
            departureDate = LocalDate.of(2026, 9, 18),
            preferredDepartureMinutes = null,
        )

        assertEquals(
            listOf(first, second),
            filterConnectedFindJourneys(journeys, criteria, ZoneId.of("UTC")),
        )
    }

    @Test fun preferredTimeIncludesThirtyMinuteBoundariesAndExcludesMinuteBeyond() {
        val date = LocalDate.of(2026, 10, 1)
        val zone = ZoneId.of("Europe/London")
        val candidates = listOf(
            localItem("minus-31", date, 8 * 60 + 29, zone),
            localItem("minus-30", date, 8 * 60 + 30, zone),
            localItem("exact", date, 9 * 60, zone),
            localItem("plus-30", date, 9 * 60 + 30, zone),
            localItem("plus-31", date, 9 * 60 + 31, zone),
        )
        val criteria = ConnectedFindCriteria(
            departureDate = date,
            preferredDepartureMinutes = 9 * 60,
        )

        assertEquals(
            listOf("minus-30", "exact", "plus-30"),
            filterConnectedFindJourneys(candidates, criteria, zone).map { it.journey.id },
        )
    }

    @Test fun dateEligibilityIsAppliedBeforePreferredTime() {
        val zone = ZoneId.of("Europe/London")
        val selectedDate = LocalDate.of(2026, 10, 1)
        val candidates = listOf(
            localItem("selected-date", selectedDate, 9 * 60, zone),
            localItem("next-date", selectedDate.plusDays(1), 9 * 60, zone),
        )
        val criteria = ConnectedFindCriteria(
            departureDate = selectedDate,
            preferredDepartureMinutes = 9 * 60,
        )

        assertEquals(
            listOf("selected-date"),
            filterConnectedFindJourneys(candidates, criteria, zone).map { it.journey.id },
        )
    }

    @Test fun preferredTimeUsesTheInjectedLocalZoneNearUtcMidnight() {
        val boundary = item("boundary", "Mansfield", "Nottingham", "2026-09-18T23:15:00Z")
        val criteria = ConnectedFindCriteria(
            departureDate = LocalDate.of(2026, 9, 19),
            preferredDepartureMinutes = 15,
        )

        assertEquals(
            listOf(boundary),
            filterConnectedFindJourneys(listOf(boundary), criteria, ZoneId.of("Europe/London")),
        )
        assertTrue(filterConnectedFindJourneys(listOf(boundary), criteria, ZoneId.of("UTC")).isEmpty())
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
            val source = first.copy(
                journey = first.journey.copy(seatsRemaining = 0),
                request = request,
                canRequest = false,
                canRerequest = false,
            )
            val match = matchConnectedFindJourneys(
                listOf(source), ConnectedFindCriteria("mans", "nott"),
            ).single().item
            assertSame(source, match)
            assertSame(request, match.request)
            assertFalse(match.canRequest)
            assertEquals(source.canRerequest, match.canRerequest)
        }
        val unrelatedFull = first.copy(journey = first.journey.copy(seatsRemaining = 0), canRequest = false)
        assertTrue(matchConnectedFindJourneys(
            listOf(unrelatedFull), ConnectedFindCriteria("mans"),
        ).isEmpty())
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

    @Test fun resolvedTwoEndedFindDerivesRequestedSegmentAndIncompleteSearchesDoNot() {
        val from = GeographicCoordinate(53.0380, -1.2034)
        val to = GeographicCoordinate(52.9548, -1.1581)
        val resolved = ConnectedFindCriteria(
            origin = "  Hucknall ",
            destination = " Nottingham ",
            originCoordinate = from,
            destinationCoordinate = to,
        )

        assertEquals("Hucknall", resolved.requestedBroadAreaSegmentOrNull()?.originArea)
        assertEquals("Nottingham", resolved.requestedBroadAreaSegmentOrNull()?.destinationArea)
        assertNull(resolved.copy(originCoordinate = null).requestedBroadAreaSegmentOrNull())
        assertNull(resolved.copy(destination = "").requestedBroadAreaSegmentOrNull())
        assertNull(resolved.copy(origin = "Nottingham").requestedBroadAreaSegmentOrNull())
        assertNull(ConnectedFindCriteria(departureDate = LocalDate.of(2026, 9, 18))
            .requestedBroadAreaSegmentOrNull())
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

    @Test fun exactSameAreaJourneyMatchesGeographically() {
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

        val result = matchConnectedFindJourneys(listOf(located), criteria).single()

        assertSame(located, result.item)
        assertTrue(result.geographicMatch is GeographicJourneyMatch.Compatible)
    }

    @Test fun nearbyBroadAreasMatchGeographicallyEvenWhenTheirTextDoesNot() {
        val offeredOrigin = GeographicCoordinate(53.1432, -1.1984)
        val offeredDestination = GeographicCoordinate(52.9548, -1.1581)
        val located = first.copy(
            journey = first.journey.copy(
                originCoordinate = offeredOrigin,
                destinationCoordinate = offeredDestination,
            ),
        )
        val criteria = ConnectedFindCriteria(
            origin = "Nearby origin",
            destination = "Nearby destination",
            originCoordinate = GeographicCoordinate(53.18, -1.20),
            destinationCoordinate = GeographicCoordinate(52.99, -1.15),
        )

        val result = matchConnectedFindJourneys(listOf(located), criteria).single()

        assertSame(located, result.item)
        assertTrue(result.geographicMatch is GeographicJourneyMatch.Compatible)
    }

    @Test fun geographicMatchExplanationUsesRoundedCompatibleEndpointDistances() {
        val result = ConnectedFindJourneyResult(
            item = first,
            geographicMatch = GeographicJourneyMatch.Compatible(
                originDistance = GeographicDistance(2.49),
                destinationDistance = GeographicDistance(4.5),
            ),
        )

        assertEquals(
            ConnectedFindGeographicMatchExplanation(
                pickupAreaKilometres = 2,
                dropOffAreaKilometres = 5,
            ),
            result.geographicMatchExplanation,
        )
    }

    @Test fun unavailableGeographicDistancesProduceNoMatchExplanation() {
        val legacy = ConnectedFindJourneyResult(
            item = first,
            geographicMatch = GeographicJourneyMatch.InsufficientGeographicData,
        )
        val incompatible = ConnectedFindJourneyResult(
            item = first,
            geographicMatch = GeographicJourneyMatch.Incompatible(
                originDistance = GeographicDistance(16.0),
                destinationDistance = GeographicDistance(1.0),
            ),
        )

        assertNull(legacy.geographicMatchExplanation)
        assertNull(incompatible.geographicMatchExplanation)
    }

    @Test fun actualConnectedFindResultsRankTheCloserJourneyAtBothEndsFirst() {
        val farther = locatedItem("farther", originKilometres = 5.0, destinationKilometres = 6.0,
            departure = "2026-09-18T08:00:00Z")
        val closer = locatedItem("closer", originKilometres = 2.0, destinationKilometres = 3.0,
            departure = "2026-09-18T10:00:00Z")
        val outsideTolerance = locatedItem("outside", originKilometres = 16.0, destinationKilometres = 0.0,
            departure = "2026-09-18T07:00:00Z")

        val results = matchConnectedFindJourneys(
            journeys = listOf(farther, outsideTolerance, closer),
            criteria = rankedCriteria(),
            policy = encodedDistancePolicy,
        )

        assertEquals(listOf("closer", "farther"), results.map { it.item.journey.id })
        assertEquals(listOf(5.0, 11.0), results.map { it.geographicScore!!.combinedEndpointDistance.kilometres })
        assertEquals(
            listOf(
                ConnectedFindGeographicMatchExplanation(2, 3),
                ConnectedFindGeographicMatchExplanation(5, 6),
            ),
            results.map { it.geographicMatchExplanation },
        )
    }

    @Test fun rankingUsesCombinedDistanceWhenEndpointsHaveATradeOff() {
        val closerAtOrigin = locatedItem(
            "closer-origin", originKilometres = 1.0, destinationKilometres = 8.0,
        )
        val closerAtDestination = locatedItem(
            "closer-destination", originKilometres = 4.0, destinationKilometres = 2.0,
        )

        val results = matchConnectedFindJourneys(
            listOf(closerAtOrigin, closerAtDestination),
            rankedCriteria(),
            policy = encodedDistancePolicy,
        )

        assertEquals(listOf("closer-destination", "closer-origin"), results.map { it.item.journey.id })
    }

    @Test fun equalGeographicScoresUseDepartureThenJourneyIdAsStableTieBreaks() {
        val later = locatedItem(
            "a-later", originKilometres = 2.0, destinationKilometres = 4.0,
            departure = "2026-09-18T10:00:00Z",
        )
        val earlyZ = locatedItem(
            "z-early", originKilometres = 3.0, destinationKilometres = 3.0,
            departure = "2026-09-18T08:00:00Z",
        )
        val earlyA = locatedItem(
            "a-early", originKilometres = 1.0, destinationKilometres = 5.0,
            departure = "2026-09-18T08:00:00Z",
        )

        val results = matchConnectedFindJourneys(
            listOf(later, earlyZ, earlyA),
            rankedCriteria(),
            policy = encodedDistancePolicy,
        )

        assertEquals(listOf("a-early", "z-early", "a-later"), results.map { it.item.journey.id })
    }

    @Test fun activePreferredTimeUsesProximityAfterExistingGeographicRanking() {
        val early = locatedItem(
            "early", originKilometres = 2.0, destinationKilometres = 4.0,
            departure = "2026-09-18T08:40:00Z",
        )
        val exact = locatedItem(
            "exact", originKilometres = 3.0, destinationKilometres = 3.0,
            departure = "2026-09-18T09:00:00Z",
        )
        val late = locatedItem(
            "late", originKilometres = 1.0, destinationKilometres = 5.0,
            departure = "2026-09-18T09:20:00Z",
        )

        val results = matchConnectedFindJourneys(
            listOf(late, early, exact),
            rankedCriteria().copy(
                departureDate = LocalDate.of(2026, 9, 18),
                preferredDepartureMinutes = 9 * 60,
            ),
            zoneId = ZoneId.of("UTC"),
            policy = encodedDistancePolicy,
        )

        assertEquals(listOf("exact", "early", "late"), results.map { it.item.journey.id })
    }

    @Test fun preferredTimeDoesNotOverrideGeographyOrAvailabilityEligibility() {
        val date = LocalDate.of(2026, 9, 18)
        val eligible = locatedItem("eligible", 2.0, 2.0, "2026-09-18T09:00:00Z")
        val geographicallyIncompatible = locatedItem(
            "incompatible", 16.0, 2.0, "2026-09-18T09:00:00Z",
        )
        val unavailable = locatedItem("unavailable", 2.0, 2.0, "2026-09-18T09:00:00Z")
            .copy(canRequest = false)
        val criteria = rankedCriteria().copy(
            departureDate = date,
            preferredDepartureMinutes = 9 * 60,
        )

        val results = matchConnectedFindJourneys(
            listOf(unavailable, geographicallyIncompatible, eligible),
            criteria,
            zoneId = ZoneId.of("UTC"),
            policy = encodedDistancePolicy,
        )

        assertEquals(listOf("eligible"), results.map { it.item.journey.id })
    }

    @Test fun legacyFallbackResultsAreUnscoredAndDeterministicAfterGeographicResults() {
        val legacyZ = item("z-legacy", "Mansfield", "Nottingham", "2026-09-18T08:00:00Z")
        val geographic = locatedItem(
            "geographic", originKilometres = 5.0, destinationKilometres = 5.0,
            departure = "2026-09-18T10:00:00Z",
        )
        val legacyA = item("a-legacy", "Mansfield", "Nottingham", "2026-09-18T08:00:00Z")

        val results = matchConnectedFindJourneys(
            listOf(legacyZ, geographic, legacyA),
            rankedCriteria(),
            policy = encodedDistancePolicy,
        )

        assertEquals(listOf("geographic", "a-legacy", "z-legacy"), results.map { it.item.journey.id })
        assertTrue(results.first().geographicScore != null)
        assertTrue(results.drop(1).all { it.geographicScore == null })
    }

    @Test fun originOutsideGeographicToleranceDoesNotMatch() {
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

        assertTrue(matchConnectedFindJourneys(listOf(locatedElsewhere), criteria).isEmpty())
    }

    @Test fun destinationMustAlsoBeWithinGeographicTolerance() {
        val origin = GeographicCoordinate(53.1432, -1.1984)
        val offeredDestination = GeographicCoordinate(52.9548, -1.1581)
        val located = first.copy(
            journey = first.journey.copy(
                originCoordinate = origin,
                destinationCoordinate = offeredDestination,
            ),
        )
        val criteria = ConnectedFindCriteria(
            origin = "Mansfield",
            destination = "Nottingham",
            originCoordinate = origin,
            destinationCoordinate = GeographicCoordinate(51.5074, -0.1278),
        )

        assertTrue(matchConnectedFindJourneys(listOf(located), criteria).isEmpty())
    }

    @Test fun riderCanMatchFromDeclaredViaToDriverDestination() {
        val origin = GeographicCoordinate(53.1432, -1.1984)
        val via = GeographicCoordinate(53.0380, -1.2034)
        val destination = GeographicCoordinate(52.9548, -1.1581)
        val routed = first.copy(journey = first.journey.copy(
            originCoordinate = origin,
            destinationCoordinate = destination,
            routeWaypoints = listOf(ConnectedRouteWaypoint("Hucknall", via)),
        ))
        val criteria = ConnectedFindCriteria(
            origin = "Hucknall",
            destination = "Nottingham",
            originCoordinate = via,
            destinationCoordinate = destination,
        )

        assertEquals("first", matchConnectedFindJourneys(listOf(routed), criteria).single().item.journey.id)
    }

    @Test fun riderCannotMatchDeclaredRouteInReverse() {
        val origin = GeographicCoordinate(53.1432, -1.1984)
        val via = GeographicCoordinate(53.0380, -1.2034)
        val destination = GeographicCoordinate(52.9548, -1.1581)
        val routed = first.copy(journey = first.journey.copy(
            originCoordinate = origin,
            destinationCoordinate = destination,
            routeWaypoints = listOf(ConnectedRouteWaypoint("Hucknall", via)),
        ))
        val criteria = ConnectedFindCriteria(
            origin = "Nottingham",
            destination = "Hucknall",
            originCoordinate = destination,
            destinationCoordinate = via,
        )

        assertTrue(matchConnectedFindJourneys(listOf(routed), criteria).isEmpty())
    }

    @Test fun journeyWithoutViaRetainsEndpointMatcher() {
        val origin = GeographicCoordinate(53.1432, -1.1984)
        val destination = GeographicCoordinate(52.9548, -1.1581)
        val noVia = first.copy(journey = first.journey.copy(
            originCoordinate = origin,
            destinationCoordinate = destination,
        ))
        val criteria = ConnectedFindCriteria(
            origin = "Nearby Mansfield",
            destination = "Nearby Nottingham",
            originCoordinate = GeographicCoordinate(53.18, -1.20),
            destinationCoordinate = GeographicCoordinate(52.99, -1.15),
        )

        assertTrue(matchConnectedFindJourneys(listOf(noVia), criteria).single().geographicMatch is GeographicJourneyMatch.Compatible)
    }

    @Test fun missingCoordinatesFallBackToTextWithoutWideningTheSearch() {
        val origin = GeographicCoordinate(53.1432, -1.1984)
        val destination = GeographicCoordinate(52.9548, -1.1581)
        val located = first.copy(
            journey = first.journey.copy(
                originCoordinate = origin,
                destinationCoordinate = destination,
            ),
        )
        val unresolvedRider = ConnectedFindCriteria("Mansfield", "Nottingham")
        val partiallyResolvedRider = unresolvedRider.copy(originCoordinate = origin)
        val resolvedRider = unresolvedRider.copy(
            originCoordinate = origin,
            destinationCoordinate = destination,
        )

        listOf(unresolvedRider, partiallyResolvedRider, resolvedRider).forEach { criteria ->
            val result = matchConnectedFindJourneys(listOf(first), criteria).single()
            assertSame(first, result.item)
            assertEquals(GeographicJourneyMatch.InsufficientGeographicData, result.geographicMatch)
        }
        val unresolvedResult = matchConnectedFindJourneys(listOf(located), unresolvedRider).single()
        assertSame(located, unresolvedResult.item)
        assertEquals(
            GeographicJourneyMatch.InsufficientGeographicData,
            unresolvedResult.geographicMatch,
        )
        assertTrue(matchConnectedFindJourneys(
            listOf(first), resolvedRider.copy(origin = "Sheffield"),
        ).isEmpty())
        assertTrue(matchConnectedFindJourneys(
            listOf(located), unresolvedRider.copy(origin = "Sheffield"),
        ).isEmpty())
    }

    @Test fun geographicMatchingCannotRestoreJourneysExcludedByConnectedDiscoveryRules() {
        val origin = GeographicCoordinate(53.1432, -1.1984)
        val destination = GeographicCoordinate(52.9548, -1.1581)
        val located = ConnectedJourney(
            id = "eligible",
            driverUid = "driver",
            originArea = "Mansfield",
            destinationArea = "Nottingham",
            departureEpochMillis = 200,
            seatCapacity = 2,
            seatsRemaining = 1,
            originCoordinate = origin,
            destinationCoordinate = destination,
        )
        val snapshot = ConnectedJourneySnapshot(listOf(
            located,
            located.copy(id = "full", seatsRemaining = 0),
            located.copy(id = "own", driverUid = "rider"),
            located.copy(id = "cancelled", status = ConnectedJourneyStatus.CANCELLED),
            located.copy(id = "completed", status = ConnectedJourneyStatus.COMPLETED),
            located.copy(id = "departed", departureEpochMillis = 100),
        ))
        val discoverable = connectedHomeJourneys(snapshot, viewerUid = "rider", nowEpochMillis = 100)
        val criteria = ConnectedFindCriteria(
            origin = "Nearby origin",
            destination = "Nearby destination",
            originCoordinate = GeographicCoordinate(53.18, -1.20),
            destinationCoordinate = GeographicCoordinate(52.99, -1.15),
        )

        val results = matchConnectedFindJourneys(discoverable, criteria)

        assertEquals(listOf("eligible"), results.map { it.item.journey.id })
        assertTrue(results.all { it.geographicMatch is GeographicJourneyMatch.Compatible })
    }

    private fun item(id: String, origin: String, destination: String, departure: String) = ConnectedHomeJourney(
        ConnectedJourney(id, "driver", origin, destination, Instant.parse(departure).toEpochMilli(), 2, 1),
        request = null, canRequest = true,
    )

    private fun localItem(
        id: String,
        date: LocalDate,
        minuteOfDay: Int,
        zoneId: ZoneId,
    ): ConnectedHomeJourney = ConnectedHomeJourney(
        ConnectedJourney(
            id = id,
            driverUid = "driver",
            originArea = "Mansfield",
            destinationArea = "Nottingham",
            departureEpochMillis = date.atTime(minuteOfDay / 60, minuteOfDay % 60)
                .atZone(zoneId).toInstant().toEpochMilli(),
            seatCapacity = 2,
            seatsRemaining = 1,
        ),
        request = null,
        canRequest = true,
    )

    private fun locatedItem(
        id: String,
        originKilometres: Double,
        destinationKilometres: Double,
        departure: String = "2026-09-18T08:00:00Z",
    ): ConnectedHomeJourney = item(id, "Mansfield", "Nottingham", departure).let { source ->
        source.copy(
            journey = source.journey.copy(
                originCoordinate = GeographicCoordinate(0.0, originKilometres),
                destinationCoordinate = GeographicCoordinate(1.0, destinationKilometres),
            ),
        )
    }

    private fun rankedCriteria() = ConnectedFindCriteria(
        origin = "Mansfield",
        destination = "Nottingham",
        originCoordinate = GeographicCoordinate(0.0, 0.0),
        destinationCoordinate = GeographicCoordinate(1.0, 0.0),
    )

    private val encodedDistancePolicy = BroadAreaJourneyMatchPolicy(
        distanceCalculator = GeographicDistanceCalculator { _, offered ->
            GeographicDistance(offered.longitude)
        },
    )
}
