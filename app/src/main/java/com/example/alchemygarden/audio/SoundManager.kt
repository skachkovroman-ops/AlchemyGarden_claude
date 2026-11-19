package com.example.alchemygarden.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.example.alchemygarden.domain.Sfx

/** Звуки лежат в res/raw. Если файла нет — звук просто молча пропускается. */
@SuppressLint("DiscouragedApi")
class SoundManager(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build()
    private val ids = HashMap<Sfx, Int>()

    var enabled = true

    init {
        Sfx.values().forEach { sfx ->
            val res = context.resources.getIdentifier(sfx.resName, "raw", context.packageName)
            if (res != 0) ids[sfx] = pool.load(context, res, 1)
        }
    }

    fun play(sfx: Sfx, volume: Float = 0.8f) {
        if (!enabled) return
        ids[sfx]?.let { pool.play(it, volume, volume, 1, 0, 1f) }
    }

    fun release() = pool.release()
}
