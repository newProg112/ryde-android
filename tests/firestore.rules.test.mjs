import { after, before, beforeEach, test } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  Timestamp, collection, deleteDoc, doc, getDoc, getDocs, increment, query, runTransaction, serverTimestamp, setDoc, updateDoc, where, writeBatch,
} from "firebase/firestore";
import { deleteApp, initializeApp } from "firebase/app";
import {
  connectAuthEmulator,
  createUserWithEmailAndPassword,
  getAuth,
  signInWithEmailAndPassword,
  signOut,
} from "firebase/auth";

const MANUAL_PROJECT_ID = "ryde-79893";
const RULES_TEST_PROJECT_ID = "demo-ryde-rules-test";
const RULES_TEST_FIRESTORE_HOST = "127.0.0.1:8180";
const RULES_TEST_AUTH_HOST = "127.0.0.1:9199";
const projectId = process.env.GCLOUD_PROJECT;
const firestoreEmulatorHost = process.env.FIRESTORE_EMULATOR_HOST;
const authEmulatorHost = process.env.FIREBASE_AUTH_EMULATOR_HOST;

assert.equal(
  projectId,
  RULES_TEST_PROJECT_ID,
  `Refusing to run rules tests outside the dedicated ${RULES_TEST_PROJECT_ID} namespace. Use npm run test:rules:emulator.`,
);
assert.notEqual(projectId, MANUAL_PROJECT_ID);
assert.equal(
  firestoreEmulatorHost,
  RULES_TEST_FIRESTORE_HOST,
  `Refusing to run rules tests outside dedicated Firestore ${RULES_TEST_FIRESTORE_HOST}.`,
);
assert.equal(
  authEmulatorHost,
  RULES_TEST_AUTH_HOST,
  `Refusing to run rules tests outside dedicated Auth ${RULES_TEST_AUTH_HOST}.`,
);

const [firestoreHost, firestorePort] = firestoreEmulatorHost.split(":");
let environment;
let authApp;
let auth;

before(async () => {
  environment = await initializeTestEnvironment({
    projectId,
    firestore: {
      host: firestoreHost,
      port: Number(firestorePort),
      rules: fs.readFileSync("firestore.rules", "utf8"),
    },
  });
  authApp = initializeApp({ projectId, apiKey: "local-emulator-only" }, "ryde-auth-integration");
  auth = getAuth(authApp);
  connectAuthEmulator(auth, `http://${authEmulatorHost}`, { disableWarnings: true });
});

beforeEach(async () => {
  await environment.clearFirestore();
  await environment.withSecurityRulesDisabled(async (context) => {
    const batch = writeBatch(context.firestore());
    for (const uid of TEST_PROFILE_UIDS) {
      batch.set(doc(context.firestore(), `users/${uid}`), profile(uid, displayNameFor(uid)));
    }
    await batch.commit();
  });
});
after(async () => {
  await environment.cleanup();
  await deleteApp(authApp);
});

const profile = (uid, displayName = "Alex") => ({ uid, displayName });
const TEST_PROFILE_UIDS = [
  "alex", "driver", "rider", "other", "stranger", "new", "pending",
  "rider-a", "rider-b", "accepted-rider", "declined-rider",
];
const displayNameFor = (uid) => {
  if (uid === "rider-a" || uid === "rider-b") return "Shared rider name";
  if (uid === "rider") return "Riley Rider";
  return `User ${uid}`;
};
const place = (uid, label, area) => ({ uid, label, area });
const journey = (driverUid, seats = 2) => ({
  driverUid,
  originArea: "Mansfield",
  destinationArea: "Nottingham",
  departureAt: Timestamp.fromMillis(Date.now() + 86_400_000),
  seatCapacity: seats,
  seatsRemaining: seats,
  status: "OPEN",
});
const request = (journeyId, driverUid, riderUid, status = "PENDING", riderDisplayName = displayNameFor(riderUid)) => ({
  journeyId, driverUid, riderUid, status, riderDisplayName,
});
const legacyRequest = (journeyId, driverUid, riderUid, status = "PENDING") => ({
  journeyId, driverUid, riderUid, status,
});
const guard = (driverUid, acceptanceCount = 0, lastAcceptedRequestId = null) => ({
  driverUid, acceptanceCount, lastAcceptedRequestId,
});
const confirmedTrip = (requestId, requestData, journeyData, overrides = {}) => ({
  journeyId: requestData.journeyId,
  acceptedRequestId: requestId,
  driverUid: requestData.driverUid,
  riderUid: requestData.riderUid,
  originArea: journeyData.originArea,
  destinationArea: journeyData.destinationArea,
  departureAt: journeyData.departureAt,
  status: "CONFIRMED",
  driverDisplayName: displayNameFor(requestData.driverUid),
  ...overrides,
});
const legacyConfirmedTrip = (requestId, requestData, journeyData, overrides = {}) => {
  const { driverDisplayName: ignored, ...legacy } = confirmedTrip(requestId, requestData, journeyData, overrides);
  return legacy;
};

const createJourney = (
  db,
  journeyId,
  driverUid,
  seats = 2,
  guardData = guard(driverUid),
  journeyData = journey(driverUid, seats),
) => {
  const batch = writeBatch(db);
  batch.set(doc(db, `journeys/${journeyId}`), journeyData);
  batch.set(doc(db, `journeyAcceptanceGuards/${journeyId}`), guardData);
  return batch.commit();
};

const acceptanceBatch = (
  db,
  journeyId,
  requestId,
  seatsRemaining,
  acceptanceCount,
  tripData,
  tripId = requestId,
) => {
  const batch = writeBatch(db);
  batch.update(doc(db, `journeys/${journeyId}`), { seatsRemaining });
  batch.update(doc(db, `seatRequests/${requestId}`), { status: "ACCEPTED" });
  batch.update(doc(db, `journeyAcceptanceGuards/${journeyId}`), {
    acceptanceCount,
    lastAcceptedRequestId: requestId,
  });
  if (tripData) batch.set(doc(db, `confirmedTrips/${tripId}`), tripData);
  return batch.commit();
};

const acceptRequest = (db, journeyId, requestId, seatsRemaining, acceptanceCount) =>
  runTransaction(db, async (transaction) => {
    const journeyRef = doc(db, `journeys/${journeyId}`);
    const requestRef = doc(db, `seatRequests/${requestId}`);
    const guardRef = doc(db, `journeyAcceptanceGuards/${journeyId}`);
    const [journeySnapshot, requestSnapshot] = await Promise.all([
      transaction.get(journeyRef),
      transaction.get(requestRef),
    ]);
    transaction.update(journeyRef, { seatsRemaining });
    transaction.update(requestRef, { status: "ACCEPTED" });
    transaction.update(guardRef, { acceptanceCount, lastAcceptedRequestId: requestId });
    transaction.set(
      doc(db, `confirmedTrips/${requestId}`),
      confirmedTrip(requestId, requestSnapshot.data(), journeySnapshot.data()),
    );
  });

const requestSeatLikeGateway = (db, journeyId, riderUid) => runTransaction(db, async (transaction) => {
  const journeyRef = doc(db, `journeys/${journeyId}`);
  const [journeySnapshot, profileSnapshot] = await Promise.all([
    transaction.get(journeyRef),
    transaction.get(doc(db, `users/${riderUid}`)),
  ]);
  if (!journeySnapshot.exists()) throw new Error("Journey unavailable");
  if (!profileSnapshot.exists()) throw new Error("Rider profile unavailable");
  transaction.set(
    doc(db, `seatRequests/${journeyId}_${riderUid}`),
    request(journeyId, journeySnapshot.data().driverUid, riderUid, "PENDING", profileSnapshot.data().displayName),
  );
});

const cancelConfirmedBatch = (db, journeyId, requestId, seatsRemaining, options = {}) => {
  const batch = writeBatch(db);
  if (options.omit !== "journey") batch.update(doc(db, `journeys/${journeyId}`), {
    seatsRemaining, ...options.journey,
  });
  if (options.omit !== "request") batch.update(doc(db, `seatRequests/${requestId}`), {
    status: "CANCELLED_AFTER_ACCEPTANCE", ...options.request,
  });
  if (options.omit !== "trip") batch.update(doc(db, `confirmedTrips/${requestId}`), {
    status: "CANCELLED_BY_RIDER", cancelledAt: serverTimestamp(), ...options.trip,
  });
  if (options.omit !== "guard") batch.update(doc(db, `journeyAcceptanceGuards/${journeyId}`), {
    acceptanceCount: increment(-1), lastCancelledRequestId: requestId, ...options.guard,
  });
  return batch.commit();
};

const cancelConfirmedLikeGateway = (db, journeyId, requestId) => runTransaction(db, async (transaction) => {
  const tripRef = doc(db, `confirmedTrips/${requestId}`);
  const requestRef = doc(db, `seatRequests/${requestId}`);
  const journeyRef = doc(db, `journeys/${journeyId}`);
  const tripSnapshot = await transaction.get(tripRef);
  const requestSnapshot = await transaction.get(requestRef);
  const journeySnapshot = await transaction.get(journeyRef);
  if (tripSnapshot.data().status !== "CONFIRMED" || requestSnapshot.data().status !== "ACCEPTED") {
    throw new Error("Booking is terminal");
  }
  transaction.update(tripRef, { status: "CANCELLED_BY_RIDER", cancelledAt: serverTimestamp() });
  transaction.update(requestRef, { status: "CANCELLED_AFTER_ACCEPTANCE" });
  transaction.update(journeyRef, { seatsRemaining: journeySnapshot.data().seatsRemaining + 1 });
  transaction.update(doc(db, `journeyAcceptanceGuards/${journeyId}`), {
    acceptanceCount: increment(-1), lastCancelledRequestId: requestId,
  });
});

