package com.hyper.island.island

import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal object IslandColors {
    val Surface = Color(0xFF000000)
    val Primary = Color(0xFFFFFFFF)
    val Secondary = Color(0xFF8E8E93)
    val Tertiary = Color(0xFF636366)
    val Track = Color(0xFF2C2C2E)
    val Elevated = Color(0xFF1C1C1E)
    val Green = Color(0xFF30D158)
    val Red = Color(0xFFFF453A)
}

internal fun <T> islandSpring() = spring<T>(dampingRatio = 0.78f, stiffness = 420f)

/**
 * Toque em um controle interno da ilha, com leve encolhimento ao pressionar e vibração.
 * Consome o toque: a ilha não recolhe quando um botão é usado.
 */
@Composable
internal fun Modifier.islandClick(onClick: () -> Unit): Modifier {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, label = "press")
    val view = LocalView.current
    val latest by rememberUpdatedState(onClick)
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                },
                onTap = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    latest()
                },
            )
        }
}

/** Capa do álbum ou ícone do app; um bloco escuro quando não há imagem. */
@Composable
internal fun Artwork(bitmap: Bitmap?, size: Dp, corner: Dp, modifier: Modifier = Modifier) {
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    val shaped = modifier
        .size(size)
        .clip(RoundedCornerShape(corner))
    if (image != null) {
        Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = shaped)
    } else {
        Box(shaped.background(IslandColors.Track))
    }
}

/** Equalizador animado. Só é composto enquanto a música toca. */
@Composable
internal fun EqualizerBars(modifier: Modifier = Modifier, color: Color = IslandColors.Green) {
    val transition = rememberInfiniteTransition(label = "eq")
    val durations = intArrayOf(420, 560, 380, 500)
    val levels = durations.mapIndexed { i, d ->
        transition.animateFloat(
            initialValue = 0.25f + 0.1f * i,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(d), RepeatMode.Reverse),
            label = "eq$i",
        )
    }
    Canvas(modifier.size(width = 16.dp, height = 12.dp)) {
        val count = levels.size
        val barW = size.width / (count * 2 - 1)
        levels.forEachIndexed { i, level ->
            val h = size.height * level.value
            drawRoundRect(
                color = color,
                topLeft = Offset(i * barW * 2, (size.height - h) / 2),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2),
            )
        }
    }
}

// Ícones desenhados à mão: dispensam bibliotecas de ícones e ficam nítidos em qualquer densidade.

@Composable
internal fun PlayGlyph(size: Dp, color: Color = IslandColors.Primary) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.24f, h * 0.14f)
            lineTo(w * 0.86f, h * 0.5f)
            lineTo(w * 0.24f, h * 0.86f)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
internal fun PauseGlyph(size: Dp, color: Color = IslandColors.Primary) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val barW = w * 0.22f
        val r = CornerRadius(barW * 0.35f)
        drawRoundRect(color, Offset(w * 0.2f, h * 0.14f), Size(barW, h * 0.72f), r)
        drawRoundRect(color, Offset(w * 0.58f, h * 0.14f), Size(barW, h * 0.72f), r)
    }
}

@Composable
internal fun SkipGlyph(size: Dp, forward: Boolean, color: Color = IslandColors.Primary) {
    Canvas(
        Modifier
            .size(size)
            .graphicsLayer { scaleX = if (forward) 1f else -1f },
    ) {
        val w = this.size.width
        val h = this.size.height
        fun triangle(start: Float) = Path().apply {
            moveTo(w * start, h * 0.22f)
            lineTo(w * (start + 0.4f), h * 0.5f)
            lineTo(w * start, h * 0.78f)
            close()
        }
        drawPath(triangle(0.1f), color)
        drawPath(triangle(0.48f), color)
    }
}

@Composable
internal fun CloseGlyph(size: Dp, color: Color = IslandColors.Secondary) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val stroke = w * 0.12f
        drawLine(color, Offset(w * 0.25f, w * 0.25f), Offset(w * 0.75f, w * 0.75f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.75f, w * 0.25f), Offset(w * 0.25f, w * 0.75f), stroke, StrokeCap.Round)
    }
}
