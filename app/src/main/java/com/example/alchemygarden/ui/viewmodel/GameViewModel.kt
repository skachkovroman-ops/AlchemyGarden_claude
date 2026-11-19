package com.example.alchemygarden.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.alchemygarden.data.local.AssetConfigLoader
import com.example.alchemygarden.data.local.GameStorage
import com.example.alchemygarden.data.model.GameConfig
import com.example.alchemygarden.data.model.PlayerState
import com.example.alchemygarden.data.model.biome
import com.example.alchemygarden.domain.GameEngine
import com.example.alchemygarden.domain.GameEvent
import com.example.alchemygarden.domain.Outcome
import com.example.alchemygarden.notifications.ExpeditionNotifier
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class GameViewModel(
    val config: GameConfig,
    private val storage: GameStorage,
    private val notifier: ExpeditionNotifier,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    val engine = GameEngine(config)

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    /** «Текущее время» для UI — обновляется раз в секунду, пока игра открыта. */
    private val _now = MutableStateFlow(clock())
    val now: StateFlow<Long> = _now.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            _state.value = storage.load()
            val t = clock()
            _now.value = t
            commit(engine.tick(_state.value, t))
            _loaded.value = true
            launch { _state.debounce(500).collect { storage.save(it) } }
            while (true) {
                delay(1000)
                val n = clock()
                _now.value = n
                commit(engine.tick(_state.value, n))
            }
        }
    }

    private fun commit(o: Outcome): Boolean {
        _state.value = o.state
        o.events.forEach { _events.tryEmit(it) }
        return o.ok
    }

    private fun act(block: (PlayerState, Long) -> Outcome): Boolean =
        commit(block(_state.value, clock()))

    // ── Действия игрока ──
    fun plantSeed(slot: Int, plantId: String) { act { s, t -> engine.plantSeed(s, slot, plantId, t) } }
    fun waterPot(slot: Int) { act { s, t -> engine.waterPot(s, slot, t) } }
    fun harvestPot(slot: Int) { act { s, t -> engine.harvestPot(s, slot, t) } }
    fun hatch(homId: String) { act { s, _ -> engine.hatchHomunculus(s, homId) } }
    fun craft(recipeId: String) { act { s, t -> engine.enqueueCraft(s, recipeId, t) } }
    fun catchButterfly() { act { s, t -> engine.catchButterfly(s, t) } }

    fun startExpedition(biomeId: String) {
        val biome = config.biome(biomeId) ?: return
        if (act { s, t -> engine.startExpedition(s, biomeId, t) }) {
            _state.value.activeExpedition?.let {
                notifier.schedule(biome.name, it.startedAtMillis + it.durationMillis)
            }
        }
    }

    fun claimExpedition() {
        if (act { s, t -> engine.claimExpedition(s, t) }) notifier.cancel()
    }

    /** Вызывается из Activity.onStop — чтобы прогресс не потерялся при закрытии. */
    fun saveNow() {
        if (!_loaded.value) return
        viewModelScope.launch { storage.save(_state.value) }
    }

    class Factory(private val appContext: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val config = AssetConfigLoader(appContext).loadConfig()
            return GameViewModel(config, GameStorage(appContext), ExpeditionNotifier(appContext)) as T
        }
    }
}
