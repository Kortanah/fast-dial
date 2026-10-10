"use strict";

const functions                         = require("firebase-functions/v1");
const { onCall, onRequest, HttpsError } = require("firebase-functions/v2/https");
const { onSchedule }                   = require("firebase-functions/v2/scheduler");
const { defineSecret }                 = require("firebase-functions/params");
const { initializeApp, getApps }       = require("firebase-admin/app");
const { getAuth }                       = require("firebase-admin/auth");
const { getFirestore, Timestamp }       = require("firebase-admin/firestore");
const { getMessaging }                  = require("firebase-admin/messaging");
const crypto = require("crypto");
const https  = require("https");

if (!getApps().length) initializeApp();

// ─────────────────────────────────────────────────────────────────────────────
// SECRET
// ─────────────────────────────────────────────────────────────────────────────
const paystackSecretKey = defineSecret("PAYSTACK_SECRET_KEY");

// ─────────────────────────────────────────────────────────────────────────────
// INTERNAL CONFIG  —  never sent to the client
// Action configurations can specify duration in days (durationDays) or minutes (durationMinutes)
// ─────────────────────────────────────────────────────────────────────────────
const ACTION_CONFIGS = {
  tier_a: { durationDays: 30, val: 1000 },       // 30 days
  tier_b: { durationDays: 90, val: 2500 },       // 90 days
  tier_1m: { durationDays: 30, val: 1000 },      // alias
  tier_3m: { durationDays: 90, val: 2500 },      // alias
  tier_5m: { durationMinutes: 5, val: 100 },     // 5 minutes
};

// Opaque action keys sent by client → mapped to internal config key
const ACTION_TO_CONFIG = {
  act_x1: "tier_a",
  act_x2: "tier_b",
  act_test_5m: "tier_5m",
};

// Offline grace period returned to the client for session management.
const OFFLINE_GRACE_PERIOD_MS = 48 * 60 * 60 * 1000; // 48 hours

// Gate flag id written in Firestore.
const DEFAULT_FLAG_ID = "flag_a1";

// ─────────────────────────────────────────────────────────────────────────────
// PRIVATE HELPERS
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Resolve configuration from an opaque action string.
 * Dynamically queries Firestore (config/packages, config/flags, or packages collection)
 * for price/amount and duration (days/minutes/months).
 * Falls back to static ACTION_CONFIGS if not found or on Firestore read error.
 */
