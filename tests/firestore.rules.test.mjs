import { after, before, beforeEach, test } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  Timestamp, collection, deleteDoc, doc, getDoc, getDocs, query, setDoc, updateDoc, where, writeBatch,
} from "firebase/firestore";
import { deleteApp, initializeApp } from "firebase/app";
import {
  connectAuthEmulator,
  createUserWithEmailAndPassword,
  getAuth,
  signInWithEmailAndPassword,
  signOut,
} from "firebase/auth";

const projectId = "ryde-79893";
let environment;
let authApp;
let auth;

before(async () => {
  environment = await initializeTestEnvironment({
    projectId,
    firestore: {
      host: "127.0.0.1",
      port: 8080,
      rules: fs.readFileSync("firestore.rules", "utf8"),
    },
  });
  authApp = initializeApp({ projectId, apiKey: "local-emulator-only" }, "ryde-auth-integration");
  auth = getAuth(authApp);
  connectAuthEmulator(auth, "http://127.0.0.1:9099", { disableWarnings: true });
});

beforeEach(async () => environment.clearFirestore());
after(async () => {
  await environment.cleanup();
  await deleteApp(authApp);
});

const profile = (uid, displayName = "Alex") => ({ uid, displayName });
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
const request = (journeyId, driverUid, riderUid, status = "PENDING") => ({
  journeyId, driverUid, riderUid, status,
});
const guard = (driverUid, acceptanceCount = 0, lastAcceptedRequestId = null) => ({
  driverUid, acceptanceCount, lastAcceptedRequestId,
});

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

const acceptRequest = (db, journeyId, requestId, seatsRemaining, acceptanceCount) => {
  const batch = writeBatch(db);
  batch.update(doc(db, `journeys/${journeyId}`), { seatsRemaining });
  batch.update(doc(db, `seatRequests/${requestId}`), { status: "ACCEPTED" });
  batch.update(doc(db, `journeyAcceptanceGuards/${journeyId}`), {
    acceptanceCount,
    lastAcceptedRequestId: requestId,
  });
  return batch.commit();
};

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

test("only the driver may decide and acceptance requires an atomic one-seat decrement", async () => {
  const driver = environment.authenticatedContext("driver").firestore();
  const rider = environment.authenticatedContext("rider").firestore();
  await assertSucceeds(createJourney(driver, "j1", "driver", 2));
  await assertSucceeds(setDoc(doc(rider, "seatRequests/j1_rider"), request("j1", "driver", "rider")));
  await assertFails(updateDoc(doc(rider, "seatRequests/j1_rider"), { status: "ACCEPTED" }));
  await assertFails(updateDoc(doc(driver, "seatRequests/j1_rider"), { status: "ACCEPTED" }));

  const batch = writeBatch(driver);
  batch.update(doc(driver, "journeys/j1"), { seatsRemaining: 1 });
  batch.update(doc(driver, "seatRequests/j1_rider"), { status: "ACCEPTED" });
  batch.update(doc(driver, "journeyAcceptanceGuards/j1"), {
    acceptanceCount: 1,
    lastAcceptedRequestId: "j1_rider",
  });
  await assertSucceeds(batch.commit());
  assert.equal((await getDoc(doc(driver, "journeys/j1"))).data().seatsRemaining, 1);
  assert.deepEqual((await getDoc(doc(driver, "journeyAcceptanceGuards/j1"))).data(), guard("driver", 1, "j1_rider"));
  await assertFails(updateDoc(doc(driver, "seatRequests/j1_rider"), { status: "DECLINED" }));
  await assertFails(acceptRequest(driver, "j1", "j1_rider", 0, 2));
  assert.equal((await getDoc(doc(driver, "journeys/j1"))).data().seatsRemaining, 1);
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
});

test("the expected successful reads return documents", async () => {
  const db = environment.authenticatedContext("alex").firestore();
  await assertSucceeds(setDoc(doc(db, "users/alex"), profile("alex")));
  const result = await assertSucceeds(getDoc(doc(db, "users/alex")));
  assert.equal(result.data().displayName, "Alex");
});
