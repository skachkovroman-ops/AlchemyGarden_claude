package com.example.alchemygarden.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.alchemygarden.data.model.PlayerState
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "alchemy_save")

class GameStorage(private val context: Context) {
    private val key = stringPreferencesKey("player_state")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun load(): PlayerState {
        val raw = context.dataStore.data.first()[key] ?: return PlayerState()
        return runCatching { json.decodeFromString<PlayerState>(raw) }.getOrDefault(PlayerState())
    }

    suspend fun save(state: PlayerState) {
        context.dataStore.edit { it[key] = json.encodeToString(state) }
    }
}