async function acceptedCancellationFixture(seats = 2) {
  const driver = environment.authenticatedContext("driver").firestore();
  const rider = environment.authenticatedContext("rider").firestore();
  await assertSucceeds(createJourney(driver, "j1", "driver", seats));
  await assertSucceeds(requestSeatLikeGateway(rider, "j1", "rider"));
  await assertSucceeds(acceptRequest(driver, "j1", "j1_rider", seats - 1, 1));
  return { driver, rider };
}

test("legacy confirmed trips remain participant private and cancellable", async () => {
  const { driver, rider } = await acceptedCancellationFixture();
  await environment.withSecurityRulesDisabled(async (context) => {
    const adminTrip = doc(context.firestore(), "confirmedTrips/j1_rider");
    const current = (await getDoc(adminTrip)).data();
    const { driverDisplayName: ignored, ...legacy } = current;
    await setDoc(adminTrip, legacy);
  });

  const legacy = await assertSucceeds(getDoc(doc(rider, "confirmedTrips/j1_rider")));
  assert.equal("driverDisplayName" in legacy.data(), false);
  await assertSucceeds(cancelConfirmedLikeGateway(rider, "j1", "j1_rider"));
  const cancelled = (await getDoc(doc(rider, "confirmedTrips/j1_rider"))).data();
  assert.equal(cancelled.status, "CANCELLED_BY_RIDER");
  assert.equal("driverDisplayName" in cancelled, false);
  assert.deepEqual((await getDoc(doc(driver, "confirmedTrips/j1_rider"))).data(), cancelled);
});

test("rider releases exactly one allocation and both participants retain private cancelled history", async () => {
  const { driver, rider } = await acceptedCancellationFixture(1);
  const before = (await getDoc(doc(rider, "confirmedTrips/j1_rider"))).data();
  await assertFails(getDoc(doc(rider, "journeyAcceptanceGuards/j1")));
  await assertSucceeds(cancelConfirmedLikeGateway(rider, "j1", "j1_rider"));
  const cancelled = (await getDoc(doc(rider, "confirmedTrips/j1_rider"))).data();
  assert.equal(cancelled.status, "CANCELLED_BY_RIDER");
  assert.ok(cancelled.cancelledAt instanceof Timestamp);
  assert.deepEqual(cancelled, { ...before, status: "CANCELLED_BY_RIDER", cancelledAt: cancelled.cancelledAt });
  assert.deepEqual((await getDoc(doc(driver, "confirmedTrips/j1_rider"))).data(), cancelled);
  for (const db of [driver, rider]) {
    assert.equal((await getDoc(doc(db, "seatRequests/j1_rider"))).data().status, "CANCELLED_AFTER_ACCEPTANCE");
  }
  assert.equal((await getDoc(doc(driver, "journeys/j1"))).data().status, "OPEN");
  assert.equal((await getDoc(doc(driver, "journeys/j1"))).data().seatsRemaining, 1);
  assert.deepEqual((await getDoc(doc(driver, "journeyAcceptanceGuards/j1"))).data(), {
    ...guard("driver", 0, "j1_rider"), lastCancelledRequestId: "j1_rider",
  });
  await assertFails(cancelConfirmedBatch(rider, "j1", "j1_rider", 1));
  await assert.rejects(cancelConfirmedLikeGateway(rider, "j1", "j1_rider"), /Booking is terminal/);
  await assertFails(requestSeatLikeGateway(rider, "j1", "rider"));
  await assertFails(updateDoc(doc(rider, "seatRequests/j1_rider"), { status: "CANCELLED" }));
  await assertFails(getDoc(doc(rider, "journeyAcceptanceGuards/j1")));
  const other = environment.authenticatedContext("other").firestore();
  const stranger = environment.authenticatedContext("stranger").firestore();
  const anonymous = environment.unauthenticatedContext().firestore();
  for (const db of [other, stranger, anonymous]) {
    await assertFails(getDoc(doc(db, "confirmedTrips/j1_rider")));
    await assertFails(getDoc(doc(db, "seatRequests/j1_rider")));
    await assertFails(cancelConfirmedBatch(db, "j1", "j1_rider", 2));
  }
  await assertFails(deleteDoc(doc(rider, "confirmedTrips/j1_rider")));
  await assertFails(deleteDoc(doc(driver, "seatRequests/j1_rider")));
  await assertFails(getDocs(collection(driver, "confirmedTrips")));
  await assertSucceeds(requestSeatLikeGateway(other, "j1", "other"));
  await assertSucceeds(acceptRequest(driver, "j1", "j1_other", 0, 1));
  assert.equal((await getDoc(doc(driver, "journeys/j1"))).data().seatsRemaining, 0);
  assert.equal((await getDoc(doc(driver, "journeyAcceptanceGuards/j1"))).data().acceptanceCount, 1);
  assert.equal((await getDoc(doc(rider, "confirmedTrips/j1_rider"))).data().status, "CANCELLED_BY_RIDER");
});

test("only the accepted rider may cancel and every coupled write is required", async () => {
  const { driver, rider } = await acceptedCancellationFixture();
  for (const db of [driver, environment.authenticatedContext("other").firestore(), environment.unauthenticatedContext().firestore()]) {
    await assertFails(cancelConfirmedBatch(db, "j1", "j1_rider", 2));
  }
  for (const omit of ["journey", "request", "trip", "guard"]) {
    await assertFails(cancelConfirmedBatch(rider, "j1", "j1_rider", 2, { omit }));
  }
  assert.equal((await getDoc(doc(driver, "journeys/j1"))).data().seatsRemaining, 1);
  assert.equal((await getDoc(doc(driver, "journeyAcceptanceGuards/j1"))).data().acceptanceCount, 1);
  assert.equal((await getDoc(doc(rider, "confirmedTrips/j1_rider"))).data().status, "CONFIRMED");
});

test("malformed cancellation cannot change protected data restore extra seats or forge time", async () => {
  const { driver, rider } = await acceptedCancellationFixture();
  const invalidOptions = [
    { journey: { seatCapacity: 3 } }, { journey: { driverUid: "rider" } }, { journey: { status: "CANCELLED" } },
    { request: { status: "CANCELLED" } }, { request: { journeyId: "other" } },
    { request: { riderUid: "other" } }, { request: { driverUid: "other" } },
    { trip: { cancelledAt: Timestamp.fromMillis(0) } }, { trip: { cancelledAt: "bad" } },
    { trip: { status: "CONFIRMED" } }, { trip: { originArea: "Derby" } },
    { trip: { destinationArea: "Derby" } }, { trip: { departureAt: Timestamp.fromMillis(0) } },
    { trip: { acceptedRequestId: "other" } }, { trip: { journeyId: "other" } },
    { trip: { riderUid: "other" } }, { trip: { driverUid: "other" } }, { trip: { extra: true } },
    { guard: { acceptanceCount: 1 } }, { guard: { acceptanceCount: -1 } },
    { guard: { lastCancelledRequestId: "other" } }, { guard: { lastAcceptedRequestId: null } },
    { guard: { driverUid: "rider" } },
  ];
  for (const options of invalidOptions) await assertFails(cancelConfirmedBatch(rider, "j1", "j1_rider", 2, options));
  for (const remaining of [0, 1, 3]) await assertFails(cancelConfirmedBatch(rider, "j1", "j1_rider", remaining));
  assert.equal((await getDoc(doc(driver, "journeys/j1"))).data().seatsRemaining, 1);
  await assertSucceeds(cancelConfirmedBatch(rider, "j1", "j1_rider", 2));
  await assertFails(updateDoc(doc(rider, "confirmedTrips/j1_rider"), { cancelledAt: serverTimestamp() }));
});

test("cancellation after departure is rejected without changing any document", async () => {
  const { driver, rider } = await acceptedCancellationFixture();
  await environment.withSecurityRulesDisabled(async (context) => {
    const batch = writeBatch(context.firestore());
    for (const path of ["journeys/j1", "confirmedTrips/j1_rider"]) batch.update(doc(context.firestore(), path), { departureAt: Timestamp.fromMillis(0) });
    await batch.commit();
  });
  await assertFails(cancelConfirmedBatch(rider, "j1", "j1_rider", 2));
  assert.equal((await getDoc(doc(driver, "journeys/j1"))).data().seatsRemaining, 1);
  assert.equal((await getDoc(doc(driver, "journeyAcceptanceGuards/j1"))).data().acceptanceCount, 1);
  assert.equal((await getDoc(doc(rider, "confirmedTrips/j1_rider"))).data().status, "CONFIRMED");
});

test("a rider can cancel an earlier acceptance without changing other trips or offers", async () => {
  const { driver, rider } = await acceptedCancellationFixture(3);
  const other = environment.authenticatedContext("other").firestore();
  await assertSucceeds(requestSeatLikeGateway(other, "j1", "other"));
  await assertSucceeds(acceptRequest(driver, "j1", "j1_other", 1, 2));
  await assertSucceeds(createJourney(driver, "independent", "driver", 1));
  await assertSucceeds(cancelConfirmedLikeGateway(rider, "j1", "j1_rider"));
  assert.deepEqual((await getDoc(doc(driver, "journeyAcceptanceGuards/j1"))).data(), {
    ...guard("driver", 1, "j1_other"), lastCancelledRequestId: "j1_rider",
  });
  assert.equal((await getDoc(doc(other, "confirmedTrips/j1_other"))).data().status, "CONFIRMED");
  assert.equal((await getDoc(doc(driver, "journeys/independent"))).data().seatsRemaining, 1);
  await assertSucceeds(cancelConfirmedLikeGateway(other, "j1", "j1_other"));
  assert.equal((await getDoc(doc(driver, "journeys/j1"))).data().seatsRemaining, 3);
  assert.equal((await getDoc(doc(driver, "journeyAcceptanceGuards/j1"))).data().acceptanceCount, 0);
});

