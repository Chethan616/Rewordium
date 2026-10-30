import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
    id("com.google.firebase.appdistribution")
    id("kotlin-android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("key.properties")
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

// --- CORRECT KOTLIN SYNTAX FOR READING LOCAL PROPERTIES ---
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

// One version for the app and the keyboard module: gradle.properties (local.properties can override it).
val projectVersionCode: String = localProperties.getProperty("projectVersionCode") ?: providers.gradleProperty("projectVersionCode").get()
val projectVersionName: String = localProperties.getProperty("projectVersionName") ?: providers.gradleProperty("projectVersionName").get()

// A helper function to get other properties (like your API key)
fun getProperty(key: String): String {
    return localProperties.getProperty(key) ?: ""
}

// Function to get FIREBASE_API_KEY from multiple sources
fun getFirebaseApiKey(): String {
    val localValue = localProperties.getProperty("FIREBASE_API_KEY")
    if (!localValue.isNullOrBlank()) return localValue
    
    val envFile = rootProject.file("../.env")
    if (envFile.exists()) {
        envFile.readLines().forEach { line ->
            if (line.startsWith("FIREBASE_API_KEY=")) {
                return line.substringAfter("=").trim()
            }
        }
    }
    
    val envVar = System.getenv("FIREBASE_API_KEY")
    if (!envVar.isNullOrBlank()) return envVar
    
    return ""
}

// Function to get GROQ_API_KEY from multiple sources (matching keyboard module logic)
fun getGroqApiKey(): String {
    // Try local.properties first
    val localValue = localProperties.getProperty("GROQ_API_KEY")
    if (!localValue.isNullOrBlank()) return localValue
    
    // Try .env file in parent directory
    val envFile = rootProject.file("../.env")
    if (envFile.exists()) {
        envFile.readLines().forEach { line ->
            if (line.startsWith("GROQ_API_KEY=")) {
                return line.substringAfter("=").trim()
            }
        }
    }
    
    // Try environment variable
    val envVar = System.getenv("GROQ_API_KEY")
    if (!envVar.isNullOrBlank()) return envVar
    
    // Return empty string if not found
    return ""
}
// --- END OF CORRECTION ---

// Auto-inject local .env FIREBASE_API_KEY into google-services.json at build time if redacted
val localFirebaseKey = getFirebaseApiKey()
if (localFirebaseKey.isNotBlank()) {
    val gsFile = file("google-services.json")
    if (gsFile.exists()) {
        val gsText = gsFile.readText()
        if (gsText.contains("FIREBASE_API_KEY_REDACTED")) {
            gsFile.writeText(gsText.replace("FIREBASE_API_KEY_REDACTED", localFirebaseKey))
        }
    }
}

android {
    namespace = "com.noxquill.rewordium"
    compileSdk = 37
    ndkVersion = "28.2.13676358" // NDK r28 with 16 KB page size support

    compileOptions {
        // Flag to enable support for the new language APIs
        isCoreLibraryDesugaringEnabled = true
        // Sets Java compatibility to Java 17
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
    }

    buildFeatures {
        buildConfig = true
        resValues = true
        compose = true // ENABLE JETPACK COMPOSE
    }

    packagingOptions {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/license.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/notice.txt"
            excludes += "META-INF/ASL2.0"
        }
        // 16 KB page size support - ensure uncompressed native libraries with proper alignment
        jniLibs {
            useLegacyPackaging = false // Use uncompressed native libs for 16 KB support
        }
    }

    // Configure page alignment for 16 KB support
    androidResources {
        noCompress += ""  // Forces proper alignment
    }

    defaultConfig {
        // A new listing: Rewordium – Smart Keyboard (paid). The old app, com.noxquill.rewordium, lives on main.
        applicationId = "com.chethan616.keyboard"
        minSdk = 26
        targetSdk = 36
        versionCode = projectVersionCode.toInt()
        versionName = projectVersionName
        vectorDrawables.useSupportLibrary = true

        multiDexEnabled = true

        ndk {
            debugSymbolLevel = "SYMBOL_TABLE"
        }

        resValue("integer", "google_play_services_version", "12451000")

        // --- CORRECT KOTLIN SYNTAX FOR BUILDFIELD ---
        buildConfigField("String", "GROQ_API_KEY", "\"${getGroqApiKey()}\"")
        // The admin panel's device lock (a salted PBKDF2 hash, never the password). Kept in local.properties or
        // the environment so it isn't in the public repo; blank leaves the admin panel locked.
        buildConfigField("String", "ADMIN_PASSWORD_SALT", "\"${localProperties.getProperty("ADMIN_PASSWORD_SALT") ?: System.getenv("ADMIN_PASSWORD_SALT") ?: ""}\"")
        buildConfigField("String", "ADMIN_PASSWORD_HASH", "\"${localProperties.getProperty("ADMIN_PASSWORD_HASH") ?: System.getenv("ADMIN_PASSWORD_HASH") ?: ""}\"")
    }

    signingConfigs {
        create("release") {
            // Priority: Environment Variables (CI) -> key.properties (Local) -> Defaults
            keyAlias = System.getenv("KEY_ALIAS") ?: keystoreProperties.getProperty("keyAlias") ?: "upload"
            keyPassword = System.getenv("KEY_PASSWORD") ?: keystoreProperties.getProperty("keyPassword") ?: "YOUR_KEY_PASSWORD"
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: keystoreProperties.getProperty("storePassword") ?: "YOUR_STORE_PASSWORD"
            
            val keystorePath = if (System.getenv("KEY_ALIAS") != null) "keystore.jks" else (keystoreProperties.getProperty("storeFile") ?: "upload-keystore.jks")
            storeFile = file(keystorePath)
        }
    }

    // Enable ABI splits to reduce download size
    bundle {
        abi {
            enableSplit = true
        }
        density {
            enableSplit = true
        }
        language {
            enableSplit = true
        }
    }

    // Disable lint errors blocking release build
    lint {
        abortOnError = false
        checkReleaseBuilds = false
        disable += setOf("InvalidPackage", "MissingTranslation")
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true // Remove unused resources
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            
            // 16 KB page size support - ensure proper alignment
            packagingOptions {
                jniLibs {
                    useLegacyPackaging = false
                }
            }
        }
        
        debug {
            // 16 KB page size support for debug builds
            packagingOptions {
                jniLibs {
                    useLegacyPackaging = false
                }
            }
        }
    }
}

