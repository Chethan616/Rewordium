/**
 * Firebase Firestore Structure for Force Update
 * 
 * Collection: app_config
 * Document: version_control
 * 
 * Create this document in your Firebase console with the following structure.
 * See firestore_version_config.json for the exact JSON structure to copy.
 */

const versionConfig = {
  "minimum_version": "1.0.3",
  "latest_version": "1.0.4", 
  "minimum_build_number": "3",
  "latest_build_number": "4",
  "force_update": false,
  "update_message": "A new version with improved keyboard functionality is available. Please update to continue using the app.",
  "play_store_url": "https://play.google.com/store/apps/details?id=com.noxquill.rewordium",
  "force_update_title": "Update Required",
  "optional_update_title": "Update Available"
};

/**
 * How to use:
 * 
 * 1. Go to Firebase Console → Firestore Database
 * 2. Create a collection called "app_config"
 * 3. Create a document with ID "version_control"
 * 4. Copy the JSON from firestore_version_config.json and paste into Firebase
 * 
 * Control scenarios:
 * 
 * FORCE UPDATE (blocks app usage):
 * - Set minimum_version to a higher version than current app
 * - OR set force_update: true
 * 
 * OPTIONAL UPDATE (shows update dialog but allows skip):
 * - Set latest_version to a higher version than current app
 * - Keep force_update: false
 * 
 * NO UPDATE:
 * - Keep all versions same or lower than current app
 * - Set force_update: false
 * 
 * For your current app version 1.0.3+3:
 * - To force update: Set minimum_version: "1.0.4" and minimum_build_number: "4"
 * - To show optional update: Set latest_version: "1.0.4" and latest_build_number: "4"
 */

// Export the configuration for use in other scripts if needed
module.exports = versionConfig;