test("simultaneous duplicate cancellation releases exactly once", async () => {
  const { driver, rider } = await acceptedCancellationFixture();
  const outcomes = await Promise.allSettled([
    cancelConfirmedLikeGateway(rider, "j1", "j1_rider"), cancelConfirmedLikeGateway(rider, "j1", "j1_rider"),
  ]);
  assert.equal(outcomes.filter(result => result.status === "fulfilled").length, 1);
  assert.equal((await getDoc(doc(driver, "journeys/j1"))).data().seatsRemaining, 2);
  assert.equal((await getDoc(doc(driver, "journeyAcceptanceGuards/j1"))).data().acceptanceCount, 0);
});

test("excessive release within capacity cannot cancel another rider allocation", async () => {
  const { driver, rider } = await acceptedCancellationFixture(3);
  const other = environment.authenticatedContext("other").firestore();
  await assertSucceeds(requestSeatLikeGateway(other, "j1", "other"));
  await assertSucceeds(acceptRequest(driver, "j1", "j1_other", 1, 2));
  await assertFails(cancelConfirmedBatch(rider, "j1", "j1_rider", 3, { guard: { acceptanceCount: 0 } }));
  assert.equal((await getDoc(doc(driver, "journeys/j1"))).data().seatsRemaining, 1);
  assert.equal((await getDoc(doc(driver, "journeyAcceptanceGuards/j1"))).data().acceptanceCount, 2);
});

test("cancellation racing another acceptance fails safely and completes on retry", async () => {
  const { driver, rider } = await acceptedCancellationFixture(2);
  const other = environment.authenticatedContext("other").firestore();
  await assertSucceeds(requestSeatLikeGateway(other, "j1", "other"));
  const acceptLikeGateway = () => runTransaction(driver, async (transaction) => {
    const requestRef = doc(driver, "seatRequests/j1_other");
    const journeyRef = doc(driver, "journeys/j1");
    const guardRef = doc(driver, "journeyAcceptanceGuards/j1");
    const requestData = (await transaction.get(requestRef)).data();
    const journeyData = (await transaction.get(journeyRef)).data();
    const guardData = (await transaction.get(guardRef)).data();
    assert.equal(requestData.status, "PENDING");
    assert.ok(journeyData.seatsRemaining > 0);
    assert.equal(guardData.acceptanceCount, journeyData.seatCapacity - journeyData.seatsRemaining);
    transaction.update(requestRef, { status: "ACCEPTED" });
    transaction.update(journeyRef, { seatsRemaining: journeyData.seatsRemaining - 1 });
    transaction.update(guardRef, { acceptanceCount: guardData.acceptanceCount + 1, lastAcceptedRequestId: "j1_other" });
    transaction.set(doc(driver, "confirmedTrips/j1_other"), confirmedTrip("j1_other", requestData, journeyData));
  });
  const outcomes = await Promise.allSettled([
    cancelConfirmedLikeGateway(rider, "j1", "j1_rider"), acceptLikeGateway(),
  ]);
  assert.ok(outcomes.some(result => result.status === "fulfilled"));
  const currentJourney = (await getDoc(doc(driver, "journeys/j1"))).data();
  const currentGuard = (await getDoc(doc(driver, "journeyAcceptanceGuards/j1"))).data();
  assert.equal(currentGuard.acceptanceCount, currentJourney.seatCapacity - currentJourney.seatsRemaining);
  assert.ok(currentJourney.seatsRemaining >= 0 && currentJourney.seatsRemaining <= currentJourney.seatCapacity);
  // Rules can reject a stale optimistic write before the SDK retries it. A fresh
  // command must succeed without any partial mutation from the rejected command.
  if (outcomes[0].status === "rejected") await assertSucceeds(cancelConfirmedLikeGateway(rider, "j1", "j1_rider"));
  if (outcomes[1].status === "rejected") await assertSucceeds(acceptLikeGateway());
  assert.equal((await getDoc(doc(driver, "journeys/j1"))).data().seatsRemaining, 1);
  assert.deepEqual((await getDoc(doc(driver, "journeyAcceptanceGuards/j1"))).data(), {
    ...guard("driver", 1, "j1_other"), lastCancelledRequestId: "j1_rider",
  });
  assert.equal((await getDoc(doc(rider, "confirmedTrips/j1_rider"))).data().status, "CANCELLED_BY_RIDER");
  assert.equal((await getDoc(doc(other, "confirmedTrips/j1_other"))).data().status, "CONFIRMED");
});

test("acceptance cannot piggyback capacity or guard changes on an unrelated journey", async () => {
  const { driver } = await acceptedCancellationFixture(2);
  const other = environment.authenticatedContext("other").firestore();
  await assertSucceeds(createJourney(driver, "independent", "driver", 2));
  await assertSucceeds(requestSeatLikeGateway(other, "j1", "other"));
  const sourceJourney = (await getDoc(doc(driver, "journeys/j1"))).data();
  const sourceRequest = (await getDoc(doc(driver, "seatRequests/j1_other"))).data();
  const batch = writeBatch(driver);
  batch.update(doc(driver, "journeys/j1"), { seatsRemaining: 0 });
  batch.update(doc(driver, "journeyAcceptanceGuards/j1"), { acceptanceCount: 2, lastAcceptedRequestId: "j1_other" });
  batch.update(doc(driver, "seatRequests/j1_other"), { status: "ACCEPTED" });
  batch.set(doc(driver, "confirmedTrips/j1_other"), confirmedTrip("j1_other", sourceRequest, sourceJourney));
  batch.update(doc(driver, "journeys/independent"), { seatsRemaining: 1 });
  batch.update(doc(driver, "journeyAcceptanceGuards/independent"), { acceptanceCount: 1, lastAcceptedRequestId: "j1_other" });
  await assertFails(batch.commit());
  assert.equal((await getDoc(doc(driver, "journeys/independent"))).data().seatsRemaining, 2);
  assert.equal((await getDoc(doc(driver, "journeyAcceptanceGuards/independent"))).data().acceptanceCount, 0);
});

const cancelJourneyLikeGateway = (db, journeyId) => runTransaction(db, async (transaction) => {
  const journeyRef = doc(db, `journeys/${journeyId}`);
  const source = (await transaction.get(journeyRef)).data();
  const sourceGuard = (await transaction.get(doc(db, `journeyAcceptanceGuards/${journeyId}`))).data();
  if (source.status !== "OPEN") throw new Error("Journey is terminal");
  assert.equal(sourceGuard.acceptanceCount, source.seatCapacity - source.seatsRemaining);
  transaction.update(journeyRef, { status: "CANCELLED", cancelledAt: serverTimestamp() });
});

