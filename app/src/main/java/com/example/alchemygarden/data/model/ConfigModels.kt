package com.example.alchemygarden.data.model

import kotlinx.serialization.Serializable

@Serializable
data class GameSettings(
    val potSlots: Int = 6,
    val maxCraftQueue: Int = 4,
    val autoWaterIntervalSeconds: Int = 120,
    val maxButterfliesPerExpedition: Int = 5,
    /** Множитель ВСЕХ таймеров. Для отладки поставь 0.05 — растения растут за секунды. */
    val timeScale: Double = 1.0
)

@Serializable
enum class EffectType { AUTO_WATER, FASTER_BREWING, BONUS_HARVEST, FASTER_EXPEDITIONS }

@Serializable
data class GameConfig(
    val settings: GameSettings = GameSettings(),
    val plants: List<PlantConfig>,
    val recipes: List<RecipeConfig>,
    val biomes: List<BiomeConfig>,
    val homunculi: List<HomunculusConfig>
)

@Serializable
data class PlantConfig(
    val id: String,
    val name: String,
    val biomeSource: String,
    val growthTimeSeconds: Int,
    val yieldAmount: Int,
    val description: String,
    val emoji: String = "🌿",
    val hint: String = "",
    val note: String = ""
)

@Serializable
data class IngredientRequirement(
    val plantId: String? = null,
    val itemId: String? = null,
    val amount: Int
)

@Serializable
data class RecipeConfig(
    val id: String,
    val name: String,
    val craftingTimeSeconds: Int,
    val purpose: String,
    val ingredients: List<IngredientRequirement>,
    val emoji: String = "🧪"
)

@Serializable
data class BiomeConfig(
    val id: String,
    val name: String,
    val description: String = "",
    val emoji: String = "🌲",
    val expeditionTimeSeconds: Int,
    val requiredSupplies: List<IngredientRequirement>,
    val rewardPlantIds: List<String>,
    val rewardHomunculusIds: List<String> = emptyList(),
    val unlocksBiomeIds: List<String> = emptyList(),
    /** Позиция узла на карте, доли от ширины/высоты (0..1). */
    val mapX: Float = 0.5f,
    val mapY: Float = 0.5f
)

@Serializable
data class HomunculusConfig(
    val id: String,
    val name: String,
    val bonusDescription: String,
    val recipe: List<IngredientRequirement>,
    val effect: EffectType,
    /** AUTO_WATER: сколько горшков за цикл; остальные: доля (0.2 = 20%). */
    val effectValue: Double,
    val emoji: String = "🐾",
    val hint: String = "",
    val note: String = ""
)

fun GameConfig.plant(id: String) = plants.firstOrNull { it.id == id }
fun GameConfig.recipe(id: String) = recipes.firstOrNull { it.id == id }
fun GameConfig.biome(id: String) = biomes.firstOrNull { it.id == id }
fun GameConfig.homunculus(id: String) = homunculi.firstOrNull { it.id == id }

fun GameConfig.describe(reqs: List<IngredientRequirement>): String =
    reqs.joinToString(", ") { r ->
        val name = r.plantId?.let { plant(it)?.name } ?: r.itemId?.let { recipe(it)?.name } ?: "?"
        "${r.amount}× $name"
    }
