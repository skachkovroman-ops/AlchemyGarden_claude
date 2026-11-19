package com.example.alchemygarden.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alchemygarden.data.model.GameConfig
import com.example.alchemygarden.data.model.IngredientRequirement
import com.example.alchemygarden.data.model.plant
import com.example.alchemygarden.data.model.recipe
import com.example.alchemygarden.ui.theme.Berry
import com.example.alchemygarden.ui.theme.Forest
import com.example.alchemygarden.ui.theme.Ink
import com.example.alchemygarden.ui.theme.Parchment
import com.example.alchemygarden.ui.theme.WoodBorder

@Composable
fun ResourceBadge(iconNames: List<String>, emoji: String, count: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier.clip(shape).background(Parchment.copy(alpha = 0.92f))
            .border(1.dp, WoodBorder.copy(alpha = 0.6f), shape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GameImage(iconNames, Modifier.size(28.dp), fallbackEmoji = emoji, emojiSize = 18.sp)
        Spacer(Modifier.width(4.dp))
        Text("×$count", color = Ink, fontWeight = FontWeight.Bold)
    }
}

/** Строка «иконка + имя + есть/нужно». Красная, если не хватает. */
@Composable
fun RequirementChip(config: GameConfig, req: IngredientRequirement, have: Int) {
    val plant = req.plantId?.let { config.plant(it) }
    val recipe = req.itemId?.let { config.recipe(it) }
    val icon = plant?.let { Art.plantIcon(it.id) } ?: recipe?.let { Art.itemIcon(it.id) } ?: emptyList()
    val emoji = plant?.emoji ?: recipe?.emoji ?: "❔"
    val name = plant?.name ?: recipe?.name ?: "?"
    val ok = have >= req.amount
    Row(Modifier.padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        GameImage(icon, Modifier.size(22.dp), fallbackEmoji = emoji, emojiSize = 14.sp)
        Spacer(Modifier.width(4.dp))
        Text(
            "$name  $have/${req.amount}",
            style = MaterialTheme.typography.bodySmall,
            color = if (ok) Forest else Berry,
            fontWeight = if (ok) FontWeight.Normal else FontWeight.Bold
        )
    }
}
