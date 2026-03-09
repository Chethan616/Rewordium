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

package com.noxquill.rewordium.keyboard.app.ext

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.noxquill.rewordium.keyboard.R
import com.noxquill.rewordium.keyboard.app.LocalNavController
import com.noxquill.rewordium.keyboard.app.Routes
import com.noxquill.rewordium.keyboard.lib.compose.FlorisScreen
import com.noxquill.rewordium.keyboard.lib.compose.HeroCard
import com.noxquill.rewordium.keyboard.lib.compose.SectionHeader
import com.noxquill.rewordium.keyboard.lib.compose.SettingItem
import com.noxquill.rewordium.keyboard.lib.compose.SettingsGroup
import org.florisboard.lib.compose.stringRes

@Composable
fun ExtensionHomeScreen() = FlorisScreen {
    title = stringRes(R.string.ext__home__title)
    previewFieldVisible = false
    iconSpaceReserved = false

    val navController = LocalNavController.current

    content {
        // Hero Card for Themes
        HeroCard(
            title = "Themes",
            subtitle = "Personalize your keyboard",
            icon = Icons.Default.ColorLens,
            gradientColors = listOf(
                MaterialTheme.colorScheme.secondary,
                MaterialTheme.colorScheme.primary
            ),
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Import Theme Section
        SectionHeader(title = "Import Theme")
        
        ImportExtensionBox(navController)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Select Theme Section
        SectionHeader(title = "Select Theme")
        
        SettingsGroup {
            SettingItem(
                title = "Browse Themes",
                subtitle = "ReBoard Material You themes",
                icon = Icons.Default.Palette,
                iconTint = MaterialTheme.colorScheme.primary,
                showDivider = false,
                onClick = {
                    navController.navigate(Routes.Ext.List(ExtensionListScreenType.EXT_THEME, false))
                },
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}
