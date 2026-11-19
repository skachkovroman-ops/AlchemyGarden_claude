package com.example.alchemygarden.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Track(val resName: String) {
    GREENHOUSE("music_greenhouse"),
    LABORATORY("music_laboratory"),
    MAP("music_map"),
    ENCYCLOPEDIA("music_encyclopedia")
}

/**
 * Фоновая музыка: один трек играет по кругу, при смене экрана плавно перетекает в следующий.
 * Файлы лежат в res/raw (music_*.ogg). Если файла нет — просто тишина.
 * Вызывать только с главного потока.
 */
@SuppressLint("DiscouragedApi")
class MusicManager(private val context: Context, private val scope: CoroutineScope) {

    private class Voice(val track: Track, val player: MediaPlayer, var volume: Float = 0f)

    private var desired: Track? = null
    private var enabled = true
    private var paused = false

    private var current: Voice? = null
    private val fading = mutableListOf<Voice>()
    private var rampJob: Job? = null

    /** Громкость музыки относительно максимума: звуки игры должны быть слышнее. */
    var targetVolume = 0.55f

    fun setTrack(track: Track?) { desired = track; sync() }
    fun setEnabled(on: Boolean) { enabled = on; sync() }
    fun setPaused(p: Boolean) { paused = p; sync() }

    private fun sync() {
        val want = if (enabled && !paused) desired else null
        if (want == current?.track) return

        // текущий голос уходит в затухание, новый (если нужен) нарастает
        current?.let { fading += it }
        current = want?.let { createVoice(it) }

        rampJob?.cancel()
        rampJob = scope.launch {
            val fadeStart = fading.associateWith { it.volume }
            val steps = 30
            repeat(steps) { i ->
                val f = (i + 1) / steps.toFloat()
                current?.let { it.volume = f * targetVolume; it.player.setVolume(it.volume, it.volume) }
                fading.forEach {
                    it.volume = (fadeStart[it] ?: 0f) * (1f - f)
                    it.player.setVolume(it.volume, it.volume)
                }
                delay(50)
            }
            fading.forEach { release(it) }
            fading.clear()
        }
    }

    private fun createVoice(track: Track): Voice? {
        val res = context.resources.getIdentifier(track.resName, "raw", context.packageName)
        if (res == 0) return null
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val mp = runCatching { MediaPlayer.create(context, res, attrs, 0) }.getOrNull() ?: return null
        mp.isLooping = true
        mp.setVolume(0f, 0f)
        mp.start()
        return Voice(track, mp)
    }

    private fun release(v: Voice) {
        runCatching { v.player.stop() }
        runCatching { v.player.release() }
    }

    fun release() {
        rampJob?.cancel()
        current?.let { release(it) }
        fading.forEach { release(it) }
        current = null
        fading.clear()
    }
}
