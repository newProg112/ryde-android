# Backend migration boundary

## Phase 9C-1 architecture and schema

`LOCAL_DEMO` remains Firebase-free and permanently available. In `CONNECTED`, Firebase Auth
owns the session and Firestore owns the profile plus the narrow 9C-1 journey/request/confirmed-trip slice.
Firebase types stay behind connected gateways/stores; the connected UI does not expose
unmigrated capabilities. Reads use the Firestore server source and an in-memory cache.
Failures become safe messages, cancellation is rethrown, and sign-out clears session data.
Firebase calls have a 15-second boundary timeout. A background refresh keeps an existing
Ready profile visible; initial load failures transition to the safe error state rather than
remaining in Loading.

```text
/users/{uid}
  uid: string                 # equals path uid; immutable
  displayName: string         # 1..60 characters

/users/{uid}/savedPlaces/home
/users/{uid}/savedPlaces/work
  uid: string                 # equals path uid; immutable
  label: "Home" | "Work"      # fixed by document ID
  area: string                # broad area, 1..60 chars, no digit or comma
```

No private address, postcode, coordinates, exact location, or live location belongs in this
schema. `firestore.rules` validates exact field sets and types, grants access only when the
authenticated UID owns the path, and ends with an explicit default deny.

## Emulator setup

Download the Android config for application ID `uk.rydeapp.ryde` in project `ryde-79893` and
place it locally at `app/google-services.json`. It is required for connected builds and
ignored by Git; local-demo builds work without it. Never copy its values into tracked files
or logs.

```powershell
npm install
firebase emulators:start --only auth,firestore --project ryde-79893
```

Auth uses `9099`, Firestore `8080`, and the Emulator UI `4000`. The Android Emulator reaches
the development host as `10.0.2.2`, which the debug Firebase factory configures before any
SDK operation. This is the manual CONNECTED environment; its disposable accounts, profiles,
journeys, requests and confirmed trips remain under project namespace `ryde-79893`.

Run the automated rules suite in its separate demo namespace and on its dedicated ports with:

```powershell
npm run test:rules:emulator
```

That command uses `firebase.rules-test.json`, project `demo-ryde-rules-test`, Auth `9199`, and
Firestore `8180`. It can run while the manual emulators are running because neither its ports nor
its project namespace overlap. The test harness fails before clearing Firestore unless Firebase
CLI supplies those exact automated-test identifiers; do not run `npm run test:rules` directly.
The rules-test demo project has no production resources, and `emulators:exec` starts and stops
only its dedicated local emulators without deployment.

## Mode selection

Local demo is the safe default (and does not need running emulators):

```powershell
.\gradlew.bat installDebug
.\gradlew.bat installDebug -PrydeAppMode=LOCAL_DEMO
```

Connected mode must be selected explicitly for debug:

```powershell
.\gradlew.bat installDebug -PrydeAppMode=CONNECTED
```

The property becomes `BuildConfig.RYDE_APP_MODE`; there is no user-facing switch. Release
always compiles `LOCAL_DEMO`, regardless of the property, and connected construction also
checks `BuildConfig.DEBUG`.

## Deployment status

Nothing in Phase 9C-1 has been deployed. The commands above create only disposable emulator
users/documents and load rules locally. Production Firestore retains its existing deny-all
rules. Later Phase 9C slices still need Circles and trip lifecycle;
messaging, notifications, maps/GPS, payments, Functions, Storage and other excluded services
also remain out of this slice.

Phase 9A keeps the product in `LOCAL_DEMO` by default. `RydeAppComposition` creates one
session-local `FakeRydeRepository` in that mode and exposes an explicitly fictional Sam
session. `CONNECTED` never falls back to the fake: callers must inject a configured
repository. There is no user-facing mode switch.

`RydeRepository` remains the single app dependency and aggregates these capabilities:

