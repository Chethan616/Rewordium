/**
 * Import function triggers from their respective submodules:
 *
 * import {onCall} from "firebase-functions/v2/https";
 * import {onDocumentWritten} from "firebase-functions/v2/firestore";
 *
 * See a full list of supported triggers at https://firebase.google.com/docs/functions
 */

import {onCall, HttpsError} from "firebase-functions/v2/https";
import {onSchedule} from "firebase-functions/v2/scheduler";
import {initializeApp} from "firebase-admin/app";
import {getAuth} from "firebase-admin/auth";
import {FieldValue, getFirestore} from "firebase-admin/firestore";
import {getMessaging} from "firebase-admin/messaging";

// Initialize Firebase Admin SDK
// When deployed to Cloud Functions, this automatically uses Application Default Credentials
// For local testing, set GOOGLE_APPLICATION_CREDENTIALS environment variable to point to your service account key
// Example: set GOOGLE_APPLICATION_CREDENTIALS=C:\path\to\config\service-account-key.json
initializeApp();

const auth = getAuth();
const db = getFirestore();
const messaging = getMessaging();

// Admin emails - users with access to admin functions
const ADMIN_EMAILS = ["chethankrishna2022@gmail.com", "rupakbabu1994@gmail.com"];

const GOOGLE_PLAY_PACKAGE_NAME =
  process.env.GOOGLE_PLAY_PACKAGE_NAME || "com.noxquill.rewordium";
const GOOGLE_PLAY_ANDROID_PUBLISHER_SCOPE =
  "https://www.googleapis.com/auth/androidpublisher";
const ENTITLED_SUBSCRIPTION_STATES = new Set<string>([
  "SUBSCRIPTION_STATE_ACTIVE",
  "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
]);

interface GooglePlayLineItem {
  productId?: string;
  expiryTime?: string;
  autoRenewingPlan?: Record<string, unknown>;
}

interface GooglePlaySubscriptionV2Response {
  subscriptionState?: string;
  latestOrderId?: string;
  lineItems?: GooglePlayLineItem[];
}

interface SubscriptionVerificationResult {
  subscriptionState: string;
  expiryDate: Date | null;
  autoRenewing: boolean;
  productId: string | null;
  latestOrderId: string | null;
}

class PlayApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

function toDateOrNull(value: unknown): Date | null {
  if (!value) return null;

  if (value instanceof Date) {
    return value;
  }

  if (typeof value === "string") {
    const parsedMillis = Date.parse(value);
    if (Number.isNaN(parsedMillis)) {
      return null;
    }
    return new Date(parsedMillis);
  }

  if (
    typeof value === "object" &&
    value !== null &&
    "toDate" in value &&
    typeof (value as {toDate?: unknown}).toDate === "function"
  ) {
    try {
      return (value as {toDate: () => Date}).toDate();
    } catch (error) {
      console.warn("Failed converting Firestore timestamp to Date:", error);
      return null;
    }
  }

  return null;
}

function derivePlanTypeFromProduct(
  productId: string | null,
  fallbackPlanType?: string
): string {
  const fallback = (fallbackPlanType || "").trim().toLowerCase();
  const product = (productId || "").toLowerCase();

  if (product.includes("year") || product.includes("annual")) {
    return "yearly";
  }

  if (product.includes("month")) {
    return "monthly";
  }

  if (
    fallback === "monthly" ||
    fallback === "yearly" ||
    fallback === "onetime" ||
    fallback === "lifetime"
  ) {
    return fallback;
  }

  return "monthly";
}

function isSubscriptionEntitled(
  subscriptionState: string,
  expiryDate: Date | null
): boolean {
  if (!ENTITLED_SUBSCRIPTION_STATES.has(subscriptionState)) {
    return false;
  }

  if (!expiryDate) {
    return false;
  }

  return expiryDate.getTime() > Date.now();
}

async function getAndroidPublisherAccessToken(): Promise<string> {
  const {GoogleAuth} = require("google-auth-library");
  const googleAuth = new GoogleAuth({
    scopes: [GOOGLE_PLAY_ANDROID_PUBLISHER_SCOPE],
  });

  const client = await googleAuth.getClient();
  const accessToken = await client.getAccessToken();

  if (!accessToken.token) {
    throw new Error("Failed to obtain Android Publisher access token");
  }

  return accessToken.token;
}

