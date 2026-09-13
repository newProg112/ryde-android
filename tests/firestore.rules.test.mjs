import { after, before, beforeEach, test } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  Timestamp, collection, doc, getDoc, getDocs, query, setDoc, updateDoc, where, writeBatch,
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

test("authenticated driver creates broad journey and authenticated rider discovers it", async () => {
  const driver = environment.authenticatedContext("driver").firestore();
  const rider = environment.authenticatedContext("rider").firestore();
  const anonymous = environment.unauthenticatedContext().firestore();
  await assertSucceeds(setDoc(doc(driver, "journeys/j1"), journey("driver")));
  await assertSucceeds(getDoc(doc(rider, "journeys/j1")));
  await assertSucceeds(getDocs(collection(rider, "journeys")));
  await assertFails(getDocs(collection(anonymous, "journeys")));
  await assertFails(setDoc(doc(rider, "journeys/forged"), journey("driver")));
  await assertFails(setDoc(doc(driver, "journeys/private"), { ...journey("driver"), originArea: "12 High Street" }));
  await assertFails(setDoc(doc(driver, "journeys/too-many"), journey("driver", 9)));
});

test("requests enforce real participants deterministic ownership and self-request denial", async () => {
  const driver = environment.authenticatedContext("driver").firestore();
  const rider = environment.authenticatedContext("rider").firestore();
  const stranger = environment.authenticatedContext("stranger").firestore();
  await assertSucceeds(setDoc(doc(driver, "journeys/j1"), journey("driver")));
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
  await assertSucceeds(setDoc(doc(driver, "journeys/j1"), journey("driver", 2)));
  await assertSucceeds(setDoc(doc(rider, "seatRequests/j1_rider"), request("j1", "driver", "rider")));
  await assertFails(updateDoc(doc(rider, "seatRequests/j1_rider"), { status: "ACCEPTED" }));
  await assertFails(updateDoc(doc(driver, "seatRequests/j1_rider"), { status: "ACCEPTED" }));

  const batch = writeBatch(driver);
  batch.update(doc(driver, "journeys/j1"), { seatsRemaining: 1 });
  batch.update(doc(driver, "seatRequests/j1_rider"), { status: "ACCEPTED" });
  await assertSucceeds(batch.commit());
  assert.equal((await getDoc(doc(driver, "journeys/j1"))).data().seatsRemaining, 1);
  await assertFails(updateDoc(doc(driver, "seatRequests/j1_rider"), { status: "DECLINED" }));
});

test("decline preserves seats and simultaneous candidates cannot overbook", async () => {
  const driver = environment.authenticatedContext("driver").firestore();
  const riderA = environment.authenticatedContext("rider-a").firestore();
  const riderB = environment.authenticatedContext("rider-b").firestore();
  await assertSucceeds(setDoc(doc(driver, "journeys/j1"), journey("driver", 1)));
  await assertSucceeds(setDoc(doc(driver, "journeys/j2"), journey("driver", 1)));
  await assertSucceeds(setDoc(doc(riderA, "seatRequests/j1_rider-a"), request("j1", "driver", "rider-a")));
  await assertSucceeds(setDoc(doc(riderB, "seatRequests/j1_rider-b"), request("j1", "driver", "rider-b")));
  await assertSucceeds(setDoc(doc(riderB, "seatRequests/j2_rider-b"), request("j2", "driver", "rider-b")));
  await assertSucceeds(updateDoc(doc(driver, "seatRequests/j2_rider-b"), { status: "DECLINED" }));
  assert.equal((await getDoc(doc(driver, "journeys/j2"))).data().seatsRemaining, 1);

  const accepted = writeBatch(driver);
  accepted.update(doc(driver, "journeys/j1"), { seatsRemaining: 0 });
  accepted.update(doc(driver, "seatRequests/j1_rider-a"), { status: "ACCEPTED" });
  await assertSucceeds(accepted.commit());
  await assertFails(updateDoc(doc(driver, "journeys/j1"), { seatsRemaining: -1 }));
  const overbook = writeBatch(driver);
  overbook.update(doc(driver, "journeys/j1"), { seatsRemaining: -1 });
  overbook.update(doc(driver, "seatRequests/j1_rider-b"), { status: "ACCEPTED" });
  await assertFails(overbook.commit());
});

test("the expected successful reads return documents", async () => {
  const db = environment.authenticatedContext("alex").firestore();
  await assertSucceeds(setDoc(doc(db, "users/alex"), profile("alex")));
  const result = await assertSucceeds(getDoc(doc(db, "users/alex")));
  assert.equal(result.data().displayName, "Alex");
});
