package com.hyper.island.overlay

import android.content.Context
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.WindowManager
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * Posição e tamanhos da ilha em pixels.
 *
 * As medidas copiam o Hyper Island de fábrica do HyperOS 3 no Poco X7 Pro, medidas em
 * capturas de tela de 1220 px de largura (valores "ref"), e escalam proporcionalmente
 * à largura real da tela. Assim as duas ilhas têm exatamente o mesmo tamanho e posição.
 */
data class IslandGeometry(
    val screenWidth: Int,
    val centerX: Int,
    /** Topo da cápsula. */
    val top: Int,
    val compact: IntSize,
    val peek: IntSize,
    /** Painéis grandes ficam abaixo da barra de status, como no HyperOS. */
    val panelTop: Int,
    val panelWidth: Int,
    val panelCorner: Int,
    val landscape: Boolean,
    val density: Float,
    /** Fator entre a tela real e a referência de 1220 px. */
    val scale: Float,
) {
    fun dp(value: Float): Int = (value * density).roundToInt()

    fun ref(value: Float): Int = (value * scale).roundToInt()

    /** X da janela para que ela fique centrada no recorte sem sair da tela. */
    fun windowX(width: Int): Int = (centerX - width / 2).coerceIn(0, (screenWidth - width).coerceAtLeast(0))

    companion object {
        const val REF_WIDTH = 1220f

        // Hyper Island de fábrica (px na referência de 1220 px).
        const val REF_COMPACT_WIDTH = 334f
        const val REF_COMPACT_HEIGHT = 104f
        const val REF_COMPACT_TOP = 10f
        const val REF_PEEK_WIDTH = 560f
        const val REF_PANEL_TOP = 142f
        const val REF_PANEL_MARGIN = 44f
        const val REF_PANEL_CORNER = 96f

        fun compute(context: Context, windowManager: WindowManager): IslandGeometry {
            val density = context.resources.displayMetrics.density

            val metrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            val width = bounds.width()
            val height = bounds.height()
            val landscape = width > height
            val scale = minOf(width, height) / REF_WIDTH
            fun ref(v: Float) = (v * scale).roundToInt()

            val cutout = metrics.windowInsets.displayCutout
                ?: context.getSystemService(DisplayManager::class.java)
                    ?.getDisplay(Display.DEFAULT_DISPLAY)?.cutout
            // O HyperOS informa o recorte como a faixa inteira da barra de status; usamos só o centro horizontal.
            val hole: Rect? = cutout?.boundingRectTop?.takeIf { !it.isEmpty }
            val centerX = hole?.centerX() ?: (width / 2)

            val compactHeight = ref(REF_COMPACT_HEIGHT)
            return IslandGeometry(
                screenWidth = width,
                centerX = centerX,
                top = ref(REF_COMPACT_TOP),
                compact = IntSize(ref(REF_COMPACT_WIDTH), compactHeight),
                peek = IntSize(ref(REF_PEEK_WIDTH).coerceAtMost(width), compactHeight),
                panelTop = ref(REF_PANEL_TOP),
                panelWidth = width - 2 * ref(REF_PANEL_MARGIN),
                panelCorner = ref(REF_PANEL_CORNER),
                landscape = landscape,
                density = density,
                scale = scale,
            )
        }
    }
}
