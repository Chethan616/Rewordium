# App Update Guide

## 🚀 Force Update System for Rewordium

This guide explains how to manage app updates for users in your closed testing environment using Firebase Firestore.

---

## 📋 Quick Reference

### Current App Version
- **Version**: 1.0.3+3 (from pubspec.yaml)
- **Status**: Force update system implemented and ready

### Firebase Structure
- **Collection**: `app_config`
- **Document**: `version_control`
- **Location**: Firebase Console → Firestore Database

---

## 🎯 Update Scenarios

### 1. **No Update Required** (Current State)
```json
{
  "minimum_version": "1.0.3",
  "latest_version": "1.0.3",
  "force_update": false
}
```
**Result**: No update prompts shown to users

### 2. **Optional Update** (Recommended First)
```json
{
  "minimum_version": "1.0.3",
  "latest_version": "1.0.4", 
  "latest_build_number": "4",
  "force_update": false
}
```
**Result**: Users see "Update Available" dialog but can choose "Later"

### 3. **Force Update** (Blocks App Usage)
```json
{
  "minimum_version": "1.0.4",
  "minimum_build_number": "4",
  "force_update": false
}
```
**Result**: Users cannot use app until they update

### 4. **Emergency Force Update** (Immediate)
```json
{
  "force_update": true
}
```
**Result**: All users forced to update regardless of version

---

## 🛠️ Step-by-Step Update Process

### Phase 1: Prepare New Version
1. **Update pubspec.yaml**:
   ```yaml
   version: 1.0.4+4
   ```

2. **Build and Upload**:
   ```bash
   flutter build appbundle --release
   ```

3. **Upload to Play Console**:
   - Go to Play Console → Rewordium
   - Upload to Closed Testing track
   - Wait for processing (usually 2-4 hours)

### Phase 2: Gradual Rollout (Recommended)

#### Week 1: Optional Update
1. Go to Firebase Console → Firestore → `app_config` → `version_control`
2. Edit these fields:
   ```
   latest_version: "1.0.4"
   latest_build_number: "4"
   update_message: "New version available with improved features!"
   ```

#### Week 2: Force Update
1. Edit these fields:
   ```
   minimum_version: "1.0.4"
   minimum_build_number: "4"
   update_message: "Please update to continue using the app."
   ```

### Phase 3: Monitor and Adjust
- Check user adoption rates
- Monitor for any issues
- Adjust messaging as needed

---

## 🎛️ Firebase Console Controls

### Access Firebase
1. Go to [Firebase Console](https://console.firebase.google.com)
2. Select **Rewordium** project
3. Click **Firestore Database**
4. Navigate to `app_config` → `version_control`

### Edit Update Settings
1. Click on the field you want to edit
2. Click the **pencil icon**
3. Change the value
4. Click **checkmark** to save
5. Changes take effect immediately

---

## 📱 User Experience Flow

### Force Update Flow
1. User opens app (v1.0.3)
2. App checks Firebase for version requirements
3. Sees minimum_version is 1.0.4
4. Shows blocking dialog: "Update Required"
5. User taps "Update Now"
6. Redirected to Play Store closed testing
7. Downloads and installs v1.0.4
8. App works normally

### Optional Update Flow
1. User opens app (v1.0.3)
2. App checks Firebase for version requirements
3. Sees latest_version is 1.0.4
4. Shows dismissible dialog: "Update Available"
5. User can choose:
   - "Update" → Goes to Play Store
   - "Later" → Continues using app

---

## ⚙️ Configuration Fields Reference

| Field | Type | Purpose | Example |
|-------|------|---------|---------|
| `minimum_version` | string | Force update threshold | `"1.0.4"` |
| `latest_version` | string | Optional update threshold | `"1.0.4"` |
| `minimum_build_number` | string | Force update build | `"4"` |
| `latest_build_number` | string | Optional update build | `"4"` |
| `force_update` | boolean | Emergency force update | `true` |
| `update_message` | string | Dialog message | `"Please update..."` |
| `play_store_url` | string | Direct Play Store link | `"https://play.google.com/store/apps/details?id=com.noxquill.rewordium"` |
| `force_update_title` | string | Force dialog title | `"Update Required"` |
| `optional_update_title` | string | Optional dialog title | `"Update Available"` |

---

## 🧪 Testing Updates

### Test Force Update
1. Ensure you have v1.0.3 installed
2. Set `minimum_version: "1.0.4"` in Firebase
3. Open app → Should see force update dialog
4. Set back to `minimum_version: "1.0.3"` to disable

### Test Optional Update
1. Ensure you have v1.0.3 installed
2. Set `latest_version: "1.0.4"` in Firebase
3. Open app → Should see optional update dialog
4. Can dismiss with "Later" button

### Test Manual Update Check
1. Open app → Settings → App Information
2. Tap "Check for Updates"
3. Should show current status

---

## 🚨 Emergency Procedures

### Rollback Update Requirement
If you need to quickly disable force updates:

1. Go to Firebase immediately
2. Set `force_update: false`
3. Set `minimum_version: "1.0.3"`
4. Changes take effect within 1 hour

### Critical Bug Fix
For emergency updates:

1. Set `force_update: true` in Firebase
2. Update `update_message` to explain urgency
3. All users will be forced to update immediately

---

## 📊 Monitoring

### Check Update Adoption
- Monitor Play Console analytics
- Check Firebase Firestore access logs
- Monitor app crash reports

### User Feedback
- Monitor app reviews for update issues
- Check support channels for update problems
- Adjust messaging based on feedback

---

## 🔧 Troubleshooting

### Users Not Seeing Updates
- Check Firebase document exists
- Verify field names are correct
- Check app has internet connection
- Verify minimum_version is higher than user's version

### Update Dialog Not Appearing
- Check ForceUpdateService is initialized in main.dart
- Verify Firebase rules allow read access
- Check app logs for Firebase connection errors

### Play Store Link Not Working
- Verify `play_store_url` is correct
- Check user has access to closed testing
- Ensure Play Store app is installed

---

## 📝 Update Checklist

### Before Each Update
- [ ] Update pubspec.yaml version
- [ ] Test new version thoroughly
- [ ] Upload to Play Console closed testing
- [ ] Wait for Play Console processing
- [ ] Prepare Firebase update message

### During Rollout
- [ ] Start with optional update
- [ ] Monitor adoption rates
- [ ] Check for user feedback
- [ ] Switch to force update if needed

### After Update
- [ ] Monitor crash reports
- [ ] Check user reviews
- [ ] Document lessons learned
- [ ] Plan next update cycle

---

## 📞 Quick Actions

### Enable Optional Update
```json
{
  "latest_version": "1.0.4",
  "latest_build_number": "4"
}
```

### Enable Force Update
```json
{
  "minimum_version": "1.0.4",
  "minimum_build_number": "4"
}
```

### Emergency Force Update
```json
{
  "force_update": true,
  "update_message": "Critical security update required."
}
```

### Disable All Updates
```json
{
  "minimum_version": "1.0.3",
  "latest_version": "1.0.3",
  "force_update": false
}
```

---

## 🎯 Success Metrics

### Target Adoption Rates
- **Week 1 (Optional)**: 60% adoption
- **Week 2 (Force)**: 95% adoption
- **Week 3**: 99% adoption

### Monitor These Metrics
- Update dialog show rate
- Update completion rate
- App store redirect rate
- Post-update crash rate

---

*Last Updated: July 21, 2025*
*System Status: ✅ Active and Ready*
