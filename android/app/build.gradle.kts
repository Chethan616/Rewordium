import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
    id("com.google.firebase.appdistribution")
    id("kotlin-android")
    id("dev.flutter.flutter-gradle-plugin")
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

// This part is for Flutter versioning, and it's already in correct Kotlin syntax.
val flutterVersionCode: String = localProperties.getProperty("flutter.versionCode") ?: "1"
val flutterVersionName: String = localProperties.getProperty("flutter.versionName") ?: "1.0"

// A helper function to get other properties (like your API key)
fun getProperty(key: String): String {
    return localProperties.getProperty(key) ?: ""
}
// --- END OF CORRECTION ---

android {
    namespace = "com.noxquill.rewordium"
    compileSdk = 36
    ndkVersion = "28.0.12433566" // Updated to r28 for 16 KB support

    compileOptions {
        // Flag to enable support for the new language APIs
        isCoreLibraryDesugaringEnabled = true
        // Sets Java compatibility to Java 11
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }

    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
    }

    buildFeatures {
        buildConfig = true
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
        // 16 KB page size support - ensure uncompressed native libraries
        jniLibs {
            useLegacyPackaging = false // Use uncompressed native libs for 16 KB support
        }
    }

    defaultConfig {
        applicationId = "com.noxquill.rewordium"
        minSdk = 24
        targetSdk = 35
        versionCode = flutterVersionCode.toInt()
        versionName = flutterVersionName
        vectorDrawables.useSupportLibrary = true

        multiDexEnabled = true

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64"))
            // Enable 16 KB page size support
            debugSymbolLevel = "SYMBOL_TABLE"
        }

        resValue("integer", "google_play_services_version", "12451000")

        // --- CORRECT KOTLIN SYNTAX FOR BUILDFIELD ---
        buildConfigField("String", "GROQ_API_KEY", "\"${getProperty("GROQ_API_KEY")}\"")
    }

    signingConfigs {
        create("release") {
            keyAlias = "upload"
            keyPassword = "noxquill2025"
            storeFile = file("upload-keystore.jks")
            storePassword = "noxquill2025"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
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

flutter {
    source = "../.."
}

dependencies {
    // Your dependencies were duplicated, so I have cleaned them up.
    // This is the complete, correct set.
    
    // Core & Coroutines
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
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
    implementation("pl.droidsonroids.gif:android-gif-drawable:1.2.28")

    // Retrofit for Network Calls
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Core library desugaring for Java 8+ APIs
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-messaging:24.0.0")
    
    // Google Play In-App Updates (replaces deprecated Play Core)
    implementation("com.google.android.play:app-update:2.1.0")
    implementation("com.google.android.play:app-update-ktx:2.1.0")
}