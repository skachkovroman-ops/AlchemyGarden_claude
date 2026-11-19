package com.example.alchemygarden.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ActivePot(
    val slotIndex: Int,
    val plantId: String,
    val plantedAtMillis: Long,
    /** Время полива (вторая половина роста идёт только после полива). */
    val wateredAtMillis: Long? = null
)

@Serializable
data class CraftJob(
    val recipeId: String,
    val startedAtMillis: Long,
    val durationMillis: Long
)

@Serializable
data class ActiveExpedition(
    val biomeId: String,
    val startedAtMillis: Long,
    val durationMillis: Long,
    val butterfliesCaught: Int = 0
)

@Serializable
data class PlayerState(
    val version: Int = 1,
    /** Растения, которые можно сажать (семена бесконечные). */
    val unlockedSeeds: Set<String> = setOf("plant_01", "plant_02"),
    val discoveredPlants: Set<String> = setOf("plant_01", "plant_02", "plant_03", "plant_05"),
    val unlockedBiomes: Set<String> = setOf("biome_01"),
    val completedBiomes: Set<String> = emptySet(),
    /** Найденные «ядра»: можно выводить этих гомункулов. */
    val unlockedHomunculi: Set<String> = emptySet(),
    val hatchedHomunculi: Set<String> = emptySet(),
    val craftedRecipes: Set<String> = emptySet(),
    // Стартовый запас решает «курицу и яйцо»: смолянка и мох нужны для первой экспедиции,
    // а семена этих растений находятся только в первой экспедиции.
    val inventoryPlants: Map<String, Int> = mapOf(
        "plant_01" to 4, "plant_02" to 4, "plant_03" to 1, "plant_05" to 1
    ),
    val inventorySupplies: Map<String, Int> = emptyMap(),
    val potSlotCount: Int = 6,
    val pots: List<ActivePot> = emptyList(),
    val craftQueue: List<CraftJob> = emptyList(),
    val activeExpedition: ActiveExpedition? = null,
    val lastAutoWaterAtMillis: Long = 0L,
    /** Общий выключатель музыки и звуков. */
    val soundEnabled: Boolean = true
)