- `HomeContentRepository` — demo/home content;
- `AccountSessionRepository` — observable session restoration and identity;
- `ProfileRepository` — profile, saved places, trust, history and repeat prefills;
- `RideDiscoveryRepository` — matching and outgoing seat requests;
- `OfferedJourneyRepository` — offers and incoming request decisions;
- `TripRepository` — confirmed trips and lifecycle;
- `CircleRepository` — memberships;
- `CoordinationRepository` — conversations, messages and activity;
- `ObservableRydeRepository` — immutable app snapshots and refresh.

Cached/domain reads stay synchronous where the current UI needs an immediate policy result.
Writes are suspend-capable. Long-lived session and app data use `StateFlow`; the
Compose-independent `RydeAppStateHolder` observes them, owns command refresh/invalidation,
and converts failures to a safe app-level retry state. Repository implementations own all
backend-to-domain conversion.

## Migration sequence

- **9B:** Firebase-emulator session/Auth, profile and saved places.
- **9C-1:** broad-area discovery, offers and one-seat request decisions.
- **9C lifecycle:** private confirmed trips plus rider/driver cancellation and retained history.
- **Messaging Phase 1:** participant-private confirmed-trip text coordination with an
  open-screen realtime stream.
- **Later:** migrate only the remaining explicitly selected capabilities while the local fake
  remains available for demos and tests.

Pricing, eligibility, broad-area privacy, trust/safety overrides, message content limits,
participant access and journey lifecycle transitions remain Compose-, Context- and
Firebase-free domain policies. Connected phases must enforce the same invariants with
Firebase Security Rules and trusted server operations. UI checks are guidance only; they
must never become authoritative authorization or integrity controls.

### 9C-1 two-account journey slice

The first 9C slice adds only these emulator-owned documents:

```text
/journeys/{journeyId}
  driverUid: string          # authenticated creator; immutable
  originArea: string         # broad area, 1..60 chars, no digit or comma
  destinationArea: string    # broad area, 1..60 chars, no digit or comma
  departureAt: timestamp
  seatCapacity: int          # 1..8; immutable
  seatsRemaining: int        # initially capacity; 0..capacity
  status: "OPEN" | "CANCELLED"
  cancelledAt: timestamp     # required only for CANCELLED; server time

/seatRequests/{journeyId}_{riderUid}
  journeyId: string          # immutable
  driverUid: string          # must equal the referenced journey owner; immutable
  riderUid: string           # authenticated creator; immutable
  status: "PENDING" | "ACCEPTED" | "DECLINED" | "CANCELLED" | "CANCELLED_AFTER_ACCEPTANCE"

/journeyAcceptanceGuards/{journeyId}
  driverUid: string          # equals the referenced journey owner; immutable
  acceptanceCount: int       # initially 0; equals capacity minus remaining
  lastAcceptedRequestId: string | null
  lastCancelledRequestId: string # optional; set by accepted-seat cancellation

/confirmedTrips/{acceptedRequestId}
  journeyId: string          # copied from the accepted request; immutable
  acceptedRequestId: string  # equals the document ID; immutable
  driverUid: string          # copied from the accepted request; immutable
  riderUid: string           # copied from the accepted request; immutable
  originArea: string         # copied from the journey broad area; immutable
  destinationArea: string    # copied from the journey broad area; immutable
  departureAt: timestamp     # copied from the journey; immutable
  status: "CONFIRMED" | "CANCELLED_BY_RIDER"
  cancelledAt: timestamp     # required only for CANCELLED_BY_RIDER; server time

/confirmedTrips/{acceptedRequestId}/messages/{opaqueMessageId}
  senderUid: string          # exactly the authenticated driver or rider
  body: string               # nonblank, <= 500 chars, no prohibited control chars
  sentAt: timestamp          # server timestamp; immutable
```

