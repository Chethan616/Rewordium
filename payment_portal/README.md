# Rewordium Payment Portal

## 🚀 Quick Start

### Start Server
```bash
cd payment_portal
python -m http.server 8080
```

### Access Portal
- **Main Portal**: http://localhost:8080/index.html
- **Firebase Test**: http://localhost:8080/firebase-test.html
- **Auth Diagnostic**: http://localhost:8080/auth-diagnostic.html

## 💳 Switch to Live Payments

Edit `index.html` around line 506:

```javascript
// Change these two lines:
const USE_LIVE_PAYMENTS = true; // Set to true for real money
const RAZORPAY_LIVE_KEY = 'rzp_live_your_actual_key_here'; // Your live key
```

## 🔧 Features

- ✅ Real Firebase Authentication (Google + Email/Password)
- ✅ Razorpay Payment Integration (Test + Live modes)
- ✅ Subscription Status Persistence
- ✅ Firebase Firestore Database Integration
- ✅ Flutter App Compatible User Structure

## 📁 Files

- `index.html` - Main payment portal
- `firebase-test.html` - Database connection testing
- `auth-diagnostic.html` - Authentication troubleshooting
