/*
 * Copyright (C) 2026 The ReBoard Contributors
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

package com.noxquill.rewordium.keyboard.app.settings.stickerstudio

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.GifBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.noxquill.rewordium.keyboard.app.LocalNavController
import com.noxquill.rewordium.keyboard.app.Routes
import com.noxquill.rewordium.keyboard.ime.media.sticker.UserStickerStore
import com.noxquill.rewordium.keyboard.lib.compose.FlorisScreen
import com.noxquill.rewordium.keyboard.lib.compose.HeroCard
import com.noxquill.rewordium.keyboard.lib.compose.SectionHeader
import com.noxquill.rewordium.keyboard.lib.compose.SettingItem
import com.noxquill.rewordium.keyboard.lib.compose.SettingsGroup

/**
 * Sticker Studio landing screen — three actions:
 *
 *  - My Stickers   → grid of [UserStickerStore.entriesFlow]
 *  - Create new    → blank editor canvas (gallery / camera picker chooses
 *                    the base image; "blank" starts with a transparent
 *                    512x512 canvas the user fills with overlays)
 *  - Browse premade → bundled Microsoft Fluent UI Emoji pack
 *  - Import GIF    → animated WebP flow (ffmpeg-kit)
 *
 * All flows write through [UserStickerStore.import] so the IME's sticker
 * panel picks them up via its existing StateFlow collector without any
 * IPC or broadcast plumbing.
 */
@Composable
fun StickerStudioScreen() = FlorisScreen {
    title = "Sticker Studio"
    previewFieldVisible = false

    val navController = LocalNavController.current
    val context = LocalContext.current

    content {
        val store = remember { UserStickerStore.get(context) }
        val entries by store.entriesFlow.collectAsState()

        HeroCard(
            title = "Make your own stickers",
            subtitle = "${entries.size} sticker${if (entries.size == 1) "" else "s"} so far. Long-press in the keyboard to favorite.",
            icon = Icons.Outlined.Brush,
        )

        SectionHeader(title = "Create")

        SettingsGroup {
            SettingItem(
                title = "New sticker",
                subtitle = "Crop, cut out, draw, add text",
                icon = Icons.Outlined.AddCircleOutline,
                iconTint = MaterialTheme.colorScheme.primary,
                onClick = {
                    navController.navigate(Routes.Settings.StickerEditor(sourceUri = null, gifMode = true))
                },
            )
            SettingItem(
                title = "Import animated GIF",
                subtitle = "Convert a GIF to an animated sticker",
                icon = Icons.Outlined.GifBox,
                iconTint = MaterialTheme.colorScheme.tertiary,
                showDivider = false,
                onClick = {
                    navController.navigate(Routes.Settings.StickerEditor(sourceUri = null, gifMode = true))
                },
            )
        }

        SectionHeader(title = "Library")

        SettingsGroup {
            SettingItem(
                title = "My stickers",
                subtitle = "${entries.size} saved",
                icon = Icons.Outlined.Collections,
                iconTint = MaterialTheme.colorScheme.secondary,
                onClick = { navController.navigate(Routes.Settings.MyStickers) },
            )
            SettingItem(
                title = "Browse premade",
                subtitle = "Fluent UI Emoji • free to use",
                icon = Icons.Outlined.AutoAwesome,
                iconTint = MaterialTheme.colorScheme.primary,
                showDivider = false,
                onClick = { navController.navigate(Routes.Settings.PremadeLibrary) },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
