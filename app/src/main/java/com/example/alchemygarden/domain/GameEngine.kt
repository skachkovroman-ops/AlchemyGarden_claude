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

    private fun ms(seconds: Int): Long =
        (seconds * 1000L * config.settings.timeScale).toLong().coerceAtLeast(1L)

    private fun ceilSeconds(millis: Long): Long = (millis.coerceAtLeast(0L) + 999L) / 1000L

    private fun fail(state: PlayerState, text: String) =
        Outcome(state, listOf(GameEvent(text, Sfx.ERROR)), ok = false)

    // ───────── Бонусы гомункулов ─────────

    fun effect(state: PlayerState, type: EffectType): Double =
        config.homunculi
            .filter { it.id in state.hatchedHomunculi && it.effect == type }
            .sumOf { it.effectValue }

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
        if (ids.isEmpty()) return emptyMap()
        val m = mutableMapOf<String, Int>()
        repeat(picks) { val id = ids.random(random); m[id] = (m[id] ?: 0) + 1 }
        return m
    }

    // ───────── Оранжерея ─────────

    fun growthMillis(plantId: String): Long = ms(plants[plantId]?.growthTimeSeconds ?: 1)

    /**
     * Рост в два этапа: первая половина идёт сама, потом растение «просит пить»
     * и вторая половина идёт только после полива.
     */
    fun potStatus(pot: ActivePot, now: Long): PotStatus {
        val g = growthMillis(pot.plantId)
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
        if (potStatus(pot, now).stage != PotStage.THIRSTY) return Outcome(state, ok = false)
        return Outcome(
            state.copy(pots = state.pots.map { if (it.slotIndex == slot) it.copy(wateredAtMillis = now) else it }),
            listOf(GameEvent("", Sfx.WATER))
        )
    }

    fun harvestPot(state: PlayerState, slot: Int, now: Long): Outcome {
        val pot = state.pots.firstOrNull { it.slotIndex == slot } ?: return fail(state, "Горшок пуст")
        val plant = plants[pot.plantId] ?: return fail(state, "Неизвестное растение")
        if (potStatus(pot, now).stage != PotStage.READY) return fail(state, "Ещё не созрело")
        val bonus = if (random.nextDouble() < effect(state, EffectType.BONUS_HARVEST)) 1 else 0
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
        val next = consume(state, hom.recipe).copy(hatchedHomunculi = state.hatchedHomunculi + id)
        return Outcome(next, listOf(GameEvent("Вылупился: ${hom.name}!", Sfx.UNLOCK)))
    }

    // ───────── Лаборатория ─────────

    fun craftDurationMillis(recipe: RecipeConfig, state: PlayerState): Long =
        (ms(recipe.craftingTimeSeconds) * (1.0 - effect(state, EffectType.FASTER_BREWING)))
            .toLong().coerceAtLeast(1L)

    fun craftBlocker(state: PlayerState, recipe: RecipeConfig): String? = when {
        state.craftQueue.size >= config.settings.maxCraftQueue -> "Очередь варки полна"
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

        val loot = rollLoot(biome.rewardPlantIds, random.nextInt(2, 4))
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
            .filter { potStatus(it, now).stage == PotStage.THIRSTY }
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
        val a = collectFinished(state, now)
        val b = autoWater(a.state, now)
        return Outcome(b.state, a.events + b.events)
    }
}
