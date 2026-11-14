package com.hyper.island.overlay

import android.content.Context
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.WindowManager
import androidx.compose.ui.unit.IntSize
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
    /** Largura dos painéis grandes (alerta, painel expandido). */
    val panelWidth: Int,
    val landscape: Boolean,
    val density: Float,
) {
    fun dp(value: Float): Int = (value * density).roundToInt()

    /** X da janela para que ela fique centrada no recorte sem sair da tela. */
    fun windowX(width: Int): Int = (centerX - width / 2).coerceIn(0, (screenWidth - width).coerceAtLeast(0))

    companion object {
        // Valores padrão; ficam ajustáveis na central de personalização (Etapa 4).
        const val COMPACT_WIDTH_DP = 96f
        const val COMPACT_HEIGHT_DP = 28f
        const val PEEK_WIDTH_DP = 196f

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

            // O HyperOS informa o recorte como a faixa inteira da barra de status, não o furo real.
            // Por isso só usamos o centro dele; o tamanho é fixo em dp, menor que o espaço
            // entre os ícones de notificação (esquerda) e os de sinal/bateria (direita).
            val compactHeight = dp(COMPACT_HEIGHT_DP)
            val centerX = hole?.centerX() ?: (width / 2)
            val centerY = hole?.centerY() ?: dp(18f)
            val top = (centerY - compactHeight / 2).coerceAtLeast(dp(2f))

            val compactWidth = dp(COMPACT_WIDTH_DP)
            val peekWidth = dp(PEEK_WIDTH_DP).coerceAtMost(width - dp(24f))
            val panelWidth = (width - dp(24f)).coerceAtMost(dp(400f))

            return IslandGeometry(
                screenWidth = width,
                centerX = centerX,
                top = top,
                compact = IntSize(compactWidth, compactHeight),
                peek = IntSize(peekWidth, compactHeight),
                panelWidth = panelWidth,
                landscape = landscape,
                density = density,
            )
        }
    }
}