test("driver cancels an empty offer once with server time and frozen capacity and guard", async () => {
  const driver = environment.authenticatedContext("driver").firestore();
  await assertSucceeds(createJourney(driver, "j1", "driver", 2));
  const before = (await getDoc(doc(driver, "journeys/j1"))).data();
  await assertSucceeds(cancelJourneyLikeGateway(driver, "j1"));
  const closed = (await getDoc(doc(driver, "journeys/j1"))).data();
  assert.ok(closed.cancelledAt instanceof Timestamp);
  assert.deepEqual(closed, { ...before, status: "CANCELLED", cancelledAt: closed.cancelledAt });
  assert.deepEqual((await getDoc(doc(driver, "journeyAcceptanceGuards/j1"))).data(), guard("driver"));
  await assertFails(updateDoc(doc(driver, "journeys/j1"), { status: "CANCELLED", cancelledAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(driver, "journeys/j1"), { status: "OPEN" }));
  await assertFails(deleteDoc(doc(driver, "journeys/j1")));
  assert.deepEqual((await getDoc(doc(driver, "journeys/j1"))).data(), closed);
});

test("driver cancellation preserves pending history freezes all actions and removes discovery", async () => {
  const driver = environment.authenticatedContext("driver").firestore();
  const rider = environment.authenticatedContext("rider").firestore();
  const other = environment.authenticatedContext("other").firestore();
  await assertSucceeds(createJourney(driver, "j1", "driver", 2));
  await assertSucceeds(requestSeatLikeGateway(rider, "j1", "rider"));
  const pending = (await getDoc(doc(rider, "seatRequests/j1_rider"))).data();
  await assertSucceeds(cancelJourneyLikeGateway(driver, "j1"));
  assert.deepEqual((await getDoc(doc(rider, "seatRequests/j1_rider"))).data(), pending);
  assert.deepEqual((await getDoc(doc(driver, "seatRequests/j1_rider"))).data(), pending);
  assert.equal((await getDocs(query(collection(rider, "journeys"), where("status", "==", "OPEN")))).size, 0);
  await assertFails(requestSeatLikeGateway(other, "j1", "other"));
  await assertFails(acceptRequest(driver, "j1", "j1_rider", 1, 1));
  await assertFails(updateDoc(doc(driver, "seatRequests/j1_rider"), { status: "DECLINED" }));
  await assertFails(updateDoc(doc(rider, "seatRequests/j1_rider"), { status: "CANCELLED" }));
  assert.deepEqual((await getDoc(doc(driver, "journeyAcceptanceGuards/j1"))).data(), guard("driver"));
});

test("driver cancellation keeps multiple confirmed trips private and never restores seats", async () => {
  const { driver, rider } = await acceptedCancellationFixture(3);
  const other = environment.authenticatedContext("other").firestore();
  const stranger = environment.authenticatedContext("stranger").firestore();
  await assertSucceeds(requestSeatLikeGateway(other, "j1", "other"));
  await assertSucceeds(acceptRequest(driver, "j1", "j1_other", 1, 2));
  const guardBefore = (await getDoc(doc(driver, "journeyAcceptanceGuards/j1"))).data();
  const tripBefore = (await getDoc(doc(rider, "confirmedTrips/j1_rider"))).data();
  await assertSucceeds(cancelJourneyLikeGateway(driver, "j1"));
  assert.equal((await getDoc(doc(driver, "journeys/j1"))).data().seatsRemaining, 1);
  assert.deepEqual((await getDoc(doc(driver, "journeyAcceptanceGuards/j1"))).data(), guardBefore);
  assert.deepEqual((await getDoc(doc(rider, "confirmedTrips/j1_rider"))).data(), tripBefore);
  assert.deepEqual((await getDoc(doc(driver, "confirmedTrips/j1_rider"))).data(), tripBefore);
  assert.equal((await getDoc(doc(other, "confirmedTrips/j1_other"))).data().status, "CONFIRMED");
  await assertFails(cancelConfirmedBatch(rider, "j1", "j1_rider", 2));
  await assertFails(requestSeatLikeGateway(rider, "j1", "rider"));
  for (const db of [stranger, environment.unauthenticatedContext().firestore()]) {
    await assertFails(getDoc(doc(db, "confirmedTrips/j1_rider")));
    await assertFails(getDoc(doc(db, "seatRequests/j1_rider")));
    await assertFails(getDoc(doc(db, "journeyAcceptanceGuards/j1")));
  }
  await assertFails(getDoc(doc(rider, "journeyAcceptanceGuards/j1")));
  await assertFails(getDoc(doc(other, "confirmedTrips/j1_rider")));
  await assertFails(getDocs(collection(driver, "confirmedTrips")));
  await assertFails(updateDoc(doc(driver, "confirmedTrips/j1_rider"), { status: "CANCELLED_BY_DRIVER" }));
  await assertFails(updateDoc(doc(driver, "seatRequests/j1_rider"), { riderUid: "other" }));
});

test("previous rider cancellation retains attribution and restored capacity stays unavailable", async () => {
  const { driver, rider } = await acceptedCancellationFixture(1);
  await assertSucceeds(cancelConfirmedLikeGateway(rider, "j1", "j1_rider"));
  const tripBefore = (await getDoc(doc(rider, "confirmedTrips/j1_rider"))).data();
  const guardBefore = (await getDoc(doc(driver, "journeyAcceptanceGuards/j1"))).data();
  await assertSucceeds(cancelJourneyLikeGateway(driver, "j1"));
  assert.deepEqual((await getDoc(doc(rider, "confirmedTrips/j1_rider"))).data(), tripBefore);
  assert.equal(tripBefore.status, "CANCELLED_BY_RIDER");
  assert.equal((await getDoc(doc(rider, "seatRequests/j1_rider"))).data().status, "CANCELLED_AFTER_ACCEPTANCE");
  assert.equal((await getDoc(doc(driver, "journeys/j1"))).data().seatsRemaining, 1);
  assert.deepEqual((await getDoc(doc(driver, "journeyAcceptanceGuards/j1"))).data(), guardBefore);
  await assertFails(requestSeatLikeGateway(environment.authenticatedContext("new").firestore(), "j1", "new"));
  await assertFails(cancelConfirmedBatch(rider, "j1", "j1_rider", 1));
});

test("only the owning driver may close an offer and malformed or partial transitions fail", async () => {
  const driver = environment.authenticatedContext("driver").firestore();
  await assertSucceeds(createJourney(driver, "j1", "driver", 2));
  for (const db of [environment.authenticatedContext("rider").firestore(), environment.authenticatedContext("stranger").firestore(), environment.unauthenticatedContext().firestore()]) {
    await assertFails(updateDoc(doc(db, "journeys/j1"), { status: "CANCELLED", cancelledAt: serverTimestamp() }));
  }
  for (const invalid of [
    { status: "CANCELLED" }, { cancelledAt: serverTimestamp() },
    { status: "CANCELLED", cancelledAt: "bad" }, { status: "CANCELLED", cancelledAt: Timestamp.fromMillis(0) },
    { status: "CONFIRMED", cancelledAt: serverTimestamp() },
    ...[{ driverUid: "rider" }, { originArea: "Derby" }, { destinationArea: "Derby" },
      { departureAt: Timestamp.fromMillis(0) }, { seatCapacity: 3 }, { seatsRemaining: 1 }, { extra: true }]
      .map(fields => ({ status: "CANCELLED", cancelledAt: serverTimestamp(), ...fields })),
  ]) await assertFails(updateDoc(doc(driver, "journeys/j1"), invalid));
  assert.equal((await getDoc(doc(driver, "journeys/j1"))).data().status, "OPEN");
  await assertSucceeds(cancelJourneyLikeGateway(driver, "j1"));
});

test("driver cancellation cannot piggyback request trip or cross-journey guard changes", async () => {
  const { driver, rider } = await acceptedCancellationFixture(2);
  const pending = environment.authenticatedContext("pending").firestore();
  await assertSucceeds(requestSeatLikeGateway(pending, "j1", "pending"));
  await assertSucceeds(createJourney(driver, "independent", "driver", 1));
  for (const [path, fields] of [
    ["seatRequests/j1_pending", { status: "DECLINED" }],
    ["seatRequests/j1_rider", { status: "CANCELLED_AFTER_ACCEPTANCE" }],
    ["seatRequests/j1_pending", { journeyId: "independent" }],
    ["confirmedTrips/j1_rider", { status: "CANCELLED_BY_DRIVER" }],
    ["confirmedTrips/j1_rider", { originArea: "Derby" }],
    ["journeyAcceptanceGuards/j1", { acceptanceCount: 0 }],
    ["journeyAcceptanceGuards/independent", { acceptanceCount: 1, lastAcceptedRequestId: "j1_rider" }],
  ]) {
    const batch = writeBatch(driver);
    batch.update(doc(driver, "journeys/j1"), { status: "CANCELLED", cancelledAt: serverTimestamp() });
    batch.update(doc(driver, path), fields);
    await assertFails(batch.commit());
  }
  assert.equal((await getDoc(doc(driver, "journeys/j1"))).data().status, "OPEN");
  assert.equal((await getDoc(doc(rider, "confirmedTrips/j1_rider"))).data().status, "CONFIRMED");
  assert.equal((await getDoc(doc(driver, "journeys/independent"))).data().seatsRemaining, 1);
});

test("driver cancellation fails closed for departed unguarded and inconsistent journeys", async () => {
  const driver = environment.authenticatedContext("driver").firestore();
  await environment.withSecurityRulesDisabled(async context => {
    const db = context.firestore();
    for (const [id, source] of [["departed", { ...journey("driver"), departureAt: Timestamp.fromMillis(0) }], ["legacy", journey("driver")], ["inconsistent", journey("driver")]]) {
      await setDoc(doc(db, `journeys/${id}`), source);
    }
    await setDoc(doc(db, "journeyAcceptanceGuards/departed"), guard("driver"));
    await setDoc(doc(db, "journeyAcceptanceGuards/inconsistent"), guard("driver", 1, "other"));
  });
  for (const id of ["departed", "legacy", "inconsistent"]) {
    await assertFails(updateDoc(doc(driver, `journeys/${id}`), { status: "CANCELLED", cancelledAt: serverTimestamp() }));
  }
});

test("simultaneous driver cancellations close once without changing allocation counts", async () => {
  const { driver } = await acceptedCancellationFixture(1);
  const results = await Promise.allSettled([cancelJourneyLikeGateway(driver, "j1"), cancelJourneyLikeGateway(driver, "j1")]);
  assert.equal(results.filter(result => result.status === "fulfilled").length, 1);
  assert.equal((await getDoc(doc(driver, "journeys/j1"))).data().status, "CANCELLED");
  assert.equal((await getDoc(doc(driver, "journeys/j1"))).data().seatsRemaining, 0);
  assert.equal((await getDoc(doc(driver, "journeyAcceptanceGuards/j1"))).data().acceptanceCount, 1);
});

test("driver cancellation racing request creation leaves terminal preserved history or no request", async () => {
  const driver = environment.authenticatedContext("driver").firestore();
  const rider = environment.authenticatedContext("rider").firestore();
  await assertSucceeds(createJourney(driver, "j1", "driver", 1));
  const results = await Promise.allSettled([cancelJourneyLikeGateway(driver, "j1"), requestSeatLikeGateway(rider, "j1", "rider")]);
  if (results[0].status === "rejected") await assertSucceeds(cancelJourneyLikeGateway(driver, "j1"));
  assert.equal((await getDoc(doc(driver, "journeys/j1"))).data().status, "CANCELLED");
  const requests = await getDocs(query(collection(driver, "seatRequests"), where("driverUid", "==", "driver")));
  assert.ok(requests.size <= 1);
  if (requests.size) {
    assert.equal(requests.docs[0].data().status, "PENDING");
    await assertFails(acceptRequest(driver, "j1", "j1_rider", 0, 1));
  }
  assert.equal((await getDoc(doc(driver, "journeyAcceptanceGuards/j1"))).data().acceptanceCount, 0);
});

test("driver cancellation racing acceptance preserves the winning allocation or rejects acceptance", async () => {
  const driver = environment.authenticatedContext("driver").firestore();
  const rider = environment.authenticatedContext("rider").firestore();
  await assertSucceeds(createJourney(driver, "j1", "driver", 1));
  await assertSucceeds(requestSeatLikeGateway(rider, "j1", "rider"));
  const results = await Promise.allSettled([cancelJourneyLikeGateway(driver, "j1"), acceptRequest(driver, "j1", "j1_rider", 0, 1)]);
  if (results[0].status === "rejected") await assertSucceeds(cancelJourneyLikeGateway(driver, "j1"));
  const closed = (await getDoc(doc(driver, "journeys/j1"))).data();
  const allocated = (await getDoc(doc(driver, "journeyAcceptanceGuards/j1"))).data().acceptanceCount;
  const requestStatus = (await getDoc(doc(rider, "seatRequests/j1_rider"))).data().status;
  assert.equal(closed.status, "CANCELLED");
  assert.equal(allocated, closed.seatCapacity - closed.seatsRemaining);
  const trips = await getDocs(query(collection(driver, "confirmedTrips"), where("driverUid", "==", "driver")));
  assert.equal(trips.size, allocated);
  assert.equal(requestStatus, allocated === 1 ? "ACCEPTED" : "PENDING");
});

test("driver cancellation racing rider release never restores seats after closure", async () => {
  const { driver, rider } = await acceptedCancellationFixture(1);
  const results = await Promise.allSettled([cancelJourneyLikeGateway(driver, "j1"), cancelConfirmedLikeGateway(rider, "j1", "j1_rider")]);
  if (results[0].status === "rejected") await assertSucceeds(cancelJourneyLikeGateway(driver, "j1"));
  const closed = (await getDoc(doc(driver, "journeys/j1"))).data();
  const sourceTrip = (await getDoc(doc(rider, "confirmedTrips/j1_rider"))).data();
  const allocated = (await getDoc(doc(driver, "journeyAcceptanceGuards/j1"))).data().acceptanceCount;
  assert.equal(closed.status, "CANCELLED");
  assert.equal(allocated, closed.seatCapacity - closed.seatsRemaining);
  assert.equal(closed.seatsRemaining, sourceTrip.status === "CANCELLED_BY_RIDER" ? 1 : 0);
  await assertFails(cancelConfirmedBatch(rider, "j1", "j1_rider", 1));
});

test("Auth emulator supports local email registration, sign-out and sign-in", async () => {
  const email = `phase9b-${Date.now()}@example.test`;
  const created = await createUserWithEmailAndPassword(auth, email, "password-123");
  const uid = created.user.uid;
  await signOut(auth);
  const signedIn = await signInWithEmailAndPassword(auth, email, "password-123");
  assert.equal(signedIn.user.uid, uid);
  await signOut(auth);
});

test("unauthenticated profile and saved-place access is denied", async () => {
  const db = environment.unauthenticatedContext().firestore();
  await assertFails(getDoc(doc(db, "users/alex")));
  await assertFails(setDoc(doc(db, "users/alex"), profile("alex")));
  await assertFails(setDoc(doc(db, "users/alex/savedPlaces/home"), place("alex", "Home", "Nottingham")));
});

test("a user can read and write their own profile and Home and Work", async () => {
  const db = environment.authenticatedContext("alex").firestore();
  const userRef = doc(db, "users/alex");
  await assertSucceeds(setDoc(userRef, profile("alex")));
  await assertSucceeds(getDoc(userRef));
  await assertSucceeds(updateDoc(userRef, { displayName: "Alex R" }));
  await assertSucceeds(setDoc(doc(db, "users/alex/savedPlaces/home"), place("alex", "Home", "Sutton-in-Ashfield")));
  await assertSucceeds(setDoc(doc(db, "users/alex/savedPlaces/work"), place("alex", "Work", "Nottingham")));
  await assertSucceeds(getDoc(doc(db, "users/alex/savedPlaces/home")));
});

test("a user cannot read or write another user's data", async () => {
  const alex = environment.authenticatedContext("alex").firestore();
  await environment.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "users/beth"), profile("beth", "Beth"));
    await setDoc(doc(context.firestore(), "users/beth/savedPlaces/home"), place("beth", "Home", "Derby"));
  });
  await assertFails(getDoc(doc(alex, "users/beth")));
  await assertFails(setDoc(doc(alex, "users/beth"), profile("beth", "Changed")));
  await assertFails(getDoc(doc(alex, "users/beth/savedPlaces/home")));
  await assertFails(setDoc(doc(alex, "users/beth/savedPlaces/work"), place("beth", "Work", "Leicester")));
});