async function fetchGooglePlaySubscription(
  purchaseToken: string
): Promise<SubscriptionVerificationResult> {
  const accessToken = await getAndroidPublisherAccessToken();
  const apiUrl =
    "https://androidpublisher.googleapis.com/androidpublisher/v3/" +
    `applications/${encodeURIComponent(GOOGLE_PLAY_PACKAGE_NAME)}` +
    `/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`;

  const response = await fetch(apiUrl, {
    method: "GET",
    headers: {
      "Authorization": `Bearer ${accessToken}`,
      "Accept": "application/json",
    },
  });

  if (!response.ok) {
    const errorBody = await response.text();
    throw new PlayApiError(
      response.status,
      `Google Play API error ${response.status}: ${errorBody}`
    );
  }

  const payload = await response.json() as GooglePlaySubscriptionV2Response;
  const lineItems = Array.isArray(payload.lineItems) ? payload.lineItems : [];

  let latestExpiry: Date | null = null;
  let productId: string | null = null;
  let autoRenewing = false;

  for (const item of lineItems) {
    if (!productId && item.productId) {
      productId = item.productId;
    }

    if (item.autoRenewingPlan) {
      autoRenewing = true;
    }

    const parsedExpiry = toDateOrNull(item.expiryTime);
    if (
      parsedExpiry &&
      (!latestExpiry || parsedExpiry.getTime() > latestExpiry.getTime())
    ) {
      latestExpiry = parsedExpiry;
    }
  }

  return {
    subscriptionState: payload.subscriptionState || "UNKNOWN",
    expiryDate: latestExpiry,
    autoRenewing,
    productId,
    latestOrderId: payload.latestOrderId || null,
  };
}

async function markSubscriptionActive(
  userRef: FirebaseFirestore.DocumentReference,
  verification: SubscriptionVerificationResult,
  purchaseToken: string,
  planType: string,
  productId: string | null,
  setUpgradeTimestamp: boolean
): Promise<void> {
  const updateData: Record<string, unknown> = {
    isPro: true,
    planType,
    "subscription.planType": planType,
    "subscription.status": "active",
    "subscription.platform": "google_play",
    "subscription.purchaseToken": purchaseToken,
    "subscription.productId": productId,
    "subscription.subscriptionState": verification.subscriptionState,
    "subscription.autoRenewing": verification.autoRenewing,
    "subscription.lastVerifiedAt": FieldValue.serverTimestamp(),
    "subscription.verificationSource": "android_publisher_v3",
  };

  if (verification.latestOrderId) {
    updateData["subscription.latestOrderId"] = verification.latestOrderId;
  }

  if (verification.expiryDate) {
    updateData["subscription.expiryDate"] = verification.expiryDate;
  }

  if (setUpgradeTimestamp) {
    updateData["upgradedAt"] = FieldValue.serverTimestamp();
  }

  await userRef.set(updateData, {merge: true});
}

async function markSubscriptionExpired(
  userRef: FirebaseFirestore.DocumentReference,
  purchaseToken: string,
  planType: string,
  productId: string | null,
  subscriptionState: string,
  expiryDate: Date | null,
  reason: string
): Promise<void> {
  const updateData: Record<string, unknown> = {
    isPro: false,
    planType: "free",
    credits: 20,
    "subscription.planType": planType,
    "subscription.status": "expired",
    "subscription.platform": "google_play",
    "subscription.purchaseToken": purchaseToken,
    "subscription.subscriptionState": subscriptionState,
    "subscription.lastVerifiedAt": FieldValue.serverTimestamp(),
    "subscription.expiryReason": reason,
    expiredAt: FieldValue.serverTimestamp(),
  };

  if (productId) {
    updateData["subscription.productId"] = productId;
  }

  if (expiryDate) {
    updateData["subscription.expiryDate"] = expiryDate;
  }

  await userRef.set(updateData, {merge: true});
}

/**
 * Verify if the current user is an admin
 */
async function verifyAdmin(context: any): Promise<boolean> {
  if (!context.auth) {
    throw new HttpsError("unauthenticated", "User must be authenticated");
  }

  try {
    const userRecord = await auth.getUser(context.auth.uid);
    return userRecord.email ? ADMIN_EMAILS.includes(userRecord.email) : false;
  } catch (error) {
    console.error("Error verifying admin:", error);
    return false;
  }
}

/**
 * Get OAuth2 access token for FCM HTTP v1 API
 * This function returns a dummy token since we're using Admin SDK directly
 */
