package com.hyper.island.overlay

import android.content.Context
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.WindowManager
import androidx.compose.ui.unit.IntSize
import com.hyper.island.island.IslandMode
import kotlin.math.roundToInt

/**
 * Posição e tamanhos da ilha em pixels, calculados a partir do recorte real da câmera.
 * No Poco X7 Pro o furo fica centralizado no topo; a cápsula é centrada nele.
 */
data class IslandGeometry(
    val screenWidth: Int,
    val centerX: Int,
    val top: Int,
    val compact: IntSize,
    val peek: IntSize,
    val expanded: IntSize,
    val landscape: Boolean,
) {
    fun sizeFor(mode: IslandMode): IntSize = when (mode) {
        IslandMode.COMPACT -> compact
        IslandMode.PEEK -> peek
        IslandMode.EXPANDED -> expanded
    }

    /** X da janela para que ela fique centrada no recorte sem sair da tela. */
    fun windowX(width: Int): Int = (centerX - width / 2).coerceIn(0, (screenWidth - width).coerceAtLeast(0))

    companion object {
        fun compute(context: Context, windowManager: WindowManager): IslandGeometry {
            val density = context.resources.displayMetrics.density
            fun dp(v: Float) = (v * density).roundToInt()

            val metrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            val width = bounds.width()
            val height = bounds.height()
            val landscape = width > height

            val cutout = metrics.windowInsets.displayCutout
                ?: context.getSystemService(DisplayManager::class.java)
                    ?.getDisplay(Display.DEFAULT_DISPLAY)?.cutout
            val hole: Rect? = cutout?.boundingRectTop?.takeIf { !it.isEmpty }

            val compactHeight: Int
            val top: Int
            val centerX: Int
            if (hole != null) {
                compactHeight = (hole.height() + dp(10f)).coerceIn(dp(28f), dp(44f))
                top = (hole.centerY() - compactHeight / 2).coerceAtLeast(0)
                centerX = hole.centerX()
            } else {
                compactHeight = dp(32f)
                top = dp(6f)
                centerX = width / 2
            }

            val compactWidth = (compactHeight * 3.4f).roundToInt().coerceAtLeast(dp(104f))
            val peekWidth = dp(232f).coerceAtMost(width - dp(24f))
            val expandedWidth = (width - dp(24f)).coerceAtMost(dp(420f))
            val expandedHeight = dp(164f)

            return IslandGeometry(
                screenWidth = width,
                centerX = centerX,
                top = top,
                compact = IntSize(compactWidth, compactHeight),
                peek = IntSize(peekWidth, compactHeight),
                expanded = IntSize(expandedWidth, expandedHeight),
                landscape = landscape,
            )
        }
    }
}
