import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * Copyright (C) 2025 The ReBoard Contributors
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

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val projectMinSdk: String by project
val projectCompileSdk: String by project

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

android {
    namespace = "org.florisboard.libnative"
    compileSdk = projectCompileSdk.toInt()
    ndkVersion = tools.versions.ndk.get()

    defaultConfig {
        minSdk = projectMinSdk.toInt()

        externalNativeBuild {
            cmake {
                targets("fl_native")
                arguments(
                    "-DCMAKE_ANDROID_API=$minSdk",
                    "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384",
                )
            }
        }

        ndk {
            // Only build arm64-v8a for 16 KB page size support
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
            )
        }
        create("beta") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    sourceSets {
        maybeCreate("main").apply {
            java {
                srcDirs("src/main/kotlin")
            }
        }
    }

    externalNativeBuild {
        cmake {
            version = tools.versions.cmake.get()
            path("src/main/rust/CMakeLists.txt")
        }
    }
    
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    packagingOptions {
        jniLibs {
            useLegacyPackaging = false  // 16 KB page size support
            // Exclude 32-bit and x86_64 architectures - only keep arm64-v8a for 16KB support
            excludes += listOf("**/armeabi-v7a/*.so", "**/x86/*.so", "**/x86_64/*.so")
        }
    }
}

tasks.named("clean") {
    doLast {
        delete("src/main/rust/target")
    }
}

dependencies {
    // none
}
