/*
 * Copyright (C) 2024-2025 The ReBoard Contributors
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

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.noxquill.rewordium.keyboard.R
import com.noxquill.rewordium.keyboard.app.LocalNavController
import com.noxquill.rewordium.keyboard.app.Routes
import com.noxquill.rewordium.keyboard.extensionManager
import com.noxquill.rewordium.keyboard.ime.theme.ThemeExtension
import com.noxquill.rewordium.keyboard.lib.compose.FlorisScreen
import com.noxquill.rewordium.keyboard.lib.ext.Extension
import com.noxquill.rewordium.keyboard.lib.ext.ExtensionManager
import com.noxquill.rewordium.keyboard.lib.observeAsNonNullState
import org.florisboard.lib.compose.FlorisTextButton
import org.florisboard.lib.compose.florisScrollbar
import org.florisboard.lib.compose.stringRes

enum class ExtensionListScreenType(
    val id: String,
    @StringRes val titleResId: Int,
    val getExtensionIndex: (ExtensionManager) -> ExtensionManager.ExtensionIndex<*>,
    val launchExtensionCreate: ((NavController) -> Unit)?,
) {
    EXT_THEME(
        id = "ext-theme",
        titleResId = R.string.ext__list__ext_theme,
        getExtensionIndex = { it.themes },
        launchExtensionCreate = { it.navigate(Routes.Ext.Edit("null", ThemeExtension.SERIAL_TYPE)) },
    ),
    EXT_KEYBOARD(
        id = "ext-keyboard",
        titleResId = R.string.ext__list__ext_keyboard,
        getExtensionIndex = { it.keyboardExtensions },
        launchExtensionCreate = null,
    ),
    EXT_LANGUAGEPACK(
        id = "ext-languagepack",
        titleResId = R.string.ext__list__ext_languagepack,
        getExtensionIndex = { it.languagePacks },
        launchExtensionCreate = null,
    );
}

@Composable
fun ExtensionListScreen(type: ExtensionListScreenType, showUpdate: Boolean) = FlorisScreen {
    title = stringRes(type.titleResId)
    previewFieldVisible = false
    scrollable = false
    iconSpaceReserved = false

    val context = LocalContext.current
    val navController = LocalNavController.current
    val extensionManager by context.extensionManager()
    val extensionIndex by type.getExtensionIndex(extensionManager).observeAsNonNullState()
    
    // All visible themes
    val userThemes = extensionIndex

    var fabHeight by remember {
        mutableStateOf(0)
    }
    val fabHeightDp = with(LocalDensity.current) { fabHeight.toDp() + 16.dp }
    val listState = rememberLazyListState()

    content {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .florisScrollbar(state = listState, isVertical = true),
            state = listState,
            contentPadding = PaddingValues(bottom = fabHeightDp, top = 8.dp),
        ) {
            // Hero section for themes
            if (type == ExtensionListScreenType.EXT_THEME) {
                item {
                    ThemeHeroCard()
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // User Installed Themes Section
                if (userThemes.isNotEmpty()) {
                    item {
                        SectionTitle(title = "🎨 Your Themes", subtitle = "Imported themes for your keyboard")
                    }
                    items(userThemes) { ext ->
                        ModernExtensionCard(
                            extension = ext,
                            navController = navController,
                            extensionManager = extensionManager,
                        )
                    }
                }
                
                // Empty state when no themes
                if (userThemes.isEmpty()) {
                    item {
                        EmptyThemesCard(navController)
                    }
                }
            } else {
                // Non-theme extensions - show normally
                items(extensionIndex) { ext ->
                    ModernExtensionCard(
                        extension = ext,
                        navController = navController,
                        extensionManager = extensionManager,
                    )
                }
            }
        }
    }

    if (type.launchExtensionCreate != null) {
        floatingActionButton {
            ExtendedFloatingActionButton(
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringRes(id = R.string.ext__editor__title_create_any),
                    )
                },
                text = {
                    Text(
                        text = stringRes(id = R.string.ext__editor__title_create_any),
                    )
                },
                modifier = Modifier.onGloballyPositioned {
                    fabHeight = it.size.height
                },
                shape = FloatingActionButtonDefaults.extendedFabShape,
                onClick = { type.launchExtensionCreate.invoke(navController) },
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ThemeHeroCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(20.dp))
                Column {
                    Text(
                        text = "Your Themes",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Import .flex themes to customize",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyThemesCard(navController: NavController) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No custom themes yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Import themes from the Extensions page or create your own!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            FlorisTextButton(
                onClick = {
                    navController.navigate(Routes.Ext.Import(ExtensionImportScreenType.EXT_ANY, null))
                },
                text = "Import Theme",
                colors = ButtonDefaults.filledTonalButtonColors(),
            )
        }
    }
}

@Composable
private fun ModernExtensionCard(
    extension: Extension,
    navController: NavController,
    extensionManager: ExtensionManager,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isPressed) 
            MaterialTheme.colorScheme.surfaceContainerHighest
        else 
            MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = tween(100),
        label = "background"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = { navController.navigate(Routes.Ext.View(extension.meta.id)) }
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Theme icon with gradient background
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = extension.meta.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!extension.meta.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = extension.meta.description!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }
                
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Action buttons
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                FlorisTextButton(
                    onClick = {
                        navController.navigate(Routes.Ext.View(extension.meta.id))
                    },
                    icon = Icons.Outlined.Info,
                    text = stringRes(id = R.string.ext__list__view_details),
                    colors = ButtonDefaults.textButtonColors(),
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (extensionManager.canDelete(extension)) {
                    FlorisTextButton(
                        onClick = {
                            navController.navigate(Routes.Ext.Edit(extension.meta.id))
                        },
                        icon = Icons.Default.Edit,
                        text = stringRes(R.string.action__edit),
                    )
                }
            }
        }
    }
}
