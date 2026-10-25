package com.noxquill.rewordium.app.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noxquill.rewordium.app.ui.avatar.BotAvatar
import com.noxquill.rewordium.app.ui.avatar.BotMood
import com.noxquill.rewordium.app.ui.expressivePress
import com.noxquill.rewordium.app.ui.haptics.LocalHaptics
import kotlinx.coroutines.launch

private val onboardingContent = listOf(
    "Meet your writing companion" to "Clearer sentences, sharper ideas, and a voice that still sounds like you.",
    "Write wherever you are" to "Use the Rewordium keyboard and accessibility actions in the apps you already use.",
    "Say hello to Jade" to "Talk through a draft, find a better tone, or start with a blank page.",
    "You stay in control" to "Choose when Rewordium helps. Your writing is always yours to review.",
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { onboardingContent.size })
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current
    var lastPage by remember { mutableIntStateOf(0) }
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != lastPage) haptics.tick()
        lastPage = pagerState.currentPage
    }
    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                BotAvatar(type = "flower", mood = BotMood.Idle, size = 34.dp, still = true, label = "Rewordium")
                Text("rewordium", style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = { haptics.click(); onFinish() }) { Text("Skip") }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 0.dp)) { page ->
            val (headline, description) = onboardingContent[page]
            Column(Modifier.fillMaxSize().padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.fillMaxWidth().height(285.dp), contentAlignment = Alignment.Center) {
                    Surface(
                        modifier = Modifier.size(224.dp),
                        shape = MaterialShapes.Puffy.toShape(),
                        color = if (page == 2) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                    ) {}
                    when (page) {
                        0 -> {
                            BotAvatar(type = "flower", mood = BotMood.Happy, size = 112.dp, modifier = Modifier.align(Alignment.Center), label = "Flower mascot")
                            BotAvatar(type = "ghost", mood = BotMood.Idle, size = 56.dp, modifier = Modifier.align(Alignment.TopEnd).padding(end = 30.dp, top = 35.dp), label = "Ghost mascot")
                            BotAvatar(type = "triangle", mood = BotMood.Idle, size = 50.dp, modifier = Modifier.align(Alignment.BottomStart).padding(start = 35.dp, bottom = 42.dp), label = "Triangle mascot")
                            BotAvatar(type = "clover", mood = BotMood.Idle, size = 44.dp, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 45.dp, bottom = 32.dp), label = "Clover mascot")
                        }
                        1 -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                    Icon(Icons.Filled.Keyboard, null, Modifier.padding(20.dp).size(58.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                    BotAvatar(type = "cat", mood = BotMood.Idle, size = 42.dp, still = true)
                                    BotAvatar(type = "droid", mood = BotMood.Happy, size = 54.dp)
                                    BotAvatar(type = "drop", mood = BotMood.Idle, size = 42.dp, still = true)
                                }
                            }
                        }
                        2 -> {
                            BotAvatar(type = "blob", mood = BotMood.Thinking, size = 120.dp, modifier = Modifier.align(Alignment.Center), label = "Jade mascot")
                            BotAvatar(type = "star", mood = BotMood.Happy, size = 53.dp, modifier = Modifier.align(Alignment.TopStart).padding(start = 45.dp, top = 38.dp), label = "Star mascot")
                            BotAvatar(type = "alien", mood = BotMood.Idle, size = 52.dp, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 45.dp, bottom = 42.dp), label = "Alien mascot")
                        }
                        else -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                BotAvatar(type = "droid", mood = BotMood.Sleeping, size = 102.dp, label = "Droid mascot")
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                    Icon(Icons.Filled.Shield, null, Modifier.padding(11.dp).size(26.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
                Text(headline, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp, lineHeight = 36.sp), textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text(description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(0.9f))
                if (page == 2) {
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.AutoAwesome, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Text("Your ideas, a little more polished", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("0${pagerState.currentPage + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            LinearWavyProgressIndicator(progress = { (pagerState.currentPage + 1) / onboardingContent.size.toFloat() }, modifier = Modifier.weight(1f))
            Text("0${onboardingContent.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                haptics.click()
                if (pagerState.currentPage < onboardingContent.lastIndex) scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                else onFinish()
            },
            modifier = Modifier.fillMaxWidth().height(58.dp).expressivePress(),
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(if (pagerState.currentPage == onboardingContent.lastIndex) "Let’s write" else "Continue", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { haptics.click(); onFinish() }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("I’ll set things up later", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
