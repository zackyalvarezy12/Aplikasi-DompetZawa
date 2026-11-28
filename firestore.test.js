const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

test("Unauthenticated users are denied across user profile and financial subcollections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("transactions").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Anon",
      avatarInitials: "AN",
      avatarColorHex: 123,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Owner can create and partially update profile, account, transaction, budget, recurring bill, and audit log", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const past = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Zacky Pratama",
      email: "zacky@example.com",
      avatarInitials: "ZP",
      avatarColorHex: 0x065f46,
      themePreset: "AUTO_TIME_PASTEL",
      biometricEnabled: true,
      autoSalaryEnabled: true,
      monthlyBaseSalary: 10000000,
      salaryPaydayDay: 17,
      salaryWorkingDays: 22,
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Zacky Baru",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("accounts").doc("acc_main").set({
      userId: ALICE_UID,
      workspaceId: "ws_personal",
      name: "Dompet Utama",
      type: "CASH",
      balance: 0,
      creditLimit: 0,
      billingDay: 1,
      dueDay: 15,
      institutionName: "Tunai",
      colorHex: 0x047857,
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_1").set({
      userId: ALICE_UID,
      workspaceId: "ws_personal",
      type: "EXPENSE",
      amount: 35000,
      accountId: "acc_main",
      categoryId: "cat_p_makan",
      title: "Makan Siang",
      note: "Nasi Padang",
      tags: "#makan",
      dateMillis: Date.now(),
      createdByMemberId: "mem_owner",
      createdByName: "Zacky Baru",
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("recurring_rules").doc("rec_pln").set({
      userId: ALICE_UID,
      workspaceId: "ws_personal",
      title: "Listrik PLN",
      amount: 350000,
      type: "EXPENSE",
      accountId: "acc_main",
      categoryId: "cat_p_tagihan",
      frequency: "MONTHLY",
      nextDueDateMillis: Date.now() + 86400000 * 3,
      active: true,
      reminderEnabled: true,
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("audit_logs").doc("log_1").set({
      userId: ALICE_UID,
      workspaceId: "ws_personal",
      actorMemberId: "mem_owner",
      actorName: "Zacky Baru",
      action: "Aktifkan Biometrik",
      detail: "Kunci wajah diaktifkan",
      eventTimeMillis: Date.now(),
      createdAt: past,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions").get()
  );
});

test("Rejects cross-user reads/writes, @DocumentId 'id' leakage, immutable audit log edits, and future timestamps", async () => {
  const past = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(BOB_UID).collection("transactions").doc("bob_tx").set({
      userId: BOB_UID,
      workspaceId: "ws_bob",
      type: "INCOME",
      amount: 5000000,
      accountId: "acc_bob",
      categoryId: "cat_gaji",
      title: "Gaji Bob",
      dateMillis: Date.now(),
      createdByMemberId: "mem_bob",
      createdByName: "Bob",
      createdAt: past,
      updatedAt: past,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("transactions").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("transactions").doc("bob_tx").get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("transactions").doc("bob_tx").update({
      title: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject extra 'id' field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("budgets").doc("bdg_bad").set({
      id: "bdg_bad",
      userId: ALICE_UID,
      workspaceId: "ws_personal",
      categoryId: "cat_p_makan",
      monthYear: "2026-11",
      limitAmount: 1000000,
      rolloverAmount: 0,
      rolloverEnabled: true,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Reject future server timestamp spoofing
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Future Spoof",
      avatarInitials: "FS",
      avatarColorHex: 123,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Audit logs cannot be mutated once created
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("audit_logs").doc("log_imm").set({
      userId: ALICE_UID,
      workspaceId: "ws_personal",
      actorMemberId: "mem_owner",
      actorName: "Alice",
      action: "Login",
      eventTimeMillis: Date.now(),
      createdAt: past,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("audit_logs").doc("log_imm").update({
      action: "Tampered",
    })
  );
});
