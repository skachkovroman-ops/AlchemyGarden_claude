package com.example.alchemygarden.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.alchemygarden.ui.theme.Ink
import com.example.alchemygarden.ui.theme.Parchment
import com.example.alchemygarden.ui.theme.ParchmentDark
import com.example.alchemygarden.ui.theme.Wood
import com.example.alchemygarden.ui.theme.WoodBorder

@Composable
fun ParchmentCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val base = modifier
        .shadow(4.dp, shape)
        .clip(shape)
        .background(Brush.verticalGradient(listOf(Parchment, ParchmentDark)))
        .border(2.dp, WoodBorder.copy(alpha = 0.6f), shape)
    CompositionLocalProvider(LocalContentColor provides Ink) {
        Box(if (onClick != null) base.clickable(onClick = onClick) else base) {
            GameImage(
                names = listOf("ui_parchment_texture"),
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.5f
            )
            Column(Modifier.fillMaxWidth().padding(12.dp), content = content)
        }
    }
}

@Composable
fun ParchmentDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        ParchmentCard(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
fun WoodButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Wood,
            contentColor = Parchment,
            disabledContainerColor = Wood.copy(alpha = 0.35f),
            disabledContentColor = Parchment.copy(alpha = 0.7f)
        )
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

@Composable
fun ScreenBackground(imageName: String, fallbackColors: List<Color>) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(fallbackColors))) {
        GameImage(listOf(imageName), Modifier.fillMaxSize(), ContentScale.Crop)
    }
}