Any authenticated emulator user may read the intentionally small journey document so that
offers can be discovered. Only the owning driver may create or cancel their journey. Remaining
seats change only through linked acceptance or rider cancellation. A request can be read only
by its rider or the referenced driver. Rules require
the deterministic request ID, derive/verify both participants against Auth and the referenced
journey, reject self-requests, and permit only `PENDING -> ACCEPTED|DECLINED` by the driver.
The rider alone may cancel a pending request. The rider may reopen that same deterministic
document from `CANCELLED -> PENDING` only while the referenced journey is still upcoming, open,
has capacity, and remains consistent with its acceptance guard. Drivers cannot decide a cancelled
request. Accepted requests can only move to terminal `CANCELLED_AFTER_ACCEPTANCE`
through the matching confirmed-seat cancellation; declined requests remain terminal.

Acceptance is a Firestore transaction. Its request update is allowed only when the same atomic
write changes `seatsRemaining` from N to N-1 and N was positive. Firestore transaction retries
plus `getAfter()` rules prevent simultaneous accepts from overbooking. Rules still cannot make
a client-supplied `driverUid` magically server-authored; instead they compare it to the immutable
journey owner on every create and keep it immutable.

Every journey and its private acceptance guard are created atomically. Existing journeys without
a guard fail closed: client rules cannot create a guard later, create requests against the journey,
decrement it, or decide its requests. The guard is readable only by its driver, cannot be listed or
deleted, and is not exposed through the broadly readable journey document.

Acceptance atomically updates the journey, request and guard and creates exactly one confirmed
trip whose deterministic document ID equals the accepted request ID. Rules require exactly one
positive seat decrement, exactly one `PENDING -> ACCEPTED` request named by the guard, an
incremented guard count, agreement between that count and
`seatCapacity - seatsRemaining` before and after the write, and a newly created confirmed trip.
The confirmed-trip create rule validates all four writes and every trip field against the
persisted request and journey. Each of the other acceptance writes requires that new trip, so
neither an acceptance without a trip nor a standalone trip can commit. The request's terminal
state and deterministic trip ID prevent replay. Declines still change only the guarded request.

Confirmed trips are private. Only their driver and rider can read them; the client loads them with
separate `driverUid == currentUid` and `riderUid == currentUid` queries and never lists the whole
collection. Trip identifiers, participants, route and departure cannot change, and trips cannot
be deleted. Only the rider can update a confirmed trip through the coupled cancellation below.
A driver can issue the trip create only inside the valid acceptance transaction;
rider, stranger and standalone creates are denied. Acceptance
also requires `departureAt > request.time`, while decline behaviour is unchanged.
The rules cannot establish real-world seat occupancy, prevent colluding accounts, compel a driver
to accept fairly, repair legacy or privileged-server writes, or keep a pending request available
after another acceptance consumes the last seat.

State transitions in 9C-1 are deliberately limited:

```text
journey + guard:  create OPEN(capacity) + count 0
                  -> OPEN(remaining - 1) + count + 1 per linked acceptance
                  -> OPEN(remaining + 1) + count - 1 per linked rider cancellation
                  -> CANCELLED + frozen remaining/count on driver cancellation
request:          create PENDING -> ACCEPTED -> CANCELLED_AFTER_ACCEPTANCE
                                 -> DECLINED
                                 -> CANCELLED -> PENDING while the journey is
                                                 upcoming and has capacity
confirmed trip:  absent -> CONFIRMED only with the matching request acceptance
                       -> CANCELLED_BY_RIDER only with the matching seat release
```

Rider-owned pending-request cancellation and safe re-requesting remain supported. An upcoming
confirmed booking also supports the rider cancellation described below. Driver journey cancellation
is supported as described below. There is no private pickup/drop-off, exact/live
location, pricing/payment, notification, Circle, trust, rating, Function or Storage data in
connected mode.

### Confirmed-trip messaging Phase 1

Messaging is a dedicated connected coordination capability; it does not use the local-demo
coordination records and does not make the whole connected repository realtime. The open
conversation observes its parent confirmed trip, linked journey and the latest 100 messages.
The message query is ordered by server `sentAt`, with document ID as the deterministic client
tie-break. Pending local timestamp writes are not rendered as optimistic messages. Cancelling,
switching tabs or accounts, signing out, navigating back, or opening another conversation
cancels the flow and removes every Firestore registration.

