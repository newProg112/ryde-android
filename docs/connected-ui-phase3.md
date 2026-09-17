# CONNECTED UI Phase 3: Trips

Normal authenticated Trips uses the snapshot observed by `ConnectedReadyApp` from its
existing `ConnectedRydeRepository`. It does not create repositories or subscribe to
Firestore listeners. LOCAL_DEMO keeps its existing screens and commands.

The rider section includes the signed-in user's requests and persisted confirmed trips.
Requests join to their genuine journey for broad route areas and departure. Confirmed
trips use their immutable route/departure fields and replace the linked accepted request
in the list. Other users' requests/trips are excluded. Document identifiers and Firebase
UIDs are never rendered as text.

Request labels represent pending, accepted, declined, cancelled and cancelled-after-
acceptance states. `ConnectedJourneyLifecycle` resolves driver cancellation and unavailable
journeys. If a request's journey is missing or mismatched, its record remains visible with
details unavailable; no route or departure is fabricated. Confirmed trip history similarly
retains rider cancellation, driver cancellation or unavailable lifecycle state.

Owned journeys are already available in the shared snapshot. A separate driver section
shows their broad route, departure, role and OPEN/CANCELLED status. It has no driver
cancellation, Accept/Decline or incoming-request management controls. Those remain in
Journey Lab until a later phase. Profile's Journey Lab entry remains intact.

Confirmed rider cancellation reuses `canCancelConnectedConfirmedSeat`, including participant,
resolved lifecycle and departure eligibility. Compose displays a confirmation and consumes
its selected trip ID before forwarding it. The app-level callback checks current repository
truth again and invokes `cancelConnectedConfirmedSeat(tripId)` once. The existing repository
command executes the tested transaction and refreshes genuine state. No lifecycle mutations,
seat arithmetic or optimistic status changes are implemented in the screen.

The existing app-level busy guard survives tab changes and disables cancellation and Refresh
while a command runs. A command failure requires successful Refresh before another write,
because the transaction may have committed before its refresh failed. Read failures preserve
the last snapshot and allow Refresh retry. Home's Manage requests opens normal Trips; Find
retains its pending/confirmed request labels and existing discovery behavior.

Refresh remains explicit. The existing separate repository queries are not an atomic read
snapshot; backend transactions/rules remain authoritative. No pending withdrawal control is
added in this phase. An accepted request without a loaded confirmed-trip record shows its
genuine accepted status but has no cancellation control until the confirmed trip is loaded.

For manual verification with empty Firebase emulator data, sign in and open Trips: No trips
yet and Refresh should appear. Create a future offer with another account through Journey Lab,
request it in normal Find, then open normal Trips to see pending state. Accept in the driver's
Journey Lab, refresh rider Trips and confirm the seat. Select Cancel my seat, keep it once,
then confirm cancellation. The rider should retain cancelled history after refresh; the driver
should see the restored capacity. No previous test users or journeys are required.

Automated screen tests use the real connected repository with in-memory gateway doubles or
pure snapshot fixtures. They do not seed or clear the manual Firebase emulators. The existing
actual gateway instrumentation test remains opt-in on separate test ports and skips under the
normal `connectedDebugAndroidTest` command.

## Verification on 17 September 2026

- `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug -PrydeAppMode=CONNECTED`:
  BUILD SUCCESSFUL (final run: 44s); 135 unit tests, zero failures or skips.
- `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug -PrydeAppMode=LOCAL_DEMO`:
  BUILD SUCCESSFUL (38s); 135 unit tests, zero failures or skips.
- `.\gradlew.bat connectedDebugAndroidTest -PrydeAppMode=CONNECTED`:
  BUILD SUCCESSFUL (1m 37s) on Pixel_4, Android 11. All 32 non-opt-in tests passed.
  The existing gateway test exits through its opt-in `AssumptionViolatedException`.
  The generated HTML/XML report counts this assumption as one failure among 33 tests
  rather than a skip, despite the successful Gradle task. Its Firebase operations did not run.
- Lint: zero errors and 12 warnings in unchanged dependency declarations, Find's existing
  Modifier parameter order and two existing unused profile resources. No new Trips warnings.
- `git diff --check`: passed, with Git's LF-to-CRLF notices for modified files.

The initial sandboxed Gradle attempt failed with a network permission error. Required checks
then ran successfully outside the sandbox with the existing Gradle/Android environment.
No Firebase emulator process, schema, rules, backend contract or manual data was changed.
No deployment, commit or push was performed. Manual two-account Firebase verification of
normal Trips has not been performed in this phase; the empty state and lifecycle UI paths
were exercised by automated fixtures independent of the manual emulator state.
