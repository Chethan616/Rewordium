# 🚀 Production Deployment Guide

## Overview
This guide provides step-by-step instructions for securely deploying your Firebase FCM push notifications in production using the service account credentials.

## 🔒 Security Implementation

### Step 1: Secure Backend Token Service

**CRITICAL**: Never include the service account JSON file in your mobile app. Instead, create a secure backend service to generate OAuth2 tokens.

#### Option A: Node.js Backend Service

Create a separate backend service (e.g., using Express.js):

```javascript
// server.js
const express = require('express');
const { google } = require('googleapis');
const fs = require('fs');

const app = express();
app.use(express.json());

// Load service account credentials (keep this file secure on your server)
const serviceAccount = JSON.parse(fs.readFileSync('path/to/rewordium-4a89181f09b0.json'));

// Create JWT client
const jwtClient = new google.auth.JWT(
  serviceAccount.client_email,
  null,
  serviceAccount.private_key,
  ['https://www.googleapis.com/auth/firebase.messaging']
);

// Endpoint to get access token
app.post('/api/fcm-token', async (req, res) => {
  try {
    // Verify admin authentication here (JWT token, API key, etc.)
    const adminToken = req.headers.authorization;
    if (!isValidAdminToken(adminToken)) {
      return res.status(401).json({ error: 'Unauthorized' });
    }

    // Get access token
    await jwtClient.authorize();
    const accessToken = jwtClient.credentials.access_token;
    
    res.json({ 
      access_token: accessToken,
      expires_in: 3600 
    });
  } catch (error) {
    console.error('Error generating token:', error);
    res.status(500).json({ error: 'Token generation failed' });
  }
});

function isValidAdminToken(token) {
  // Implement your admin authentication logic here
  // Verify JWT token, check admin credentials, etc.
  return true; // Placeholder
}

app.listen(3000, () => {
  console.log('FCM Token Service running on port 3000');
});
```

#### Option B: Firebase Cloud Functions

Create a Cloud Function to generate tokens:

```javascript
// functions/index.js
const functions = require('firebase-functions');
const admin = require('firebase-admin');
const { google } = require('googleapis');

admin.initializeApp();

exports.getFCMToken = functions.https.onCall(async (data, context) => {
  // Verify admin authentication
  if (!context.auth || !isAdmin(context.auth.uid)) {
    throw new functions.https.HttpsError('permission-denied', 'Admin access required');
  }

  try {
    // Use service account to get OAuth2 token
    const serviceAccount = require('./rewordium-4a89181f09b0.json');
    
    const jwtClient = new google.auth.JWT(
      serviceAccount.client_email,
      null,
      serviceAccount.private_key,
      ['https://www.googleapis.com/auth/firebase.messaging']
    );

    await jwtClient.authorize();
    return {
      access_token: jwtClient.credentials.access_token,
      expires_in: 3600
    };
  } catch (error) {
    throw new functions.https.HttpsError('internal', 'Token generation failed');
  }
});

async function isAdmin(uid) {
  // Check if user is admin
  const userRecord = await admin.auth().getUser(uid);
  return userRecord.email === 'chethankrishna2022@gmail.com';
}
```

### Step 2: Update Flutter AdminService

Update your AdminService to fetch tokens from your secure backend:

```dart
// lib/services/admin_service.dart

class AdminService {
  // Your secure token endpoint
  static const String _tokenEndpoint = 'https://your-backend.com/api/fcm-token';
  // OR for Cloud Functions:
  // static const String _tokenEndpoint = 'https://your-region-your-project.cloudfunctions.net/getFCMToken';

  static const String _projectId = 'rewordium';
  static const String _fcmEndpoint = 'https://fcm.googleapis.com/v1/projects/$_projectId/messages:send';

  // Get OAuth2 access token from secure backend
  static Future<String?> _getAccessToken() async {
    try {
      final user = FirebaseAuth.instance.currentUser;
      if (user == null) return null;

      // Get Firebase ID token for authentication
      final idToken = await user.getIdToken();

      final response = await http.post(
        Uri.parse(_tokenEndpoint),
        headers: {
          'Authorization': 'Bearer $idToken',
          'Content-Type': 'application/json',
        },
      );

      if (response.statusCode == 200) {
        final data = json.decode(response.body);
        return data['access_token'];
      } else {
        debugPrint('Token request failed: ${response.statusCode} - ${response.body}');
        return null;
      }
    } catch (e) {
      debugPrint('Error getting access token: $e');
      return null;
    }
  }

  // Rest of your AdminService methods remain the same...
}
```

