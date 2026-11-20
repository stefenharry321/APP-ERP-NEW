const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "kumar_ops_alice";
const BOB_UID = "external_user_bob";

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

function validInventoryPayload(ts) {
  return {
    sku: "KF-CHK-10KG",
    name: "Chakki Fresh Whole Wheat Atta",
    category: "FINISHED_ATTA",
    warehouse: "Main Mill Plant",
    quantityMt: 48.5,
    capacityMt: 100.0,
    lowThresholdMt: 20.0,
    unitPriceInr: 34500.0,
    batchCode: "BATCH-2026-A1",
    syncSource: "FIREBASE",
    createdAt: ts,
    updatedAt: ts,
  };
}

function validShipmentPayload(ts, status = "LOADING") {
  return {
    trackingCode: "KF-SHP-8842",
    clientName: "Metro Foods Wholesale",
    destination: "Lucknow Distribution Hub",
    productName: "Chakki Fresh Atta 10kg",
    weightMt: 18.5,
    truckNumber: "UP-32-KT-4421",
    status,
    etaText: "Today, 6:30 PM",
    warehouseOrigin: "Main Mill Plant",
    createdAt: ts,
    updatedAt: ts,
  };
}

function validMillPayload(ts) {
  return {
    machineName: "Buhler Roller Mill RM-01",
    machineType: "ROLLER_MILL",
    zone: "Grinding Floor A",
    healthScore: 86,
    temperatureC: 54.2,
    vibrationMmS: 2.1,
    runningHours: 4210.5,
    outputRateMtHr: 6.4,
    status: "OPTIMAL",
    nextServiceDate: "2026-12-04",
    createdAt: ts,
    updatedAt: ts,
  };
}

function validAiReportPayload(ts) {
  return {
    reportType: "SALES_FORECAST",
    title: "Q4 Festive Atta Demand Forecast",
    summary: "Projected +18.4% demand surge in Sharbati Gold Atta.",
    predictedGrowthPct: 18.4,
    recommendedRestockMt: 65.0,
    forecastLabels: ["D1", "D2", "D3", "D4", "D5", "D6", "D7"],
    forecastValues: [42.0, 44.5, 47.0, 49.2, 52.0, 54.8, 58.1],
    createdAt: ts,
    updatedAt: ts,
  };
}

test("Unauthenticated users cannot read or write any Kumar Foods collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const ts = new Date(Date.now() - 2000);

  await assertFails(unauthDb.collection(`users/${ALICE_UID}/inventory`).get());
  await assertFails(
    unauthDb
      .doc(`users/${ALICE_UID}/inventory/inv_1`)
      .set(validInventoryPayload(ts))
  );
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/shipments`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/mills`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/ai_reports`).get());
});

test("Cross-user isolation: Bob cannot read or mutate Alice's inventory or mills", async () => {
  const ts = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .doc(`users/${ALICE_UID}/inventory/inv_1`)
      .set(validInventoryPayload(ts));
    await ctx
      .firestore()
      .doc(`users/${ALICE_UID}/mills/mill_1`)
      .set(validMillPayload(ts));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection(`users/${ALICE_UID}/inventory`).get());
  await assertFails(bobDb.doc(`users/${ALICE_UID}/inventory/inv_1`).get());
  await assertFails(
    bobDb
      .doc(`users/${ALICE_UID}/inventory/inv_1`)
      .update({ quantityMt: 0, updatedAt: new Date(Date.now() - 1000) })
  );
  await assertFails(bobDb.doc(`users/${ALICE_UID}/mills/mill_1`).delete());
});

test("Inventory CRUD: Kotlin-mirrored create, partial stock update, and shadow/id/future-ts rejections", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // List empty subcollection succeeds
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/inventory`).get());

  // Valid create (no @DocumentId `id` field)
  await assertSucceeds(
    aliceDb
      .doc(`users/${ALICE_UID}/inventory/inv_chakki_01`)
      .set(validInventoryPayload(created))
  );

  // Partial stock & threshold update succeeds
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/inventory/inv_chakki_01`).update({
      quantityMt: 53.5,
      lowThresholdMt: 22.0,
      syncSource: "SUPABASE_ERP",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/inventory/inv_chakki_01`).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost/shadow field injection & redundant id field
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/inventory/inv_ghost`).set({
      ...validInventoryPayload(created),
      id: "inv_ghost",
    })
  );
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/inventory/inv_future`).set({
      ...validInventoryPayload(new Date(Date.now() + 120000)),
    })
  );
});

