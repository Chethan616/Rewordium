import java.util.Properties

plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
    id("kotlin-android")
    id("dev.flutter.flutter-gradle-plugin")
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
    namespace = "com.example.yc_startup"
    compileSdk = 35
    ndkVersion = "27.0.12077973"

    compileOptions {
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
    }

    defaultConfig {
        applicationId = "com.example.yc_startup"
        minSdk = 21
        targetSdk = 34
        versionCode = flutterVersionCode.toInt()
        versionName = flutterVersionName

        multiDexEnabled = true

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64"))
        }

        resValue("integer", "google_play_services_version", "12451000")

        // --- CORRECT KOTLIN SYNTAX FOR BUILDFIELD ---
        buildConfigField("String", "GROQ_API_KEY", "\"${getProperty("GROQ_API_KEY")}\"")
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    implementation("com.google.android.material:material:1.11.0")

    // Lottie & GIF
    implementation("com.airbnb.android:lottie:6.4.0")
    implementation("pl.droidsonroids.gif:android-gif-drawable:1.2.28")

    // Retrofit for Network Calls
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Firebase (If you have it in your other build file)
    // implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    // implementation("com.google.firebase:firebase-analytics")
}