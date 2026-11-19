package com.example.alchemygarden.ui.screens.laboratory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.alchemygarden.data.model.PlayerState
import com.example.alchemygarden.data.model.RecipeConfig
import com.example.alchemygarden.data.model.recipe
import com.example.alchemygarden.ui.components.*
import com.example.alchemygarden.ui.theme.*
import com.example.alchemygarden.ui.viewmodel.GameViewModel
import kotlinx.coroutines.delay

@Composable
fun LaboratoryScreen(vm: GameViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val config = vm.config
    var showStorage by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        ScreenBackground("bg_laboratory", listOf(Color(0xFF6B4A33), Color(0xFF3F2C20)))
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .padding(top = HubInsets.top, bottom = HubInsets.bottom, start = 12.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Лаборатория", style = MaterialTheme.typography.headlineMedium, color = Parchment, modifier = Modifier.weight(1f))
                WoodButton("🎒 Склад", { showStorage = true })
            }

            // ── Котёл и очередь варки ──
            val brewing = state.craftQueue.isNotEmpty()
            val frame by produceState(0, brewing) {
                while (brewing) { delay(450); value = 1 - value }
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                GameImage(
                    names = if (brewing) listOf("cauldron_boiling_${frame + 1}", "cauldron_idle") else listOf("cauldron_idle"),
                    modifier = Modifier.size(130.dp), fallbackEmoji = if (brewing) "🍲" else "🫕", emojiSize = 72.sp
                )
                if (!brewing) {
                    Text("Котёл остыл — выбери рецепт ниже", color = Parchment.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.craftQueue.forEachIndexed { i, job ->
                            val r = config.recipe(job.recipeId)
                            val end = job.startedAtMillis + job.durationMillis
                            val left = ((end - now).coerceAtLeast(0L) + 999L) / 1000L
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                GameImage(Art.itemIcon(job.recipeId), Modifier.size(36.dp), fallbackEmoji = r?.emoji ?: "🧪", emojiSize = 22.sp)
                                Text(formatSeconds(left), color = Parchment, style = MaterialTheme.typography.bodySmall)
                                if (i == 0) {
                                    LinearProgressIndicator(
                                        progress = { ((now - job.startedAtMillis).toFloat() / job.durationMillis).coerceIn(0f, 1f) },
                                        modifier = Modifier.width(48.dp), color = Amber, trackColor = Parchment.copy(alpha = 0.2f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Рецепты ──
            ParchmentCard(Modifier.fillMaxWidth().weight(1f)) {
                Text("Рецепты", style = MaterialTheme.typography.titleMedium)
                LazyColumn {
                    items(config.recipes, key = { it.id }) { recipe -> RecipeRow(vm, recipe, state) }
                }
            }
        }
    }

    if (showStorage) StorageDialog(vm, state) { showStorage = false }
}

@Composable
private fun RecipeRow(vm: GameViewModel, recipe: RecipeConfig, state: PlayerState) {
    val engine = vm.engine
    val blocker = engine.craftBlocker(state, recipe)
    val seconds = engine.craftDurationMillis(recipe, state) / 1000
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        GameImage(Art.itemIcon(recipe.id), Modifier.size(52.dp), fallbackEmoji = recipe.emoji, emojiSize = 30.sp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(recipe.name, fontWeight = FontWeight.Bold)
            Text(recipe.purpose, style = MaterialTheme.typography.bodySmall, color = Ink.copy(alpha = 0.75f))
            recipe.ingredients.forEach { RequirementChip(vm.config, it, engine.have(state, it)) }
            Text(
                "⏱ ${formatSeconds(seconds)} · на складе: ${state.inventorySupplies[recipe.id] ?: 0}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        WoodButton("Варить", { vm.craft(recipe.id) }, enabled = blocker == null)
    }
    HorizontalDivider(color = WoodBorder.copy(alpha = 0.3f))
}

@Composable
private fun StorageDialog(vm: GameViewModel, state: PlayerState, onDismiss: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    val config = vm.config
    ParchmentDialog(onDismiss) {
        Text("Склад", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("🌿 Травы") })
            FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("🫖 Припасы") })
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(96.dp),
            modifier = Modifier.heightIn(max = 320.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (tab == 0) {
                val list = config.plants.filter { (state.inventoryPlants[it.id] ?: 0) > 0 }
                items(list, key = { it.id }) { p ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ResourceBadge(Art.plantIcon(p.id), p.emoji, state.inventoryPlants[p.id] ?: 0)
                        Text(p.name, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                }
            } else {
                val list = config.recipes.filter { (state.inventorySupplies[it.id] ?: 0) > 0 }
                items(list, key = { it.id }) { r ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ResourceBadge(Art.itemIcon(r.id), r.emoji, state.inventorySupplies[r.id] ?: 0)
                        Text(r.name, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                }
            }
        }
        if ((tab == 0 && state.inventoryPlants.isEmpty()) || (tab == 1 && state.inventorySupplies.isEmpty())) {
            Text("Здесь пока пусто.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