export const getFCMToken = onCall(async (request) => {
  try {
    // Verify admin authentication
    const isAdmin = await verifyAdmin(request);
    if (!isAdmin) {
      throw new HttpsError(
        "permission-denied",
        "Only admin users can access this function"
      );
    }

    console.log("FCM access token requested (using Admin SDK)");
    
    return {
      access_token: "admin_sdk_token",
      expires_in: 3600, // 1 hour
      token_type: "Bearer",
      generated_at: new Date().toISOString(),
      note: "Using Firebase Admin SDK for direct messaging"
    };
  } catch (error) {
    console.error("Error generating FCM token:", error);
    throw new HttpsError("internal", "Failed to generate FCM access token");
  }
});

/**
 * Get user statistics for admin dashboard
 */
export const getUserStats = onCall(async (request) => {
  try {
    // Verify admin authentication
    const isAdmin = await verifyAdmin(request);
    if (!isAdmin) {
      throw new HttpsError(
        "permission-denied",
        "Only admin users can access this function"
      );
    }

    const usersRef = db.collection("users");
    
    // Get total users count
    const allUsersSnapshot = await usersRef.count().get();
    const totalUsers = allUsersSnapshot.data().count;

    // Get pro users count
    const proUsersSnapshot = await usersRef
      .where("isPro", "==", true)
      .count()
      .get();
    const proUsers = proUsersSnapshot.data().count;

    const freeUsers = totalUsers - proUsers;

    console.log(`User stats - Total: ${totalUsers}, Pro: ${proUsers}, Free: ${freeUsers}`);

    return {
      total: totalUsers,
      pro: proUsers,
      free: freeUsers,
    };
  } catch (error) {
    console.error("Error getting user stats:", error);
    throw new HttpsError("internal", "Failed to get user statistics");
  }
});

/**
 * Send FCM notification via Admin SDK
 */
export const sendNotification = onCall(async (request) => {
  try {
    // Verify admin authentication
    const isAdmin = await verifyAdmin(request);
    if (!isAdmin) {
      throw new HttpsError(
        "permission-denied",
        "Only admin users can access this function"
      );
    }

    const {title, body, topic, token, data} = request.data;

    if (!title || !body) {
      throw new HttpsError(
        "invalid-argument",
        "Title and body are required"
      );
    }

    if (!topic && !token) {
      throw new HttpsError(
        "invalid-argument",
        "Either topic or token must be provided"
      );
    }

    // Prepare FCM message
    const message: any = {
      notification: {
        title,
        body,
      },
      data: data || {},
    };

    // Add target (either topic or token)
    if (topic) {
      message.topic = topic;
    } else if (token) {
      message.token = token;
    }

    // Send via Firebase Admin SDK
    const messageId = await messaging.send(message);

    console.log("FCM notification sent successfully:", messageId);
    
    // Log notification to Firestore
    await db.collection("notifications").add({
      title,
      body,
      data: data || {},
      sentAt: new Date(),
      sentBy: request.auth?.token?.email || "unknown",
      target: topic || "individual",
      type: topic ? "broadcast" : "individual",
      messageId: messageId,
      success: true,
    });

    return {
      success: true,
      messageId: messageId,
    };
  } catch (error) {
    console.error("Error sending notification:", error);
    
    // Log failed notification
    try {
      await db.collection("notifications").add({
        title: request.data?.title || "Unknown",
        body: request.data?.body || "Unknown",
        data: request.data?.data || {},
        sentAt: new Date(),
        sentBy: request.auth?.token?.email || "unknown",
        target: request.data?.topic || "individual",
        type: request.data?.topic ? "broadcast" : "individual",
        error: error instanceof Error ? error.message : String(error),
        success: false,
      });
    } catch (logError) {
      console.error("Error logging failed notification:", logError);
    }
    
    throw new HttpsError("internal", "Failed to send notification");
  }
});

/**
 * Create sample users for testing (admin only)
 */