async function resolveActionConfig(action) {
  if (!action || typeof action !== "string") {
    throw new HttpsError("invalid-argument", "A valid action string is required.");
  }

  const db = getFirestore();
  const staticConfigKey = ACTION_TO_CONFIG[action] || action;
  let dynamicConfig = null;

  try {
    // 1. Try checking config/packages document
    const packagesDoc = await db.collection("config").doc("packages").get();
    if (packagesDoc.exists) {
      const pData = packagesDoc.data() || {};
      const itemsMap = pData.packages || pData.items || pData.plans || pData;
      let matchedPkg = null;

      if (Array.isArray(itemsMap)) {
        matchedPkg = itemsMap.find((p) => p && (p.id === action || p.id === staticConfigKey || p.action === action));
      } else if (itemsMap && typeof itemsMap === "object") {
        matchedPkg = itemsMap[action] || itemsMap[staticConfigKey];
      }

      if (matchedPkg) {
        const val = matchedPkg.price ?? matchedPkg.val ?? matchedPkg.amount ?? matchedPkg.cost;
        if (val !== undefined && val !== null) {
          const unit = String(matchedPkg.durationUnit || matchedPkg.unit || "days").toLowerCase();
          const durVal = Number(matchedPkg.durationValue ?? matchedPkg.duration ?? matchedPkg.durationDays ?? 0);

          let durationDays = 0;
          let durationMinutes = 0;

          if (unit.includes("min")) {
            durationMinutes = durVal;
          } else if (unit.includes("month") || unit.includes("m")) {
            durationDays = durVal * 30;
          } else {
            durationDays = durVal;
          }

          if (matchedPkg.durationDays !== undefined) durationDays = Number(matchedPkg.durationDays);
          if (matchedPkg.durationMinutes !== undefined) durationMinutes = Number(matchedPkg.durationMinutes);

          dynamicConfig = {
            val: Number(val),
            durationDays,
            durationMinutes
          };
        }
      }
    }

    // 2. Try checking config/flags options
    if (!dynamicConfig) {
      const flagsDoc = await db.collection("config").doc("flags").get();
      if (flagsDoc.exists) {
        const flagsData = flagsDoc.data() || {};
        for (const flagId in flagsData) {
          const flagObj = flagsData[flagId];
          if (flagObj && Array.isArray(flagObj.options)) {
            const matchedOpt = flagObj.options.find((o) => o && (o.action === action || o.action === staticConfigKey));
            if (matchedOpt) {
              const val = matchedOpt.price ?? matchedOpt.val ?? matchedOpt.amount;
              if (val !== undefined && val !== null) {
                const unit = String(matchedOpt.durationUnit || matchedOpt.unit || "days").toLowerCase();
                const durVal = Number(matchedOpt.durationValue ?? matchedOpt.duration ?? matchedOpt.durationDays ?? 0);

                let durationDays = 0;
                let durationMinutes = 0;

                if (unit.includes("min")) {
                  durationMinutes = durVal;
                } else if (unit.includes("month") || unit.includes("m")) {
                  durationDays = durVal * 30;
                } else {
                  durationDays = durVal;
                }

                if (matchedOpt.durationDays !== undefined) durationDays = Number(matchedOpt.durationDays);
                if (matchedOpt.durationMinutes !== undefined) durationMinutes = Number(matchedOpt.durationMinutes);

                dynamicConfig = {
                  val: Number(val),
                  durationDays,
                  durationMinutes
                };
                break;
              }
            }
          }
        }
      }
    }

    // 3. Try checking packages/{action} or packages/{staticConfigKey} document directly
    if (!dynamicConfig) {
      const pkgDoc = await db.collection("packages").doc(action).get();
      const targetDoc = pkgDoc.exists ? pkgDoc : await db.collection("packages").doc(staticConfigKey).get();
      if (targetDoc.exists) {
        const pData = targetDoc.data() || {};
        const val = pData.price ?? pData.val ?? pData.amount ?? pData.cost;
        if (val !== undefined && val !== null) {
          const unit = String(pData.durationUnit || pData.unit || "days").toLowerCase();
          const durVal = Number(pData.durationValue ?? pData.duration ?? pData.durationDays ?? 0);

          let durationDays = 0;
          let durationMinutes = 0;

          if (unit.includes("min")) {
            durationMinutes = durVal;
          } else if (unit.includes("month") || unit.includes("m")) {
            durationDays = durVal * 30;
          } else {
            durationDays = durVal;
          }

          if (pData.durationDays !== undefined) durationDays = Number(pData.durationDays);
          if (pData.durationMinutes !== undefined) durationMinutes = Number(pData.durationMinutes);

          dynamicConfig = {
            val: Number(val),
            durationDays,
            durationMinutes
          };
        }
      }
    }
  } catch (e) {
    console.warn(`[resolveActionConfig] Error checking dynamic package config for '${action}':`, e);
  }

  if (dynamicConfig) {
    console.log(`[resolveActionConfig] DYNAMIC config resolved for '${action}': val=${dynamicConfig.val}, durationDays=${dynamicConfig.durationDays}, durationMinutes=${dynamicConfig.durationMinutes}`);
    return { configKey: staticConfigKey, config: dynamicConfig };
  }

  const staticConfig = ACTION_CONFIGS[staticConfigKey];
  if (!staticConfig) {
    throw new HttpsError("invalid-argument", `Unknown action '${action}'.`);
  }

  console.log(`[resolveActionConfig] STATIC fallback config resolved for '${action}': val=${staticConfig.val}`);
  return { configKey: staticConfigKey, config: staticConfig };
}

/**
 * Get user email — tries Firebase Auth first, then Firestore profile.
 */
async function getUserEmail(uid) {
  try {
    const record = await getAuth().getUser(uid);
    if (record && record.email) return record.email;
  } catch (_) { /* fall through */ }

  try {
    const doc = await getFirestore().collection("users").doc(uid).get();
    if (doc.exists && doc.data().email) return doc.data().email;
  } catch (_) { /* fall through */ }

  throw new HttpsError("failed-precondition", "User email address is required but could not be found.");
}

/**
 * Raw HTTPS POST wrapper.
 */
