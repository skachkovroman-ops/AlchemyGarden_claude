package com.example.alchemygarden.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alchemygarden.data.model.PlantConfig
import com.example.alchemygarden.domain.PotStage
import com.example.alchemygarden.domain.PotStatus
import com.example.alchemygarden.ui.theme.Forest
import com.example.alchemygarden.ui.theme.Ink
import com.example.alchemygarden.ui.theme.WaterBlue

/**
 * Один горшок. Слои (все картинки на ОДНОМ холсте 512×512): растение → передняя стенка горшка.
 * Так растение «растёт из» горшка без подгонки координат.
 */
@Composable
fun PotCard(
    plant: PlantConfig?,
    status: PotStatus?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, label = "potPress")
    val bob by rememberInfiniteTransition(label = "bob").animateFloat(
        initialValue = 0f, targetValue = -6f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bobY"
    )
    val stage = status?.stage
    val stageNum = when (stage) {
        PotStage.SPROUT -> 1
        PotStage.THIRSTY, PotStage.GROWING -> 2
        PotStage.READY -> 3
        null -> 1
    }
    // Если есть картинка стадии (цельный «горшок + растение»), отдельный горшок не рисуем.
    val hasStageArt = plant != null && drawableExists(Art.plantStage(plant.id, stageNum))

    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(124.dp)) {
            if (plant != null && stage != null) {
                val s = when (stage) {
                    PotStage.SPROUT -> 1
                    PotStage.THIRSTY, PotStage.GROWING -> 2
                    PotStage.READY -> 3
                }
                GameImage(
                    names = Art.plantStage(plant.id, s),
                    modifier = Modifier.fillMaxSize().offset(y = if (stage == PotStage.READY && !hasStageArt) bob.dp else 0.dp),
                    fallback = {
                        Box(Modifier.fillMaxSize().padding(bottom = 40.dp), contentAlignment = Alignment.Center) {
                            Text(
                                when (s) { 1 -> "🌱"; 2 -> "🌿"; else -> plant.emoji },
                                fontSize = 38.sp
                            )
                        }
                    }
                )
            }
            if (!hasStageArt) GameImage(
                names = listOf("pot_empty"),
                modifier = Modifier.fillMaxSize(),
                fallback = {
                    Box(
                        Modifier.fillMaxSize()
                            .padding(start = 22.dp, end = 22.dp, top = 62.dp, bottom = 6.dp)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                            .background(Color(0xFFB5703F))
                    )
                }
            )
            when (stage) {
                PotStage.THIRSTY -> StatusBubble("status_water", "💧")
                PotStage.READY -> StatusBubble("status_ready", "✨")
                else -> Unit
            }
        }
        if (plant == null || status == null) {
            Text("Пусто", style = MaterialTheme.typography.bodySmall, color = Ink.copy(alpha = 0.6f))
        } else {
            Text(
                plant.name, style = MaterialTheme.typography.bodySmall, color = Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.width(108.dp)
            )
            when (status.stage) {
                PotStage.READY -> Text("Готово!", color = Forest, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                PotStage.THIRSTY -> Text("Нужен полив", color = WaterBlue, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                else -> Text(formatSeconds(status.secondsLeft), color = Ink.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
            }
            LinearProgressIndicator(
                progress = { status.progress },
                modifier = Modifier.width(80.dp).padding(top = 2.dp),
                color = Forest,
                trackColor = Ink.copy(alpha = 0.15f)
            )
        }
    }
}

@Composable
private fun BoxScope.StatusBubble(imageName: String, emoji: String) {
    GameImage(
        names = listOf(imageName),
        modifier = Modifier.align(Alignment.TopEnd).size(34.dp),
        fallbackEmoji = emoji,
        emojiSize = 24.sp
    )
}