test("identity mutation, extra fields, private-looking areas and extra place IDs are denied", async () => {
  const db = environment.authenticatedContext("alex").firestore();
  await assertSucceeds(setDoc(doc(db, "users/alex"), profile("alex")));
  await assertFails(setDoc(doc(db, "users/alex"), { uid: "beth", displayName: "Alex" }));
  await assertFails(setDoc(doc(db, "users/alex"), { ...profile("alex"), role: "admin" }));
  await assertFails(updateDoc(doc(db, "users/alex"), { displayName: "   " }));
  await assertFails(updateDoc(doc(db, "users/alex"), { displayName: "Alex\u0007" }));
  await assertFails(setDoc(doc(db, "users/alex/savedPlaces/home"), place("alex", "Home", "12 High Street")));
  await assertFails(setDoc(doc(db, "users/alex/savedPlaces/gym"), place("alex", "Gym", "Nottingham")));
});

test("unrecognised collections remain denied", async () => {
  const db = environment.authenticatedContext("alex").firestore();
  await assertFails(setDoc(doc(db, "messages/message-one"), { ownerUid: "alex" }));
  await assertFails(getDoc(doc(db, "messages/message-one")));
});

test("journey and private zeroed acceptance guard must be created atomically", async () => {
  const driver = environment.authenticatedContext("driver").firestore();
  const rider = environment.authenticatedContext("rider").firestore();
  const stranger = environment.authenticatedContext("stranger").firestore();
  const anonymous = environment.unauthenticatedContext().firestore();
  await assertFails(setDoc(doc(driver, "journeys/unguarded"), journey("driver")));
  await assertFails(setDoc(doc(driver, "journeyAcceptanceGuards/orphan"), guard("driver")));
  await assertSucceeds(createJourney(driver, "j1", "driver"));
  await assertSucceeds(getDoc(doc(rider, "journeys/j1")));
  await assertSucceeds(getDocs(collection(rider, "journeys")));
  await assertSucceeds(getDoc(doc(driver, "journeyAcceptanceGuards/j1")));
  await assertFails(getDoc(doc(rider, "journeyAcceptanceGuards/j1")));
  await assertFails(getDoc(doc(stranger, "journeyAcceptanceGuards/j1")));
  await assertFails(getDocs(collection(driver, "journeyAcceptanceGuards")));
  await assertFails(deleteDoc(doc(driver, "journeyAcceptanceGuards/j1")));
  await assertFails(getDocs(collection(anonymous, "journeys")));

  await assertFails(createJourney(rider, "forged", "driver"));
  await assertFails(createJourney(
    driver,
    "private",
    "driver",
    2,
    guard("driver"),
    { ...journey("driver"), originArea: "12 High Street" },
  ));
  await assertFails(createJourney(driver, "too-many", "driver", 9));
  await assertFails(createJourney(driver, "wrong-guard-owner", "driver", 2, guard("stranger")));
  await assertFails(createJourney(driver, "nonzero-guard", "driver", 2, guard("driver", 1, "forged")));
});

test("requests enforce real participants deterministic ownership and self-request denial", async () => {
  const driver = environment.authenticatedContext("driver").firestore();
  const rider = environment.authenticatedContext("rider").firestore();
  const stranger = environment.authenticatedContext("stranger").firestore();
  await assertSucceeds(createJourney(driver, "j1", "driver"));
  await assertSucceeds(setDoc(doc(rider, "seatRequests/j1_rider"), request("j1", "driver", "rider")));
  await assertSucceeds(getDoc(doc(driver, "seatRequests/j1_rider")));
  await assertSucceeds(getDoc(doc(rider, "seatRequests/j1_rider")));
  await assertSucceeds(getDocs(query(collection(driver, "seatRequests"), where("driverUid", "==", "driver"))));
  await assertSucceeds(getDocs(query(collection(rider, "seatRequests"), where("riderUid", "==", "rider"))));
  await assertFails(getDocs(collection(stranger, "seatRequests")));
  await assertFails(getDoc(doc(stranger, "seatRequests/j1_rider")));
  await assertFails(setDoc(doc(rider, "seatRequests/random"), request("j1", "driver", "rider")));
  await assertFails(setDoc(doc(rider, "seatRequests/j1_rider"), request("j1", "stranger", "rider")));
  await assertFails(setDoc(doc(driver, "seatRequests/j1_driver"), request("j1", "driver", "driver")));
});

test("request identity is the rider profile snapshot and remains participant private", async () => {
  const driver = environment.authenticatedContext("driver").firestore();
  const rider = environment.authenticatedContext("rider").firestore();
  const stranger = environment.authenticatedContext("stranger").firestore();
  const noProfile = environment.authenticatedContext("no-profile").firestore();
  await assertSucceeds(createJourney(driver, "identity", "driver", 2));

  await assertFails(setDoc(
    doc(rider, "seatRequests/identity_rider"),
    request("identity", "driver", "rider", "PENDING", "Spoofed rider"),
  ));
  await assertFails(setDoc(
    doc(noProfile, "seatRequests/identity_no-profile"),
    request("identity", "driver", "no-profile", "PENDING", "No profile"),
  ));
  await assertSucceeds(requestSeatLikeGateway(rider, "identity", "rider"));

  const driverRequest = await assertSucceeds(getDoc(doc(driver, "seatRequests/identity_rider")));
  const riderRequest = await assertSucceeds(getDoc(doc(rider, "seatRequests/identity_rider")));
  assert.equal(driverRequest.data().riderDisplayName, "Riley Rider");
  assert.deepEqual(driverRequest.data(), riderRequest.data());
  await assertFails(getDoc(doc(stranger, "seatRequests/identity_rider")));
  await assertFails(getDoc(doc(driver, "users/rider")));

  await assertSucceeds(updateDoc(doc(rider, "users/rider"), { displayName: "Riley Renamed" }));
  assert.equal((await getDoc(doc(rider, "seatRequests/identity_rider"))).data().riderDisplayName, "Riley Rider");
  await assertFails(updateDoc(doc(rider, "seatRequests/identity_rider"), { riderDisplayName: "Riley Renamed" }));
  await assertFails(updateDoc(doc(driver, "seatRequests/identity_rider"), { riderDisplayName: "Riley Renamed" }));
});

