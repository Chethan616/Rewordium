pluginManagement {
    val flutterSdkPath = run {
        val properties = java.util.Properties()
        val localPropertiesFile = file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { properties.load(it) }
        }
        val sdkPath = properties.getProperty("flutter.sdk") ?: System.getenv("FLUTTER_ROOT")
        require(sdkPath != null) { "flutter.sdk not set in local.properties and FLUTTER_ROOT environment variable not found" }
        sdkPath
    }

    includeBuild("$flutterSdkPath/packages/flutter_tools/gradle")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") } // ADD JitPack for AndroidLiquidGlass
    }
}

plugins {
    id("dev.flutter.flutter-plugin-loader") version "1.0.0"
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false  // For backdrop module
    // START: FlutterFire Configuration
    id("com.google.gms.google-services") version("4.4.2") apply false
    id("com.google.firebase.appdistribution") version "5.0.0" apply false
    // END: FlutterFire Configuration
    id("org.jetbrains.kotlin.android") version "2.1.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.1.21" apply false  // For pure JVM modules
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21" apply false  // For Compose in backdrop
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.21" apply false  // For ReBoard
    id("com.google.devtools.ksp") version "2.1.21-2.0.2" apply false  // For ReBoard Room/KSP
    id("com.mikepenz.aboutlibraries.plugin") version "11.2.3" apply false  // For ReBoard
    id("org.jetbrains.kotlinx.kover") version "0.9.1" apply false  // For ReBoard testing
}

// Configure version catalogs for ReBoard dependencies
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://central.sonatype.com/repository/maven-snapshots/")
    }

    versionCatalogs {
        create("tools") {
            from(files("gradle/tools.versions.toml"))
        }
    }
}

include(":app")

// ReBoard Keyboard Module
include(":reboard_keyboard")
project(":reboard_keyboard").projectDir = file("reboard_keyboard")

// ReBoard Library Modules
include(":reboard_lib:android")
project(":reboard_lib:android").projectDir = file("reboard_lib/android")
include(":reboard_lib:color")
project(":reboard_lib:color").projectDir = file("reboard_lib/color")
include(":reboard_lib:compose")
project(":reboard_lib:compose").projectDir = file("reboard_lib/compose")
include(":reboard_lib:kotlin")
project(":reboard_lib:kotlin").projectDir = file("reboard_lib/kotlin")
include(":reboard_lib:native")
project(":reboard_lib:native").projectDir = file("reboard_lib/native")
include(":reboard_lib:snygg")
project(":reboard_lib:snygg").projectDir = file("reboard_lib/snygg")

// ReBoard Native Library (Rust)
include(":reboard_libnative")
project(":reboard_libnative").projectDir = file("reboard_libnative")
