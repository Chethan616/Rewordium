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

package com.noxquill.rewordium.keyboard

import android.content.Context
import android.content.pm.PackageManager

/**
 * Helper object to provide app info values that would normally come from BuildConfig.
 * Since this is a library module, we need to get these values at runtime.
 */
object ReboardInfo {
    // Default values (will be overwritten at runtime if possible)
    var VERSION_NAME: String = "3.0.1"
        private set
    var VERSION_CODE: Int = 121
        private set
    var APPLICATION_ID: String = "com.noxquill.rewordium"
        private set

    /**
     * Initialize the app info from the host application context.
     * Should be called once during app startup.
     */
    fun init(context: Context) {
        try {
            val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            VERSION_NAME = packageInfo.versionName ?: VERSION_NAME
            VERSION_CODE = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
            APPLICATION_ID = context.packageName
        } catch (e: Exception) {
            // Keep default values if we can't get package info
        }
    }
}