test("Shipments: valid progression and Terminal State Lock once DELIVERED", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb
      .doc(`users/${ALICE_UID}/shipments/shp_01`)
      .set(validShipmentPayload(created, "IN_TRANSIT"))
  );

  // Transition to DELIVERED succeeds
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/shipments/shp_01`).update({
      status: "DELIVERED",
      etaText: "Delivered",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Any further update after DELIVERED MUST fail (Terminal State Lock)
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/shipments/shp_01`).update({
      status: "LOADING",
      etaText: "Reopened",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Mills & AI Reports: create, optional lastAiAdvice update, and bounded array validation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Mill create without optional lastAiAdvice succeeds
  await assertSucceeds(
    aliceDb
      .doc(`users/${ALICE_UID}/mills/mill_rm01`)
      .set(validMillPayload(created))
  );

  // Mill update with AI diagnostic advice succeeds
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/mills/mill_rm01`).update({
      status: "MAINTENANCE",
      nextServiceDate: "2026-11-24",
      healthScore: 92,
      lastAiAdvice: "Inspect roller bearing lubrication within 48 hours.",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Mill invalid healthScore > 100 fails
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/mills/mill_rm01`).update({
      healthScore: 150,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // AI Report create succeeds
  await assertSucceeds(
    aliceDb
      .doc(`users/${ALICE_UID}/ai_reports/rep_01`)
      .set(validAiReportPayload(created))
  );

  // AI Report with > 14 forecast items fails (DoW guard)
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/ai_reports/rep_bad`).set({
      ...validAiReportPayload(created),
      forecastLabels: Array.from({ length: 20 }, (_, i) => `D${i}`),
    })
  );
});

test("GST Invoices & Party Ledger: CRUD, PAID terminal state lock, and cross-user denial", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Create GST Invoice
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/gst_invoices/inv_gst_01`).set({
      invoiceNumber: "KF/26-27/0101",
      partyName: "Metro Wholesale Mart",
      partyGstin: "09AAACK1234F1Z5",
      hsnCode: "1101",
      itemDescription: "Chakki Fresh Atta 10kg",
      quantityMt: 20.0,
      taxableAmountInr: 700000.0,
      gstRatePct: 5.0,
      cgstInr: 17500.0,
      sgstInr: 17500.0,
      totalAmountInr: 735000.0,
      paymentStatus: "UNPAID",
      invoiceDate: "2026-11-20",
      syncSource: "SUPABASE_ERP",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Mark invoice PAID succeeds
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/gst_invoices/inv_gst_01`).update({
      paymentStatus: "PAID",
      syncSource: "FIREBASE",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Paid invoice is terminally locked against tampering
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/gst_invoices/inv_gst_01`).update({
      paymentStatus: "UNPAID",
      syncSource: "FIREBASE",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Create Party Ledger Khata entry
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/party_ledger/led_01`).set({
      partyName: "Metro Wholesale Mart",
      partyPhone: "+91-9876543210",
      partyCity: "Lucknow",
      entryType: "CREDIT_PAYMENT",
      amountInr: 350000.0,
      referenceNo: "RTGS-998812",
      notes: "Part payment against KF/26-27/0101",
      entryDate: "2026-11-20",
      syncSource: "FIREBASE",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Cross-user Bob denied
  await assertFails(bobDb.collection(`users/${ALICE_UID}/gst_invoices`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/party_ledger`).get());
});
