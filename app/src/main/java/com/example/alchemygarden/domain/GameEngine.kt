package com.example.alchemygarden.domain

import com.example.alchemygarden.data.model.*
import kotlin.random.Random

enum class Sfx(val resName: String) {
    TAP("sfx_tap"), PLANT("sfx_plant"), WATER("sfx_water"), HARVEST("sfx_harvest"),
    BREW_START("sfx_brew_start"), BREW_DONE("sfx_brew_done"), PAGE("sfx_page"),
    EXPEDITION_START("sfx_expedition_start"), EXPEDITION_DONE("sfx_expedition_done"),
    BUTTERFLY("sfx_butterfly"), UNLOCK("sfx_unlock"), ERROR("sfx_error")
}

data class GameEvent(val text: String, val sfx: Sfx? = null)

data class Outcome(
    val state: PlayerState,
    val events: List<GameEvent> = emptyList(),
    val ok: Boolean = true
)

enum class PotStage { SPROUT, THIRSTY, GROWING, READY }
data class PotStatus(val stage: PotStage, val progress: Float, val secondsLeft: Long)
data class ExpeditionStatus(val progress: Float, val secondsLeft: Long, val finished: Boolean)

/**
 * Вся игровая логика — чистые функции: (состояние, время) -> новое состояние.
 * Никакого Android и никаких таймеров: время всегда приходит параметром `now`,
 * поэтому офлайн-прогресс считается сам, а логику легко тестировать.
 */
class GameEngine(val config: GameConfig, private val random: Random = Random.Default) {

    private val plants = config.plants.associateBy { it.id }
    private val recipes = config.recipes.associateBy { it.id }
    private val biomes = config.biomes.associateBy { it.id }
    private val homunculi = config.homunculi.associateBy { it.id }
    private val upgrades = config.upgrades.associateBy { it.id }

    private fun ms(seconds: Int): Long =
        (seconds * 1000L * config.settings.timeScale).toLong().coerceAtLeast(1L)

    private fun ceilSeconds(millis: Long): Long = (millis.coerceAtLeast(0L) + 999L) / 1000L

    private fun fail(state: PlayerState, text: String) =
        Outcome(state, listOf(GameEvent(text, Sfx.ERROR)), ok = false)

    // ───────── Уровни, улучшения и бусты ─────────

    fun homunculusLevel(state: PlayerState, homId: String): Int =
        (state.homunculusLevels[homId] ?: 1).coerceAtLeast(1)

    fun isHomunculusEvolved(state: PlayerState, homId: String): Boolean =
        homunculusLevel(state, homId) >= 5

    fun effect(state: PlayerState, type: EffectType): Double =
        config.homunculi
            .filter { it.id in state.hatchedHomunculi && it.effect == type }
            .sumOf { hom ->
                val lvl = homunculusLevel(state, hom.id)
                val levelMult = 1.0 + (lvl - 1) * 0.25
                val evoBonus = if (lvl >= 5) {
                    when (type) {
                        EffectType.AUTO_WATER -> 1.0
                        EffectType.FASTER_BREWING -> 0.10
                        EffectType.BONUS_HARVEST -> 0.15
                        EffectType.FASTER_EXPEDITIONS -> 0.10
                    }
                } else 0.0
                hom.effectValue * levelMult + evoBonus
            }

    fun upgradeLevel(state: PlayerState, upgradeId: String): Int =
        state.upgradeLevels[upgradeId] ?: 0

    fun upgradeBonus(state: PlayerState, target: UpgradeTarget): Double =
        config.upgrades
            .filter { it.target == target }
            .sumOf { cfg -> cfg.bonusPerLevel * upgradeLevel(state, cfg.id) }

    fun maxCraftQueue(state: PlayerState): Int =
        (config.settings.maxCraftQueue + upgradeBonus(state, UpgradeTarget.CRAFT_QUEUE).toInt())
            .coerceAtLeast(1)

    fun upgradeCost(cfg: UpgradeConfig, currentLevel: Int): List<IngredientRequirement> {
        val mult = currentLevel + 1
        return cfg.baseCost.map { it.copy(amount = (it.amount * mult).coerceAtLeast(1)) }
    }

    fun upgradeEssenceCost(cfg: UpgradeConfig, currentLevel: Int): Int =
        (cfg.baseEssenceCost * (currentLevel + 1)).coerceAtLeast(0)