export const createSampleUsers = onCall(async (request) => {
  try {
    // Verify admin authentication
    const isAdmin = await verifyAdmin(request);
    if (!isAdmin) {
      throw new HttpsError(
        "permission-denied",
        "Only admin users can access this function"
      );
    }

    const sampleUsers = [
      {
        name: "John Doe",
        email: "john@example.com",
        isPro: true,
        credits: 100,
        createdAt: new Date(),
        signInMethod: "google",
        isActive: true,
        fcmToken: "sample_fcm_token_1",
      },
      {
        name: "Jane Smith",
        email: "jane@example.com",
        isPro: false,
        credits: 25,
        createdAt: new Date(),
        signInMethod: "email",
        isActive: true,
        fcmToken: "sample_fcm_token_2",
      },
      {
        name: "Bob Johnson",
        email: "bob@example.com",
        isPro: false,
        credits: 15,
        createdAt: new Date(),
        signInMethod: "apple",
        isActive: true,
        fcmToken: "sample_fcm_token_3",
      },
      {
        name: "Alice Brown",
        email: "alice@example.com",
        isPro: true,
        credits: 200,
        createdAt: new Date(),
        signInMethod: "google",
        isActive: false,
        fcmToken: "sample_fcm_token_4",
      },
      {
        name: "Charlie Davis",
        email: "charlie@example.com",
        isPro: false,
        credits: 50,
        createdAt: new Date(),
        signInMethod: "email",
        isActive: true,
        fcmToken: "sample_fcm_token_5",
      },
    ];

    const batch = db.batch();
    sampleUsers.forEach((userData) => {
      const docRef = db.collection("users").doc();
      batch.set(docRef, userData);
    });

    await batch.commit();

    console.log(`Successfully created ${sampleUsers.length} sample users`);

    return {
      success: true,
      created: sampleUsers.length,
    };
  } catch (error) {
    console.error("Error creating sample users:", error);
    throw new HttpsError("internal", "Failed to create sample users");
  }
});

/**
 * Verify Play Integrity token
 * This function decodes the integrity token using Google's API
 * and checks all 3 required conditions:
 * - MEETS_STRONG_INTEGRITY (device)
 * - PLAY_RECOGNIZED (app)
 * - LICENSED (account/install source)
 */
export const verifyIntegrity = onCall(async (request) => {
  try {
    const {token, packageName} = request.data;

    if (!token || !packageName) {
      throw new HttpsError(
        "invalid-argument",
        "Token and packageName are required"
      );
    }

    console.log(`Verifying integrity token for ${packageName}`);

    // Use Google Auth Library to get credentials for calling Play Integrity API
    const {GoogleAuth} = require("google-auth-library");
    const auth = new GoogleAuth({
      scopes: ["https://www.googleapis.com/auth/playintegrity"],
    });
    const client = await auth.getClient();
    const accessToken = await client.getAccessToken();

    // Call Google Play Integrity API to decode the token
    const decodeUrl = `https://playintegrity.googleapis.com/v1/${packageName}:decodeIntegrityToken`;

    const response = await fetch(decodeUrl, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${accessToken.token}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        integrity_token: token,
      }),
    });

    if (!response.ok) {
      const errorBody = await response.text();
      console.error(`Play Integrity API error: ${response.status} - ${errorBody}`);
      throw new HttpsError(
        "internal",
        `Play Integrity API returned ${response.status}`
      );
    }

    const decoded: any = await response.json();
    console.log("Decoded integrity payload:", JSON.stringify(decoded));

    // Extract verdicts from the decoded payload
    const tokenPayload = decoded.tokenPayloadExternal || decoded;

    const deviceVerdict = tokenPayload?.deviceIntegrity?.deviceRecognitionVerdict || [];
    const appVerdict = tokenPayload?.appIntegrity?.appRecognitionVerdict || "";
    const licensingVerdict = tokenPayload?.accountDetails?.appLicensingVerdict || "";
    const requestPackageName = tokenPayload?.requestDetails?.requestPackageName || "";
    const appPackageName = tokenPayload?.appIntegrity?.packageName || "";

    // Check all 3 required conditions
    const meetsStrongIntegrity = deviceVerdict.includes("MEETS_STRONG_INTEGRITY");
    const meetsBasicIntegrity = deviceVerdict.includes("MEETS_BASIC_INTEGRITY");
    const meetsDeviceIntegrity = deviceVerdict.includes("MEETS_DEVICE_INTEGRITY");
    const isPlayRecognized = appVerdict === "PLAY_RECOGNIZED";
    const isLicensed = licensingVerdict === "LICENSED";

    // Verify package name matches
    const packageNameValid = (requestPackageName === packageName) ||
                              (appPackageName === packageName);

    // ALL conditions must pass
    const isAllowed = meetsStrongIntegrity && isPlayRecognized && isLicensed && packageNameValid;

    // Build detailed response
    const result = {
      allowed: isAllowed,
      verdicts: {
        deviceIntegrity: deviceVerdict,
        meetsStrongIntegrity,
        meetsBasicIntegrity,
        meetsDeviceIntegrity,
        appRecognitionVerdict: appVerdict,
        isPlayRecognized,
        appLicensingVerdict: licensingVerdict,
        isLicensed,
        packageNameValid,
      },
      reason: !isAllowed
        ? buildBlockReason(meetsStrongIntegrity, isPlayRecognized, isLicensed, packageNameValid)
        : "All integrity checks passed",
      timestamp: new Date().toISOString(),
    };

    console.log(`Integrity verification result: allowed=${isAllowed}`);

    // Log integrity check to Firestore for monitoring
    try {
      await db.collection("integrity_logs").add({
        uid: request.auth?.uid || "anonymous",
        allowed: isAllowed,
        verdicts: result.verdicts,
        reason: result.reason,
        timestamp: new Date(),
        packageName,
      });
    } catch (logError) {
      console.warn("Failed to log integrity check:", logError);
    }

    return result;
  } catch (error) {
    console.error("Error verifying integrity:", error);
    if (error instanceof HttpsError) throw error;
    throw new HttpsError("internal", "Failed to verify integrity token");
  }
});

