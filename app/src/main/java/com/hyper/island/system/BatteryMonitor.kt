package com.hyper.island.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BatteryInfo(val level: Int, val charging: Boolean) {
    val low: Boolean get() = level <= LOW_THRESHOLD && !charging

    companion object {
        const val LOW_THRESHOLD = 15
    }
}

/**
 * Bateria com consumo mínimo:
 * - Sempre escuta só eventos raros (conectar/desconectar carregador, bateria baixa/ok).
 * - Atualização contínua (ACTION_BATTERY_CHANGED) só enquanto a ilha está aberta.
 * O nível é lido direto do BatteryManager, sem polling.
 */
class BatteryMonitor(
    private val context: Context,
    private val onAttentionEvent: () -> Unit,
) {
    private val batteryManager = context.getSystemService(BatteryManager::class.java)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<BatteryInfo> = _state.asStateFlow()

    private var started = false
    private var live = false

    private val eventReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            refresh()
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED, Intent.ACTION_BATTERY_LOW -> onAttentionEvent()
            }
        }
    }

    private val liveReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) = refresh()
    }

    fun start() {
        if (started) return
        started = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
        }
        ContextCompat.registerReceiver(context, eventReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        refresh()
    }

    /** Liga a atualização contínua só enquanto há UI de bateria visível. */
    fun setLive(enabled: Boolean) {
        if (!started || enabled == live) return
        live = enabled
        if (enabled) {
            ContextCompat.registerReceiver(
                context,
                liveReceiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        } else {
            runCatching { context.unregisterReceiver(liveReceiver) }
        }
        refresh()
    }

    fun stop() {
        if (!started) return
        setLive(false)
        runCatching { context.unregisterReceiver(eventReceiver) }
        started = false
    }

    fun refresh() {
        _state.value = read()
    }

    private fun read(): BatteryInfo {
        val level = batteryManager
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 } ?: 0
        val charging = batteryManager?.isCharging ?: false
        return BatteryInfo(level, charging)
    }
}