    fun buyUpgrade(state: PlayerState, upgradeId: String): Outcome {
        val cfg = upgrades[upgradeId] ?: return fail(state, "Неизвестное улучшение")
        val curLvl = upgradeLevel(state, upgradeId)
        if (curLvl >= cfg.maxLevel) return fail(state, "Достигнут максимальный уровень")
        val cost = upgradeCost(cfg, curLvl)
        val essenceCost = upgradeEssenceCost(cfg, curLvl)
        if (!hasAll(state, cost) || state.essence < essenceCost) {
            return fail(state, "Не хватает ресурсов или эссенции")
        }
        val nextLvl = curLvl + 1
        val consumed = consume(state, cost).copy(
            essence = state.essence - essenceCost,
            upgradeLevels = state.upgradeLevels + (upgradeId to nextLvl)
        )
        val updated = if (cfg.target == UpgradeTarget.POT_SLOTS) {
            val extraSlots = upgradeBonus(consumed, UpgradeTarget.POT_SLOTS).toInt()
            consumed.copy(potSlotCount = (config.settings.potSlots + extraSlots).coerceAtLeast(consumed.potSlotCount))
        } else consumed
        return Outcome(updated, listOf(GameEvent("Улучшено: ${cfg.name} (Ур. $nextLvl)", Sfx.UNLOCK)))
    }

    fun homunculusUpgradeCost(hom: HomunculusConfig, currentLevel: Int): List<IngredientRequirement> {
        val mult = currentLevel
        return hom.upgradeIngredients.map { it.copy(amount = (it.amount * mult).coerceAtLeast(1)) }
    }

    fun homunculusUpgradeEssenceCost(hom: HomunculusConfig, currentLevel: Int): Int =
        (hom.upgradeBaseEssence * currentLevel).coerceAtLeast(1)

    fun upgradeHomunculus(state: PlayerState, homId: String): Outcome {
        val hom = homunculi[homId] ?: return fail(state, "Неизвестное существо")
        if (homId !in state.hatchedHomunculi) return fail(state, "Сначала выведите существо")
        val curLvl = homunculusLevel(state, homId)
        if (curLvl >= hom.maxLevel) return fail(state, "Достигнут максимум эволюции")
        val cost = homunculusUpgradeCost(hom, curLvl)
        val essenceCost = homunculusUpgradeEssenceCost(hom, curLvl)
        if (!hasAll(state, cost) || state.essence < essenceCost) {
            return fail(state, "Не хватает материалов или эссенции")
        }
        val nextLvl = curLvl + 1
        val next = consume(state, cost).copy(
            essence = state.essence - essenceCost,
            homunculusLevels = state.homunculusLevels + (homId to nextLvl)
        )
        val msg = if (nextLvl == hom.maxLevel) {
            "${hom.name} достиг эволюции (Ур. $nextLvl)!"
        } else {
            "${hom.name} улучшен до Ур. $nextLvl"
        }
        return Outcome(next, listOf(GameEvent(msg, Sfx.UNLOCK)))
    }

    fun activeBoostValue(state: PlayerState, type: BoostType, now: Long): Double =
        state.activeBoosts
            .filter { it.type == type && it.expiresAtMillis > now }
            .sumOf { it.value }

    fun hasWeatherShield(state: PlayerState, now: Long): Boolean =
        activeBoostValue(state, BoostType.WEATHER_SHIELD, now) > 0.0

    fun drinkElixir(state: PlayerState, recipeId: String, now: Long): Outcome {
        val recipe = recipes[recipeId] ?: return fail(state, "Неизвестный эликсир")
        val boost = recipe.boost ?: return fail(state, "Этот предмет нельзя выпить как усилитель")
        val count = state.inventorySupplies[recipeId] ?: 0
        if (count <= 0) return fail(state, "Нет на складе")

        val durationMs = ms(boost.durationSeconds)
        val cleaned = state.activeBoosts.filter { it.expiresAtMillis > now }
        val newBoost = ActiveBoost(
            recipeId = recipe.id,
            type = boost.type,
            value = boost.value,
            expiresAtMillis = now + durationMs
        )
        val next = state.copy(
            inventorySupplies = state.inventorySupplies.dec(recipeId, 1),
            activeBoosts = cleaned + newBoost
        )
        return Outcome(next, listOf(GameEvent("Применён эликсир: ${recipe.name}", Sfx.BREW_DONE)))
    }

    private fun pruneBoosts(state: PlayerState, now: Long): PlayerState {
        val active = state.activeBoosts.filter { it.expiresAtMillis > now }
        return if (active.size == state.activeBoosts.size) state else state.copy(activeBoosts = active)
    }