test("duplicate display names remain distinct requests keyed by deterministic request id", async () => {
  const driver = environment.authenticatedContext("driver").firestore();
  const riderA = environment.authenticatedContext("rider-a").firestore();
  const riderB = environment.authenticatedContext("rider-b").firestore();
  await assertSucceeds(createJourney(driver, "same-name", "driver", 2));
  await assertSucceeds(requestSeatLikeGateway(riderA, "same-name", "rider-a"));
  await assertSucceeds(requestSeatLikeGateway(riderB, "same-name", "rider-b"));

  const requests = await assertSucceeds(getDocs(query(
    collection(driver, "seatRequests"),
    where("driverUid", "==", "driver"),
  )));
  assert.deepEqual(requests.docs.map((item) => item.id).sort(), ["same-name_rider-a", "same-name_rider-b"]);
  assert.deepEqual(requests.docs.map((item) => item.data().riderDisplayName), ["Shared rider name", "Shared rider name"]);
});

test("gateway transaction can blindly create a deterministic request without reading private absence", async () => {
  const driver = environment.authenticatedContext("driver").firestore();
  const rider = environment.authenticatedContext("rider").firestore();
  const requestRef = doc(rider, "seatRequests/j1_rider");
  await assertSucceeds(createJourney(driver, "j1", "driver", 1));

  await assertFails(getDoc(requestRef));
  await assertSucceeds(requestSeatLikeGateway(rider, "j1", "rider"));
  assert.deepEqual((await getDoc(requestRef)).data(), request("j1", "driver", "rider"));
});

test("rider can cancel and safely re-request the same available journey", async () => {
  const driver = environment.authenticatedContext("driver").firestore();
  const rider = environment.authenticatedContext("rider").firestore();
  const stranger = environment.authenticatedContext("stranger").firestore();
  const requestRef = doc(rider, "seatRequests/j1_rider");
  await assertSucceeds(createJourney(driver, "j1", "driver", 1));
  await assertSucceeds(requestSeatLikeGateway(rider, "j1", "rider"));
  await assertFails(requestSeatLikeGateway(rider, "j1", "rider"));

  await assertFails(updateDoc(doc(driver, "seatRequests/j1_rider"), { status: "CANCELLED" }));
  await assertFails(updateDoc(doc(stranger, "seatRequests/j1_rider"), { status: "CANCELLED" }));
  await assertSucceeds(updateDoc(requestRef, { status: "CANCELLED" }));
  await assertFails(updateDoc(doc(driver, "seatRequests/j1_rider"), { status: "ACCEPTED" }));
  await assertFails(updateDoc(doc(driver, "seatRequests/j1_rider"), { status: "DECLINED" }));
  await assertSucceeds(updateDoc(doc(rider, "users/rider"), { displayName: "Riley Re-requested" }));
  await assertFails(setDoc(requestRef, request("j1", "driver", "rider", "PENDING", "Spoofed rider")));
  await assertSucceeds(requestSeatLikeGateway(rider, "j1", "rider"));
  const rerequested = (await getDoc(requestRef)).data();
  assert.equal(rerequested.status, "PENDING");
  assert.equal(rerequested.riderDisplayName, "Riley Re-requested");
});

test("legacy requests stay operable and a re-request adds the current verified snapshot", async () => {
  const driver = environment.authenticatedContext("driver").firestore();
  const rider = environment.authenticatedContext("rider").firestore();
  await assertSucceeds(createJourney(driver, "legacy-request", "driver", 2));
  await environment.withSecurityRulesDisabled(async (context) => {
    await setDoc(
      doc(context.firestore(), "seatRequests/legacy-request_rider"),
      legacyRequest("legacy-request", "driver", "rider"),
    );
  });

  const legacy = await assertSucceeds(getDoc(doc(driver, "seatRequests/legacy-request_rider")));
  assert.equal("riderDisplayName" in legacy.data(), false);
  await assertSucceeds(updateDoc(doc(rider, "seatRequests/legacy-request_rider"), { status: "CANCELLED" }));
  await assertSucceeds(updateDoc(doc(rider, "users/rider"), { displayName: "Current Rider" }));
  await assertSucceeds(requestSeatLikeGateway(rider, "legacy-request", "rider"));
  assert.deepEqual(
    (await getDoc(doc(driver, "seatRequests/legacy-request_rider"))).data(),
    request("legacy-request", "driver", "rider", "PENDING", "Current Rider"),
  );
});

test("rider cancellation racing driver acceptance leaves one consistent outcome", async () => {
  const driver = environment.authenticatedContext("driver").firestore();
  const rider = environment.authenticatedContext("rider").firestore();
  await assertSucceeds(createJourney(driver, "j1", "driver", 1));
  await assertSucceeds(setDoc(
    doc(rider, "seatRequests/j1_rider"),
    request("j1", "driver", "rider"),
  ));

  const outcomes = await Promise.allSettled([
    updateDoc(doc(rider, "seatRequests/j1_rider"), { status: "CANCELLED" }),
    acceptRequest(driver, "j1", "j1_rider", 0, 1),
  ]);
  assert.equal(outcomes.filter(({ status }) => status === "fulfilled").length, 1);
  assert.equal(outcomes.filter(({ status }) => status === "rejected").length, 1);

  const finalRequest = (await getDoc(doc(rider, "seatRequests/j1_rider"))).data();
  const finalJourney = (await getDoc(doc(driver, "journeys/j1"))).data();
  const finalGuard = (await getDoc(doc(driver, "journeyAcceptanceGuards/j1"))).data();
  if (finalRequest.status === "ACCEPTED") {
    assert.equal(finalJourney.seatsRemaining, 0);
    assert.deepEqual(finalGuard, guard("driver", 1, "j1_rider"));
  } else {
    assert.equal(finalRequest.status, "CANCELLED");
    assert.equal(finalJourney.seatsRemaining, 1);
    assert.deepEqual(finalGuard, guard("driver"));
  }
});

test("re-request is denied after capacity is consumed or departure has passed", async () => {
  const driver = environment.authenticatedContext("driver").firestore();
  const riderA = environment.authenticatedContext("rider-a").firestore();
  const riderB = environment.authenticatedContext("rider-b").firestore();
  await assertSucceeds(createJourney(driver, "j1", "driver", 1));
  await assertSucceeds(setDoc(doc(riderA, "seatRequests/j1_rider-a"), request("j1", "driver", "rider-a")));
  await assertSucceeds(updateDoc(doc(riderA, "seatRequests/j1_rider-a"), { status: "CANCELLED" }));
  await assertSucceeds(setDoc(doc(riderB, "seatRequests/j1_rider-b"), request("j1", "driver", "rider-b")));
  await assertSucceeds(acceptRequest(driver, "j1", "j1_rider-b", 0, 1));
  await assertFails(updateDoc(doc(riderA, "seatRequests/j1_rider-a"), { status: "PENDING" }));

  await environment.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, "journeys/expired"), {
      ...journey("driver", 1),
      departureAt: Timestamp.fromMillis(Date.now() - 60_000),
    });
    await setDoc(doc(db, "journeyAcceptanceGuards/expired"), guard("driver"));
    await setDoc(
      doc(db, "seatRequests/expired_rider-a"),
      request("expired", "driver", "rider-a", "CANCELLED"),
    );
  });
  await assertFails(updateDoc(doc(riderA, "seatRequests/expired_rider-a"), { status: "PENDING" }));
  await assertFails(setDoc(
    doc(riderB, "seatRequests/expired_rider-b"),
    request("expired", "driver", "rider-b"),
  ));
});

test("departed pending request cannot be accepted and leaves every document unchanged", async () => {
  const driver = environment.authenticatedContext("driver").firestore();
  await environment.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, "journeys/expired-acceptance"), {
      ...journey("driver", 1),
      departureAt: Timestamp.fromMillis(Date.now() - 60_000),
    });
    await setDoc(doc(db, "journeyAcceptanceGuards/expired-acceptance"), guard("driver"));
    await setDoc(
      doc(db, "seatRequests/expired-acceptance_rider"),
      request("expired-acceptance", "driver", "rider"),
    );
  });

  await assertFails(acceptRequest(
    driver,
    "expired-acceptance",
    "expired-acceptance_rider",
    0,
    1,
  ));

  assert.equal(
    (await getDoc(doc(driver, "seatRequests/expired-acceptance_rider"))).data().status,
    "PENDING",
  );
  assert.equal((await getDoc(doc(driver, "journeys/expired-acceptance"))).data().seatsRemaining, 1);
  assert.deepEqual(
    (await getDoc(doc(driver, "journeyAcceptanceGuards/expired-acceptance"))).data(),
    guard("driver"),
  );
  assert.equal((await getDocs(query(
    collection(driver, "confirmedTrips"),
    where("driverUid", "==", "driver"),
  ))).size, 0);
});