The structurally valid confirmed trip is the sole participant authorization anchor. Its driver
and rider retain read/list access to history after rider cancellation, driver cancellation, or a
missing/inconsistent linked journey. Create is stricter: the trip must remain `CONFIRMED`; the
linked journey must exist, be structurally valid and `OPEN`; and driver, route and departure must
exactly match the immutable trip snapshot. Departure passing alone does not close coordination,
because no real completion/progress state exists yet. Malformed parent identity fails closed.
Messages are create-only; update/delete, sender spoofing, extra fields, client timestamps,
strangers and unauthenticated callers are denied.

The UI opens messaging only from normal Trip Details. A rider has one action for their confirmed
trip. A driver has a distinct action for every accepted rider, keyed by that rider's confirmed-trip
ID rather than the journey ID. Cancellation or invalidation changes an already-open thread to
read-only while retaining history. Content checks enforce structure, not safety guarantees; the
client explicitly advises using public pickup places and not sharing a home address, phone number
or live location. Phase 1 has no inbox, unread state, push, attachments, read receipts, typing,
editing/deleting, moderation or location sharing.

## Manual two-emulator test

1. Start the disposable manual emulators with
   `firebase emulators:start --only auth,firestore --project ryde-79893`.
   Do not use `npm run test:rules` against these ports. Automated rules verification uses
   `npm run test:rules:emulator` and is isolated as described above.
2. Install the explicit connected debug build on two Android emulators with
   `.\gradlew.bat installDebug -PrydeAppMode=CONNECTED` (select each emulator as needed).
3. On emulator A create/sign into a driver account, create an offer using town/district areas,
   a future `YYYY-MM-DD HH:mm` departure, and 1..8 seats.
4. On emulator B create/sign into a different rider account, tap **Refresh**, find the offer,
   and request one seat. The driver's own offer is never requestable.
5. On B open **Your requests**, cancel the pending request, and confirm it remains Cancelled after
   refresh or relaunch. Re-request it while the journey is upcoming and still has capacity.
6. On A tap **Refresh**, then accept or decline the pending request. On B tap **Refresh** and
   confirm the same status. Accepted bookings can be cancelled from Trips, but neither accepted
   nor declined requests can be withdrawn using the pending-request action or re-requested.
7. After acceptance, open **Trips** on both A and B. Confirm each sees exactly one private
   confirmed trip with the genuine broad-area route, departure, `Status: CONFIRMED`, and the
   appropriate `You're driving` or `You're riding` role. A third account must not see the trip.
8. For the acceptance path, repeat with a one-seat offer and two rider accounts; only one pending
   request can be accepted and the other acceptance must fail without a negative seat count.
9. From rider Trip Details and each accepted rider row in driver Trip Details, open **Messages**.
   Send in both directions and confirm the other open screen updates without Refresh.
10. Confirm two accepted riders on one offer open different histories. A third account must be
    unable to read or write either nested message collection.
11. Cancel the rider seat or driver journey while the other conversation remains open. Existing
    history must remain visible, the composer must disappear, and a new send must fail safely.

Remaining work includes richer discovery/query design, Circles, and any trusted backend operation needed for stronger
multi-document invariants. None of those capabilities are silently delegated to the fictional
local-demo repository in connected mode.

### Accepted-seat rider cancellation (emulator only)

Before departure, the rider can select **Cancel my seat** in Trips and confirm that the seat
will be returned and this booking cannot be reopened. A single Firestore transaction performs:

```text
journey: OPEN(remaining N) -> OPEN(remaining N + 1)
request: ACCEPTED -> CANCELLED_AFTER_ACCEPTANCE (terminal)
trip: CONFIRMED -> CANCELLED_BY_RIDER, cancelledAt = server timestamp
guard: count C -> C - 1, lastCancelledRequestId = acceptedRequestId
```

