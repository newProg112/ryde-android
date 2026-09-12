# Backend migration boundary

## Phase 9B architecture and schema

`LOCAL_DEMO` remains Firebase-free and permanently available. In `CONNECTED`, Firebase Auth
owns the session and Firestore owns only the profile slice. Firebase types stay behind
`ConnectedAuthGateway` and `ConnectedProfileStore`; the connected Phase 9B UI does not expose
capabilities awaiting Phase 9C. Reads use the Firestore server source and an in-memory cache.
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
SDK operation. Run isolated rules tests without deployment with:

```powershell
firebase emulators:exec --only auth,firestore --project ryde-79893 "npm run test:rules"
```

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

Nothing in Phase 9B has been deployed. The commands above create only disposable emulator
users/documents and load rules locally. Production Firestore retains its existing deny-all
rules. Phase 9C still needs discovery, journeys, offers, requests, Circles and trip lifecycle;
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

- **9B (this slice):** Firebase-emulator session/Auth, profile and saved places.
- **9C:** migrate discovery, offers, seat requests, Circles and trip lifecycle while the
  local fake remains available for demos and tests.
- **9D:** replace conversation/activity snapshots with realtime messaging and coordination
  streams.

Pricing, eligibility, broad-area privacy, trust/safety overrides, message content limits,
participant access and journey lifecycle transitions remain Compose-, Context- and
Firebase-free domain policies. Connected phases must enforce the same invariants with
Firebase Security Rules and trusted server operations. UI checks are guidance only; they
must never become authoritative authorization or integrity controls.
