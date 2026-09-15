# Backend migration boundary

## Phase 9C-1 architecture and schema

`LOCAL_DEMO` remains Firebase-free and permanently available. In `CONNECTED`, Firebase Auth
owns the session and Firestore owns the profile plus the narrow 9C-1 journey/request slice.
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
journeys and requests remain under project namespace `ryde-79893`.

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
- **9C-1 (this slice):** broad-area discovery, offers and one-seat request decisions.
- **Later 9C:** migrate Circles and trip lifecycle while the
  local fake remains available for demos and tests.
- **9D:** replace conversation/activity snapshots with realtime messaging and coordination
  streams.

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
  status: "OPEN"             # immutable in this slice

/seatRequests/{journeyId}_{riderUid}
  journeyId: string          # immutable
  driverUid: string          # must equal the referenced journey owner; immutable
  riderUid: string           # authenticated creator; immutable
  status: "PENDING" | "ACCEPTED" | "DECLINED" | "CANCELLED"

/journeyAcceptanceGuards/{journeyId}
  driverUid: string          # equals the referenced journey owner; immutable
  acceptanceCount: int       # initially 0; equals capacity minus remaining
  lastAcceptedRequestId: string | null
```

Any authenticated emulator user may read the intentionally small journey document so that
offers can be discovered. Only the driver may create their journey or change its remaining
seat count. A request can be read only by its rider or the referenced driver. Rules require
the deterministic request ID, derive/verify both participants against Auth and the referenced
journey, reject self-requests, and permit only `PENDING -> ACCEPTED|DECLINED` by the driver.
The rider alone may cancel a pending request. The rider may reopen that same deterministic
document from `CANCELLED -> PENDING` only while the referenced journey is still upcoming, open,
has capacity, and remains consistent with its acceptance guard. Drivers cannot decide a cancelled
request, while accepted and declined requests are terminal.

Acceptance is a Firestore transaction. Its request update is allowed only when the same atomic
write changes `seatsRemaining` from N to N-1 and N was positive. Firestore transaction retries
plus `getAfter()` rules prevent simultaneous accepts from overbooking. Rules still cannot make
a client-supplied `driverUid` magically server-authored; instead they compare it to the immutable
journey owner on every create and keep it immutable.

Every journey and its private acceptance guard are created atomically. Existing journeys without
a guard fail closed: client rules cannot create a guard later, create requests against the journey,
decrement it, or decide its requests. The guard is readable only by its driver, cannot be listed or
deleted, and is not exposed through the broadly readable journey document.

Acceptance atomically updates the journey, request and guard. Rules require exactly one positive
seat decrement, exactly one `PENDING -> ACCEPTED` request named by the guard, an incremented guard
count, and agreement between that count and `seatCapacity - seatsRemaining` before and after the
write. Each of the three write rules checks the other two documents with `get()` and `getAfter()`.
This reverse link prevents a driver from reducing availability without accepting the matching
request; the request's terminal state prevents replay. Declines change only the guarded request.
The rules cannot establish real-world seat occupancy, prevent colluding accounts, compel a driver
to accept fairly, repair legacy or privileged-server writes, or keep a pending request available
after another acceptance consumes the last seat.

State transitions in 9C-1 are deliberately limited:

```text
journey + guard:  create OPEN(capacity) + count 0
                  -> OPEN(remaining - 1) + count + 1 per linked acceptance
request:          create PENDING -> ACCEPTED
                                 -> DECLINED
                                 -> CANCELLED -> PENDING while the journey is
                                                 upcoming and has capacity
```

Only rider-owned pending-request cancellation and safe re-requesting are supported. Accepted
and declined requests remain terminal. There is no offer cancellation, confirmed-trip or later
journey lifecycle, private pickup/drop-off, exact/live location, pricing/payment, messaging,
notification, Circle, trust, rating, Function or Storage data in connected mode.

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
   confirm the same terminal status; neither terminal status can be cancelled or re-requested.
7. For the acceptance path, repeat with a one-seat offer and two rider accounts; only one pending
   request can be accepted and the other acceptance must fail without a negative seat count.

Remaining 9C work includes offer cancellation policy, journey and confirmed-trip lifecycle,
richer discovery/query design, Circles, and any trusted backend operation needed for stronger
multi-document invariants. None of those capabilities are silently delegated to the fictional
local-demo repository in connected mode.
