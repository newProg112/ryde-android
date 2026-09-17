# CONNECTED UI Phase 4: Offer and driver management

Normal Offer now creates a genuine journey through `ConnectedReadyApp` and the existing
`ConnectedRydeRepository.createConnectedJourney` command. The form collects broad origin
and destination areas, a future local departure through date/time pickers, and 1–8 spare
seats. It reuses the existing departure helpers and `ConnectedJourneyValidator`; the
repository also validates at submission. Draft fields survive tab changes and same-account
state restoration. Successful creation opens normal Trips and clears the submitted area
fields when Offer is next shown.

Trips keeps its separate rider and driver sections. Each owned journey shows its genuine
route, departure, remaining capacity and plain lifecycle label, with its incoming seat
requests grouped underneath. Requests show pending, accepted, declined, rider-withdrawn,
rider-cancelled-seat and driver-cancelled lifecycle states. No rider display-name data
exists in the current request contract, so requests have no invented person details or
rendered identifiers. Missing/mismatched journey records retain safe unavailable request
history without actions or synthetic route fields.

Accept/Decline invoke `decideConnectedRequest`. Both require an owned, open linked journey
and a pending request from another account. Acceptance additionally requires a future
departure and a spare seat. Decline remains valid for a full/departed open journey under
the existing gateway and rules. The shared presentation eligibility helper is also used
at the app callback boundary against current repository truth and current time. The
existing backend transaction/rules make the final decision; no seat arithmetic, optimistic
status mutation, new repository or Firestore listener exists in these screens.

Whole-journey cancellation reuses `ConnectedJourneyLifecycle.canCancelJourney` and
`cancelConnectedJourney`. It requires an owned, open, future journey and explicit confirmation
that all confirmed riders and pending requests are affected. Confirmation is consumed before
the command, disappears when resolved eligibility changes, and is rechecked at the app
boundary. Driver cancellation continues to resolve through the authoritative linked
journey, preserving historical request/trip records.

Creation, decisions, cancellation and refresh share the existing app-level busy guard and
scope, which survive tab changes. Controls disable while commands run. A write/refresh
failure requires a successful explicit Refresh before another write, since a write may
have committed before its read failed. Creation does not have a backend idempotency key;
after an ambiguous creation failure, inspect refreshed Trips before deliberately offering
again. Refresh reads are still separate queries, not an atomic snapshot.

Journey Lab stays intact and reachable from Profile, retaining all its driver controls and
engineering sections. Pending rider-request withdrawal remains Lab-only; normal Find/Home
still provide request/re-request, and Trips provides confirmed-seat cancellation. No new
navigation destination, schema, rules, configuration, contract or LOCAL_DEMO change is made.

Unit tests cover ownership, time, capacity, terminal lifecycle, unavailable history and
action eligibility. Compose tests exercise the real connected repository with in-memory
auth/profile/journey gateways: privacy validation, draft restoration, pickers, creation,
acceptance, decline, cancellation, tab changes, single in-flight commands and recovery
after committed writes whose refresh fails. The instrumentation command selects only the
UI package, excluding Firebase gateway tests and all actual Firebase writes. Manual
two-account acceptance is intentionally left to the reviewers.

## Verification on 17 September 2026

- Initial baseline: `git status --short --branch` showed clean `main...origin/main`;
  `git -c safe.directory=C:/Users/adam4/AndroidStudioProjects/Ryde rev-list --left-right --count HEAD...origin/main`
  returned `0 0`. This compares the existing local origin tracking reference.
- Initial `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest -PrydeAppMode=CONNECTED`
  was blocked by sandbox network permissions (`SocketException: Permission denied: getsockopt`).
  The escalated retry succeeded in 1m 45s.
- An initial combined instrumentation command used an unquoted dotted `-Pandroid...` property;
  PowerShell split it, causing Gradle task selection to fail before any tests ran. Quoting
  the whole property fixed the command.
- `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug connectedDebugAndroidTest -PrydeAppMode=CONNECTED '-Pandroid.testInstrumentationRunnerArguments.package=uk.rydeapp.ryde.ui'`:
  BUILD SUCCESSFUL in 2m 22s. 139 unit tests passed, zero failures/errors/skips.
  All 40 UI instrumentation tests passed on Pixel_4, Android 11, zero failures/errors/skips:
  navigation 23, Lab offers 3, Lab trips 8, normal Offer 2, normal Trips 4.
  Firebase gateway tests were excluded by the package filter.
- `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug -PrydeAppMode=LOCAL_DEMO`:
  BUILD SUCCESSFUL in 58s. 139 unit tests passed, zero failures/errors/skips.
  Lint: zero errors and 12 existing warnings; no new warnings or hints.
- Final `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug -PrydeAppMode=CONNECTED`
  after the lint hint fix: BUILD SUCCESSFUL in 33s. 139 unit tests passed, zero
  failures/errors/skips. Lint: zero errors and the same 12 existing warnings, no hints.
  The final debug APK is CONNECTED; it was not installed/launched for a manual Firebase test.
- `git diff --check`: passed (Git emitted normal LF-to-CRLF notices).

The only production adjustment after the 40-test instrumentation run was changing the
creation counter from boxed integer state to `mutableIntStateOf` to address lint's hint.
No Firebase emulator process/data or backend file was changed. No commit, push or
deployment was performed.

## Manual two-account acceptance

1. Driver: use normal Offer to create a future broad-area journey with two spare seats.
   Confirm automatic navigation to Trips, Driver role, route/departure, capacity and no requests.
2. Rider: Refresh normal Find, request the journey, then verify pending state in Trips.
3. Driver: Refresh Trips, Accept the pending request, verify confirmed request status and
   capacity reduced by one. Rider: Refresh Trips and verify the confirmed seat.
4. Driver: create a second future offer in normal Offer. Rider: request it. Driver: Refresh
   Trips and Decline; verify declined history and unchanged capacity. Rider: Refresh and
   verify declined status.
5. Driver: select Cancel journey on the first offer, choose Keep journey once and verify
   nothing changes, then confirm cancellation. Rider: Refresh and verify driver-cancelled
   history. Driver: verify no remaining decision/cancellation actions on that journey.
6. Check five-tab navigation, privacy text, disabled in-flight controls and Profile →
   Journey Lab → Back to Ryde. Check LOCAL_DEMO still has its existing Offer/Trips appearance.
