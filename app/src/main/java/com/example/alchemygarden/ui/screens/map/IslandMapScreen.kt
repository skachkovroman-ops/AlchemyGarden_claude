package com.example.alchemygarden.ui.screens.map

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.alchemygarden.data.model.BiomeConfig
import com.example.alchemygarden.data.model.PlayerState
import com.example.alchemygarden.data.model.plant
import com.example.alchemygarden.ui.components.*
import com.example.alchemygarden.ui.theme.*
import com.example.alchemygarden.ui.viewmodel.GameViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private val HomeX = 0.5f
private val HomeY = 0.92f

private data class Butterfly(val id: Long, val x: Float, val y: Float)

@Composable
fun IslandMapScreen(vm: GameViewModel, onClose: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val config = vm.config
    var selected by remember { mutableStateOf<String?>(null) }
    var butterflies by remember { mutableStateOf(listOf<Butterfly>()) }

    val exp = state.activeExpedition
    val expStatus = exp?.let { vm.engine.expeditionStatus(it, now) }
    val running = exp != null && expStatus?.finished == false

    LaunchedEffect(running) {
        if (!running) { butterflies = emptyList(); return@LaunchedEffect }
        while (true) {
            delay(Random.nextLong(10_000, 18_000))
            val b = Butterfly(System.nanoTime(), Random.nextFloat() * 0.7f + 0.15f, Random.nextFloat() * 0.6f + 0.2f)
            butterflies = butterflies + b
            launch { delay(8_000); butterflies = butterflies.filterNot { it.id == b.id } }
        }
    }

    Box(Modifier.fillMaxSize()) {
        ScreenBackground("bg_map_parchment", listOf(Color(0xFFEBDDB4), Color(0xFFD9C48F)))

        BoxWithConstraints(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 56.dp, bottom = 12.dp)
        ) {
            val w = maxWidth
            val h = maxHeight
            val points = listOf(Offset(HomeX, HomeY)) + config.biomes.map { Offset(it.mapX, it.mapY) }

            // Пунктирные тропинки
            Canvas(Modifier.fillMaxSize()) {
                for (i in 0 until config.biomes.size) {
                    val open = config.biomes[i].id in state.unlockedBiomes
                    val a = points[i]; val b = points[i + 1]
                    drawLine(
                        color = Ink.copy(alpha = if (open) 0.7f else 0.2f),
                        start = Offset(a.x * size.width, a.y * size.height),
                        end = Offset(b.x * size.width, b.y * size.height),
                        strokeWidth = 4.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f))
                    )
                }
            }

            // Дом
            GameImage(
                listOf("map_home"), Modifier.size(56.dp).offset(w * HomeX - 28.dp, h * HomeY - 28.dp),
                fallbackEmoji = "🏡", emojiSize = 36.sp
            )

            // Туман над закрытыми узлами
            config.biomes.forEach { biome ->
                FogPatch(
                    Modifier.offset(w * biome.mapX - 90.dp, h * biome.mapY - 90.dp),
                    visible = biome.id !in state.unlockedBiomes, size = 180.dp
                )
            }

            // Узлы
            config.biomes.forEach { biome ->
                BiomeNode(
                    biome = biome, state = state,
                    timerText = if (exp?.biomeId == biome.id && expStatus != null)
                        (if (expStatus.finished) "Готово!" else formatSeconds(expStatus.secondsLeft)) else null,
                    modifier = Modifier.offset(w * biome.mapX - 48.dp, h * biome.mapY - 48.dp),
                    onClick = { selected = biome.id }
                )
            }

            // Идущий алхимик
            if (exp != null && expStatus != null) {
                val target = config.biomes.firstOrNull { it.id == exp.biomeId }
                if (target != null) {
                    val fx = lerp(HomeX, target.mapX, expStatus.progress)
                    val fy = lerp(HomeY, target.mapY, expStatus.progress)
                    Alchemist(Modifier.offset(w * fx - 24.dp, h * fy - 56.dp), walking = !expStatus.finished)
                }
            }

            // Бабочки
            butterflies.forEach { b ->
                ButterflySprite(Modifier.offset(w * b.x - 20.dp, h * b.y - 20.dp)) {
                    butterflies = butterflies.filterNot { it.id == b.id }
                    vm.catchButterfly()
                }
            }
        }

        // Верхняя панель
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WoodButton("← Назад", onClose)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Карта острова", style = MaterialTheme.typography.titleLarge, color = Ink)
                if (running && exp != null) {
                    Text(
                        "Бабочки: ${exp.butterfliesCaught}/${config.settings.maxButterfliesPerExpedition} — лови по пути!",
                        style = MaterialTheme.typography.bodySmall, color = Ink
                    )
                } else if (expStatus?.finished == true) {
                    Text("Алхимик вернулся! Нажми на узел, чтобы забрать находки.", style = MaterialTheme.typography.bodySmall, color = Forest, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    selected?.let { id ->
        val biome = config.biomes.first { it.id == id }
        ExpeditionDialog(vm, biome, state, now) { selected = null }
    }
}

@Composable
private fun FogPatch(modifier: Modifier, visible: Boolean, size: Dp) {
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(1800), label = "fog")
    if (alpha > 0.01f) {
        Box(modifier.size(size).graphicsLayer { this.alpha = alpha }) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(listOf(FogGray.copy(alpha = 0.95f), FogGray.copy(alpha = 0f))), CircleShape
                )
            )
            GameImage(listOf("fog_cloud"), Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun BiomeNode(
    biome: BiomeConfig, state: PlayerState, timerText: String?,
    modifier: Modifier, onClick: () -> Unit
) {
    val unlocked = biome.id in state.unlockedBiomes
    val done = biome.id in state.completedBiomes
    Column(
        modifier.width(96.dp).clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
            if (!unlocked) {
                GameImage(listOf("node_locked"), Modifier.fillMaxSize(), fallbackEmoji = "❓", emojiSize = 40.sp)
            } else {
                GameImage(Art.biomeNode(biome.id), Modifier.fillMaxSize(), fallbackEmoji = biome.emoji, emojiSize = 44.sp)
                if (done) {
                    GameImage(listOf("node_done"), Modifier.size(28.dp).align(Alignment.TopEnd), fallbackEmoji = "⭐", emojiSize = 18.sp)
                }
            }
        }
        Text(
            if (unlocked) biome.name else "???",
            style = MaterialTheme.typography.bodySmall, color = Ink, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, maxLines = 2
        )
        if (timerText != null) Text("⏳ $timerText", style = MaterialTheme.typography.bodySmall, color = Forest, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Alchemist(modifier: Modifier, walking: Boolean) {
    val frame by produceState(0, walking) {
        while (walking) { delay(180); value = (value + 1) % 4 }
    }
    GameImage(
        names = if (walking) listOf("alchemist_walk_${frame + 1}", "alchemist_idle") else listOf("alchemist_idle"),
        modifier = modifier.size(48.dp), fallbackEmoji = "🧙", emojiSize = 34.sp
    )
}

@Composable
private fun ButterflySprite(modifier: Modifier, onTap: () -> Unit) {
    val frame by produceState(0) { while (true) { delay(220); value = 1 - value } }
    val wobble by rememberInfiniteTransition(label = "bf").animateFloat(
        0f, 10f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "bfy"
    )
    GameImage(
        names = listOf("butterfly_${frame + 1}"),
        modifier = modifier.size(40.dp).offset(y = wobble.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap),
        fallbackEmoji = "🦋", emojiSize = 28.sp
    )
}

@Composable
private fun ExpeditionDialog(vm: GameViewModel, biome: BiomeConfig, state: PlayerState, now: Long, onDismiss: () -> Unit) {
    val engine = vm.engine
    val unlocked = biome.id in state.unlockedBiomes
    val exp = state.activeExpedition
    val isActiveHere = exp?.biomeId == biome.id
    val status = if (isActiveHere && exp != null) engine.expeditionStatus(exp, now) else null

    ParchmentDialog(onDismiss) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            GameImage(Art.biomeArt(biome.id), Modifier.fillMaxWidth().height(120.dp), fallbackEmoji = biome.emoji, emojiSize = 64.sp)
            Text(if (unlocked) biome.name else "Неизвестная тропа", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            if (!unlocked) {
                Text("Туман скрывает эту тропу. Пройди предыдущий регион, и он рассеется.", textAlign = TextAlign.Center)
                return@Column
            }
            Text(biome.description, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("Время в пути: ${formatSeconds(engine.expeditionDurationMillis(biome, state) / 1000)}", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Требуется:", style = MaterialTheme.typography.bodySmall)
            Column(Modifier.fillMaxWidth()) {
                biome.requiredSupplies.forEach { RequirementChip(vm.config, it, engine.have(state, it)) }
            }
            Spacer(Modifier.height(6.dp))
            val rewardNames = biome.rewardPlantIds.mapNotNull { vm.config.plant(it)?.name }
            Text(
                if (biome.id in state.completedBiomes) "Находки: ${rewardNames.joinToString(", ")}" else "Находки: неизведанно…",
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))

            when {
                status != null && status.finished ->
                    WoodButton("Забрать находки", { vm.claimExpedition(); onDismiss() })
                status != null -> {
                    Text("Алхимик в пути… ${formatSeconds(status.secondsLeft)}", fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(progress = { status.progress }, Modifier.fillMaxWidth().padding(top = 6.dp), color = Forest)
                }
                else -> {
                    val blocker = engine.expeditionBlocker(state, biome)
                    if (blocker != null) Text(blocker, color = Berry, style = MaterialTheme.typography.bodySmall)
                    WoodButton("Отправиться в путь!", { vm.startExpedition(biome.id); onDismiss() }, enabled = blocker == null)
                }
            }
        }
    }
}
