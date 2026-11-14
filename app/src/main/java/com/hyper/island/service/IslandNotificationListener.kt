package com.hyper.island.service

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.hyper.island.integration.MediaRepository
import com.hyper.island.integration.NotificationRepository

/**
 * Recebe as notificações do sistema e dá acesso às MediaSessions dos players.
 * Requer "Acesso às notificações" nos ajustes.
 */
class IslandNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationRepository.onConnected(this)
        MediaRepository.start(applicationContext, component(this))
    }

    override fun onListenerDisconnected() {
        NotificationRepository.onDisconnected()
        MediaRepository.stop()
        // O HyperOS às vezes desconecta o listener em segundo plano: pede para religar.
        runCatching { NotificationListenerService.requestRebind(component(this)) }
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: NotificationListenerService.RankingMap) {
        NotificationRepository.onPosted(this, sbn, rankingMap)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: NotificationListenerService.RankingMap) {
        NotificationRepository.remove(sbn.key)
    }

    companion object {
        fun component(context: Context) = ComponentName(context, IslandNotificationListener::class.java)
    }
}
