/**
 * Import function triggers from their respective submodules:
 *
 * import {onCall} from "firebase-functions/v2/https";
 * import {onDocumentWritten} from "firebase-functions/v2/firestore";
 *
 * See a full list of supported triggers at https://firebase.google.com/docs/functions
 */

import {onCall, HttpsError} from "firebase-functions/v2/https";
import {initializeApp} from "firebase-admin/app";
import {getAuth} from "firebase-admin/auth";
import {getFirestore} from "firebase-admin/firestore";
import {getMessaging} from "firebase-admin/messaging";

// Initialize Firebase Admin SDK
// When deployed to Cloud Functions, this automatically uses Application Default Credentials
// For local testing, set GOOGLE_APPLICATION_CREDENTIALS environment variable to point to your service account key
// Example: set GOOGLE_APPLICATION_CREDENTIALS=C:\path\to\config\service-account-key.json
initializeApp();

const auth = getAuth();
const db = getFirestore();
const messaging = getMessaging();

// Admin email - only this user can access admin functions
const ADMIN_EMAIL = "chethankrishna2022@gmail.com";

/**
 * Verify if the current user is an admin
 */
async function verifyAdmin(context: any): Promise<boolean> {
  if (!context.auth) {
    throw new HttpsError("unauthenticated", "User must be authenticated");
  }

  try {
    const userRecord = await auth.getUser(context.auth.uid);
    return userRecord.email === ADMIN_EMAIL;
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