function externalPost(path, payload, secretKey) {
  return new Promise((resolve, reject) => {
    const body = JSON.stringify(payload);
    const req = https.request(
      `https://api.paystack.co${path}`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${secretKey}`,
          "Content-Type": "application/json",
          "Content-Length": Buffer.byteLength(body),
        },
        timeout: 10000,
      },
      (res) => {
        let raw = "";
        res.on("data", (chunk) => { raw += chunk; });
        res.on("end", () => {
          try { resolve({ statusCode: res.statusCode, body: JSON.parse(raw) }); }
          catch (e) { reject(e); }
        });
      }
    );
    req.on("error", reject);
    req.on("timeout", () => { req.destroy(); reject(new Error("External request timed out")); });
    req.write(body);
    req.end();
  });
}

/**
 * Raw HTTPS GET wrapper.
 */
function externalGet(path, secretKey) {
  return new Promise((resolve, reject) => {
    const req = https.request(
      `https://api.paystack.co${path}`,
      {
        method: "GET",
        headers: { Authorization: `Bearer ${secretKey}` },
        timeout: 10000,
      },
      (res) => {
        let raw = "";
        res.on("data", (chunk) => { raw += chunk; });
        res.on("end", () => {
          try { resolve({ statusCode: res.statusCode, body: JSON.parse(raw) }); }
          catch (e) { reject(e); }
        });
      }
    );
    req.on("error", reject);
    req.on("timeout", () => { req.destroy(); reject(new Error("External request timed out")); });
    req.end();
  });
}

/**
 * Apply access rights to user document.
 * Idempotent: skips write if reference was already processed.
 */
async function applyVerifiedAccess(uid, reference, configKey, config, reportedVal, source) {
  const db = getFirestore();

  // Deduplication check
  const dedupRef = db.collection("processedTokens").doc(reference);
  const dedupSnap = await dedupRef.get();
  if (dedupSnap.exists) {
    console.log(`[${source}] Reference ${reference} already processed — skipping duplicate write.`);
    return { alreadyProcessed: true };
  }

  const resolvedConfig = config || ACTION_CONFIGS[configKey] || { durationDays: 30 };
  const now = new Date();

  let durationMs = 0;
  if (resolvedConfig.durationDays) {
    durationMs += resolvedConfig.durationDays * 24 * 60 * 60 * 1000;
  }
  if (resolvedConfig.durationMinutes) {
    durationMs += resolvedConfig.durationMinutes * 60 * 1000;
  }

  const expiresAt = new Date(now.getTime() + durationMs);
  const nowTs     = Timestamp.fromDate(now);
  const expiresTs = Timestamp.fromDate(expiresAt);

  const userDocRef = db.collection("users").doc(uid);
  const userSnap   = await userDocRef.get();

  if (!userSnap.exists) {
    console.error(`[${source}] User document not found for UID ${uid}.`);
    throw new Error(`User document not found for UID ${uid}`);
  }

  const userData = userSnap.data() || {};
  const flagsMap = userData.flags || {};

  // Build atomic update: access record + gate flag deactivations
  const updates = {
    "access.status":          "active",
    "access.tier":            configKey,
    "access.durationDays":    resolvedConfig.durationDays || 0,
    "access.durationMinutes": resolvedConfig.durationMinutes || 0,
    "access.val":             reportedVal,
    "access.ref":             reference,
    "access.startAt":         nowTs,
    "access.expiresAt":       expiresTs,

    [`flags.${DEFAULT_FLAG_ID}.active`]: false,
  };

  // Deactivate all active flags in userData.flags
  Object.keys(flagsMap).forEach((flagId) => {
    if (flagsMap[flagId] && flagsMap[flagId].active === true) {
      updates[`flags.${flagId}.active`] = false;
    }
  });

  await db.runTransaction(async (txn) => {
    txn.update(userDocRef, updates);
    txn.set(dedupRef, {
      uid,
      configKey,
      val: reportedVal,
      processedAt: nowTs,
      source,
    });
  });

  console.log(
    `[${source}] Access updated for UID ${uid}: ` +
    `tier=${configKey}, startAt=${now.toISOString()}, expiresAt=${expiresAt.toISOString()}`
  );

  return { alreadyProcessed: false, expiresAt: expiresAt.toISOString() };
}

/**
 * Re-activate gate flag if access has expired.
 */
