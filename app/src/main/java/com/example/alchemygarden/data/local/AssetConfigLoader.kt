package com.example.alchemygarden.data.local

import android.content.Context
import com.example.alchemygarden.data.model.GameConfig
import kotlinx.serialization.json.Json

class AssetConfigLoader(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    fun loadConfig(): GameConfig {
        val raw = context.assets.open("game_config.json").bufferedReader().use { it.readText() }
        return json.decodeFromString<GameConfig>(raw)
    }
}