test("accepted and declined requests remain terminal for riders", async () => {
  const driver = environment.authenticatedContext("driver").firestore();
  const acceptedRider = environment.authenticatedContext("accepted-rider").firestore();
  const declinedRider = environment.authenticatedContext("declined-rider").firestore();
  await assertSucceeds(createJourney(driver, "j1", "driver", 2));
  await assertSucceeds(setDoc(
    doc(acceptedRider, "seatRequests/j1_accepted-rider"),
    request("j1", "driver", "accepted-rider"),
  ));
  await assertSucceeds(setDoc(
    doc(declinedRider, "seatRequests/j1_declined-rider"),
    request("j1", "driver", "declined-rider"),
  ));
  await assertSucceeds(acceptRequest(driver, "j1", "j1_accepted-rider", 1, 1));
  await assertSucceeds(updateDoc(doc(driver, "seatRequests/j1_declined-rider"), { status: "DECLINED" }));

  for (const [db, requestId] of [
    [acceptedRider, "j1_accepted-rider"],
    [declinedRider, "j1_declined-rider"],
  ]) {
    await assertFails(updateDoc(doc(db, `seatRequests/${requestId}`), { status: "CANCELLED" }));
    await assertFails(requestSeatLikeGateway(db, "j1", requestId.substring("j1_".length)));
  }
});

test("only the driver may decide and acceptance requires an atomic one-seat decrement", async () => {
  const driver = environment.authenticatedContext("driver").firestore();
  const rider = environment.authenticatedContext("rider").firestore();
  await assertSucceeds(createJourney(driver, "j1", "driver", 2));
  await assertSucceeds(setDoc(doc(rider, "seatRequests/j1_rider"), request("j1", "driver", "rider")));
  await assertFails(updateDoc(doc(rider, "seatRequests/j1_rider"), { status: "ACCEPTED" }));
  await assertFails(updateDoc(doc(driver, "seatRequests/j1_rider"), { status: "ACCEPTED" }));

  await assertSucceeds(acceptRequest(driver, "j1", "j1_rider", 1, 1));
  assert.equal((await getDoc(doc(driver, "journeys/j1"))).data().seatsRemaining, 1);
  assert.deepEqual((await getDoc(doc(driver, "journeyAcceptanceGuards/j1"))).data(), guard("driver", 1, "j1_rider"));
  assert.equal((await getDoc(doc(driver, "confirmedTrips/j1_rider"))).data().status, "CONFIRMED");
  await assertFails(updateDoc(doc(driver, "seatRequests/j1_rider"), { status: "DECLINED" }));
  await assertFails(acceptRequest(driver, "j1", "j1_rider", 0, 2));
  assert.equal((await getDoc(doc(driver, "journeys/j1"))).data().seatsRemaining, 1);
});

test("confirmed trip is created once and remains private and immutable", async () => {
  const driver = environment.authenticatedContext("driver").firestore();
  const rider = environment.authenticatedContext("rider").firestore();
  const stranger = environment.authenticatedContext("stranger").firestore();
  const anonymous = environment.unauthenticatedContext().firestore();
  await assertSucceeds(createJourney(driver, "j1", "driver", 1));
  await assertSucceeds(setDoc(doc(rider, "seatRequests/j1_rider"), request("j1", "driver", "rider")));

  await assertSucceeds(acceptRequest(driver, "j1", "j1_rider", 0, 1));

  const driverTrip = await assertSucceeds(getDoc(doc(driver, "confirmedTrips/j1_rider")));
  const riderTrip = await assertSucceeds(getDoc(doc(rider, "confirmedTrips/j1_rider")));
  assert.deepEqual(driverTrip.data(), riderTrip.data());
  assert.equal(driverTrip.data().acceptedRequestId, "j1_rider");
  assert.equal(driverTrip.data().status, "CONFIRMED");
  assert.equal(driverTrip.data().driverDisplayName, "User driver");
  await assertFails(getDoc(doc(rider, "users/driver")));
  assert.equal(
    (await assertSucceeds(getDocs(query(
      collection(driver, "confirmedTrips"),
      where("driverUid", "==", "driver"),
    )))).size,
    1,
  );
  assert.equal(
    (await assertSucceeds(getDocs(query(
      collection(rider, "confirmedTrips"),
      where("riderUid", "==", "rider"),
    )))).size,
    1,
  );
  await assertFails(getDoc(doc(stranger, "confirmedTrips/j1_rider")));
  await assertFails(getDocs(query(
    collection(stranger, "confirmedTrips"),
    where("driverUid", "==", "driver"),
  )));
  await assertFails(getDoc(doc(anonymous, "confirmedTrips/j1_rider")));
  await assertFails(getDocs(query(
    collection(anonymous, "confirmedTrips"),
    where("riderUid", "==", "rider"),
  )));
  await assertFails(getDocs(collection(driver, "confirmedTrips")));
  await assertFails(updateDoc(doc(driver, "confirmedTrips/j1_rider"), { status: "CONFIRMED" }));
  await assertFails(updateDoc(doc(rider, "confirmedTrips/j1_rider"), { originArea: "Derby" }));
  await assertFails(deleteDoc(doc(driver, "confirmedTrips/j1_rider")));
  await assertFails(deleteDoc(doc(rider, "confirmedTrips/j1_rider")));

  await assertFails(acceptRequest(driver, "j1", "j1_rider", 0, 1));
  assert.equal((await getDocs(query(
    collection(driver, "confirmedTrips"),
    where("driverUid", "==", "driver"),
  ))).size, 1);
});

test("acceptance and confirmed trip creation require all four valid coupled writes", async () => {
  const driver = environment.authenticatedContext("driver").firestore();
  const rider = environment.authenticatedContext("rider").firestore();
  const stranger = environment.authenticatedContext("stranger").firestore();
  const sourceJourney = journey("driver", 1);
  const sourceRequest = request("j1", "driver", "rider");
  const validTrip = confirmedTrip("j1_rider", sourceRequest, sourceJourney);
  await assertSucceeds(createJourney(driver, "j1", "driver", 1, guard("driver"), sourceJourney));
  await assertSucceeds(setDoc(doc(rider, "seatRequests/j1_rider"), sourceRequest));

  await assertFails(acceptanceBatch(driver, "j1", "j1_rider", 0, 1, null));
  await assertFails(setDoc(doc(driver, "confirmedTrips/j1_rider"), validTrip));
  await assertFails(setDoc(doc(rider, "confirmedTrips/j1_rider"), validTrip));
  await assertFails(setDoc(doc(stranger, "confirmedTrips/j1_rider"), validTrip));
  assert.equal((await getDoc(doc(driver, "seatRequests/j1_rider"))).data().status, "PENDING");
  assert.equal((await getDoc(doc(driver, "journeys/j1"))).data().seatsRemaining, 1);
  assert.deepEqual((await getDoc(doc(driver, "journeyAcceptanceGuards/j1"))).data(), guard("driver"));
});

test("confirmed trip exact identifiers participants route departure status and fields are enforced", async () => {
  const driver = environment.authenticatedContext("driver").firestore();
  const rider = environment.authenticatedContext("rider").firestore();
  const invalidCases = [
    { tripId: (requestId) => `${requestId}-other`, overrides: {} },
    { overrides: { acceptedRequestId: "other" } },
    { overrides: { journeyId: "other" } },
    { overrides: { driverUid: "stranger" } },
    { overrides: { riderUid: "stranger" } },
    { overrides: { originArea: "Derby" } },
    { overrides: { status: "PENDING" } },
    { overrides: { driverDisplayName: "Forged Driver" } },
    { overrides: { driverDisplayName: "\u0007" } },
    { legacy: true, overrides: {} },
    { overrides: { forged: true } },
  ];

  for (const [index, invalid] of invalidCases.entries()) {
    const journeyId = `invalid-${index}`;
    const requestId = `${journeyId}_rider`;
    const sourceJourney = journey("driver", 1);
    const sourceRequest = request(journeyId, "driver", "rider");
    await assertSucceeds(createJourney(
      driver,
      journeyId,
      "driver",
      1,
      guard("driver"),
      sourceJourney,
    ));
    await assertSucceeds(setDoc(doc(rider, `seatRequests/${requestId}`), sourceRequest));
    const invalidTrip = invalid.legacy
      ? legacyConfirmedTrip(requestId, sourceRequest, sourceJourney, invalid.overrides)
      : confirmedTrip(requestId, sourceRequest, sourceJourney, invalid.overrides);
    await assertFails(acceptanceBatch(
      driver,
      journeyId,
      requestId,
      0,
      1,
      invalidTrip,
      invalid.tripId?.(requestId) ?? requestId,
    ));
  }

  const journeyId = "invalid-departure";
  const requestId = `${journeyId}_rider`;
  const sourceJourney = journey("driver", 1);
  const sourceRequest = request(journeyId, "driver", "rider");
  await assertSucceeds(createJourney(driver, journeyId, "driver", 1, guard("driver"), sourceJourney));
  await assertSucceeds(setDoc(doc(rider, `seatRequests/${requestId}`), sourceRequest));
  await assertFails(acceptanceBatch(
    driver,
    journeyId,
    requestId,
    0,
    1,
    confirmedTrip(requestId, sourceRequest, sourceJourney, {
      departureAt: Timestamp.fromMillis(sourceJourney.departureAt.toMillis() + 1_000),
    }),
  ));

  const missingProfileJourneyId = "missing-driver-profile";
  const missingProfileRequestId = `${missingProfileJourneyId}_rider`;
  const missingProfileJourney = journey("driver", 1);
  const missingProfileRequest = request(missingProfileJourneyId, "driver", "rider");
  await assertSucceeds(createJourney(
    driver,
    missingProfileJourneyId,
    "driver",
    1,
    guard("driver"),
    missingProfileJourney,
  ));
  await assertSucceeds(setDoc(doc(rider, `seatRequests/${missingProfileRequestId}`), missingProfileRequest));
  await environment.withSecurityRulesDisabled(async (context) => {
    await deleteDoc(doc(context.firestore(), "users/driver"));
  });
  await assertFails(acceptanceBatch(
    driver,
    missingProfileJourneyId,
    missingProfileRequestId,
    0,
    1,
    confirmedTrip(missingProfileRequestId, missingProfileRequest, missingProfileJourney),
  ));
});

