import { after, before, beforeEach, test } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc, updateDoc } from "firebase/firestore";
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
  await assertFails(setDoc(doc(db, "journeys/journey-one"), { ownerUid: "alex" }));
  await assertFails(getDoc(doc(db, "journeys/journey-one")));
});

test("the expected successful reads return documents", async () => {
  const db = environment.authenticatedContext("alex").firestore();
  await assertSucceeds(setDoc(doc(db, "users/alex"), profile("alex")));
  const result = await assertSucceeds(getDoc(doc(db, "users/alex")));
  assert.equal(result.data().displayName, "Alex");
});
