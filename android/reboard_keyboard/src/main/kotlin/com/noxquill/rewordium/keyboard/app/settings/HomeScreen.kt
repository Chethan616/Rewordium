/*
 * Copyright (C) 2021-2025 The ReBoard Contributors
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

package com.noxquill.rewordium.keyboard.app.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.noxquill.rewordium.keyboard.R
import com.noxquill.rewordium.keyboard.app.LocalNavController
import com.noxquill.rewordium.keyboard.app.Routes
import com.noxquill.rewordium.keyboard.lib.compose.FlorisScreen
import com.noxquill.rewordium.keyboard.lib.compose.HeroCard
import com.noxquill.rewordium.keyboard.lib.compose.SectionHeader
import com.noxquill.rewordium.keyboard.lib.compose.SettingItem
import com.noxquill.rewordium.keyboard.lib.compose.SettingsGroup
import com.noxquill.rewordium.keyboard.lib.compose.StatusCard
import com.noxquill.rewordium.keyboard.lib.util.InputMethodUtils
import org.florisboard.lib.compose.stringRes

@Composable
fun HomeScreen() = FlorisScreen {
    title = stringRes(R.string.settings__home__title)
    navigationIconVisible = false
    previewFieldVisible = true
    iconSpaceReserved = false

    val navController = LocalNavController.current
    val context = LocalContext.current

    content {
        val isFlorisBoardEnabled by InputMethodUtils.observeIsFlorisboardEnabled(foregroundOnly = true)
        val isFlorisBoardSelected by InputMethodUtils.observeIsFlorisboardSelected(foregroundOnly = true)
        
        // Status Cards
        if (!isFlorisBoardEnabled) {
            StatusCard(
                text = stringRes(R.string.settings__home__ime_not_enabled),
                isError = true,
                onClick = { InputMethodUtils.showImeEnablerActivity(context) },
            )
        } else if (!isFlorisBoardSelected) {
            StatusCard(
                text = stringRes(R.string.settings__home__ime_not_selected),
                isError = false,
                onClick = { InputMethodUtils.showImePicker(context) },
            )
        }

        // Navigation Section
        SectionHeader(title = "Navigation")
        
        SettingsGroup {
            SettingItem(
                title = "Go Back",
                subtitle = "Return to the previous screen",
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                iconTint = MaterialTheme.colorScheme.primary,
                showDivider = false,
                onClick = {
                    val popped = navController.popBackStack()
                    if (!popped) {
                        try {
                            val intent = Intent().apply {
                                setClassName("com.noxquill.rewordium", "com.noxquill.rewordium.MainActivity")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Unable to go back", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Hero Card
        HeroCard(
            title = "ReBoard",
            subtitle = "AI-Powered Keyboard",
            icon = Icons.Default.Keyboard,
            gradientColors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.tertiary
            ),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Input & Languages Section
        SectionHeader(title = "Input & Languages")
        
        SettingsGroup {
            SettingItem(
                title = stringRes(R.string.settings__localization__title),
                subtitle = "Manage languages and layouts",
                icon = Icons.Default.Language,
                iconTint = MaterialTheme.colorScheme.primary,
                onClick = { navController.navigate(Routes.Settings.Localization) },
            )
            SettingItem(
                title = stringRes(R.string.settings__typing__title),
                subtitle = "Autocorrect, suggestions, dictionaries",
                icon = Icons.Default.Spellcheck,
                iconTint = MaterialTheme.colorScheme.secondary,
                onClick = { navController.navigate(Routes.Settings.Typing) },
            )
            SettingItem(
                title = stringRes(R.string.settings__gestures__title),
                subtitle = "Swipe actions and glide typing",
                icon = Icons.Default.Gesture,
                iconTint = MaterialTheme.colorScheme.tertiary,
                showDivider = false,
                onClick = { navController.navigate(Routes.Settings.Gestures) },
            )
        }

        // Appearance Section
        SectionHeader(title = "Appearance")
        
        SettingsGroup {
            SettingItem(
                title = stringRes(R.string.settings__theme__title),
                subtitle = "Colors, styles, and dark mode",
                icon = Icons.Outlined.Palette,
                iconTint = MaterialTheme.colorScheme.primary,
                onClick = { navController.navigate(Routes.Settings.Theme) },
            )
            SettingItem(
                title = stringRes(R.string.settings__keyboard__title),
                subtitle = "Layout, sizing, and behavior",
                icon = Icons.Default.Keyboard,
                iconTint = MaterialTheme.colorScheme.secondary,
                onClick = { navController.navigate(Routes.Settings.Keyboard) },
            )
            SettingItem(
                title = stringRes(R.string.settings__smartbar__title),
                subtitle = "Quick actions bar customization",
                icon = Icons.Default.SmartButton,
                iconTint = MaterialTheme.colorScheme.tertiary,
                showDivider = false,
                onClick = { navController.navigate(Routes.Settings.Smartbar) },
            )
        }

        // Features Section
        SectionHeader(title = "Features")
        
        SettingsGroup {
            SettingItem(
                title = stringRes(R.string.settings__clipboard__title),
                subtitle = "Clipboard history and pinned items",
                icon = Icons.AutoMirrored.Outlined.Assignment,
                iconTint = MaterialTheme.colorScheme.primary,
                onClick = { navController.navigate(Routes.Settings.Clipboard) },
            )
            SettingItem(
                title = stringRes(R.string.settings__media__title),
                subtitle = "Emojis, stickers, and GIFs",
                icon = Icons.Default.SentimentSatisfiedAlt,
                iconTint = MaterialTheme.colorScheme.secondary,
                onClick = { navController.navigate(Routes.Settings.Media) },
            )
            SettingItem(
                title = stringRes(R.string.ext__home__title),
                subtitle = "Themes and add-ons",
                icon = Icons.Default.Extension,
                iconTint = MaterialTheme.colorScheme.tertiary,
                showDivider = false,
                onClick = { navController.navigate(Routes.Ext.Home) },
            )
        }

        // Advanced Section
        SectionHeader(title = "Advanced")
        
        SettingsGroup {
            SettingItem(
                title = stringRes(R.string.settings__other__title),
                subtitle = "Developer options and backup",
                icon = Icons.Outlined.Build,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                showDivider = false,
                onClick = { navController.navigate(Routes.Settings.Other) },
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}