async function reactivateGateIfExpired(uid) {
  try {
    const db = getFirestore();
    const userDocRef = db.collection("users").doc(uid);
    const snap = await userDocRef.get();

    if (!snap.exists) return;

    const data = snap.data() || {};
    const acc  = data.access || {};
    const expiresAt = acc.expiresAt && typeof acc.expiresAt.toDate === "function" ? acc.expiresAt.toDate() : null;
    const flagActive = (data.flags && data.flags[DEFAULT_FLAG_ID])
      ? data.flags[DEFAULT_FLAG_ID].active
      : null;

    if (expiresAt && expiresAt <= new Date() && flagActive !== true) {
      await userDocRef.update({
        [`flags.${DEFAULT_FLAG_ID}.active`]: true,
        "access.status": "expired",
      });
      console.log(`Gate reactivated for UID ${uid} — access expired at ${expiresAt.toISOString()}`);
    }
  } catch (err) {
    console.error(`reactivateGateIfExpired error for UID ${uid}:`, err);
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// FUNCTION 1 — getSessionState
// ─────────────────────────────────────────────────────────────────────────────
exports.getSessionState = onCall(async (request) => {
  if (!request.auth || !request.auth.uid) {
    throw new HttpsError("unauthenticated", "Authentication required.");
  }

  const uid = request.auth.uid;
  const serverTimeMs = Date.now();

  let accessState = null;
  try {
    const snap = await getFirestore().collection("users").doc(uid).get();
    if (snap.exists) {
      const acc = snap.data().access || {};
      const expiresAt = acc.expiresAt ? acc.expiresAt.toDate() : null;

      accessState = {
        status:      acc.status || "none",
        expiresAtMs: expiresAt ? expiresAt.getTime() : null,
      };

      if (expiresAt && expiresAt <= new Date()) {
        reactivateGateIfExpired(uid).catch((e) =>
          console.error("reactivateGateIfExpired failed silently:", e)
        );
      }
    }
  } catch (e) {
    console.warn("Could not read access state in getSessionState:", e);
  }

  return {
    uid,
    serverTimeMs,
    offlineGracePeriodMs: OFFLINE_GRACE_PERIOD_MS,
    serverVerified: true,
    access: accessState,
  };
});

// ─────────────────────────────────────────────────────────────────────────────
// FUNCTION 2 — resolveGateAction [OMIT]
// ─────────────────────────────────────────────────────────────────────────────
// [OMIT] exports.resolveGateAction = onCall(
const resolveGateAction = onCall(
  { secrets: [paystackSecretKey] },
  async (request) => {
    if (!request.auth || !request.auth.uid) {
      throw new HttpsError("unauthenticated", "Authentication required.");
    }

    const uid    = request.auth.uid;
    const action = request.data && request.data.action;

    const { configKey, config } = await resolveActionConfig(action);
    const email = await getUserEmail(uid);

    const secretKey = paystackSecretKey.value();
    if (!secretKey) {
      throw new HttpsError("internal", "Gateway key not configured.");
    }

    const reference = `gate_${uid}_${Date.now()}`;

    let response;
    try {
      response = await externalPost(
        "/transaction/initialize",
        {
          amount:    config.val,
          email,
          reference,
          metadata: { uid, action, configKey },
        },
        secretKey
      );
    } catch (e) {
      console.error("Initialize failed:", e);
      throw new HttpsError("internal", "Could not initialize request. Please try again.");
    }

    if (response.statusCode !== 200 || !response.body || !response.body.status) {
      console.error("Initialize error response:", response.body);
      throw new HttpsError("internal", "Gateway initialization failed.");
    }

    const authUrl = response.body.data && response.body.data.authorization_url;
    if (!authUrl) {
      throw new HttpsError("internal", "No authorization URL returned.");
    }

    console.log(`resolveGateAction: initialized ${configKey} for UID ${uid}, ref=${reference}`);
    return { url: authUrl, reference: reference };
  }
);

// ─────────────────────────────────────────────────────────────────────────────
// FUNCTION 3 — verifyGateAction (also exported as verifyPayment for backward compatibility) [OMIT]
// ─────────────────────────────────────────────────────────────────────────────
const verifyGateActionHandler = async (request) => {
  if (!request.auth || !request.auth.uid) {
    throw new HttpsError("unauthenticated", "Authentication required.");
  }

  const uid       = request.auth.uid;
  const reference = request.data && request.data.reference;

  if (!reference || typeof reference !== "string") {
    throw new HttpsError("invalid-argument", "A valid reference is required.");
  }

  const refParts = reference.split("_");
  if (refParts.length < 3 || refParts[1] !== uid) {
    console.warn(`verifyGateAction: UID mismatch — auth uid=${uid}, ref=${reference}`);
    throw new HttpsError("permission-denied", "Reference does not belong to this account.");
  }

  const secretKey = paystackSecretKey.value();
  if (!secretKey) {
    throw new HttpsError("internal", "Gateway key not configured.");
  }

  let verifyResponse;
  try {
    verifyResponse = await externalGet(
      `/transaction/verify/${encodeURIComponent(reference)}`,
      secretKey
    );
  } catch (e) {
    console.error("Verify request failed:", e);
    throw new HttpsError("internal", "Could not verify reference. Please try again.");
  }

  if (verifyResponse.statusCode !== 200 || !verifyResponse.body || !verifyResponse.body.status) {
    console.error("Verify error response:", verifyResponse.body);
    throw new HttpsError("internal", "Verification failed.");
  }

  const txData = verifyResponse.body.data;

  if (!txData || txData.status !== "success") {
    console.warn(`verifyGateAction: transaction not successful for ref=${reference}, status=${txData && txData.status}`);
    throw new HttpsError("failed-precondition", "Action has not been completed successfully.");
  }

  const meta = txData.metadata || {};
  if (meta.uid !== uid) {
    console.error(`verifyGateAction: metadata uid mismatch — auth=${uid}, meta.uid=${meta.uid}, ref=${reference}`);
    throw new HttpsError("permission-denied", "Ownership mismatch.");
  }

  const action = meta.action;
  let configKey, config;
  try {
    ({ configKey, config } = await resolveActionConfig(action));
  } catch (_) {
    console.error(`verifyGateAction: unresolvable action in metadata — action=${action}, ref=${reference}`);
    throw new HttpsError("internal", "Could not determine action config for this reference.");
  }

  const reportedVal = txData.amount;
  if (reportedVal !== config.val) {
    console.error(
      `verifyGateAction: VAL MISMATCH — ref=${reference}, uid=${uid}, ` +
      `expected=${config.val}, reported=${reportedVal}, configKey=${configKey}`
    );
    throw new HttpsError("failed-precondition", "Value mismatch.");
  }

  const result = await applyVerifiedAccess(uid, reference, configKey, config, reportedVal, "verifyGateAction");

  if (result.alreadyProcessed) {
    return { success: true, alreadyProcessed: true };
  }

  console.log(`verifyGateAction: access applied for UID ${uid}, config=${configKey}, expires=${result.expiresAt}`);
  return { success: true, alreadyProcessed: false };
};

// [OMIT] exports.verifyGateAction = onCall(
const verifyGateAction = onCall(
  { secrets: [paystackSecretKey] },
  verifyGateActionHandler
);

// ─────────────────────────────────────────────────────────────────────────────
// FUNCTION 4 — paystackWebhook [OMIT]
// ─────────────────────────────────────────────────────────────────────────────
// [OMIT] exports.paystackWebhook = onRequest(
const paystackWebhook = onRequest(
  { secrets: [paystackSecretKey] },
  async (req, res) => {
    try {
      const secretKey = paystackSecretKey.value();

      const signature = req.headers["x-paystack-signature"];
      if (!signature) {
        console.warn("Webhook rejected: missing signature header");
        return res.status(401).send("Unauthorized");
      }

      if (!req.rawBody) {
        console.error("Webhook rejected: rawBody unavailable");
        return res.status(400).send("Bad Request");
      }

      const computed = crypto
        .createHmac("sha512", secretKey)
        .update(req.rawBody)
        .digest("hex");

      if (signature !== computed) {
        console.warn("Webhook rejected: invalid signature");
        return res.status(401).send("Unauthorized");
      }

      const event = req.body;
      if (!event || event.event !== "charge.success") {
        return res.status(200).send("Event ignored");
      }

      const txData    = event.data || {};
      const reference = txData.reference || "";
      const meta      = txData.metadata || {};

      let uid = meta.uid || null;
      if (!uid && reference.startsWith("gate_")) {
        const parts = reference.split("_");
        if (parts.length >= 3) uid = parts[1];
      }

      if (!uid) {
        console.error(`Webhook: cannot determine UID from reference=${reference}`);
        return res.status(200).send("OK");
      }

      let verifyResp;
      try {
        verifyResp = await externalGet(
          `/transaction/verify/${encodeURIComponent(reference)}`,
          secretKey
        );
      } catch (e) {
        console.error(`Webhook: re-verify request failed for ref=${reference}:`, e);
        return res.status(200).send("OK");
      }

      if (
        verifyResp.statusCode !== 200 ||
        !verifyResp.body ||
        !verifyResp.body.data ||
        verifyResp.body.data.status !== "success"
      ) {
        console.error(`Webhook: re-verify failed for ref=${reference}:`, verifyResp.body);
        return res.status(200).send("OK");
      }

      const verifiedTx   = verifyResp.body.data;
      const verifiedMeta = verifiedTx.metadata || {};

      const action = verifiedMeta.action;
      let configKey, config;
      try {
        ({ configKey, config } = await resolveActionConfig(action));
      } catch (_) {
        console.error(`Webhook: unresolvable action=${action} in verified tx for ref=${reference}`);
        return res.status(200).send("OK");
      }

      const reportedVal = verifiedTx.amount;
      if (reportedVal !== config.val) {
        console.error(
          `Webhook: VAL MISMATCH — ref=${reference}, uid=${uid}, ` +
          `expected=${config.val}, reported=${reportedVal}, configKey=${configKey}`
        );
        return res.status(200).send("OK");
      }

      await applyVerifiedAccess(uid, reference, configKey, config, reportedVal, "paystackWebhook");

      return res.status(200).send("OK");

    } catch (err) {
      console.error("Webhook: unhandled error:", err);
      return res.status(200).send("OK");
    }
  }
);

// ─────────────────────────────────────────────────────────────────────────────
// FUNCTION 5 — onUserCreated (Auth Trigger on User Registration) [OMIT]
// ─────────────────────────────────────────────────────────────────────────────
// [OMIT] exports.onUserCreated = functions.auth.user().onCreate(async (user) => {
const onUserCreated = functions.auth.user().onCreate(async (user) => {
  if (!user || !user.uid) return;

  const uid = user.uid;
  const db  = getFirestore();
  const userRef = db.collection("users").doc(uid);

  try {
    const existingSnap = await userRef.get();
    const existingData = existingSnap.exists ? existingSnap.data() : {};

    const updateDoc = {};

    if (!existingData.email && user.email) updateDoc.email = user.email;
    if (!existingData.name && user.displayName) updateDoc.name = user.displayName;
    if (!existingData.phone && user.phoneNumber) updateDoc.phone = user.phoneNumber;
    if (!existingData.status) updateDoc.status = "active";
    if (existingData.isAdmin === undefined) updateDoc.isAdmin = false;
    if (existingData.isApproved === undefined) updateDoc.isApproved = true;
    if (!existingData.createdAt) updateDoc.createdAt = Timestamp.now();

    // Ensure central master config/flags document exists
    const configFlagsRef = db.collection("config").doc("flags");
    const configSnap = await configFlagsRef.get();
    if (!configSnap.exists) {
      await configFlagsRef.set({
        flag_a1: {
          active: false,
          title: "Notice",
          message: "Select an option to proceed.",
          options: [
            { buttonText: "Option A", action: "act_x1" },
            { buttonText: "Option B", action: "act_x2" }
          ]
        }
      });
      console.log("onUserCreated: Initialized central config/flags document");
    }

    // Ensure central config/default document exists
    const configDefaultRef = db.collection("config").doc("default");
    let defaultConfigSnap = await configDefaultRef.get();
    if (!defaultConfigSnap.exists) {
      const initialDefault = {
        durationDays: 40,
        durationMinutes: 0,
        tier: "default",
        val: 0,
        ref: "default"
      };
      await configDefaultRef.set(initialDefault);
      console.log("onUserCreated: Initialized central config/default document");
      defaultConfigSnap = await configDefaultRef.get();
    }

    const defaultConfig = defaultConfigSnap.exists ? (defaultConfigSnap.data() || {}) : {};

    // Helper to calculate duration in days & minutes from config/default (paused per user request)
    let durationDays = 0;
    let durationMinutes = 0;
    const ref = "";
    const tier = "";
    const val = 0;

    const now = new Date();
    const durationMs = 0;
    const expiresAt = null;

    if (!existingData.flags) {
      updateDoc.flags = {
        flag_a1: {
          active: false
        }
      };
    }

    if (!existingData.access || !existingData.access.ref) {
      updateDoc.access = {
        status: "none",
        tier: "",
        durationDays: 0,
        durationMinutes: 0,
        val: 0,
        ref: "",
        startAt: null,
        expiresAt: null
      };
    }

    if (Object.keys(updateDoc).length > 0) {
      await userRef.set(updateDoc, { merge: true });
      console.log(`onUserCreated: Initialized user UID ${uid} with default access (ref=${ref}, ${durationDays}d, ${durationMinutes}m)`);
    }
  } catch (err) {
    console.error(`onUserCreated error for UID ${uid}:`, err);
  }
});

// ─────────────────────────────────────────────────────────────────────────────
// SCHEDULED FUNCTION — Check Expired Access (Cron / Cloud Scheduler) [OMIT]
// Runs every 2 minutes to check for expired active access and update status/flags
// ─────────────────────────────────────────────────────────────────────────────
// [OMIT] exports.scheduledCheckExpiredAccess = onSchedule("every 3 hours", async (event) => {
const scheduledCheckExpiredAccess = onSchedule("every 3 hours", async (event) => {
  const db = getFirestore();
  const now = new Date();
  try {
    let docs = [];
    try {
      // Primary query: fetch users with access.status == "active"
      const snapshot = await db.collection("users")
        .where("access.status", "==", "active")
        .get();
      docs = snapshot.docs;
    } catch (queryErr) {
      console.warn("scheduledCheckExpiredAccess: Filtered query failed, falling back to full collection scan:", queryErr.message || queryErr);
      // Fallback: fetch all user documents if index or query fails
      const fullSnapshot = await db.collection("users").get();
      docs = fullSnapshot.docs;
    }

    if (!docs || docs.length === 0) {
      console.log("scheduledCheckExpiredAccess: No users found.");
      return;
    }

    let count = 0;
    for (const doc of docs) {
      const data = doc.data() || {};
      const acc = data.access || {};
      const expiresAt = acc.expiresAt && typeof acc.expiresAt.toDate === "function" ? acc.expiresAt.toDate() : null;

      if (expiresAt && expiresAt <= now) {
        const uid = doc.id;
        try {
          await reactivateGateIfExpired(uid);
          count++;
        } catch (err) {
          console.error(`Error processing expiration for UID ${uid}:`, err);
        }
      }
    }
    console.log(`scheduledCheckExpiredAccess: Successfully processed ${count} expired user(s) out of ${docs.length} checked.`);
  } catch (error) {
    console.error("scheduledCheckExpiredAccess top-level error:", error);
  }
});

// ─────────────────────────────────────────────────────────────────────────────
// FUNCTION 7 — publishAnnouncement (HTTP Callable Admin function) [OMIT]
// Writes to config/announcements and sends FCM push to topic "announcements" via Admin SDK
// ─────────────────────────────────────────────────────────────────────────────
// [OMIT] exports.publishAnnouncement = onCall(async (request) => {
const publishAnnouncement = onCall(async (request) => {
  const { title, message, category, actionText, actionUrl } = request.data || {};
  if (!title || !message) {
    throw new HttpsError("invalid-argument", "Title and message are required.");
  }

  const db = getFirestore();
  const annDocRef = db.collection("config").doc("announcements");
  const doc = await annDocRef.get();
  const currentData = doc.exists ? (doc.data() || {}) : {};
  const currentItems = Array.isArray(currentData.items) ? currentData.items : [];

  const annId = "ann_" + Date.now();
  const now = new Date();
  const formattedDate = now.toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });

  const newAnn = {
    id: annId,
    title,
    message,
    category: category || "NOTICE",
    date: formattedDate,
    timestamp: Date.now(),
    actionText: actionText || null,
    actionUrl: actionUrl || null,
  };

  currentItems.unshift(newAnn);
  await annDocRef.set({ items: currentItems }, { merge: true });

  // Send FCM push via Firebase Admin SDK to topic "announcements"
  const messaging = getMessaging();
  const payload = {
    topic: "announcements",
    notification: {
      title: title,
      body: message,
    },
    data: {
      id: annId,
      title: title,
      message: message,
      category: category || "NOTICE",
      date: formattedDate,
      actionText: actionText || "",
      actionUrl: actionUrl || "",
    },
    android: {
      notification: {
        channelId: "announcements_channel",
      },
    },
  };

  let fcmResult = null;
  try {
    fcmResult = await messaging.send(payload);
    console.log(`publishAnnouncement: FCM push sent successfully. ID: ${fcmResult}`);
  } catch (err) {
    console.error("publishAnnouncement: Error sending FCM push:", err);
  }

  return { success: true, announcement: newAnn, fcmMessageId: fcmResult };
});