    // ───────── Инвентарь ─────────

    fun have(state: PlayerState, req: IngredientRequirement): Int = when {
        req.plantId != null -> state.inventoryPlants[req.plantId] ?: 0
        req.itemId != null -> state.inventorySupplies[req.itemId] ?: 0
        else -> 0
    }

    fun hasAll(state: PlayerState, reqs: List<IngredientRequirement>) =
        reqs.all { have(state, it) >= it.amount }

    private fun Map<String, Int>.dec(key: String, n: Int): Map<String, Int> {
        val left = (this[key] ?: 0) - n
        return if (left <= 0) this - key else this + (key to left)
    }

    private fun consume(state: PlayerState, reqs: List<IngredientRequirement>): PlayerState {
        var p = state.inventoryPlants
        var s = state.inventorySupplies
        reqs.forEach { r ->
            r.plantId?.let { p = p.dec(it, r.amount) }
            r.itemId?.let { s = s.dec(it, r.amount) }
        }
        return state.copy(inventoryPlants = p, inventorySupplies = s)
    }

    private fun addPlants(state: PlayerState, add: Map<String, Int>): PlayerState {
        if (add.isEmpty()) return state
        val inv = state.inventoryPlants.toMutableMap()
        add.forEach { (id, n) -> inv[id] = (inv[id] ?: 0) + n }
        return state.copy(inventoryPlants = inv, discoveredPlants = state.discoveredPlants + add.keys)
    }

    private fun lootText(loot: Map<String, Int>) =
        loot.entries.joinToString(", ") { "${plants[it.key]?.name ?: it.key} ×${it.value}" }

    private fun rollLoot(ids: List<String>, picks: Int): Map<String, Int> {
        if (ids.isEmpty() || picks <= 0) return emptyMap()
        val m = mutableMapOf<String, Int>()
        repeat(picks) { val id = ids.random(random); m[id] = (m[id] ?: 0) + 1 }
        return m
    }

    // ───────── Оранжерея ─────────

    fun growthMillis(plantId: String, state: PlayerState? = null, now: Long = 0L): Long {
        val base = ms(plants[plantId]?.growthTimeSeconds ?: 1)
        if (state == null) return base
        val totalReduction = (upgradeBonus(state, UpgradeTarget.GROWTH_SPEED) +
            activeBoostValue(state, BoostType.FAST_GROWTH, now)).coerceAtMost(0.85)
        return (base * (1.0 - totalReduction)).toLong().coerceAtLeast(1L)
    }

    /**
     * Рост в два этапа: первая половина идёт сама, потом растение «просит пить»
     * и вторая половина идёт только после полива (либо сразу при активном WEATHER_SHIELD).
     */
    fun potStatus(pot: ActivePot, now: Long, state: PlayerState? = null): PotStatus {
        val g = growthMillis(pot.plantId, state, now)
        val shield = state != null && hasWeatherShield(state, now)
        if (shield) {
            val readyAt = pot.plantedAtMillis + g
            if (now >= readyAt) return PotStatus(PotStage.READY, 1f, 0)
            val p = ((now - pot.plantedAtMillis).toFloat() / g).coerceIn(0f, 1f)
            return PotStatus(PotStage.GROWING, p, ceilSeconds(readyAt - now))
        }
        val half = g / 2
        val phase1End = pot.plantedAtMillis + half
        if (now < phase1End) {
            val p = ((now - pot.plantedAtMillis).toFloat() / g).coerceIn(0f, 0.5f)
            return PotStatus(PotStage.SPROUT, p, ceilSeconds(phase1End - now + (g - half)))
        }
        val watered = pot.wateredAtMillis
            ?: return PotStatus(PotStage.THIRSTY, 0.5f, ceilSeconds(g - half))
        val start = maxOf(watered, phase1End)
        val readyAt = start + (g - half)
        if (now >= readyAt) return PotStatus(PotStage.READY, 1f, 0)
        val p = (0.5f + (now - start).toFloat() / g).coerceIn(0.5f, 1f)
        return PotStatus(PotStage.GROWING, p, ceilSeconds(readyAt - now))
    }

