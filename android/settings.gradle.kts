pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") } // ADD JitPack for AndroidLiquidGlass
    }
}

plugins {
    id("com.android.application") version "9.1.1" apply false
    id("com.android.library") version "9.1.1" apply false
    // Firebase services used by the native app.
    id("com.google.gms.google-services") version("4.4.2") apply false
    id("com.google.firebase.appdistribution") version "5.0.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("org.jetbrains.kotlin.jvm") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10" apply false
    id("com.google.devtools.ksp") version "2.2.10-2.0.2" apply false
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
