package com.noxquill.rewordium.app.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.noxquill.rewordium.app.ui.AvatarHero
import com.noxquill.rewordium.app.ui.Heading
import com.noxquill.rewordium.app.ui.display
import androidx.compose.ui.unit.sp
import com.noxquill.rewordium.app.ui.AvatarPickerGrid
import com.noxquill.rewordium.app.ui.MorphingBackdrop
import com.noxquill.rewordium.app.ui.avatarName
import com.noxquill.rewordium.app.ui.expressivePress
import com.noxquill.rewordium.app.ui.avatar.BotAvatar
import com.noxquill.rewordium.app.ui.avatar.BotMood
import com.noxquill.rewordium.app.ui.haptics.LocalHaptics
import com.noxquill.rewordium.data.AppSettings
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private data class OnboardingPage(val headline: String, val body: String)

private val pages = listOf(
    OnboardingPage("Meet your writing crew", "Clearer sentences, sharper ideas, and a voice that still sounds like you."),
    OnboardingPage("Write wherever you are", "Use the Rewordium keyboard and the writing assistant inside the apps you already love."),
    OnboardingPage("Say hi to Jade", "Talk through a draft, find the right tone — or just ask her to tune your keyboard."),
    OnboardingPage("Pick your avatar", "This little friend stands for you in chats and around the app. Change it any time."),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current
    val avatar by AppSettings.avatar.collectAsState()
    var lastPage by remember { mutableIntStateOf(0) }
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != lastPage) haptics.tick()
        lastPage = pagerState.currentPage
    }
    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }
    val last = pagerState.currentPage == pages.lastIndex

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                BotAvatar(type = avatar, mood = BotMood.Happy, size = 34.dp, still = true, label = "Rewordium")
                Text("REWORDIUM", style = Heading.Poster.display(24.sp))
            }
            if (!last) TextButton(onClick = { haptics.click(); scope.launch { pagerState.animateScrollToPage(pages.lastIndex) } }) { Text("Skip") }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { page ->
            val offset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .graphicsLayer {
                        alpha = 1f - offset * 0.6f
                        val s = 1f - offset * 0.08f
                        scaleX = s
                        scaleY = s
                    }
                    .padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (page == pages.lastIndex) {
                    Spacer(Modifier.height(12.dp))
                    AvatarHero(avatar, size = 150.dp)
                    Spacer(Modifier.height(10.dp))
                } else {
                    Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
                        MorphingBackdrop(phase = pagerState.currentPage + pagerState.currentPageOffsetFraction, modifier = Modifier.size(230.dp))
                        PageArt(page)
                    }
                    Spacer(Modifier.height(24.dp))
                }
                Text(Heading.Wide.text(pages[page].headline), style = Heading.Wide.display(30.sp), textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text(
                    pages[page].body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.92f),
                )
                if (page == pages.lastIndex) {
                    Spacer(Modifier.height(18.dp))
                    AvatarPickerGrid(selected = avatar, onSelect = { AppSettings.setAvatar(it) })
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("0${pagerState.currentPage + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            LinearWavyProgressIndicator(
                progress = { (pagerState.currentPage + 1 + pagerState.currentPageOffsetFraction) / pages.size.toFloat() },
                modifier = Modifier.weight(1f),
            )
            Text("0${pages.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                if (last) { haptics.success(); onFinish() }
                else { haptics.click(); scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }
            },
            modifier = Modifier.fillMaxWidth().height(60.dp).expressivePress(),
            shapes = ButtonDefaults.shapes(),
        ) {
            AnimatedContent(
                targetState = if (last) "Let’s go with ${avatarName(avatar)}" else "Continue",
                transitionSpec = { fadeIn().togetherWith(fadeOut()) },
                label = "onboarding-cta",
            ) { Text(it, style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun PageArt(page: Int) {
    Box(Modifier.size(260.dp)) {
        when (page) {
            0 -> {
                BotAvatar(type = "flower", mood = BotMood.Happy, size = 112.dp, modifier = Modifier.align(Alignment.Center), label = "Bloom")
                BotAvatar(type = "ghost", mood = BotMood.Idle, size = 58.dp, modifier = Modifier.align(Alignment.TopEnd).padding(end = 24.dp, top = 26.dp), label = "Boo")
                BotAvatar(type = "triangle", mood = BotMood.Idle, size = 52.dp, modifier = Modifier.align(Alignment.BottomStart).padding(start = 26.dp, bottom = 34.dp), label = "Tri")
                BotAvatar(type = "clover", mood = BotMood.Idle, size = 46.dp, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 36.dp, bottom = 26.dp), label = "Clover")
                BotAvatar(type = "cat", mood = BotMood.Happy, size = 44.dp, modifier = Modifier.align(Alignment.TopStart).padding(start = 34.dp, top = 30.dp), label = "Whiskers")
            }
            1 -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Icon(Icons.Filled.Keyboard, null, Modifier.padding(22.dp).size(60.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BotAvatar(type = "cat", mood = BotMood.Idle, size = 44.dp, label = "Whiskers")
                    BotAvatar(type = "droid", mood = BotMood.Working, size = 58.dp, label = "Bolt")
                    BotAvatar(type = "drop", mood = BotMood.Idle, size = 44.dp, label = "Droplet")
                }
            }
            2 -> {
                BotAvatar(type = "cloud", mood = BotMood.Thinking, size = 126.dp, modifier = Modifier.align(Alignment.Center), label = "Jade")
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = 30.dp).expressivePress()) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.AutoAwesome, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text("Make this friendlier", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 34.dp)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Tune, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text("Taller keyboard, please", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        }
    }
}