    fun plantSeed(state: PlayerState, slot: Int, plantId: String, now: Long): Outcome {
        val plant = plants[plantId] ?: return fail(state, "Неизвестное растение")
        if (plantId !in state.unlockedSeeds) return fail(state, "Семена ещё не найдены")
        if (slot !in 0 until state.potSlotCount) return fail(state, "Нет такого горшка")
        if (state.pots.any { it.slotIndex == slot }) return fail(state, "Горшок занят")
        return Outcome(
            state.copy(pots = state.pots + ActivePot(slot, plantId, now)),
            listOf(GameEvent("Посажено: ${plant.name}", Sfx.PLANT))
        )
    }

    fun waterPot(state: PlayerState, slot: Int, now: Long): Outcome {
        val pot = state.pots.firstOrNull { it.slotIndex == slot } ?: return fail(state, "Горшок пуст")
        if (potStatus(pot, now, state).stage != PotStage.THIRSTY) return Outcome(state, ok = false)
        return Outcome(
            state.copy(pots = state.pots.map { if (it.slotIndex == slot) it.copy(wateredAtMillis = now) else it }),
            listOf(GameEvent("", Sfx.WATER))
        )
    }

    fun harvestPot(state: PlayerState, slot: Int, now: Long): Outcome {
        val pot = state.pots.firstOrNull { it.slotIndex == slot } ?: return fail(state, "Горшок пуст")
        val plant = plants[pot.plantId] ?: return fail(state, "Неизвестное растение")
        if (potStatus(pot, now, state).stage != PotStage.READY) return fail(state, "Ещё не созрело")
        val harvestEffect = effect(state, EffectType.BONUS_HARVEST)
        val roll = random.nextDouble()
        val bonus = when {
            roll < (harvestEffect - 1.0).coerceAtLeast(0.0) -> 2
            roll < harvestEffect -> 1
            else -> 0
        }
        val amount = plant.yieldAmount + bonus
        val next = addPlants(
            state.copy(pots = state.pots.filterNot { it.slotIndex == slot }),
            mapOf(plant.id to amount)
        )
        val text = "Собрано: ${plant.name} ×$amount" + if (bonus > 0) " (богатый урожай!)" else ""
        return Outcome(next, listOf(GameEvent(text, Sfx.HARVEST)))
    }

    fun hatchHomunculus(state: PlayerState, id: String): Outcome {
        val hom = homunculi[id] ?: return fail(state, "Неизвестное существо")
        if (id !in state.unlockedHomunculi) return fail(state, "Ядро ещё не найдено")
        if (id in state.hatchedHomunculi) return fail(state, "Уже выведен")
        if (!hasAll(state, hom.recipe)) return fail(state, "Не хватает ингредиентов")
        val next = consume(state, hom.recipe).copy(
            hatchedHomunculi = state.hatchedHomunculi + id,
            homunculusLevels = state.homunculusLevels + (id to 1)
        )
        return Outcome(next, listOf(GameEvent("Вылупился: ${hom.name}!", Sfx.UNLOCK)))
    }

    // ───────── Лаборатория ─────────

    fun craftDurationMillis(recipe: RecipeConfig, state: PlayerState): Long {
        val totalReduction = (effect(state, EffectType.FASTER_BREWING) +
            upgradeBonus(state, UpgradeTarget.BREW_SPEED)).coerceAtMost(0.85)
        return (ms(recipe.craftingTimeSeconds) * (1.0 - totalReduction))
            .toLong().coerceAtLeast(1L)
    }

    fun craftBlocker(state: PlayerState, recipe: RecipeConfig): String? = when {
        state.craftQueue.size >= maxCraftQueue(state) -> "Очередь варки полна"
        !hasAll(state, recipe.ingredients) -> "Не хватает ингредиентов"
        else -> null
    }

    /** Ингредиенты списываются сразу, задания варятся по очереди одно за другим. */
    fun enqueueCraft(state: PlayerState, recipeId: String, now: Long): Outcome {
        val recipe = recipes[recipeId] ?: return fail(state, "Неизвестный рецепт")
        craftBlocker(state, recipe)?.let { return fail(state, it) }
        val lastEnd = state.craftQueue.maxOfOrNull { it.startedAtMillis + it.durationMillis } ?: now
        val job = CraftJob(recipeId, maxOf(lastEnd, now), craftDurationMillis(recipe, state))
        val next = consume(state, recipe.ingredients).copy(craftQueue = state.craftQueue + job)
        return Outcome(next, listOf(GameEvent("", Sfx.BREW_START)))
    }

