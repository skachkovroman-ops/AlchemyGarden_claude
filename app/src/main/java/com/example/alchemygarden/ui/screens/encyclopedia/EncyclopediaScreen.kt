package com.example.alchemygarden.ui.screens.encyclopedia

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.alchemygarden.data.model.GameConfig
import com.example.alchemygarden.data.model.PlayerState
import com.example.alchemygarden.data.model.describe
import com.example.alchemygarden.domain.GameEngine
import com.example.alchemygarden.ui.components.*
import com.example.alchemygarden.ui.theme.*
import com.example.alchemygarden.ui.viewmodel.GameViewModel

private enum class CodexTab(val title: String, val icon: String, val emoji: String) {
    PLANTS("Растения", "tab_plants", "🌿"),
    HOMUNCULI("Гомункулы", "tab_homunculi", "🐾"),
    SECRETS("Тайны", "tab_secrets", "🗺️"),
    RECIPES("Рецепты", "tab_recipes", "📜")
}

private data class CodexEntry(
    val key: String,
    val title: String,
    val art: List<String>,
    val emoji: String,
    val discovered: Boolean,
    val hint: String,
    val body: List<String>
)

@Composable
fun EncyclopediaScreen(vm: GameViewModel, onClose: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(CodexTab.PLANTS) }
    var detail by remember { mutableStateOf<CodexEntry?>(null) }
    val entries = remember(state, tab) { buildEntries(vm.config, vm.engine, state, tab) }

    Box(Modifier.fillMaxSize()) {
        ScreenBackground("bg_encyclopedia", listOf(Color(0xFF5A3A24), Color(0xFF2E1D12)))
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WoodButton("← Назад", onClose)
                Spacer(Modifier.width(12.dp))
                Text("Гербарий и Бестиарий", style = MaterialTheme.typography.titleLarge, color = Parchment)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CodexTab.values().forEach { t ->
                    FilterChip(
                        selected = tab == t, onClick = { tab = t },
                        label = { Text(t.title, style = MaterialTheme.typography.bodySmall) },
                        leadingIcon = { GameImage(listOf(t.icon), Modifier.size(18.dp), fallbackEmoji = t.emoji, emojiSize = 12.sp) }
                    )
                }
            }
            ParchmentCard(Modifier.fillMaxWidth().weight(1f)) {
                val found = entries.count { it.discovered }
                Text("Открыто: $found / ${entries.size}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(104.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(entries, key = { it.key }) { e ->
                        Column(
                            Modifier.fillMaxWidth().clickable { detail = e },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (e.discovered) GameImage(e.art, Modifier.size(64.dp), fallbackEmoji = e.emoji, emojiSize = 36.sp)
                            else GameImage(listOf("silhouette_unknown"), Modifier.size(64.dp), fallbackEmoji = "❔", emojiSize = 36.sp)
                            Text(
                                if (e.discovered) e.title else "???",
                                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, maxLines = 2
                            )
                        }
                    }
                }
            }
        }
    }

    detail?.let { e ->
        ParchmentDialog({ detail = null }) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (e.discovered) GameImage(e.art, Modifier.size(120.dp), fallbackEmoji = e.emoji, emojiSize = 64.sp)
                else GameImage(listOf("silhouette_unknown"), Modifier.size(120.dp), fallbackEmoji = "❔", emojiSize = 64.sp)
                Text(if (e.discovered) e.title else "???", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                if (e.discovered) {
                    e.body.filter { it.isNotBlank() }.forEach {
                        Text(it, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 2.dp))
                    }
                } else {
                    Text("Подсказка: ${e.hint.ifBlank { "Это ещё предстоит открыть." }}", textAlign = TextAlign.Center)
                }
            }
        }
    }
}

private fun buildEntries(config: GameConfig, engine: GameEngine, state: PlayerState, tab: CodexTab): List<CodexEntry> =
    when (tab) {
        CodexTab.PLANTS -> config.plants.map { p ->
            CodexEntry(
                p.id, p.name, Art.plantIcon(p.id), p.emoji, p.id in state.discoveredPlants, p.hint,
                listOf(
                    p.description,
                    "Рост: ${formatSeconds(engine.growthMillis(p.id) / 1000)} · урожай ×${p.yieldAmount}",
                    "Где искать: ${p.biomeSource}",
                    p.note
                )
            )
        }
        CodexTab.HOMUNCULI -> config.homunculi.map { h ->
            CodexEntry(
                h.id, h.name, Art.homCard(h.id), h.emoji, h.id in state.hatchedHomunculi,
                if (h.id in state.unlockedHomunculi) "Ядро найдено! Осталось вывести его в колбе оранжереи." else h.hint,
                listOf(h.bonusDescription, "Рецепт: ${config.describe(h.recipe)}", h.note)
            )
        }
        CodexTab.SECRETS -> config.biomes.map { b ->
            CodexEntry(
                b.id, b.name, Art.biomeArt(b.id), b.emoji, b.id in state.unlockedBiomes,
                "Туман ещё не рассеялся.",
                listOf(
                    b.description,
                    if (b.id in state.completedBiomes)
                        "Находки: " + b.rewardPlantIds.joinToString(", ") { id -> config.plants.firstOrNull { it.id == id }?.name ?: id }
                    else "Находки: неизведанно…"
                )
            )
        }
        CodexTab.RECIPES -> config.recipes.map { r ->
            CodexEntry(
                r.id, r.name, Art.itemIcon(r.id), r.emoji, r.id in state.craftedRecipes,
                "Свари это хотя бы один раз.",
                listOf(r.purpose, "Состав: ${config.describe(r.ingredients)}", "Варка: ${formatSeconds(r.craftingTimeSeconds.toLong())}")
            )
        }
    }