dependencies {
    // Your dependencies were duplicated, so I have cleaned them up.
    // This is the complete, correct set.
    
    // Core & Coroutines
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-common:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.savedstate:savedstate:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.22") // Make sure you have this

    // UI Components
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.12.0")

    // Lottie & GIF
    implementation("com.airbnb.android:lottie:6.4.0")
    implementation("pl.droidsonroids.gif:android-gif-drawable:1.2.29")

    // Retrofit for Network Calls
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Core library desugaring for Java 8+ APIs
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
    
    // ========== JETPACK COMPOSE ==========
    // Compose BOM (Bill of Materials) - manages Compose versions
    implementation(platform("androidx.compose:compose-bom-beta:2026.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.12.1")
    implementation("androidx.compose.runtime:runtime")
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.splashscreen)

    debugImplementation("androidx.compose.ui:ui-tooling")
    // ========== END COMPOSE DEPENDENCIES ==========

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:34.10.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-inappmessaging-display")
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("com.android.billingclient:billing:8.3.0")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    
    // Google Play In-App Updates (replaces deprecated Play Core)
    implementation("com.google.android.play:app-update:2.1.0")
    implementation("com.google.android.play:app-update-ktx:2.1.0")
    
    // Google Play In-App Review
    implementation("com.google.android.play:review:2.0.1")
    implementation("com.google.android.play:review-ktx:2.0.1")
    
    // Google Play Integrity API
    implementation("com.google.android.play:integrity:1.4.0")
    
    // ========== REBOARD KEYBOARD MODULE ==========
    implementation(project(":reboard_keyboard"))
    // Seed-colour schemes (materialkolor) for the app's expressive palettes.
    implementation(project(":reboard_lib:color"))

    // ========== ML KIT TEXT RECOGNITION LANGUAGE PACKS ==========
    // Script packs for native document text recognition:
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-devanagari:16.0.1")
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")
}