## 🔧 Firebase Configuration

### Step 3: Configure FCM Topics

1. **Go to Firebase Console** → Your Project → Cloud Messaging

2. **Create Topics**:
   - `all_users` - For broadcasting to all users
   - `pro_users` - For pro user notifications
   - `free_users` - For free user notifications

3. **Update your app to subscribe users to topics**:

```dart
// In your main app initialization
Future<void> subscribeToTopics() async {
  final messaging = FirebaseMessaging.instance;
  final user = FirebaseAuth.instance.currentUser;
  
  if (user != null) {
    // Subscribe to all users topic
    await messaging.subscribeToTopic('all_users');
    
    // Subscribe based on user type
    final userDoc = await FirebaseFirestore.instance
        .collection('users')
        .doc(user.uid)
        .get();
    
    final isPro = userDoc.data()?['isPro'] ?? false;
    if (isPro) {
      await messaging.subscribeToTopic('pro_users');
      await messaging.unsubscribeFromTopic('free_users');
    } else {
      await messaging.subscribeToTopic('free_users');
      await messaging.unsubscribeFromTopic('pro_users');
    }
  }
}
```

### Step 4: Security Rules

Update your Firestore security rules to protect admin data:

```javascript
// firestore.rules
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Only admin can read/write notifications
    match /notifications/{notificationId} {
      allow read, write: if request.auth != null && 
        request.auth.token.email == 'chethankrishna2022@gmail.com';
    }
    
    // Users can read their own data, admin can read/write all user data
    match /users/{userId} {
      allow read: if request.auth != null && 
        (request.auth.uid == userId || 
         request.auth.token.email == 'chethankrishna2022@gmail.com');
      
      allow write: if request.auth != null && 
        request.auth.token.email == 'chethankrishna2022@gmail.com';
    }
  }
}
```

## 🔐 Environment Security

### Step 5: Secure File Storage

1. **Never commit the JSON file to version control**:
   ```bash
   # Add to .gitignore
   *.json
   rewordium-4a89181f09b0.json
   ```

2. **Store securely on your backend server**:
   - Use environment variables
   - Encrypt at rest
   - Limit file permissions (600)
   - Use secret management services (AWS Secrets Manager, Google Secret Manager)

### Step 6: Network Security

1. **Enable HTTPS only** for your backend service
2. **Implement rate limiting** to prevent abuse
3. **Use CORS policies** to restrict origins
4. **Add IP whitelisting** if needed

## 📱 Testing in Production

### Step 7: Test the Implementation

1. **Deploy your backend service**
2. **Update the Flutter app** with the new token endpoint
3. **Test notifications**:
   ```dart
   // Test notification sending
   final success = await AdminService.sendNotificationToAllUsers(
     title: 'Production Test',
     body: 'Testing FCM in production',
   );
   print('Notification sent: $success');
   ```

## 🚀 Deployment Checklist

- [ ] Backend token service deployed and secured
- [ ] Service account JSON stored securely (not in app)
- [ ] Flutter app updated with production endpoints
- [ ] FCM topics configured in Firebase Console
- [ ] Users subscribed to appropriate topics
- [ ] Firestore security rules updated
- [ ] Environment variables configured
- [ ] HTTPS enabled on all endpoints
- [ ] Rate limiting implemented
- [ ] Monitoring and logging set up

## 📊 Monitoring

### Step 8: Set up Monitoring

1. **Firebase Analytics** - Track notification performance
2. **Cloud Logging** - Monitor token generation and FCM calls
3. **Error Reporting** - Track and fix issues quickly
4. **Performance Monitoring** - Ensure fast response times

## 🔄 Maintenance

### Regular Tasks:
- Monitor token expiration and refresh
- Review notification analytics
- Update security rules as needed
- Rotate service account keys periodically
- Monitor for failed notification deliveries

---

## 🆘 Troubleshooting

### Common Issues:

1. **Token Generation Fails**:
   - Check service account permissions
   - Verify JSON file format
   - Check network connectivity

2. **Notifications Not Received**:
   - Verify topic subscriptions
   - Check FCM token validity
   - Review Firebase Console logs

3. **Authentication Errors**:
   - Verify admin credentials
   - Check ID token validity
   - Review Firestore security rules

---

## 📞 Support

If you encounter issues:
1. Check Firebase Console logs
2. Review backend service logs
3. Test with Firebase Messaging test tool
4. Verify all configuration steps

**Your implementation is now production-ready and secure!** 🎉
