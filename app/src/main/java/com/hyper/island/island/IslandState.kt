package com.hyper.island.island

import androidx.compose.ui.unit.IntSize
import com.hyper.island.integration.IslandNotification
import com.hyper.island.integration.MediaInfo
import com.hyper.island.overlay.IslandGeometry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class IslandMode {
    /** Cápsula ao redor da câmera (com capa e equalizador quando há música tocando). */
    COMPACT,

    /** Cápsula alargada por alguns segundos (carregador, bateria baixa). */
    PEEK,

    /** Banner de notificação nova. */
    ALERT,

    /** Painel aberto ao tocar. */
    EXPANDED,
}

/** O que a ilha está mostrando agora. Cada tela tem um tamanho próprio. */
sealed interface IslandScreen {
    /** Muda só quando a tela muda de verdade, para a animação de troca não disparar à toa. */
    val key: String

    data object Compact : IslandScreen { override val key = "compact" }
    data object CompactMedia : IslandScreen { override val key = "compact-media" }
    data object Peek : IslandScreen { override val key = "peek" }
    data object Clock : IslandScreen { override val key = "clock" }

    data class Alert(val notificationKey: String) : IslandScreen {
        override val key = "alert:$notificationKey"
    }

    data class Dashboard(val hasMedia: Boolean, val rows: Int, val hasMore: Boolean) : IslandScreen {
        override val key = "dashboard"
    }

    data class Detail(val notificationKey: String) : IslandScreen {
        override val key = "detail:$notificationKey"
    }
}

/** Retângulo da ilha dentro da janela: deslocamento vertical (a partir do topo da cápsula) e tamanho. */
data class IslandFrame(val offsetY: Int, val width: Int, val height: Int) {
    val windowSize: IntSize get() = IntSize(width, offsetY + height)
}

/** Medidas de cada tela, usadas tanto pela janela quanto pela animação. */
object IslandLayout {
    const val MAX_ROWS = 3

    // Em dp.
    const val CLOCK_HEIGHT = 150f
    const val ALERT_HEIGHT = 100f
    const val DETAIL_HEIGHT = 232f
    const val ROW = 54f
    const val MORE = 22f
    const val LIST_PADDING = 10f

    /** Player expandido do Hyper Island de fábrica (px na referência de 1220 px). */
    const val REF_MEDIA_PANEL = 522f

    fun frame(geometry: IslandGeometry, screen: IslandScreen): IslandFrame = with(geometry) {
        val panelOffset = panelTop - top
        when (screen) {
            IslandScreen.Compact, IslandScreen.CompactMedia -> IslandFrame(0, compact.width, compact.height)
            IslandScreen.Peek -> IslandFrame(0, peek.width, peek.height)
            IslandScreen.Clock -> IslandFrame(panelOffset, panelWidth, dp(CLOCK_HEIGHT))
            is IslandScreen.Alert -> IslandFrame(panelOffset, panelWidth, dp(ALERT_HEIGHT))
            is IslandScreen.Dashboard -> IslandFrame(panelOffset, panelWidth, dashboardHeight(geometry, screen))
            is IslandScreen.Detail -> IslandFrame(panelOffset, panelWidth, dp(DETAIL_HEIGHT))
        }
    }

    /** Tamanho da janela para a tela (inclui o espaço acima dos painéis). */
    fun size(geometry: IslandGeometry, screen: IslandScreen): IntSize = frame(geometry, screen).windowSize

    private fun dashboardHeight(g: IslandGeometry, screen: IslandScreen.Dashboard): Int {
        var h = 0
        if (screen.hasMedia) h += g.ref(REF_MEDIA_PANEL)
        if (screen.rows > 0) {
            var list = screen.rows * ROW + LIST_PADDING
            if (!screen.hasMedia) list += LIST_PADDING
            if (screen.hasMore) list += MORE
            h += g.dp(list)
        }
        return h
    }

    fun resolve(
        mode: IslandMode,
        media: MediaInfo?,
        notifications: List<IslandNotification>,
        alertKey: String?,
        focusedKey: String?,
    ): IslandScreen = when (mode) {
        IslandMode.COMPACT -> if (media?.isPlaying == true) IslandScreen.CompactMedia else IslandScreen.Compact
        IslandMode.PEEK -> IslandScreen.Peek
        IslandMode.ALERT -> alertKey
            ?.takeIf { key -> notifications.any { it.key == key } }
            ?.let { IslandScreen.Alert(it) }
            ?: IslandScreen.Compact
        IslandMode.EXPANDED -> {
            val focused = focusedKey?.takeIf { key -> notifications.any { it.key == key } }
            when {
                focused != null -> IslandScreen.Detail(focused)
                media == null && notifications.isEmpty() -> IslandScreen.Clock
                else -> IslandScreen.Dashboard(
                    hasMedia = media != null,
                    rows = notifications.size.coerceAtMost(MAX_ROWS),
                    hasMore = notifications.size > MAX_ROWS,
                )
            }
        }
    }
}

/** Estado observável da ilha, compartilhado entre o controlador da janela e a UI. */
class IslandStateHolder(
    scope: CoroutineScope,
    initialGeometry: IslandGeometry,
    val media: StateFlow<MediaInfo?>,
    val notifications: StateFlow<List<IslandNotification>>,
) {
    private val _mode = MutableStateFlow(IslandMode.COMPACT)
    val mode: StateFlow<IslandMode> = _mode.asStateFlow()

    private val _geometry = MutableStateFlow(initialGeometry)
    val geometry: StateFlow<IslandGeometry> = _geometry.asStateFlow()

    private val _alertKey = MutableStateFlow<String?>(null)
    val alertKey: StateFlow<String?> = _alertKey.asStateFlow()

    private val _focusedKey = MutableStateFlow<String?>(null)
    val focusedKey: StateFlow<String?> = _focusedKey.asStateFlow()

    val screen: StateFlow<IslandScreen> =
        combine(_mode, media, notifications, _alertKey, _focusedKey) { mode, m, n, alert, focused ->
            IslandLayout.resolve(mode, m, n, alert, focused)
        }.stateIn(scope, SharingStarted.Eagerly, screenFor(_mode.value))

    /** Tela que seria mostrada em [mode] com os dados atuais (cálculo síncrono). */
    fun screenFor(
        mode: IslandMode,
        alertKey: String? = _alertKey.value,
        focusedKey: String? = _focusedKey.value,
    ): IslandScreen = IslandLayout.resolve(mode, media.value, notifications.value, alertKey, focusedKey)

    fun sizeFor(screen: IslandScreen): IntSize = IslandLayout.size(_geometry.value, screen)

    fun setMode(mode: IslandMode) {
        _mode.value = mode
    }

    fun setGeometry(geometry: IslandGeometry) {
        _geometry.value = geometry
    }

    fun setAlertKey(key: String?) {
        _alertKey.value = key
    }

    fun setFocusedKey(key: String?) {
        _focusedKey.value = key
    }
}

interface IslandActions {
    /** Toque numa área vazia da ilha. */
    fun onTap()
    fun onLongPress()

    /** Chamado quando a animação de tamanho termina; a janela é ajustada ao tamanho final. */
    fun onAnimationSettled()

    /** Qualquer toque em um controle interno: adia o recolhimento automático. */
    fun onInteraction()

    fun onOpenNotification(notification: IslandNotification)
    fun onDismissNotification(notification: IslandNotification)
    fun onFocusNotification(notification: IslandNotification?)

    fun onPlayPause()
    fun onNext()
    fun onPrevious()
    fun onSeek(positionMs: Long)
}
