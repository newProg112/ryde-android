# Backend migration boundary

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

## Planned phases

- **9B:** provide Firebase-emulator implementations for session restoration/Auth plus
  profile and saved-place capabilities. Firebase types remain inside the implementation.
- **9C:** migrate discovery, offers, seat requests, Circles and trip lifecycle while the
  local fake remains available for demos and tests.
- **9D:** replace conversation/activity snapshots with realtime messaging and coordination
  streams.

Pricing, eligibility, broad-area privacy, trust/safety overrides, message content limits,
participant access and journey lifecycle transitions remain Compose-, Context- and
Firebase-free domain policies. Connected phases must enforce the same invariants with
Firebase Security Rules and trusted server operations. UI checks are guidance only; they
must never become authoritative authorization or integrity controls.
