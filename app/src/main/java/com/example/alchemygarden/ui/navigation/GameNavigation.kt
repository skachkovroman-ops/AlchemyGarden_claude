package com.example.alchemygarden.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.alchemygarden.audio.MusicManager
import com.example.alchemygarden.audio.SoundManager
import com.example.alchemygarden.audio.Track
import com.example.alchemygarden.domain.Sfx
import com.example.alchemygarden.ui.components.GameImage
import com.example.alchemygarden.ui.screens.encyclopedia.EncyclopediaScreen
import com.example.alchemygarden.ui.screens.greenhouse.GreenhouseScreen
import com.example.alchemygarden.ui.screens.laboratory.LaboratoryScreen
import com.example.alchemygarden.ui.screens.map.IslandMapScreen
import com.example.alchemygarden.ui.theme.Ink
import com.example.alchemygarden.ui.theme.Parchment
import com.example.alchemygarden.ui.theme.WoodBorder
import com.example.alchemygarden.ui.viewmodel.GameViewModel
import kotlinx.coroutines.launch

private enum class Overlay { MAP, BOOK }

@Composable
fun GameRoot(vm: GameViewModel) {
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    if (!loaded) {
        Box(Modifier.fillMaxSize().background(Parchment), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by vm.state.collectAsStateWithLifecycle()
    val sound = remember { SoundManager(context) }
    val music = remember { MusicManager(context, scope) }
    DisposableEffect(Unit) { onDispose { sound.release(); music.release() } }
    val snackbar = remember { SnackbarHostState() }

    var overlay by rememberSaveable { mutableStateOf<Overlay?>(null) }
    val pager = rememberPagerState(pageCount = { 2 })

    // Общий выключатель звука
    LaunchedEffect(state.soundEnabled) {
        sound.enabled = state.soundEnabled
        music.setEnabled(state.soundEnabled)
    }
    // Какой трек играет на каком экране
    LaunchedEffect(overlay, pager.currentPage) {
        music.setTrack(
            when (overlay) {
                Overlay.MAP -> Track.MAP
                Overlay.BOOK -> Track.ENCYCLOPEDIA
                null -> if (pager.currentPage == 0) Track.GREENHOUSE else Track.LABORATORY
            }
        )
    }
    // Свернули игру — музыка замолкает, вернулись — продолжается
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { music.setPaused(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { music.setPaused(false) }

    // События игры → звук + мягкая подсказка внизу экрана
    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            e.sfx?.let { sound.play(it) }
            if (e.text.isNotBlank()) {
                snackbar.currentSnackbarData?.dismiss()
                launch { snackbar.showSnackbar(e.text) }
            }
        }
    }

    BackHandler(overlay != null) { overlay = null }

    Box(Modifier.fillMaxSize()) {
        MainHub(
            vm = vm, pager = pager, soundOn = state.soundEnabled,
            onToggleSound = { vm.toggleSound() },
            onOpenMap = { sound.play(Sfx.PAGE); overlay = Overlay.MAP },
            onOpenBook = { sound.play(Sfx.PAGE); overlay = Overlay.BOOK }
        )

        Crossfade(targetState = overlay, label = "overlay") { o ->
            if (o != null) {
                // pointerInput «съедает» касания, чтобы хаб под оверлеем не реагировал
                Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
                    when (o) {
                        Overlay.MAP -> IslandMapScreen(vm) { overlay = null }
                        Overlay.BOOK -> EncyclopediaScreen(vm) { overlay = null }
                    }
                }
            }
        }

        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 90.dp)
        )
    }
}

@Composable
private fun MainHub(
    vm: GameViewModel,
    pager: PagerState,
    soundOn: Boolean,
    onToggleSound: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenBook: () -> Unit
) {
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            if (page == 0) GreenhouseScreen(vm) else LaboratoryScreen(vm)
        }

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CornerButton("Карта", "btn_map", "🗺️", onOpenMap)
            SoundButton(soundOn, onToggleSound)
            CornerButton("Гербарий", "btn_book", "📖", onOpenBook)
        }

        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NavTab("Оранжерея", "nav_greenhouse", "🪴", pager.currentPage == 0) {
                scope.launch { pager.animateScrollToPage(0) }
            }
            NavTab("Лаборатория", "nav_laboratory", "⚗️", pager.currentPage == 1) {
                scope.launch { pager.animateScrollToPage(1) }
            }
        }
    }
}

@Composable
private fun SoundButton(on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Parchment.copy(alpha = 0.95f))
            .border(2.dp, WoodBorder, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        GameImage(
            if (on) "btn_sound_on" else "btn_sound_off", Modifier.size(28.dp),
            fallbackEmoji = if (on) "🔊" else "🔇", emojiSize = 20.sp
        )
    }
}

@Composable
private fun CornerButton(label: String, image: String, emoji: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.clip(shape).background(Parchment.copy(alpha = 0.95f))
            .border(2.dp, WoodBorder, shape).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GameImage(image, Modifier.size(36.dp), fallbackEmoji = emoji, emojiSize = 24.sp)
        Spacer(Modifier.width(6.dp))
        Text(label, color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun NavTab(label: String, image: String, emoji: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.clip(shape)
            .background(if (selected) Parchment else Parchment.copy(alpha = 0.6f))
            .border(if (selected) 3.dp else 1.dp, WoodBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GameImage(image, Modifier.size(28.dp), fallbackEmoji = emoji, emojiSize = 20.sp)
        Spacer(Modifier.width(6.dp))
        Text(label, color = Ink, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
