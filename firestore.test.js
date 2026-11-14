const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const pastTimestamp = () => new Date(Date.now() - 2000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

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

function validAgent(userId) {
  return {
    userId,
    name: "Agente WhatsApp VIP",
    channel: "whatsapp",
    description: "Responde automáticamente mensajes urgentes.",
    triggerCondition: "urgente, presupuesto",
    actionTemplate: "Hola, recibimos tu mensaje urgente y lo estamos revisando.",
    autoExecute: true,
    enabled: true,
    runsCount: 0,
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  };
}

function validRecord(userId, status = "pending") {
  return {
    userId,
    sourceApp: "gmail",
    category: "Facturas",
    intent: "archivar_factura",
    title: "Factura Hosting Noviembre",
    content: "Adjunto comprobante por $45 USD.",
    extractedEntities: "Monto: $45 USD, Proveedor: CloudHost",
    suggestedReply: "Recibido y archivado en Facturas.",
    status,
    eventTitle: "",
    eventIsoDateTime: "",
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  };
}

test("Unauthenticated user: cannot list or write agents", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/agents`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/agents`).doc("agent_1").set(validAgent(ALICE_UID)));
});

test("Authenticated user: can create, list, and update own agent", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const agentRef = aliceDb.collection(`users/${ALICE_UID}/agents`).doc("agent_1");
  await assertSucceeds(agentRef.set(validAgent(ALICE_UID)));
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/agents`).get());
  await assertSucceeds(agentRef.update({ enabled: false, autoExecute: false, updatedAt: pastTimestamp() }));
  await assertSucceeds(agentRef.update({ runsCount: 1, updatedAt: pastTimestamp() }));
});

test("Cross-user isolation: Bob cannot read or write Alice's agents or records", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/agents`).doc("agent_1").set(validAgent(ALICE_UID)));

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection(`users/${ALICE_UID}/agents`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/agents`).doc("agent_1").get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/agents`).doc("agent_2").set(validAgent(BOB_UID)));
});

test("Shadow Update & Validation: rejects extra fields and invalid status", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/agents`).doc("agent_bad").set({
      ...validAgent(ALICE_UID),
      isAdmin: true,
    })
  );
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/data_records`).doc("rec_bad").set({
      ...validRecord(ALICE_UID),
      category: "InvalidCat",
    })
  );
});

test("Terminal state locking: archived DataRecord cannot be updated", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const recRef = aliceDb.collection(`users/${ALICE_UID}/data_records`).doc("rec_1");
  await assertSucceeds(recRef.set(validRecord(ALICE_UID, "pending")));
  await assertSucceeds(recRef.update({ status: "archived", category: "Archivado", updatedAt: pastTimestamp() }));
  await assertFails(recRef.update({ status: "pending", category: "Prioritario", updatedAt: pastTimestamp() }));
});

test("Append-only logs: can create and delete, cannot update", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const logRef = aliceDb.collection(`users/${ALICE_UID}/execution_logs`).doc("log_1");
  await assertSucceeds(logRef.set({
    userId: ALICE_UID,
    agentName: "Agente Gmail",
    channel: "gmail",
    detectedIntent: "archivar_factura",
    inputPreview: "Factura #991",
    outputAction: "Archivado automáticamente",
    status: "archived",
    createdAt: pastTimestamp(),
  }));
  await assertFails(logRef.update({ status: "success" }));
  await assertSucceeds(logRef.delete());
});