    private fun collectFinished(state: PlayerState, now: Long): Outcome {
        val (done, pending) = state.craftQueue.partition { it.startedAtMillis + it.durationMillis <= now }
        if (done.isEmpty()) return Outcome(state)
        var supplies = state.inventorySupplies
        done.forEach { supplies = supplies + (it.recipeId to ((supplies[it.recipeId] ?: 0) + 1)) }
        val events = done.map { GameEvent("Готово: ${recipes[it.recipeId]?.name ?: it.recipeId}", Sfx.BREW_DONE) }
        return Outcome(
            state.copy(
                craftQueue = pending,
                inventorySupplies = supplies,
                craftedRecipes = state.craftedRecipes + done.map { it.recipeId }
            ),
            events
        )
    }

    // ───────── Доска заказов ─────────

    private fun generateOrders(state: PlayerState, now: Long): PlayerState {
        if (config.orderTemplates.isEmpty()) return state.copy(lastOrderRefreshMillis = now)
        val unlockedCount = state.unlockedBiomes.size.coerceAtLeast(1)
        val eligible = config.orderTemplates.filter { it.minUnlockedBiomes <= unlockedCount }
            .ifEmpty { config.orderTemplates }
        val picked = eligible.shuffled(random).take(config.settings.activeOrderCount.coerceAtLeast(1))
        val orders = picked.mapIndexed { idx, tpl ->
            ActiveOrder(
                id = "${tpl.id}_${now}_$idx",
                title = tpl.title,
                requirements = tpl.requirements,
                rewardEssence = tpl.rewardEssence
            )
        }
        return state.copy(currentOrders = orders, lastOrderRefreshMillis = now)
    }

    private fun refreshOrdersIfDue(state: PlayerState, now: Long): PlayerState {
        val interval = ms(config.settings.orderRefreshIntervalSeconds)
        if (state.currentOrders.isEmpty() || now - state.lastOrderRefreshMillis >= interval) {
            return generateOrders(state, now)
        }
        return state
    }

    fun manualRefreshOrders(state: PlayerState, now: Long, essenceCost: Int = 5): Outcome {
        if (config.orderTemplates.isEmpty()) return fail(state, "Заказы пока недоступны")
        if (state.essence < essenceCost) return fail(state, "Нужно $essenceCost эссенции для обновления")
        val next = generateOrders(state.copy(essence = state.essence - essenceCost), now)
        return Outcome(next, listOf(GameEvent("Список заказов обновлён", Sfx.PAGE)))
    }

    fun completeOrder(state: PlayerState, orderId: String, now: Long): Outcome {
        val order = state.currentOrders.firstOrNull { it.id == orderId }
            ?: return fail(state, "Заказ не найден")
        if (!hasAll(state, order.requirements)) return fail(state, "Не хватает ресурсов для заказа")
        val remaining = state.currentOrders.filterNot { it.id == orderId }
        val consumed = consume(state, order.requirements).copy(
            essence = state.essence + order.rewardEssence,
            currentOrders = remaining
        )
        val toppedUp = if (consumed.currentOrders.isEmpty()) generateOrders(consumed, now) else consumed
        return Outcome(
            toppedUp,
            listOf(GameEvent("Заказ выполнен: +${order.rewardEssence} эссенции", Sfx.UNLOCK))
        )
    }

    // ───────── Экспедиции ─────────

    fun expeditionDurationMillis(biome: BiomeConfig, state: PlayerState): Long =
        (ms(biome.expeditionTimeSeconds) * (1.0 - effect(state, EffectType.FASTER_EXPEDITIONS)))
            .toLong().coerceAtLeast(1L)

    fun expeditionBlocker(state: PlayerState, biome: BiomeConfig): String? = when {
        biome.id !in state.unlockedBiomes -> "Тропа скрыта туманом"
        state.activeExpedition != null -> "Алхимик уже в пути"
        !hasAll(state, biome.requiredSupplies) -> "Не хватает припасов"
        else -> null
    }

    fun expeditionStatus(exp: ActiveExpedition, now: Long): ExpeditionStatus {
        val end = exp.startedAtMillis + exp.durationMillis
        val p = ((now - exp.startedAtMillis).toFloat() / exp.durationMillis).coerceIn(0f, 1f)
        return ExpeditionStatus(p, ceilSeconds(end - now), now >= end)
    }