test("two distinct pending requests can be accepted sequentially", async () => {
  const driver = environment.authenticatedContext("driver").firestore();
  const riderA = environment.authenticatedContext("rider-a").firestore();
  const riderB = environment.authenticatedContext("rider-b").firestore();
  await assertSucceeds(createJourney(driver, "j1", "driver", 2));
  await assertSucceeds(setDoc(doc(riderA, "seatRequests/j1_rider-a"), request("j1", "driver", "rider-a")));
  await assertSucceeds(acceptRequest(driver, "j1", "j1_rider-a", 1, 1));
  await assertSucceeds(setDoc(doc(riderB, "seatRequests/j1_rider-b"), request("j1", "driver", "rider-b")));
  await assertSucceeds(acceptRequest(driver, "j1", "j1_rider-b", 0, 2));

  assert.equal((await getDoc(doc(driver, "journeys/j1"))).data().seatsRemaining, 0);
  assert.deepEqual(
    (await getDoc(doc(driver, "journeyAcceptanceGuards/j1"))).data(),
    guard("driver", 2, "j1_rider-b"),
  );
});

test("decline preserves both the seat count and acceptance guard", async () => {
  const driver = environment.authenticatedContext("driver").firestore();
  const rider = environment.authenticatedContext("rider").firestore();
  await assertSucceeds(createJourney(driver, "j1", "driver", 1));
  await assertSucceeds(setDoc(doc(rider, "seatRequests/j1_rider"), request("j1", "driver", "rider")));

  await assertSucceeds(updateDoc(doc(driver, "seatRequests/j1_rider"), { status: "DECLINED" }));

  assert.equal((await getDoc(doc(driver, "journeys/j1"))).data().seatsRemaining, 1);
  assert.deepEqual(
    (await getDoc(doc(driver, "journeyAcceptanceGuards/j1"))).data(),
    guard("driver"),
  );
});

test("standalone partial forged and multi-request acceptance writes are denied", async () => {
  const driver = environment.authenticatedContext("driver").firestore();
  const riderA = environment.authenticatedContext("rider-a").firestore();
  const riderB = environment.authenticatedContext("rider-b").firestore();
  await assertSucceeds(createJourney(driver, "j1", "driver", 2));
  await assertSucceeds(createJourney(driver, "j2", "driver", 1));
  await assertSucceeds(setDoc(doc(riderA, "seatRequests/j1_rider-a"), request("j1", "driver", "rider-a")));
  await assertSucceeds(setDoc(doc(riderB, "seatRequests/j1_rider-b"), request("j1", "driver", "rider-b")));
  await assertSucceeds(setDoc(doc(riderA, "seatRequests/j2_rider-a"), request("j2", "driver", "rider-a")));

  await assertFails(updateDoc(doc(driver, "journeys/j1"), { seatsRemaining: 1 }));
  await assertFails(updateDoc(doc(driver, "journeyAcceptanceGuards/j1"), {
    acceptanceCount: 1,
    lastAcceptedRequestId: "j1_rider-a",
  }));

  const noGuard = writeBatch(driver);
  noGuard.update(doc(driver, "journeys/j1"), { seatsRemaining: 1 });
  noGuard.update(doc(driver, "seatRequests/j1_rider-a"), { status: "ACCEPTED" });
  await assertFails(noGuard.commit());

  const noRequest = writeBatch(driver);
  noRequest.update(doc(driver, "journeys/j1"), { seatsRemaining: 1 });
  noRequest.update(doc(driver, "journeyAcceptanceGuards/j1"), {
    acceptanceCount: 1,
    lastAcceptedRequestId: "j1_rider-a",
  });
  await assertFails(noRequest.commit());

  const noJourney = writeBatch(driver);
  noJourney.update(doc(driver, "seatRequests/j1_rider-a"), { status: "ACCEPTED" });
  noJourney.update(doc(driver, "journeyAcceptanceGuards/j1"), {
    acceptanceCount: 1,
    lastAcceptedRequestId: "j1_rider-a",
  });
  await assertFails(noJourney.commit());

  const foreignRequest = writeBatch(driver);
  foreignRequest.update(doc(driver, "journeys/j1"), { seatsRemaining: 1 });
  foreignRequest.update(doc(driver, "seatRequests/j2_rider-a"), { status: "ACCEPTED" });
  foreignRequest.update(doc(driver, "journeyAcceptanceGuards/j1"), {
    acceptanceCount: 1,
    lastAcceptedRequestId: "j2_rider-a",
  });
  await assertFails(foreignRequest.commit());

  const twoRequests = writeBatch(driver);
  twoRequests.update(doc(driver, "journeys/j1"), { seatsRemaining: 1 });
  twoRequests.update(doc(driver, "seatRequests/j1_rider-a"), { status: "ACCEPTED" });
  twoRequests.update(doc(driver, "seatRequests/j1_rider-b"), { status: "ACCEPTED" });
  twoRequests.update(doc(driver, "journeyAcceptanceGuards/j1"), {
    acceptanceCount: 1,
    lastAcceptedRequestId: "j1_rider-a",
  });
  await assertFails(twoRequests.commit());

  const skippedCount = writeBatch(driver);
  skippedCount.update(doc(driver, "journeys/j1"), { seatsRemaining: 1 });
  skippedCount.update(doc(driver, "seatRequests/j1_rider-a"), { status: "ACCEPTED" });
  skippedCount.update(doc(driver, "journeyAcceptanceGuards/j1"), {
    acceptanceCount: 2,
    lastAcceptedRequestId: "j1_rider-a",
  });
  await assertFails(skippedCount.commit());

  const extraGuardField = writeBatch(driver);
  extraGuardField.update(doc(driver, "journeys/j1"), { seatsRemaining: 1 });
  extraGuardField.update(doc(driver, "seatRequests/j1_rider-a"), { status: "ACCEPTED" });
  extraGuardField.set(doc(driver, "journeyAcceptanceGuards/j1"), {
    ...guard("driver", 1, "j1_rider-a"),
    forged: true,
  });
  await assertFails(extraGuardField.commit());
});

test("existing unguarded journeys fail closed and cannot receive a client guard", async () => {
  const driver = environment.authenticatedContext("driver").firestore();
  const newRider = environment.authenticatedContext("other").firestore();
  await environment.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, "journeys/legacy"), journey("driver", 1));
    await setDoc(doc(db, "seatRequests/legacy_rider"), request("legacy", "driver", "rider"));
  });

  await assertFails(setDoc(doc(newRider, "seatRequests/legacy_other"), request("legacy", "driver", "other")));
  await assertFails(updateDoc(doc(driver, "journeys/legacy"), { seatsRemaining: 0 }));
  await assertFails(updateDoc(doc(driver, "seatRequests/legacy_rider"), { status: "ACCEPTED" }));
  await assertFails(updateDoc(doc(driver, "seatRequests/legacy_rider"), { status: "DECLINED" }));
  await assertFails(setDoc(doc(driver, "journeyAcceptanceGuards/legacy"), guard("driver")));
});

test("simultaneous candidates cannot overbook one seat", async () => {
  const driver = environment.authenticatedContext("driver").firestore();
  const riderA = environment.authenticatedContext("rider-a").firestore();
  const riderB = environment.authenticatedContext("rider-b").firestore();
  await assertSucceeds(createJourney(driver, "j1", "driver", 1));
  await assertSucceeds(setDoc(doc(riderA, "seatRequests/j1_rider-a"), request("j1", "driver", "rider-a")));
  await assertSucceeds(setDoc(doc(riderB, "seatRequests/j1_rider-b"), request("j1", "driver", "rider-b")));

  const outcomes = await Promise.allSettled([
    acceptRequest(driver, "j1", "j1_rider-a", 0, 1),
    acceptRequest(driver, "j1", "j1_rider-b", 0, 1),
  ]);
  assert.equal(outcomes.filter(({ status }) => status === "fulfilled").length, 1);
  assert.equal(outcomes.filter(({ status }) => status === "rejected").length, 1);

  const requests = await Promise.all([
    getDoc(doc(driver, "seatRequests/j1_rider-a")),
    getDoc(doc(driver, "seatRequests/j1_rider-b")),
  ]);
  assert.equal(requests.filter((snapshot) => snapshot.data().status === "ACCEPTED").length, 1);
  assert.equal((await getDoc(doc(driver, "journeys/j1"))).data().seatsRemaining, 0);
  assert.equal((await getDoc(doc(driver, "journeyAcceptanceGuards/j1"))).data().acceptanceCount, 1);
  const trips = await getDocs(query(
    collection(driver, "confirmedTrips"),
    where("driverUid", "==", "driver"),
  ));
  assert.equal(trips.size, 1);
  assert.equal(
    trips.docs[0].id,
    requests.find((snapshot) => snapshot.data().status === "ACCEPTED").id,
  );
});

test("the expected successful reads return documents", async () => {
  const db = environment.authenticatedContext("alex").firestore();
  await assertSucceeds(setDoc(doc(db, "users/alex"), profile("alex")));
  const result = await assertSucceeds(getDoc(doc(db, "users/alex")));
  assert.equal(result.data().displayName, "Alex");
});
