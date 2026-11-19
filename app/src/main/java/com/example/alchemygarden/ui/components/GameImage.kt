package com.example.alchemygarden.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Картинка по имени файла из res/drawable-nodpi. Пробует имена по очереди;
 * если ни одной картинки нет — рисует запасной вариант (эмодзи или свой composable).
 * Благодаря этому игра полностью играбельна ещё ДО появления любого арта.
 */
@SuppressLint("DiscouragedApi")
@Composable
fun GameImage(
    names: List<String>,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = 1f,
    colorFilter: ColorFilter? = null,
    fallbackEmoji: String? = null,
    emojiSize: TextUnit = 32.sp,
    fallback: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    val resId = remember(names) {
        names.firstNotNullOfOrNull { n ->
            context.resources.getIdentifier(n, "drawable", context.packageName).takeIf { it != 0 }
        }
    }
    if (resId != null) {
        Image(
            painter = painterResource(resId),
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale,
            alpha = alpha,
            colorFilter = colorFilter
        )
    } else if (fallback != null) {
        Box(modifier) { fallback() }
    } else if (fallbackEmoji != null) {
        Box(modifier, contentAlignment = Alignment.Center) { Text(fallbackEmoji, fontSize = emojiSize) }
    }
}

@Composable
fun GameImage(
    name: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    fallbackEmoji: String? = null,
    emojiSize: TextUnit = 32.sp
) = GameImage(listOf(name), modifier, contentScale, 1f, null, fallbackEmoji, emojiSize)

/** Соглашение об именах картинок. Полный список — в IMAGES.md. */
object Art {
    fun plantIcon(id: String) = listOf("icon_$id")
    fun plantStage(id: String, stage: Int) = listOf("${id}_stage$stage", "pot_stage_$stage")
    fun itemIcon(id: String) = listOf("icon_$id")
    fun homIdle(id: String) = listOf("${id}_idle")
    fun homCard(id: String) = listOf("${id}_card", "${id}_idle")
    fun biomeArt(id: String) = listOf("${id}_art")
    fun biomeNode(id: String) = listOf("${id}_node", "node_available")
}

fun formatSeconds(total: Long): String {
    val s = total.coerceAtLeast(0L)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** Есть ли в проекте хотя бы одна из этих картинок. */
@SuppressLint("DiscouragedApi")
@Composable
fun drawableExists(names: List<String>): Boolean {
    val context = LocalContext.current
    return remember(names) {
        names.any { context.resources.getIdentifier(it, "drawable", context.packageName) != 0 }
    }
}
