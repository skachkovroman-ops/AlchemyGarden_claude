package com.example.alchemygarden.data.model

import kotlinx.serialization.Serializable

@Serializable
data class GameSettings(
    val potSlots: Int = 6,
    val maxCraftQueue: Int = 4,
    val autoWaterIntervalSeconds: Int = 120,
    val maxButterfliesPerExpedition: Int = 5,
    /** Множитель ВСЕХ таймеров. Для отладки поставь 0.05 — растения растут за секунды. */
    val timeScale: Double = 1.0,
    /** Длительность цикла обновления доски заказов в секундах (по умолчанию 30 минут). */
    val orderRefreshIntervalSeconds: Int = 1800,
    /** Количество активных заказов на доске. */
    val activeOrderCount: Int = 3
)

@Serializable
enum class EffectType { AUTO_WATER, FASTER_BREWING, BONUS_HARVEST, FASTER_EXPEDITIONS }

@Serializable
enum class BoostType {
    /** Сокращает время роста растений (значение — доля, например 0.30 = на 30% быстрее). */
    FAST_GROWTH,
    /** Добавляет дополнительные броски добычи в экспедиции (значение — количество доп. бросков). */
    LUCK_EXPEDITION,
    /** Растения растут до готовности без остановки на полив (значение — 1.0). */
    WEATHER_SHIELD
}

@Serializable
enum class UpgradeTarget {
    GROWTH_SPEED,
    BREW_SPEED,
    POT_SLOTS,
    CRAFT_QUEUE
}

@Serializable
data class BoostConfig(
    val type: BoostType,
    val value: Double,
    val durationSeconds: Int
)

@Serializable
data class GameConfig(
    val settings: GameSettings = GameSettings(),
    val plants: List<PlantConfig>,
    val recipes: List<RecipeConfig>,
    val biomes: List<BiomeConfig>,
    val homunculi: List<HomunculusConfig>,
    val upgrades: List<UpgradeConfig> = emptyList(),
    val orderTemplates: List<OrderTemplate> = emptyList()
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
    val emoji: String = "🧪",
    val boost: BoostConfig? = null
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
    val note: String = "",
    val maxLevel: Int = 5,
    /** Стоимость прокачки с уровня L на L+1: базовая эссенция + ресурсы (в т.ч. из ранних биомов). */
    val upgradeBaseEssence: Int = 20,
    val upgradeIngredients: List<IngredientRequirement> = emptyList()
)

@Serializable
data class UpgradeConfig(
    val id: String,
    val name: String,
    val description: String,
    val maxLevel: Int,
    val target: UpgradeTarget,
    val bonusPerLevel: Double,
    val baseCost: List<IngredientRequirement>,
    val baseEssenceCost: Int = 0,
    val emoji: String = "🛠️"
)

@Serializable
data class OrderTemplate(
    val id: String,
    val title: String,
    val minUnlockedBiomes: Int = 1,
    val requirements: List<IngredientRequirement>,
    val rewardEssence: Int
)

fun GameConfig.plant(id: String) = plants.firstOrNull { it.id == id }
fun GameConfig.recipe(id: String) = recipes.firstOrNull { it.id == id }
fun GameConfig.biome(id: String) = biomes.firstOrNull { it.id == id }
fun GameConfig.homunculus(id: String) = homunculi.firstOrNull { it.id == id }
fun GameConfig.upgrade(id: String) = upgrades.firstOrNull { it.id == id }

fun GameConfig.describe(reqs: List<IngredientRequirement>): String =
    reqs.joinToString(", ") { r ->
        val name = r.plantId?.let { plant(it)?.name } ?: r.itemId?.let { recipe(it)?.name } ?: "?"
        "${r.amount}× $name"
    }