# 🔑 Firebase Service Account Setup Guide

## Where to Put Your New Service Account Key

### 1️⃣ Generate New Key from Firebase Console

1. Go to: https://console.firebase.google.com/project/rewordium/settings/serviceaccounts/adminsdk
2. Click **"Generate new private key"**
3. Click **"Generate key"** in the popup
4. A JSON file will download (e.g., `rewordium-abc123.json`)

---

### 2️⃣ Save the Key Locally (DO NOT COMMIT)

Save the downloaded JSON file to:

```
C:\Users\ChethanKrishna\Desktop\YC_startup-main\config\service-account-key.json
```

✅ This path is already in `.gitignore`, so it won't be committed to git.

---

### 3️⃣ How Each Part of Your App Uses the Key

#### **A) Cloud Functions** (`functions/src/index.ts`)
- ✅ **Already configured** to use Application Default Credentials
- When deployed to Firebase, it automatically uses the project's default credentials
- For local testing, set environment variable:
  ```powershell
  $env:GOOGLE_APPLICATION_CREDENTIALS="C:\Users\ChethanKrishna\Desktop\YC_startup-main\config\service-account-key.json"
  cd functions
  npm run serve
  ```

#### **B) Node.js Server** (`server/index.js` and `server/index-port8080.js`)
- ✅ **Already configured** to load from `../config/service-account-key.json`
- Just place your key at the path above and the server will automatically load it
- To run:
  ```powershell
  cd server
  npm install  # if not already done
  node index.js
  ```

---

### 4️⃣ Delete Old/Exposed Keys from Firebase Console

**CRITICAL:** Delete the old compromised keys to prevent unauthorized access:

1. Go to: https://console.cloud.google.com/iam-admin/serviceaccounts?project=rewordium
2. Find: **firebase-adminsdk-fbsvc@rewordium.iam.gserviceaccount.com**
3. Click it → **"Keys"** tab
4. Find these old key IDs and delete them:
   - `4a89181f09b0ade44b2113b890c281eb22384226`
   - `3b96bdeb3aa39c8604ca1648ae1ae7487a4e0cc1`
5. Click the three-dot menu → **"Delete"**

---

### 5️⃣ Test Everything Works

```powershell
# Test Flutter app (local)
flutter clean
flutter pub get
flutter run

# Test Node.js server (local)
cd server
node index.js

# Test Cloud Functions (local emulator)
cd functions
npm run serve
```

---

### 6️⃣ Deploy to Production

```powershell
# Deploy Cloud Functions
cd functions
firebase deploy --only functions

# The service account is automatically used in production
# No need to manually configure credentials when deployed
```

---

## ✅ Summary

| Component | Key Location | Configuration |
|-----------|-------------|---------------|
| **Cloud Functions** | Automatic (ADC) | ✅ No manual config needed when deployed |
| **Node.js Server** | `config/service-account-key.json` | ✅ Already configured |
| **Local Testing** | Set `GOOGLE_APPLICATION_CREDENTIALS` env var | Optional |

---

## 🚨 Security Reminders

- ✅ Never commit `config/service-account-key.json` (already in `.gitignore`)
- ✅ Delete old exposed keys from Firebase Console
- ✅ Rotate keys if they're ever accidentally exposed again
- ✅ Use environment variables for CI/CD (GitHub Actions secrets)

---

## Need Help?

If you get authentication errors:
1. Make sure the key file is at: `config/service-account-key.json`
2. Make sure you deleted the old keys from Firebase Console
3. Try restarting your server/app after placing the new key

