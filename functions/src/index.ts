/**
 * Import function triggers from their respective submodules:
 *
 * import {onCall} from "firebase-functions/v2/https";
 * import {onDocumentWritten} from "firebase-functions/v2/firestore";
 *
 * See a full list of supported triggers at https://firebase.google.com/docs/functions
 */

import {onCall, HttpsError} from "firebase-functions/v2/https";
import {initializeApp, cert} from "firebase-admin/app";
import {getAuth} from "firebase-admin/auth";
import {getFirestore} from "firebase-admin/firestore";
import {getMessaging} from "firebase-admin/messaging";

// Service account credentials
const serviceAccountKey = {
  "type": "service_account",
  "project_id": "rewordium",
  "private_key_id": "4a89181f09b0ade44b2113b890c281eb22384226",
  "private_key": "-----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCvOCMr8rPp/3/f\nA+YqBOvCUfBxv2V0x9b0F4/rpdmpv/XbALv+pvrP5P+SHP69NPJgc5ZlcUhXWGrb\nrmP24wy31Vv2u8yAk854cA4o2rrmDrndaiO19bQNVQIR99xHDbz/Sr5OVdX27LRp\nAHWxenaviZaJD5Xh9LwLAStKfpKDAcYGFKPstqWJuatciCyNV0Hr4PIXoEr2L35/\nUT8AB82kulyC7dv9JPfKrMnispceTHzI6DFApr5xFjj21cYeEPLjOJThRc64T7LD\nAtnFZpMGY4wGuUonmVOT1EeBQ6oh/pFlaJ/3AW8aCYZTKoWNfYFM8TQo3FTWg512\njXOIRO6zAgMBAAECggEAOrOP9PVGmSpj6I8h2Qn1AhD5gOIiV1FsDjKmoMfbeCc7\ncrd4RAQlGukZRhY4saK/YNjYlfuxxLF2e0qdauT+KchtGugnxxu9nFPOKpm8674b\nPraEaD18qMrF+scTISrVGqIrx4qyOBttBZCF3YOtp3ls2VZDXIPlS7qEilyFMSYj\n0Os/FbGXEnZL0HEM1sdMfzP71d8JsB04um6yD+X5quv2fuXpaNnZRb6HJR0GPHCp\n4uvh7EklpCC3bpaLLhbHVGXJciMkSgSrHJUV/yep5/NJoFxif+uLmy1xzvWkhLrK\nBGQRAWt6HdGeK31medVpvrdlp28z9AgCl/cjpn43IQKBgQDxmwITyddzY/HuIb1m\n29KG/eswljs5J2FOO/mPkenhA8lfXsD13AtmtD+nToOAbn6PdG8BnQNC+EBdSwLu\nQ7RdgfL4P3ov/0uvsk4sKr2JW0MB8Qrj384vb+kHXcLWV9ZJf5NMGGMD1Gw9sIDc\nNjG7GemJYSviEB9goeKkDw5D/QKBgQC5qJmVBbb7/9jUPL2m3gKd2CyRTlap0Deg\n/i8ak2F8T+kvAurO6uoQUqOlN9ZXuw6ibtmr+eJsEP11oj1YbbebFFoEoyQcri8v\nrxauxG3jMi3QI+Hy2JU7eX0f//iOFXC5tLLMdcTyshljuDcAJQVO2Wxy95PFzQPC\nE++Pd1OEbwKBgQDSBsjwgNbtNWXbd7MZVmCV/ufT7dT/4y7gfpx8ZQCmHc+RO2KM\nl8PFfU2UWFlSbTtR44qYIXDzZ7E0KIActfh2DQA1M6E5VjnqOxtfo6vuWspORscL\nvsOTUzqEr8ou4F6kt+VJEi4I50FNA0GRrP7gQi9UwIcQVqmgLDpEGd5x1QKBgD+6\nf/2HWKhnyiYQM4lz67IC4kl+eoEP2AiLN+AHdw8U3xYkCjW8rVutAj9US18R9pQL\nOOyveelea6JVbnlMMBorgjrVRTATGl9j2oVjJ9U1BETODGEvtwoTScASPV+IPImC\nXV2Rj2k/eTehpD+Idan7OB6+nRropMGZ1kGI1EJBAoGBANalxfP0fjaVqXpBwDn4\nbG+10PFdYwBh2HQOFB2uWxL2XACAqC3qS294RB313Y50261nJHDfeveIaTSaJlCH\niexXNcz6GUI6mHb0i9uOGqm8Tflcz6rlwH0tg64IkZPlKlmQ5Q9D9Dh7uzSJy+Du\n5aSX+G3o4pRawimXiWF75k1o\n-----END PRIVATE KEY-----\n",
  "client_email": "firebase-adminsdk-fbsvc@rewordium.iam.gserviceaccount.com",
  "client_id": "108883758568406410743",
  "auth_uri": "https://accounts.google.com/o/oauth2/auth",
  "token_uri": "https://oauth2.googleapis.com/token",
  "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
  "client_x509_cert_url": "https://www.googleapis.com/robot/v1/metadata/x509/firebase-adminsdk-fbsvc%40rewordium.iam.gserviceaccount.com",
  "universe_domain": "googleapis.com"
};

// Initialize Firebase Admin SDK
initializeApp({
  credential: cert(serviceAccountKey as any)
});

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
