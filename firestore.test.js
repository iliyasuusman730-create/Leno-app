const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_1";
const BOB_UID = "bob_uid_2";
const ADMIN_EMAIL = "iliyasuusman730@gmail.com";
const ADMIN_UID = "owner_uid_admin";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users are denied reads and writes across collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").get());
  await assertFails(unauthDb.collection("classes").get());
  await assertFails(unauthDb.collection("messages").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice",
      displayName: "Alice",
      role: "USER",
    })
  );
});

test("User profile creation prevents self-escalation to ADMIN, OWNER, TEACHER, or verified/official", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice",
      displayName: "Alice",
      role: "ADMIN",
      isOfficial: false,
      isVerified: false,
      teacherStatus: "NONE",
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice",
      displayName: "Alice",
      role: "USER",
      teacherStatus: "APPROVED",
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice",
      displayName: "Alice",
      role: "USER",
      isOfficial: false,
      isVerified: false,
      isRestricted: false,
      teacherStatus: "NONE",
    })
  );

  // Cross-user profile overwrite or deletion must fail
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Hacked" })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).delete());

  // Self-deletion succeeds (Google Play account deletion requirement)
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).delete());
});

test("Direct messages are isolated to sender and receiver", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext("charlie_uid_3").firestore();
  const chatId = `${ALICE_UID}_${BOB_UID}`;

  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).set({
      chatId,
      participants: [ALICE_UID, BOB_UID],
      lastMessageText: "Hello Bob",
      lastMessageTimestamp: 1000,
      updatedAt: 1000,
    })
  );

  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("m1").set({
      messageId: "m1",
      chatId,
      senderId: ALICE_UID,
      receiverId: BOB_UID,
      text: "Hello Bob",
      timestamp: 1000,
      status: "SENT",
      read: false,
    })
  );

  // Spoofing senderId fails
  await assertFails(
    charlieDb.collection("chats").doc(chatId).collection("messages").doc("m2").set({
      messageId: "m2",
      chatId,
      senderId: ALICE_UID,
      receiverId: BOB_UID,
      text: "Spoof",
      timestamp: 1001,
      status: "SENT",
      read: false,
    })
  );

  // Charlie cannot read Alice<->Bob chat or message
  await assertFails(charlieDb.collection("chats").doc(chatId).get());
  await assertFails(
    charlieDb.collection("chats").doc(chatId).collection("messages").doc("m1").get()
  );

  // Bob (receiver) can read & mark as read
  await assertSucceeds(
    bobDb.collection("chats").doc(chatId).collection("messages").doc("m1").update({
      read: true,
      status: "READ",
    })
  );
});

test("Paid class self-enrollment is blocked for students while free class enrollment succeeds", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("classes").doc("paid_cls").set({
      classId: "paid_cls",
      title: "Advanced Math",
      instructorUserId: BOB_UID,
      isPaid: true,
      price: "₦2,500",
    });
    await db.collection("classes").doc("free_cls").set({
      classId: "free_cls",
      title: "Intro Science",
      instructorUserId: BOB_UID,
      isPaid: false,
      price: "Free",
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Cannot self-enroll into a paid class without instructor/admin approval
  await assertFails(
    aliceDb.collection("enrollments").doc(`${ALICE_UID}_paid_cls`).set({
      enrollmentId: `${ALICE_UID}_paid_cls`,
      classId: "paid_cls",
      studentUserId: ALICE_UID,
      teacherUserId: BOB_UID,
      status: "ENROLLED",
      enrolledAt: 1000,
    })
  );

  // Can self-enroll into a free class
  await assertSucceeds(
    aliceDb.collection("enrollments").doc(`${ALICE_UID}_free_cls`).set({
      enrollmentId: `${ALICE_UID}_free_cls`,
      classId: "free_cls",
      studentUserId: ALICE_UID,
      teacherUserId: BOB_UID,
      status: "ENROLLED",
      enrolledAt: 1000,
    })
  );
});

test("Admin verified email can approve teacher application while normal user cannot", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, {
      email: ADMIN_EMAIL,
      email_verified: true,
    })
    .firestore();

  await assertSucceeds(
    aliceDb.collection("teacherApplications").doc(ALICE_UID).set({
      applicationId: ALICE_UID,
      userId: ALICE_UID,
      subject: "Mathematics",
      status: "PENDING",
      submittedAt: 1000,
    })
  );

  // Applicant cannot self-approve
  await assertFails(
    aliceDb.collection("teacherApplications").doc(ALICE_UID).update({
      status: "APPROVED",
    })
  );

  // Verified owner/admin can approve
  await assertSucceeds(
    adminDb.collection("teacherApplications").doc(ALICE_UID).update({
      status: "APPROVED",
    })
  );
});