Both participants retain the private trip/request history. The rider cannot re-request this
same journey; another rider can use the restored capacity. Pending withdrawal/re-request is
unchanged while the journey remains OPEN. Trips retain their original immutable source
fields, and cancellation is rejected after departure, for other participants, or on replay.

The guard count remains equal to capacity minus remaining seats before and after cancellation.
Its lastAcceptedRequestId remains historical, including when all allocations have been released
and count returns to zero. Legacy guards without lastCancelledRequestId remain valid. Riders
still cannot read guards: the transaction blindly applies increment(-1) and the cancellation
pointer; the trip update rule validates all four before/after documents, while each other write
requires that trip transition. This binds the release to exactly one confirmed booking. Partial,
malformed and excessive releases fail atomically. A stale concurrent write may receive a safe
failure; refresh and retry the command. A committed cancellation is terminal and cannot release
another seat.

Run the actual Android gateway test only with the dedicated test emulators and the explicit
instrumentation argument (alongside the Compose tests):

```powershell
firebase emulators:exec --config firebase.rules-test.json --only auth,firestore --project demo-ryde-rules-test ".\gradlew.bat connectedDebugAndroidTest -PrydeAppMode=CONNECTED -Pandroid.testInstrumentationRunnerArguments.rydeRulesEmulator=true"
```

The gateway test uses only demo-ryde-rules-test and ports 8180/9199, with unique disposable
accounts/offers. It also verifies driver cancellation, frozen pending requests and preservation
of earlier rider cancellations. It skips without that explicit argument and never clears manual data.

Manual verification: accept a one-seat booking, cancel it from the rider's Trips tab, refresh
both accounts and verify retained cancelled history and one restored seat. Repeating cancellation
or re-requesting from the original rider must fail. A third disposable rider can request that
journey and be accepted into its restored seat. Other offers and bookings remain independent.

### Driver journey cancellation (emulator only)

Only the owning driver can cancel an upcoming OPEN offer. Your offers retains the offer as
history after the confirmation explains that all confirmed riders/pending requests are affected
and cancellation cannot be undone. The transaction reads the journey and its private guard,
checks count/capacity agreement and writes only:

```text
journey: OPEN -> CANCELLED, cancelledAt = server timestamp
capacity, guard, request and trip source records: unchanged
```

The journey is the sole authoritative cancellation record. `ConnectedJourneyLifecycle` resolves
stored CONFIRMED trips to CANCELLED_BY_DRIVER when their linked journey is CANCELLED. Stored
PENDING/ACCEPTED requests similarly resolve to cancelled-by-driver history. There is no separate
persisted driver-cancellation status on requests/trips and no query-dependent fan-out operation.
Earlier CANCELLED_BY_RIDER trips retain their attribution and timestamp. Missing or mismatched
journeys make confirmed bookings unavailable and disable actions.

Cancelled journeys are excluded from normal discovery. All linked request writes, decisions and
accepted-seat releases are frozen after closure; neither riders nor drivers can reopen bookings
or return additional seats. Capacity and guard count remain historical and consistent. The driver
cannot reopen, delete or alter a cancelled offer, and riders/strangers cannot cancel it. Rules
require server cancellation time, preserve immutable fields and keep guard reads driver-only.

Acceptance, request creation, rider release and driver cancellation races leave complete valid
states. A stale optimistic operation may fail safely and require refresh/retry. Refresh reads
private sources before journeys so it resolves cancellation against the latest lifecycle authority
read in that refresh; separate server queries still do not form one atomic read snapshot.

Manual verification: cancel an empty offer, an offer with a pending request and an offer with
confirmed riders. Refresh both accounts: the driver retains an unavailable cancelled offer,
pending requests have no actions, and confirmed riders see Journey cancelled by driver without
Cancel my seat. Repeat with an earlier rider-cancelled booking: it remains Cancelled by rider.
Other offers remain independent. Automated tests use only the dedicated demo namespace/ports.
