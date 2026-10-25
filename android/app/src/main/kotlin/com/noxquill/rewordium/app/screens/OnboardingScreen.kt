package com.noxquill.rewordium.app.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.spring
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noxquill.rewordium.app.ui.expressivePress
import com.noxquill.rewordium.app.ui.JadeMascot

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val heroRotation by animateFloatAsState(
        targetValue = page * 14f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessLow),
        label = "onboarding-hero-rotation",
    )
    val headlines = listOf("Words, with a little more you.", "A keyboard that knows your voice.", "Meet Jade, your writing companion.")
    val descriptions = listOf(
        "Turn a rough thought into something clear, natural, and still yours.",
        "Reach Rewordium’s writing tools wherever you write, with your settings close at hand.",
        "Rewrite, refine, and explore ideas together. You stay in control of every word.",
    )

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f),
        MaterialTheme.colorScheme.background,
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.22f),
    )))) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        JadeMascot(Modifier.padding(6.dp), size = 26.dp, animate = false)
                    }
                    Text("rewordium", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = onFinish) { Text("Skip") }
            }

            Spacer(Modifier.weight(0.48f))
            AnimatedContent(
                targetState = page,
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.94f)) togetherWith fadeOut() },
                label = "onboarding-page",
            ) { activePage ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(242.dp), contentAlignment = Alignment.Center) {
                        Surface(
                            modifier = Modifier.size(214.dp).rotate(heroRotation),
                            shape = MaterialShapes.Puffy.toShape(),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.74f),
                        ) {}
                        Surface(
                            modifier = Modifier.size(184.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shadowElevation = 3.dp,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                when (activePage) {
                                    0 -> JadeMascot(size = 142.dp)
                                    1 -> {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                                                androidx.compose.material3.Icon(Icons.Filled.Keyboard, null, Modifier.padding(18.dp).size(48.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                            }
                                            Spacer(Modifier.height(10.dp))
                                            Text("your words, everywhere", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    else -> {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            JadeMascot(size = 118.dp, mood = "thinking")
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                androidx.compose.material3.Icon(Icons.Filled.AutoAwesome, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                                                Text(" a little help, right on time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(38.dp))
                    Text(
                        headlines[activePage],
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp, lineHeight = 36.sp),
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        descriptions[activePage],
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.88f),
                    )
                }
            }
            Spacer(Modifier.weight(0.45f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("0${page + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                LinearWavyProgressIndicator(
                    progress = { (page + 1) / 3f },
                    modifier = Modifier.weight(1f),
                )
                Text("03", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { if (page < 2) page++ else onFinish() },
                modifier = Modifier.fillMaxWidth().height(58.dp).expressivePress(),
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(if (page == 2) "Let’s write" else "Continue", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onFinish) { Text("I’ll set things up later", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(10.dp))
        }
    }
}
