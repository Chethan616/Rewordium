/*
 * Copyright (C) 2022-2025 The ReBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.mikepenz.aboutlibraries.plugin")
}

// Function to get property from local.properties, .env, or environment
fun getApiKey(envName: String): String {
    // Try local.properties first
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        val props = Properties()
        props.load(localPropertiesFile.inputStream())
        val value = props.getProperty(envName)
        if (!value.isNullOrBlank()) return value
    }
    
    // Try .env file in parent directory
    val envFile = rootProject.file("../.env")
    if (envFile.exists()) {
        envFile.readLines().forEach { line ->
            if (line.startsWith("$envName=")) {
                return line.substringAfter("=").trim()
            }
        }
    }
    
    // Try environment variable
    val envVar = System.getenv(envName)
    if (!envVar.isNullOrBlank()) return envVar
    
    // Return empty string if not found
    return ""
}

val projectMinSdk: String by project
val projectTargetSdk: String by project
val projectCompileSdk: String by project
val projectVersionCode: String by project
val projectVersionName: String by project
val projectVersionNameSuffix = projectVersionName.substringAfter("-", "").let { suffix ->
    if (suffix.isNotEmpty()) {
        "-$suffix"
    } else {
        suffix
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        freeCompilerArgs.set(listOf(
            "-opt-in=kotlin.contracts.ExperimentalContracts",
            "-Xjvm-default=all-compatibility",
            "-Xwhen-guards",
        ))
    }
}

android {
    namespace = "com.noxquill.rewordium.keyboard"
    compileSdk = projectCompileSdk.toInt()
    buildToolsVersion = tools.versions.buildTools.get()
    ndkVersion = tools.versions.ndk.get()

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
        arg("room.expandProjection", "true")
    }

    defaultConfig {
        minSdk = projectMinSdk.toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        consumerProguardFiles("proguard-rules.pro")

        // 16 KB page size support - ARM64 only
        ndk {
            abiFilters.addAll(listOf("arm64-v8a"))
            debugSymbolLevel = "SYMBOL_TABLE"
        }

        // Native suggester / gesture decoder lives under src/main/cpp/. Phase 1
        // ships a minimal JNI smoke-test surface; phase 2 layers AOSP LatinIME
        // decoder sources on top of the same library.
        externalNativeBuild {
            cmake {
                targets("rewordium_latinime")
                arguments(
                    "-DCMAKE_ANDROID_API=$minSdk",
                    "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384",
                )
            }
        }

        buildConfigField("String", "BUILD_COMMIT_HASH", "\"${getGitCommitHash().get()}\"")
        buildConfigField("String", "FLADDONS_API_VERSION", "\"v~draft2\"")
        buildConfigField("String", "FLADDONS_STORE_URL", "\"www.rewordium.tech/extensions\"")
        buildConfigField("String", "GROQ_API_KEY", "\"${getApiKey("GROQ_API_KEY")}\"")

        // Feature flags — toggle here for gradual rollout; true = enabled in all builds.
        buildConfigField("boolean", "ENABLE_PATH_SMOOTHER", "true")
        buildConfigField("boolean", "ENABLE_TRIGRAM_MODEL", "false")
        buildConfigField("boolean", "ENABLE_TFLITE_RERANKER", "false")
        buildConfigField("boolean", "ENABLE_STREAMING_AI", "false")
        buildConfigField("boolean", "ENABLE_INLINE_COMPLETIONS", "false")
        buildConfigField("boolean", "ENABLE_SMART_REPLIES", "false")
        // Gesture polish cycle (cycle 2)
        buildConfigField("boolean", "ENABLE_VELOCITY_AWARE_GESTURE", "true")
        buildConfigField("boolean", "ENABLE_PARTIAL_GESTURE_PREDICTIONS", "true")
        buildConfigField("boolean", "ENABLE_BEAM_SEARCH_GESTURES", "false")
        buildConfigField("boolean", "ENABLE_ADAPTIVE_RESAMPLE", "false")
        // Gesture polish cycle (cycle 3) — fix "hello → hell" and long-word matching
        buildConfigField("boolean", "ENABLE_GESTURE_ENDPOINT_TOLERANCE", "true")
        // Prefix-bias retired in cycle 4: it caused "tomorrow → tomorrow's". The new
        // length-match bonus (LENGTH_MATCH_MAX_BONUS) is the principled replacement.
        buildConfigField("boolean", "ENABLE_GESTURE_PREFIX_BIAS", "false")
        buildConfigField("boolean", "ENABLE_GESTURE_LENGTH_ASYMMETRY", "true")
        // Native-engine rollout (Phase 8).
        //
        // ENABLE_NATIVE_SUGGESTER = true → tap-input suggestions go through
        //   AOSP's typing Suggest pipeline (typing policy is real and shipped
        //   in open source). Working.
        //
        // ENABLE_NATIVE_GLIDE     = false → gesture decoding stays on the
        //   Kotlin StatisticalGlideTypingClassifier. AOSP open-source ships
        //   GestureSuggestPolicyFactory as an UNREGISTERED stub (Google kept
        //   the real gesture decoder proprietary), so routing gestures
        //   through native crashes with a null SuggestPolicy deref. The
        //   crash itself is defended against by JNI_OnLoad in
        //   latinime_jni.cpp registering the typing policy as a fallback,
        //   but typing-as-gesture produces garbage results — Statistical is
        //   the actually-working option. Flip back to true only after
        //   wiring a real gesture suggest policy (substantial undertaking).
        buildConfigField("boolean", "ENABLE_NATIVE_SUGGESTER", "true")
        buildConfigField("boolean", "ENABLE_NATIVE_GLIDE", "false")
        // Phase 7: when true, MediaInputLayout uses the androidx.emoji2
        // emojipicker-based NativeEmojiPanel instead of the legacy hand-
        // rolled EmojiPaletteView.
        buildConfigField("boolean", "ENABLE_NATIVE_EMOJI_PANEL", "true")
        // Phase 7r: when true, MediaInputLayout uses the Gboard-style
        // custom Compose panel (GboardEmojiPanel) — header with back +
        // search pill + category icons, paged grid, compact 6-slot
        // bottom bar. Wins over ENABLE_NATIVE_EMOJI_PANEL when both on.
        buildConfigField("boolean", "ENABLE_GBOARD_EMOJI_PANEL", "true")

        // KLIPY API key for the GIF panel. KLIPY is the post-Tenor successor
        // (Tenor sunsetting 2026-06-30, new keys closed 2026-01-13); free
        // lifetime tier; sign up at klipy.com/developers and create an app.
        // Empty default means the GIF tab renders an "API key not configured"
        // hint instead of crashing — useful for local builds without keys.
        // Override per-build via a -PklipyApiKey=... gradle property or set
        // it in your local.properties or .env file (do NOT commit a real key).
        val klipyKey = (findProperty("klipyApiKey") as String?)?.takeIf { it.isNotBlank() } 
            ?: getApiKey("KLIPY_API_KEY")
        buildConfigField("String", "KLIPY_API_KEY", "\"$klipyKey\"")

        sourceSets {
            maybeCreate("main").apply {
                assets {
                    srcDirs("src/main/assets")
                }
                java {
                    srcDirs("src/main/kotlin")
                }
            }
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    externalNativeBuild {
        cmake {
            version = tools.versions.cmake.get()
            path("src/main/cpp/CMakeLists.txt")
        }
    }

    // 16 KB page size support - ensure uncompressed native libraries
    packagingOptions {
        jniLibs {
            useLegacyPackaging = false
            // Exclude 32-bit and x86_64 architectures - only keep arm64-v8a for 16KB support
            excludes += listOf("**/armeabi-v7a/*.so", "**/x86/*.so", "**/x86_64/*.so")
        }
    }

    buildTypes {
        named("debug") {
            isJniDebuggable = false
        }

        create("beta") {
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            isMinifyEnabled = true
        }

        named("release") {
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            isMinifyEnabled = true
        }
    }

    aboutLibraries {
        configPath = "src/main/config"
    }

    lint {
        baseline = file("lint.xml")
        abortOnError = false
        checkReleaseBuilds = false
        disable += setOf("InvalidPackage", "MissingTranslation", "Instantiatable")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    // testImplementation(composeBom)
    // androidTestImplementation(composeBom)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.autofill)
    implementation(libs.androidx.collection.ktx)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.emoji2)
    implementation(libs.androidx.emoji2.views)
    // Phase 7: official AOSP/Google emoji picker. Apache 2.0. We host it via
    // Compose AndroidView in NativeEmojiPanel.kt; the original hand-rolled
    // EmojiPaletteView stays in the tree until phase 8 retires it.
    implementation(libs.androidx.emoji2.emojipicker)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.profileinstaller)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.runtime)
    implementation(libs.cache4k)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.mikepenz.aboutlibraries.core)
    implementation(libs.mikepenz.aboutlibraries.compose)
    implementation(libs.patrickgold.compose.tooltip)
    implementation(libs.patrickgold.jetpref.datastore.model)
    ksp(libs.patrickgold.jetpref.datastore.model.processor)
    implementation(libs.patrickgold.jetpref.datastore.ui)
    implementation(libs.patrickgold.jetpref.material.ui)

    implementation(project(":reboard_lib:android"))
    implementation(project(":reboard_lib:color"))
    implementation(project(":reboard_lib:compose"))
    implementation(project(":reboard_lib:kotlin"))
    implementation(project(":reboard_lib:native"))
    implementation(project(":reboard_lib:snygg"))

    // Network dependencies for AI features
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Image loading for the GIF / Sticker panels. coil-compose drives
    // AsyncImage{}; coil-gif decodes animated GIFs (Tenor thumbnails).
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")
    api(platform("com.google.firebase:firebase-bom:34.10.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    testImplementation(libs.kotlin.test.junit5)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.espresso.core)
}

fun getGitCommitHash(short: Boolean = false): Provider<String> {
    if (!File(".git").exists()) {
        return providers.provider { "null" }
    }

    val execProvider = providers.exec {
        if (short) {
            commandLine("git", "rev-parse", "--short", "HEAD")
        } else {
            commandLine("git", "rev-parse", "HEAD")
        }
    }
    return execProvider.standardOutput.asText.map { it.trim() }
}