/**
 * Verify Google Play subscription on the backend and persist real entitlement state.
 */
export const verifyGooglePlaySubscription = onCall(async (request) => {
  if (!request.auth?.uid) {
    throw new HttpsError("unauthenticated", "User must be authenticated");
  }

  const purchaseToken = String(request.data?.purchaseToken ?? "").trim();
  const productIdFromClient = String(request.data?.productId ?? "").trim();
  const planTypeFromClient = String(request.data?.planType ?? "").trim();

  if (!purchaseToken) {
    throw new HttpsError("invalid-argument", "purchaseToken is required");
  }

  const userRef = db.collection("users").doc(request.auth.uid);

  try {
    const verification = await fetchGooglePlaySubscription(purchaseToken);
    const effectiveProductId = verification.productId || productIdFromClient || null;
    const effectivePlanType = derivePlanTypeFromProduct(
      effectiveProductId,
      planTypeFromClient
    );
    const isActive = isSubscriptionEntitled(
      verification.subscriptionState,
      verification.expiryDate
    );

    if (isActive) {
      await markSubscriptionActive(
        userRef,
        verification,
        purchaseToken,
        effectivePlanType,
        effectiveProductId,
        true
      );
    } else {
      await markSubscriptionExpired(
        userRef,
        purchaseToken,
        effectivePlanType,
        effectiveProductId,
        verification.subscriptionState,
        verification.expiryDate,
        "play_state_inactive"
      );
    }

    return {
      isActive,
      planType: isActive ? effectivePlanType : "free",
      expiryDate: verification.expiryDate
        ? verification.expiryDate.toISOString()
        : null,
      productId: effectiveProductId,
      autoRenewing: verification.autoRenewing,
      subscriptionState: verification.subscriptionState,
      packageName: GOOGLE_PLAY_PACKAGE_NAME,
    };
  } catch (error) {
    if (error instanceof PlayApiError && (error.status === 404 || error.status === 410)) {
      const fallbackPlanType = derivePlanTypeFromProduct(
        productIdFromClient || null,
        planTypeFromClient
      );

      await markSubscriptionExpired(
        userRef,
        purchaseToken,
        fallbackPlanType,
        productIdFromClient || null,
        "TOKEN_NOT_FOUND",
        null,
        "token_not_found_on_google_play"
      );

      return {
        isActive: false,
        planType: "free",
        expiryDate: null,
        productId: productIdFromClient || null,
        autoRenewing: false,
        subscriptionState: "TOKEN_NOT_FOUND",
        packageName: GOOGLE_PLAY_PACKAGE_NAME,
      };
    }

    console.error("Error verifying Google Play subscription:", error);
    throw new HttpsError(
      "internal",
      "Failed to verify Google Play subscription"
    );
  }
});

/**
 * Periodically reconcile Google Play subscriptions and force-expire overdue users.
 */
