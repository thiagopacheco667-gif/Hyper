package com.hyper.island.service

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.service.notification.NotificationListenerService
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.hyper.island.data.SettingsRepository
import com.hyper.island.integration.NotificationRepository
import com.hyper.island.island.IslandMode
import com.hyper.island.overlay.IslandWindowController
import com.hyper.island.system.BatteryMonitor
import com.hyper.island.system.Permissions
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Serviço que mantém a ilha viva. Por ser um serviço de acessibilidade, o sistema
 * o religa sozinho depois de reiniciar o aparelho e o HyperOS tende a não encerrá-lo.
 * Não lê conteúdo de tela: os eventos recebidos são ignorados.
 */
class IslandAccessibilityService : AccessibilityService() {

    private var scope = MainScope()
    private var controller: IslandWindowController? = null
    private var battery: BatteryMonitor? = null
    private var enabledInSettings = true

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope.cancel()
        scope = MainScope()
        controller?.destroy()
        battery?.stop()

        val wm = getSystemService(WindowManager::class.java)
        val monitor = BatteryMonitor(this) { controller?.peek() }
        val islandController = IslandWindowController(this, wm, monitor, scope)
        battery = monitor
        controller = islandController
        monitor.start()
        _running.value = true

        // Se o acesso a notificações está concedido mas o HyperOS derrubou o listener, pede para religar.
        if (Permissions.isNotificationListenerEnabled(this) && !NotificationRepository.connected.value) {
            runCatching { NotificationListenerService.requestRebind(IslandNotificationListener.component(this)) }
        }

        scope.launch {
            SettingsRepository(applicationContext).islandEnabled.collect { enabled ->
                enabledInSettings = enabled
                updateVisibility()
            }
        }
        scope.launch {
            islandController.state.mode.collect { mode ->
                monitor.setLive(mode != IslandMode.COMPACT)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        controller?.refreshGeometry()
        updateVisibility()
    }

    private fun updateVisibility() {
        val c = controller ?: return
        val geometry = c.state.geometry.value
        // Em paisagem o furo da câmera fica na lateral; a ilha se esconde (suporte completo na Etapa 5).
        val shouldShow = enabledInSettings && !geometry.landscape
        if (shouldShow) c.show() else c.hide()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        controller?.destroy()
        controller = null
        battery?.stop()
        battery = null
        _running.value = false
        super.onDestroy()
    }

    companion object {
        private val _running = MutableStateFlow(false)

        /** Se o serviço está conectado agora (para a tela de status). */
        val running: StateFlow<Boolean> = _running.asStateFlow()
    }
}
