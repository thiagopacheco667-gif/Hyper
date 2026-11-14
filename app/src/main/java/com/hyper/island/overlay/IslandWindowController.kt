package com.hyper.island.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.hyper.island.integration.IslandNotification
import com.hyper.island.integration.MediaRepository
import com.hyper.island.integration.NotificationRepository
import com.hyper.island.island.IslandActions
import com.hyper.island.island.IslandMode
import com.hyper.island.island.IslandRoot
import com.hyper.island.island.IslandScreen
import com.hyper.island.island.IslandStateHolder
import com.hyper.island.system.BatteryMonitor
import com.hyper.island.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Dona da janela da ilha. Regras de desempenho:
 * - A janela tem sempre o tamanho da ilha (nunca tela cheia), para não roubar toques.
 * - Ao crescer, a janela aumenta primeiro e o conteúdo anima dentro dela.
 * - Ao encolher, o conteúdo anima primeiro e a janela diminui no fim.
 *   Assim não há updateViewLayout a cada quadro.
 */
class IslandWindowController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val battery: BatteryMonitor,
    scope: CoroutineScope,
) : IslandActions {

    private val handler = Handler(Looper.getMainLooper())
    private val lifecycleOwner = OverlayLifecycleOwner().apply { onCreate(); onResume() }

    val state = IslandStateHolder(
        scope = scope,
        initialGeometry = IslandGeometry.compute(context, windowManager),
        media = MediaRepository.media,
        notifications = NotificationRepository.notifications,
    )

    private var root: TouchRootLayout? = null
    private var params: WindowManager.LayoutParams? = null

    private val endPeek = Runnable {
        if (state.mode.value == IslandMode.PEEK) transitionTo(IslandMode.COMPACT)
    }
    private val endAlert = Runnable {
        if (state.mode.value == IslandMode.ALERT) transitionTo(IslandMode.COMPACT)
    }
    private val autoCollapse = Runnable {
        if (state.mode.value == IslandMode.EXPANDED) collapse()
    }

    val isShowing: Boolean get() = root != null

    init {
        // Qualquer mudança de conteúdo (música começou, notificação saiu...) ajusta a janela.
        scope.launch {
            state.screen.collect { screen ->
                val size = state.sizeFor(screen)
                growWindowTo(size.width, size.height)
                // Recalcula com os valores atuais (a emissão pode estar atrasada em relação ao modo).
                if (state.mode.value == IslandMode.ALERT && state.screenFor(IslandMode.ALERT) !is IslandScreen.Alert) {
                    // A notificação do alerta foi removida.
                    transitionTo(IslandMode.COMPACT)
                }
            }
        }
        scope.launch {
            NotificationRepository.alerts.collect { showAlert(it) }
        }
    }

    fun show() {
        if (root != null) return
        val geometry = state.geometry.value
        val size = state.sizeFor(state.screen.value)

        val layout = TouchRootLayout(context) { onOutsideTouch() }
        lifecycleOwner.attachTo(layout)
        val compose = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { IslandRoot(state, battery.state, this@IslandWindowController) }
        }
        layout.addView(
            compose,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )

        val lp = WindowManager.LayoutParams(
            size.width,
            size.height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = geometry.windowX(size.width)
            y = geometry.top
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            title = "DynamicIsland"
        }

        try {
            windowManager.addView(layout, lp)
            root = layout
            params = lp
        } catch (e: RuntimeException) {
            // Ex.: serviço desligado no meio do caminho. A próxima conexão tenta de novo.
            root = null
            params = null
        }
    }

    fun hide() {
        cancelTimers()
        state.setMode(IslandMode.COMPACT)
        state.setFocusedKey(null)
        state.setAlertKey(null)
        val view = root ?: return
        runCatching { windowManager.removeView(view) }
        root = null
        params = null
    }

    /** Recalcula posição e tamanhos (rotação, mudança de resolução). */
    fun refreshGeometry() {
        state.setGeometry(IslandGeometry.compute(context, windowManager))
        val target = state.sizeFor(state.screen.value)
        resizeWindow(target.width, target.height)
    }

    fun destroy() {
        hide()
        lifecycleOwner.onDestroy()
    }

    /** Aviso curto na cápsula (carregador conectado, bateria baixa). */
    fun peek() {
        if (!isShowing || state.mode.value != IslandMode.COMPACT) return
        transitionTo(IslandMode.PEEK)
        handler.removeCallbacks(endPeek)
        handler.postDelayed(endPeek, PEEK_DURATION_MS)
    }

    private fun showAlert(notification: IslandNotification) {
        if (!isShowing) return
        // Com o painel aberto, a notificação só entra na lista, sem interromper.
        if (state.mode.value == IslandMode.EXPANDED) return
        handler.removeCallbacks(endPeek)
        state.setAlertKey(notification.key)
        transitionTo(IslandMode.ALERT)
        handler.removeCallbacks(endAlert)
        handler.postDelayed(endAlert, ALERT_DURATION_MS)
    }

    override fun onTap() {
        when (state.mode.value) {
            IslandMode.COMPACT, IslandMode.PEEK -> {
                handler.removeCallbacks(endPeek)
                state.setFocusedKey(null)
                expand()
            }
            IslandMode.ALERT -> {
                // Tocar no alerta abre a notificação completa.
                handler.removeCallbacks(endAlert)
                state.setFocusedKey(state.alertKey.value)
                expand()
            }
            IslandMode.EXPANDED -> collapse()
        }
    }

    override fun onLongPress() {
        collapse()
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        runCatching { context.startActivity(intent) }
    }

    override fun onAnimationSettled() {
        val target = state.sizeFor(state.screen.value)
        resizeWindow(target.width, target.height)
    }

    override fun onInteraction() {
        if (state.mode.value == IslandMode.EXPANDED) scheduleAutoCollapse()
    }

    override fun onOpenNotification(notification: IslandNotification) {
        collapse()
        NotificationRepository.open(context, notification)
    }

    override fun onDismissNotification(notification: IslandNotification) {
        onInteraction()
        if (state.focusedKey.value == notification.key) state.setFocusedKey(null)
        NotificationRepository.dismiss(notification)
    }

    override fun onFocusNotification(notification: IslandNotification?) {
        onInteraction()
        val screen = state.screenFor(IslandMode.EXPANDED, focusedKey = notification?.key)
        val size = state.sizeFor(screen)
        growWindowTo(size.width, size.height)
        state.setFocusedKey(notification?.key)
    }

    override fun onPlayPause() {
        onInteraction()
        MediaRepository.playPause()
    }

    override fun onNext() {
        onInteraction()
        MediaRepository.next()
    }

    override fun onPrevious() {
        onInteraction()
        MediaRepository.previous()
    }

    override fun onSeek(positionMs: Long) {
        onInteraction()
        MediaRepository.seekTo(positionMs)
    }

    private fun expand() {
        transitionTo(IslandMode.EXPANDED)
        scheduleAutoCollapse()
    }

    private fun onOutsideTouch() {
        if (state.mode.value == IslandMode.EXPANDED) collapse()
    }

    private fun collapse() {
        handler.removeCallbacks(autoCollapse)
        if (state.mode.value != IslandMode.COMPACT) transitionTo(IslandMode.COMPACT)
        state.setFocusedKey(null)
        state.setAlertKey(null)
    }

    private fun scheduleAutoCollapse() {
        handler.removeCallbacks(autoCollapse)
        handler.postDelayed(autoCollapse, AUTO_COLLAPSE_MS)
    }

    private fun cancelTimers() {
        handler.removeCallbacks(endPeek)
        handler.removeCallbacks(endAlert)
        handler.removeCallbacks(autoCollapse)
    }

    private fun transitionTo(mode: IslandMode) {
        // Cresce já; só encolhe quando a animação terminar (onAnimationSettled).
        val target = state.sizeFor(state.screenFor(mode))
        growWindowTo(target.width, target.height)
        state.setMode(mode)
    }

    private fun growWindowTo(width: Int, height: Int) {
        val p = params ?: return
        resizeWindow(max(p.width, width), max(p.height, height))
    }

    private fun resizeWindow(width: Int, height: Int) {
        val view = root ?: return
        val p = params ?: return
        val geometry = state.geometry.value
        val x = geometry.windowX(width)
        if (p.width == width && p.height == height && p.x == x && p.y == geometry.top) return
        p.width = width
        p.height = height
        p.x = x
        p.y = geometry.top
        runCatching { windowManager.updateViewLayout(view, p) }
    }

    private companion object {
        const val PEEK_DURATION_MS = 3_000L
        const val ALERT_DURATION_MS = 5_000L
        const val AUTO_COLLAPSE_MS = 10_000L
    }
}