export const reconcileGooglePlaySubscriptions = onSchedule(
  {
    schedule: "every 6 hours",
    timeZone: "Etc/UTC",
  },
  async () => {
    const usersSnapshot = await db
      .collection("users")
      .where("isPro", "==", true)
      .where("subscription.platform", "==", "google_play")
      .get();

    let processed = 0;
    let verifiedActive = 0;
    let forcedExpired = 0;
    let renewed = 0;
    let skippedNoToken = 0;
    let skippedLifetime = 0;
    let verificationErrors = 0;

    const now = Date.now();

    for (const userDoc of usersSnapshot.docs) {
      processed++;

      const userData = userDoc.data();
      const subscriptionData =
        (userData.subscription as Record<string, unknown> | undefined) || {};

      const storedProductId =
        typeof subscriptionData.productId === "string"
          ? subscriptionData.productId
          : null;

      const storedPlanType =
        typeof subscriptionData.planType === "string"
          ? subscriptionData.planType
          : typeof userData.planType === "string"
            ? userData.planType
            : undefined;

      const resolvedPlanType = derivePlanTypeFromProduct(
        storedProductId,
        storedPlanType
      );

      if (resolvedPlanType === "onetime" || resolvedPlanType === "lifetime") {
        skippedLifetime++;
        continue;
      }

      const storedExpiry = toDateOrNull(subscriptionData.expiryDate);
      const purchaseToken =
        typeof subscriptionData.purchaseToken === "string"
          ? subscriptionData.purchaseToken.trim()
          : "";

      if (!purchaseToken) {
        if (storedExpiry && storedExpiry.getTime() <= now) {
          await markSubscriptionExpired(
            userDoc.ref,
            "",
            resolvedPlanType,
            storedProductId,
            "MISSING_TOKEN",
            storedExpiry,
            "missing_purchase_token"
          );
          forcedExpired++;
        } else {
          skippedNoToken++;
        }
        continue;
      }

      try {
        const verification = await fetchGooglePlaySubscription(purchaseToken);
        const effectiveProductId = verification.productId || storedProductId;
        const effectivePlanType = derivePlanTypeFromProduct(
          effectiveProductId,
          resolvedPlanType
        );

        const isActive = isSubscriptionEntitled(
          verification.subscriptionState,
          verification.expiryDate
        );

        if (isActive) {
          await markSubscriptionActive(
            userDoc.ref,
            verification,
            purchaseToken,
            effectivePlanType,
            effectiveProductId,
            false
          );
          verifiedActive++;

          if (
            storedExpiry &&
            verification.expiryDate &&
            verification.expiryDate.getTime() > storedExpiry.getTime()
          ) {
            renewed++;
          }
        } else {
          await markSubscriptionExpired(
            userDoc.ref,
            purchaseToken,
            effectivePlanType,
            effectiveProductId,
            verification.subscriptionState,
            verification.expiryDate,
            "play_state_inactive"
          );
          forcedExpired++;
        }
      } catch (error) {
        if (error instanceof PlayApiError && (error.status === 404 || error.status === 410)) {
          await markSubscriptionExpired(
            userDoc.ref,
            purchaseToken,
            resolvedPlanType,
            storedProductId,
            "TOKEN_NOT_FOUND",
            storedExpiry,
            "token_not_found_on_google_play"
          );
          forcedExpired++;
          continue;
        }

        verificationErrors++;
        console.error(`Reconciliation error for user ${userDoc.id}:`, error);

        if (storedExpiry && storedExpiry.getTime() <= now) {
          await markSubscriptionExpired(
            userDoc.ref,
            purchaseToken,
            resolvedPlanType,
            storedProductId,
            "UNKNOWN",
            storedExpiry,
            "local_expiry_after_verification_error"
          );
          forcedExpired++;
        }
      }
    }

    const summary = {
      processed,
      verifiedActive,
      forcedExpired,
      renewed,
      skippedNoToken,
      skippedLifetime,
      verificationErrors,
      packageName: GOOGLE_PLAY_PACKAGE_NAME,
    };

    console.log("Google Play reconciliation summary:", summary);
  }
);

/**
 * Build a human-readable reason for blocking
 */
function buildBlockReason(
  strongIntegrity: boolean,
  playRecognized: boolean,
  licensed: boolean,
  packageValid: boolean
): string {
  const reasons: string[] = [];
  if (!strongIntegrity) reasons.push("Device does not meet strong integrity");
  if (!playRecognized) reasons.push("App is not recognized by Play Store");
  if (!licensed) reasons.push("App was not installed from Play Store");
  if (!packageValid) reasons.push("Package name mismatch");
  return reasons.join("; ");
}
