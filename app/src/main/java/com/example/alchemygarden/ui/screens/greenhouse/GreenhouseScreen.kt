package com.example.alchemygarden.ui.screens.greenhouse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.alchemygarden.data.model.HomunculusConfig
import com.example.alchemygarden.data.model.PlayerState
import com.example.alchemygarden.data.model.recipe
import com.example.alchemygarden.domain.PotStage
import com.example.alchemygarden.ui.components.*
import com.example.alchemygarden.ui.theme.*
import com.example.alchemygarden.ui.viewmodel.GameViewModel

@Composable
fun GreenhouseScreen(vm: GameViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val config = vm.config
    var seedSlot by remember { mutableStateOf<Int?>(null) }
    var homId by remember { mutableStateOf<String?>(null) }

    val activeBoosts = state.activeBoosts.filter { it.expiresAtMillis > now }

    Box(Modifier.fillMaxSize()) {
        ScreenBackground("bg_greenhouse", listOf(Color(0xFFEAF2D7), Color(0xFFCFE0B0)))
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .padding(top = HubInsets.top, bottom = HubInsets.bottom, start = 12.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Оранжерея", style = MaterialTheme.typography.headlineMedium, color = Ink, modifier = Modifier.weight(1f))
                Text("✨ ${state.essence}", style = MaterialTheme.typography.titleMedium, color = Ink, fontWeight = FontWeight.Bold)
            }

            if (activeBoosts.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(activeBoosts, key = { "${it.recipeId}_${it.expiresAtMillis}" }) { b ->
                        val r = config.recipe(b.recipeId)
                        val leftSec = (((b.expiresAtMillis - now).coerceAtLeast(0L) + 999L) / 1000L)
                        ParchmentCard(Modifier.wrapContentWidth()) {
                            Text(
                                "${r?.emoji ?: "✨"} ${r?.name ?: b.recipeId} · ${formatSeconds(leftSec)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val plants = config.plants.filter { (state.inventoryPlants[it.id] ?: 0) > 0 }
                items(plants, key = { it.id }) { p ->
                    ResourceBadge(Art.plantIcon(p.id), p.emoji, state.inventoryPlants[p.id] ?: 0)
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(128.dp),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items((0 until state.potSlotCount).toList()) { slot ->
                    val pot = state.pots.firstOrNull { it.slotIndex == slot }
                    val status = pot?.let { vm.engine.potStatus(it, now, state) }
                    val plant = pot?.let { p -> config.plants.firstOrNull { it.id == p.plantId } }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        PotCard(plant, status, onClick = {
                            when {
                                pot == null -> seedSlot = slot
                                status?.stage == PotStage.READY -> vm.harvestPot(slot)
                                status?.stage == PotStage.THIRSTY -> vm.waterPot(slot)
                            }
                        })
                    }
                }
            }

            ParchmentCard(Modifier.fillMaxWidth()) {
                Text("Колбы гомункулов", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(config.homunculi, key = { it.id }) { hom -> Flask(hom, state, vm) { homId = hom.id } }
                }
            }
        }
    }

    seedSlot?.let { slot ->
        ParchmentDialog(onDismiss = { seedSlot = null }) {
            Text("Что посадить?", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                config.plants.filter { it.id in state.unlockedSeeds }.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.plantSeed(slot, p.id); seedSlot = null }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GameImage(Art.plantIcon(p.id), Modifier.size(48.dp), fallbackEmoji = p.emoji, emojiSize = 28.sp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(p.name, fontWeight = FontWeight.Bold)
                            Text(
                                "Рост: ${formatSeconds((vm.engine.growthMillis(p.id, state, now)) / 1000)} · урожай ×${p.yieldAmount}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    HorizontalDivider(color = WoodBorder.copy(alpha = 0.25f))
                }
            }
        }
    }

    homId?.let { id ->
        val hom = config.homunculi.first { it.id == id }
        HomunculusDialog(vm, hom, state) { homId = null }
    }
}

@Composable
private fun Flask(hom: HomunculusConfig, state: PlayerState, vm: GameViewModel, onClick: () -> Unit) {
    val hatched = hom.id in state.hatchedHomunculi
    val available = hom.id in state.unlockedHomunculi && !hatched
    val lvl = vm.engine.homunculusLevel(state, hom.id)
    Column(Modifier.width(84.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            hatched -> GameImage(Art.homIdle(hom.id), Modifier.size(72.dp), fallbackEmoji = hom.emoji, emojiSize = 40.sp)
            available -> GameImage(listOf("flask_egg"), Modifier.size(72.dp), fallbackEmoji = "🥚", emojiSize = 40.sp)
            else -> GameImage(listOf("flask_empty"), Modifier.size(72.dp).alpha(0.5f), fallbackEmoji = "🧪", emojiSize = 40.sp)
        }
        Text(
            if (hatched || available) "${hom.name}${if (hatched) " (Ур.$lvl)" else ""}" else "???",
            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, maxLines = 2
        )
    }
}

@Composable
private fun HomunculusDialog(vm: GameViewModel, hom: HomunculusConfig, state: PlayerState, onDismiss: () -> Unit) {
    val hatched = hom.id in state.hatchedHomunculi
    val available = hom.id in state.unlockedHomunculi && !hatched
    val curLvl = vm.engine.homunculusLevel(state, hom.id)
    val canUpgrade = hatched && curLvl < hom.maxLevel
    val upgCost = vm.engine.homunculusUpgradeCost(hom, curLvl)
    val upgEssence = vm.engine.homunculusUpgradeEssenceCost(hom, curLvl)
    val enoughForUpgrade = canUpgrade && vm.engine.hasAll(state, upgCost) && state.essence >= upgEssence

    ParchmentDialog(onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (hatched) {
                GameImage(Art.homCard(hom.id), Modifier.size(120.dp), fallbackEmoji = hom.emoji, emojiSize = 64.sp)
                Text("${hom.name} · Ур. $curLvl/${hom.maxLevel}", style = MaterialTheme.typography.titleLarge)
                if (vm.engine.isHomunculusEvolved(state, hom.id)) {
                    Text("✨ Эволюция активна", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                Text(hom.bonusDescription, textAlign = TextAlign.Center)
                if (hom.note.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(hom.note, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
                if (canUpgrade) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = WoodBorder.copy(alpha = 0.25f))
                    Spacer(Modifier.height(8.dp))
                    Text("Улучшение до Ур. ${curLvl + 1} (на Ур. ${hom.maxLevel} — эволюция):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Эссенция: ${state.essence} / $upgEssence ✨",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.essence >= upgEssence) Ink else Berry
                    )
                    Column(Modifier.fillMaxWidth()) {
                        upgCost.forEach { RequirementChip(vm.config, it, vm.engine.have(state, it)) }
                    }
                    Spacer(Modifier.height(8.dp))
                    WoodButton("Улучшить", { vm.upgradeHomunculus(hom.id) }, enabled = enoughForUpgrade)
                }
            } else if (available) {
                GameImage(listOf("flask_egg"), Modifier.size(100.dp), fallbackEmoji = "🥚", emojiSize = 56.sp)
                Text(hom.name, style = MaterialTheme.typography.titleLarge)
                Text("Ядро найдено! Нужно для выведения:", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                Column(Modifier.fillMaxWidth()) {
                    hom.recipe.forEach { RequirementChip(vm.config, it, vm.engine.have(state, it)) }
                }
                Spacer(Modifier.height(10.dp))
                WoodButton(
                    "Вывести", { vm.hatch(hom.id); onDismiss() },
                    enabled = vm.engine.hasAll(state, hom.recipe)
                )
            } else {
                GameImage(listOf("flask_empty"), Modifier.size(100.dp).alpha(0.5f), fallbackEmoji = "🧪", emojiSize = 56.sp)
                Text("Пустая колба", style = MaterialTheme.typography.titleLarge)
                Text(hom.hint.ifBlank { "Здесь ещё никого нет." }, textAlign = TextAlign.Center)
            }
        }
    }
}