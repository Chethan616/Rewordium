package com.noxquill.rewordium.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import kotlin.math.roundToInt
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.noxquill.rewordium.app.ui.haptics.LocalHaptics

data class NavTab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector = icon)

/**
 * A floating pill toolbar in place of a bottom bar, from the Material 3
 * Expressive drawing-app sample: a coral capsule; the current tab grows into
 * a dark pill with its label, the others stay compact icons.
 */
@Composable
fun FloatingNavBar(tabs: List<NavTab>, selected: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val labelStyle = monoLabel()
    val measurer = rememberTextMeasurer()
    // Every tab opens to the same width (room for the longest label), so as one
    // tab grows the other shrinks by exactly as much: the bar never changes
    // size, and a centred bar never wobbles.
    val labelWidth = remember(tabs, labelStyle, density) {
        with(density) { tabs.maxOf { measurer.measure(it.label, labelStyle).size.width }.toDp() }
    }
    val compact = TabPadding * 2 + TabIcon
    val expanded = compact + TabGap + labelWidth
    Surface(
        shape = CircleShape,
        color = scheme.secondaryContainer,
        contentColor = scheme.onSecondaryContainer,
        shadowElevation = 6.dp,
        // A ring in the page colour, so the pill reads as cut out even over same-coloured cards.
        border = androidx.compose.foundation.BorderStroke(3.dp, scheme.background),
        modifier = modifier,
    ) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { tab ->
                NavTabItem(tab, tab.route == selected, compact, expanded, labelStyle, onSelect)
            }
        }
    }
}

private val TabPadding = 14.dp
private val TabIcon = 24.dp
private val TabGap = 8.dp
private val TabHeight = 52.dp
private val TabSpring = spring<Dp>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)

/**
 * One tab. Its width and fill animate in the layout and draw phases only, so
 * a tab switch never recomposes the bar frame by frame.
 */
@Composable
private fun NavTabItem(
    tab: NavTab,
    isSelected: Boolean,
    compact: Dp,
    expanded: Dp,
    labelStyle: TextStyle,
    onSelect: (String) -> Unit,
) {
    val haptics = LocalHaptics.current
    val scheme = MaterialTheme.colorScheme
    val width = animateDpAsState(if (isSelected) expanded else compact, TabSpring, label = "nav-width")
    val fill = animateColorAsState(if (isSelected) scheme.onSecondaryContainer else scheme.onSecondaryContainer.copy(alpha = 0f), label = "nav-fill")
    // The label slides open on the same spring as the pill, so icon + label stay centred throughout.
    val reveal = animateFloatAsState(if (isSelected) 1f else 0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow), label = "nav-label")
    val content = if (isSelected) scheme.secondaryContainer else scheme.onSecondaryContainer
    Box(
        contentAlignment = Alignment.Center,
        modifier =
        Modifier
            .layout { measurable, _ ->
                val w = width.value.roundToPx()
                val h = TabHeight.roundToPx()
                val placeable = measurable.measure(Constraints.fixed(w, h))
                layout(w, h) { placeable.place(0, 0) }
            }
            .clip(CircleShape)
            .drawBehind { drawRect(fill.value) }
            .semantics {
                contentDescription = tab.label
                this.selected = isSelected
                stateDescription = if (isSelected) "Current page" else ""
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = androidx.compose.material3.ripple(),
                role = Role.Tab,
            ) { if (!isSelected) { haptics.tick(); onSelect(tab.route) } },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isSelected) tab.selectedIcon else tab.icon, contentDescription = null, tint = content, modifier = Modifier.size(TabIcon))
            Row(
                Modifier
                    .layout { measurable, _ ->
                        val placeable = measurable.measure(Constraints())
                        val w = (placeable.width * reveal.value.coerceIn(0f, 1f)).roundToInt()
                        layout(w, placeable.height) { placeable.place(0, 0) }
                    }
                    .clipToBounds()
                    .graphicsLayer { alpha = reveal.value.coerceIn(0f, 1f) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(TabGap))
                Text(tab.label, style = labelStyle, color = scheme.secondaryContainer, maxLines = 1, softWrap = false)
            }
        }
    }
}

data class FabAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * The expressive FAB menu: tap the button and coral pills unfold above it
 * (Marker · Pen · Eraser in the reference), the button morphing into a close.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    icon: ImageVector,
    label: String,
    actions: List<FabAction>,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHaptics.current
    val scheme = MaterialTheme.colorScheme
    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = { haptics.click(); onExpandedChange(it) },
                containerColor = ToggleFloatingActionButtonDefaults.containerColor(scheme.primaryContainer, scheme.secondaryContainer),
                modifier = Modifier.semantics {
                    contentDescription = if (expanded) "Close $label" else label
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                },
            ) {
                val progress = checkedProgress
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = scheme.onPrimaryContainer,
                        modifier = Modifier.graphicsLayer { alpha = 1f - progress; rotationZ = progress * 90f; scaleX = 1f - progress * 0.4f; scaleY = 1f - progress * 0.4f },
                    )
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = null,
                        tint = scheme.onSecondaryContainer,
                        modifier = Modifier.graphicsLayer { alpha = progress; rotationZ = (1f - progress) * -90f },
                    )
                }
            }
        },
    ) {
        actions.forEach { action ->
            FloatingActionButtonMenuItem(
                onClick = { haptics.click(); onExpandedChange(false); action.onClick() },
                text = { Text(action.label) },
                icon = { Icon(action.icon, contentDescription = null) },
                containerColor = scheme.secondaryContainer,
                contentColor = scheme.onSecondaryContainer,
            )
        }
    }
}