    fun startExpedition(state: PlayerState, biomeId: String, now: Long): Outcome {
        val biome = biomes[biomeId] ?: return fail(state, "Неизвестный регион")
        expeditionBlocker(state, biome)?.let { return fail(state, it) }
        val exp = ActiveExpedition(biomeId, now, expeditionDurationMillis(biome, state))
        val next = consume(state, biome.requiredSupplies).copy(activeExpedition = exp)
        return Outcome(next, listOf(GameEvent("Алхимик отправился: ${biome.name}", Sfx.EXPEDITION_START)))
    }

    fun claimExpedition(state: PlayerState, now: Long): Outcome {
        val exp = state.activeExpedition ?: return fail(state, "Нет активной экспедиции")
        val biome = biomes[exp.biomeId] ?: return fail(state, "Неизвестный регион")
        if (!expeditionStatus(exp, now).finished) return fail(state, "Алхимик ещё в пути")

        val events = mutableListOf(GameEvent("Алхимик вернулся: ${biome.name}!", Sfx.EXPEDITION_DONE))
        biome.rewardPlantIds.filter { it !in state.unlockedSeeds }
            .forEach { events += GameEvent("Найдены семена: ${plants[it]?.name}", Sfx.UNLOCK) }
        biome.rewardHomunculusIds.filter { it !in state.unlockedHomunculi }
            .forEach { events += GameEvent("Найдено ядро: ${homunculi[it]?.name}", Sfx.UNLOCK) }
        biome.unlocksBiomeIds.filter { it !in state.unlockedBiomes }
            .forEach { events += GameEvent("Туман рассеялся: ${biomes[it]?.name}", Sfx.UNLOCK) }

        val luckExtra = activeBoostValue(state, BoostType.LUCK_EXPEDITION, now).toInt().coerceAtLeast(0)
        val picks = random.nextInt(2, 4) + luckExtra
        val loot = rollLoot(biome.rewardPlantIds, picks)
        if (loot.isNotEmpty()) events += GameEvent("Находки: ${lootText(loot)}")

        val next = addPlants(
            state.copy(
                activeExpedition = null,
                unlockedSeeds = state.unlockedSeeds + biome.rewardPlantIds,
                discoveredPlants = state.discoveredPlants + biome.rewardPlantIds,
                unlockedHomunculi = state.unlockedHomunculi + biome.rewardHomunculusIds,
                unlockedBiomes = state.unlockedBiomes + biome.unlocksBiomeIds,
                completedBiomes = state.completedBiomes + biome.id
            ),
            loot
        )
        return Outcome(next, events)
    }

    fun catchButterfly(state: PlayerState, now: Long): Outcome {
        val exp = state.activeExpedition ?: return Outcome(state, ok = false)
        val biome = biomes[exp.biomeId] ?: return Outcome(state, ok = false)
        if (expeditionStatus(exp, now).finished ||
            exp.butterfliesCaught >= config.settings.maxButterfliesPerExpedition
        ) return Outcome(state, ok = false)
        val loot = rollLoot(biome.rewardPlantIds, 1)
        val next = addPlants(state.copy(activeExpedition = exp.copy(butterfliesCaught = exp.butterfliesCaught + 1)), loot)
        return Outcome(next, listOf(GameEvent("Бабочка принесла: ${lootText(loot)}", Sfx.BUTTERFLY)))
    }

    // ───────── Тик ─────────

    private fun autoWater(state: PlayerState, now: Long): Outcome {
        val count = effect(state, EffectType.AUTO_WATER).toInt()
        if (count <= 0) return Outcome(state)
        if (now - state.lastAutoWaterAtMillis < ms(config.settings.autoWaterIntervalSeconds)) return Outcome(state)
        val slots = state.pots
            .filter { potStatus(it, now, state).stage == PotStage.THIRSTY }
            .sortedBy { it.slotIndex }.take(count).map { it.slotIndex }.toSet()
        if (slots.isEmpty()) return Outcome(state)
        return Outcome(
            state.copy(
                pots = state.pots.map { if (it.slotIndex in slots) it.copy(wateredAtMillis = now) else it },
                lastAutoWaterAtMillis = now
            ),
            listOf(GameEvent("Капельный пухлик полил растения", Sfx.WATER))
        )
    }

    /** Вызывается раз в секунду и при запуске игры: догоняет всё, что произошло «пока тебя не было». */
    fun tick(state: PlayerState, now: Long): Outcome {
        val pruned = pruneBoosts(state, now)
        val withOrders = refreshOrdersIfDue(pruned, now)
        val a = collectFinished(withOrders, now)
        val b = autoWater(a.state, now)
        return Outcome(b.state, a.events + b.events)
    }
}