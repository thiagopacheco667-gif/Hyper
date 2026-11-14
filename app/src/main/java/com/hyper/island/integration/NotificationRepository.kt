package com.hyper.island.integration

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class IslandNotification(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val icon: Bitmap?,
    val title: String,
    val text: String,
    val postTime: Long,
    val contentIntent: PendingIntent?,
    val clearable: Boolean,
    val autoCancel: Boolean,
)

/**
 * Notificações recentes vistas pelo listener. Mantém só o necessário para a ilha
 * (no máximo [MAX_ITEMS]) e emite um alerta quando chega algo novo que deve chamar atenção.
 */
object NotificationRepository {
    private const val MAX_ITEMS = 20

    private val _notifications = MutableStateFlow<List<IslandNotification>>(emptyList())
    val notifications: StateFlow<List<IslandNotification>> = _notifications.asStateFlow()

    private val _alerts = MutableSharedFlow<IslandNotification>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val alerts: SharedFlow<IslandNotification> = _alerts.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private var service: NotificationListenerService? = null

    fun onConnected(listener: NotificationListenerService) {
        service = listener
        _connected.value = true
        val ranking = runCatching { listener.currentRanking }.getOrNull()
        val items = runCatching { listener.activeNotifications }.getOrNull().orEmpty()
            .filter { accept(listener, it, ranking) }
            .sortedByDescending { it.postTime }
            .take(MAX_ITEMS)
            .map { toItem(listener, it) }
        _notifications.value = items
    }

    fun onDisconnected() {
        service = null
        _connected.value = false
        _notifications.value = emptyList()
    }

    fun onPosted(listener: NotificationListenerService, sbn: StatusBarNotification, rankingMap: NotificationListenerService.RankingMap?) {
        if (!accept(listener, sbn, rankingMap)) {
            // Uma notificação que deixou de ser relevante (ex.: virou "em andamento") sai da lista.
            remove(sbn.key)
            return
        }
        val item = toItem(listener, sbn)
        val current = _notifications.value
        val previous = current.firstOrNull { it.key == item.key }
        _notifications.value = (listOf(item) + current.filter { it.key != item.key }).take(MAX_ITEMS)

        val onlyAlertOnce = sbn.notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0
        val changed = previous == null || previous.title != item.title || previous.text != item.text
        val shouldAlert = changed && (previous == null || !onlyAlertOnce) && passesInterruptionFilter(sbn, rankingMap)
        if (shouldAlert) _alerts.tryEmit(item)
    }

    fun remove(key: String) {
        val current = _notifications.value
        if (current.any { it.key == key }) _notifications.value = current.filter { it.key != key }
    }

    /** Abre o app da notificação. Retorna false se não houver ação disponível. */
    fun open(context: Context, item: IslandNotification): Boolean {
        val intent = item.contentIntent ?: return false
        val sent = try {
            val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    .toBundle()
            } else {
                null
            }
            intent.send(context, 0, null, null, null, null, options)
            true
        } catch (e: PendingIntent.CanceledException) {
            false
        }
        if (sent && item.autoCancel) dismiss(item)
        return sent
    }

    fun dismiss(item: IslandNotification) {
        if (!item.clearable) return
        runCatching { service?.cancelNotification(item.key) }
        remove(item.key)
    }

    private fun accept(
        context: Context,
        sbn: StatusBarNotification,
        rankingMap: NotificationListenerService.RankingMap?,
    ): Boolean {
        if (sbn.packageName == context.packageName) return false
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        if (n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0) return false
        if (n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return false
        if (n.category == Notification.CATEGORY_TRANSPORT) return false
        if (rankingMap != null) {
            val ranking = NotificationListenerService.Ranking()
            if (rankingMap.getRanking(sbn.key, ranking) && ranking.importance < NotificationManager.IMPORTANCE_DEFAULT) {
                return false
            }
        }
        val (title, text) = texts(n)
        return title.isNotBlank() || text.isNotBlank()
    }

    private fun passesInterruptionFilter(sbn: StatusBarNotification, rankingMap: NotificationListenerService.RankingMap?): Boolean {
        if (rankingMap == null) return true
        val ranking = NotificationListenerService.Ranking()
        if (!rankingMap.getRanking(sbn.key, ranking)) return true
        // Respeita o "Não perturbe".
        return ranking.matchesInterruptionFilter()
    }

    private fun toItem(context: Context, sbn: StatusBarNotification): IslandNotification {
        val n = sbn.notification
        val (title, text) = texts(n)
        return IslandNotification(
            key = sbn.key,
            packageName = sbn.packageName,
            appLabel = AppInfoCache.label(context, sbn.packageName),
            icon = AppInfoCache.icon(context, sbn.packageName),
            title = title,
            text = text,
            postTime = sbn.postTime,
            contentIntent = n.contentIntent,
            clearable = sbn.isClearable,
            autoCancel = n.flags and Notification.FLAG_AUTO_CANCEL != 0,
        )
    }

    private fun texts(n: Notification): Pair<String, String> {
        val extras = n.extras
        val title = (extras.getCharSequence(Notification.EXTRA_TITLE_BIG) ?: extras.getCharSequence(Notification.EXTRA_TITLE))
            ?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        return title.trim() to text.trim()
    }
}
