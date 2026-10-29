package com.noxquill.rewordium.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.noxquill.rewordium.app.ui.avatar.BotAvatar
import com.noxquill.rewordium.app.ui.avatar.BotMood
import com.noxquill.rewordium.app.ui.haptics.LocalHaptics
import com.noxquill.rewordium.data.AppSettings

/** A friendly name for each bot. Jade herself is the cloud. */
fun avatarName(type: String): String = when (type) {
    "flower" -> "Bloom"
    "clover" -> "Clover"
    "triangle" -> "Tri"
    "square" -> "Cubby"
    "blob" -> "Blobby"
    "ghost" -> "Boo"
    "circle" -> "Orbit"
    "drop" -> "Droplet"
    "star" -> "Twinkle"
    "droid" -> "Bolt"
    "mech" -> "Mecha"
    "alien" -> "Zorp"
    "hexagon" -> "Hexy"
    "cat" -> "Whiskers"
    "cloud" -> "Nimbus"
    "pill" -> "Capsule"
    "pebble" -> "Pebble"
    "puddle" -> "Puddle"
    else -> type.replaceFirstChar(Char::uppercase)
}

/**
 * One choosable bot on its own whole Material library shape. Picking it
 * fills the shape with the accent, pops it, and sets it turning.
 */
@Composable
private fun AvatarCell(type: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = remember(type) { ExpressiveShapes.avatar(type) }
    val pop by animateFloatAsState(
        if (selected) 1f else 0.88f,
        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "avatar-pick",
    )
    val fill by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "avatar-fill",
    )
    ShapeBadge(
        shape = shape,
        color = fill,
        spin = selected,
        spinMillis = 24_000,
        modifier = modifier
            .aspectRatio(1f)
            .semantics {
                contentDescription = "${avatarName(type)} avatar"
                this.selected = selected
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton, onClick = onClick)
            .expressivePress(0.86f)
            .graphicsLayer { scaleX = pop; scaleY = pop },
    ) {
        BotAvatar(
            type = type,
            mood = if (selected) BotMood.Happy else BotMood.Idle,
            size = 40.dp,
            still = !selected,
            interactive = false,
            label = null,
        )
    }
}

/** All eighteen bots in a grid that fits inside any scrolling column. */
@Composable
fun AvatarPickerGrid(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 6,
) {
    val haptics = LocalHaptics.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppSettings.avatars.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { type ->
                    AvatarCell(type, type == selected, onClick = { haptics.tick(); onSelect(type) }, modifier = Modifier.weight(1f))
                }
                repeat(columns - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** A single swipeable row of bots, for compact forms. */
@Composable
fun AvatarCarousel(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHaptics.current
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
        items(AppSettings.avatars, key = { it }) { type ->
            AvatarCell(type, type == selected, onClick = { haptics.tick(); onSelect(type) }, modifier = Modifier.size(58.dp))
        }
    }
}

/** A big, lively preview of the chosen bot with its name. */
@Composable
fun AvatarHero(type: String, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 132.dp) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // The hero wears the picked bot's own library shape, and springs into the next one on a new pick.
        ShapeMorph(shape = ExpressiveShapes.avatar(type),color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(size)) {
            AnimatedContent(
                targetState = type,
                transitionSpec = {
                    (scaleIn(spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.6f) + fadeIn())
                        .togetherWith(scaleOut(targetScale = 0.6f) + fadeOut())
                },
                label = "avatar-hero",
            ) { current ->
                BotAvatar(type = current, mood = BotMood.Happy, size = size * 0.7f, label = "${avatarName(current)}, your avatar")
            }
        }
        AnimatedContent(targetState = avatarName(type), label = "avatar-name") { name ->
            Text(name, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        }
    }
}

/** Settings' avatar chooser: preview, every bot, and a confirm button. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AvatarPickerSheet(current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val haptics = LocalHaptics.current
    var pending by rememberSaveable { mutableStateOf(current) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Pick your avatar", style = MaterialTheme.typography.headlineSmall)
            AvatarHero(pending, size = 120.dp)
            AvatarPickerGrid(selected = pending, onSelect = { pending = it })
            Button(
                onClick = { haptics.success(); onConfirm(pending) },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Be ${avatarName(pending)}", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

/** A thin outline ring used to mark the signed-in avatar. */
fun Modifier.avatarRing(color: androidx.compose.ui.graphics.Color) = border(3.dp, color, CircleShape)
