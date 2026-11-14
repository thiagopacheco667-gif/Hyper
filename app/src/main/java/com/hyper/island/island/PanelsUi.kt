package com.hyper.island.island

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyper.island.integration.IslandNotification
import com.hyper.island.integration.MediaInfo
import com.hyper.island.system.BatteryInfo
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter

/** Espaço reservado para o furo da câmera no meio da faixa superior. */
private val CameraGap = 44.dp

/**
 * Faixa superior dos painéis (altura [IslandLayout.BAND]), centrada no furo da câmera:
 * conteúdo à esquerda e à direita, nunca por baixo da lente.
 */
@Composable
private fun TopBand(
    start: @Composable RowScope.() -> Unit,
    end: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IslandLayout.BAND.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, content = start)
        Spacer(Modifier.width(CameraGap))
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            content = end,
        )
    }
}

@Composable
private fun AppLabel(notification: IslandNotification) {
    Artwork(notification.icon, size = 16.dp, corner = 5.dp)
    Spacer(Modifier.width(6.dp))
    Text(
        notification.appLabel,
        color = IslandColors.Secondary,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Banner de notificação nova. Tocar abre a versão completa (tratado pelo controlador). */
@Composable
internal fun AlertContent(notification: IslandNotification) {
    Column(Modifier.fillMaxSize()) {
        TopBand(
            start = { AppLabel(notification) },
            end = { Text(relativeTime(notification.postTime), color = IslandColors.Tertiary, fontSize = 11.sp) },
        )
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp)) {
            if (notification.title.isNotBlank()) {
                Text(
                    notification.title,
                    color = IslandColors.Primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (notification.text.isNotBlank()) {
                Text(
                    notification.text,
                    color = IslandColors.Secondary,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Painel principal: hora e bateria, player e notificações recentes. */
@Composable
internal fun DashboardContent(
    battery: BatteryInfo,
    media: MediaInfo?,
    notifications: List<IslandNotification>,
    actions: IslandActions,
) {
    val clock = rememberMinuteClock()
    val time = remember(clock) { clock.format(DateTimeFormatter.ofPattern("HH:mm")) }
    Column(Modifier.fillMaxSize()) {
        TopBand(
            start = {
                Text(time, color = IslandColors.Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            },
            end = {
                Text("${battery.level}%", color = IslandColors.Secondary, fontSize = 12.sp)
                Spacer(Modifier.width(6.dp))
                BatteryGlyph(battery)
            },
        )
        if (media != null) {
            Spacer(Modifier.height(IslandLayout.GAP.dp))
            MediaCard(media, actions)
        }
        val visible = notifications.take(IslandLayout.MAX_ROWS)
        if (visible.isNotEmpty()) {
            Spacer(Modifier.height(IslandLayout.GAP.dp))
            visible.forEach { NotificationRow(it, actions) }
        }
        val more = notifications.size - visible.size
        if (more > 0) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(IslandLayout.MORE.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (more == 1) "+1 notificação" else "+$more notificações",
                    color = IslandColors.Tertiary,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun MediaCard(media: MediaInfo, actions: IslandActions) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(IslandLayout.MEDIA_CARD.dp)
            .padding(horizontal = 18.dp),
    ) {
        Row(Modifier.height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(media.art, size = 52.dp, corner = 12.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    media.title,
                    color = IslandColors.Primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    media.artist.ifBlank { media.appLabel },
                    color = IslandColors.Secondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (media.isPlaying) {
                Spacer(Modifier.width(8.dp))
                EqualizerBars()
            }
        }
        Spacer(Modifier.height(8.dp))
        ProgressRow(media, actions)
        Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlButton(enabled = media.canSkipPrevious, onClick = actions::onPrevious) {
                SkipGlyph(22.dp, forward = false)
            }
            Spacer(Modifier.width(28.dp))
            ControlButton(enabled = true, onClick = actions::onPlayPause) {
                if (media.isPlaying) PauseGlyph(30.dp) else PlayGlyph(30.dp)
            }
            Spacer(Modifier.width(28.dp))
            ControlButton(enabled = media.canSkipNext, onClick = actions::onNext) {
                SkipGlyph(22.dp, forward = true)
            }
        }
    }
}

@Composable
private fun ControlButton(enabled: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val modifier = Modifier.size(40.dp)
    Box(
        if (enabled) modifier.islandClick(onClick) else modifier.alpha(0.3f),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Barra de progresso. A posição é calculada localmente; nenhum pedido ao player a cada segundo. */
@Composable
private fun ProgressRow(media: MediaInfo, actions: IslandActions) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(media.isPlaying, media.positionUpdatedAt) {
        now = SystemClock.elapsedRealtime()
        while (media.isPlaying) {
            delay(500)
            now = SystemClock.elapsedRealtime()
        }
    }
    val duration = media.durationMs
    Row(
        Modifier
            .fillMaxWidth()
            .height(22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (duration > 0) {
            val position = media.currentPosition(now)
            val fraction = (position.toFloat() / duration).coerceIn(0f, 1f)
            val canSeek = media.canSeek
            val latestSeek by rememberUpdatedState(actions::onSeek)
            Text(formatDuration(position), color = IslandColors.Tertiary, fontSize = 10.sp)
            Spacer(Modifier.width(8.dp))
            Canvas(
                Modifier
                    .weight(1f)
                    .height(16.dp)
                    .pointerInput(canSeek, duration) {
                        if (!canSeek) return@pointerInput
                        detectTapGestures { offset ->
                            val f = (offset.x / size.width).coerceIn(0f, 1f)
                            latestSeek((f * duration).toLong())
                        }
                    },
            ) {
                val barH = 4.dp.toPx()
                val y = (size.height - barH) / 2
                val r = CornerRadius(barH / 2)
                drawRoundRect(IslandColors.Track, Offset(0f, y), Size(size.width, barH), r)
                drawRoundRect(IslandColors.Primary, Offset(0f, y), Size(size.width * fraction, barH), r)
            }
            Spacer(Modifier.width(8.dp))
            Text(formatDuration(duration), color = IslandColors.Tertiary, fontSize = 10.sp)
        }
    }
}

@Composable
private fun NotificationRow(notification: IslandNotification, actions: IslandActions) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IslandLayout.ROW.dp)
            .islandClick { actions.onFocusNotification(notification) }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(notification.icon, size = 34.dp, corner = 9.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    notification.title.ifBlank { notification.appLabel },
                    color = IslandColors.Primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                Text(relativeTime(notification.postTime), color = IslandColors.Tertiary, fontSize = 11.sp)
            }
            if (notification.text.isNotBlank()) {
                Text(
                    notification.text,
                    color = IslandColors.Secondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (notification.clearable) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(IslandColors.Elevated)
                    .islandClick { actions.onDismissNotification(notification) },
                contentAlignment = Alignment.Center,
            ) { CloseGlyph(14.dp) }
        }
    }
}

/** Notificação completa: texto inteiro e ações. */
@Composable
internal fun NotificationDetail(notification: IslandNotification, actions: IslandActions) {
    Column(Modifier.fillMaxSize()) {
        TopBand(
            start = { AppLabel(notification) },
            end = { Text(relativeTime(notification.postTime), color = IslandColors.Tertiary, fontSize = 11.sp) },
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 20.dp, end = 20.dp, top = 6.dp),
        ) {
            if (notification.title.isNotBlank()) {
                Text(
                    notification.title,
                    color = IslandColors.Primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                notification.text,
                color = IslandColors.Secondary,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Pill("Voltar", primary = false, modifier = Modifier.weight(1f)) { actions.onFocusNotification(null) }
            if (notification.clearable) {
                Pill("Dispensar", primary = false, modifier = Modifier.weight(1f)) {
                    actions.onDismissNotification(notification)
                }
            }
            if (notification.contentIntent != null) {
                Pill("Abrir", primary = true, modifier = Modifier.weight(1f)) { actions.onOpenNotification(notification) }
            }
        }
    }
}

@Composable
private fun Pill(label: String, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (primary) IslandColors.Primary else IslandColors.Elevated)
            .islandClick(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (primary) IslandColors.Surface else IslandColors.Primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun relativeTime(postTime: Long): String {
    val diff = (System.currentTimeMillis() - postTime).coerceAtLeast(0)
    val minutes = diff / 60_000
    return when {
        minutes < 1 -> "agora"
        minutes < 60 -> "$minutes min"
        minutes < 24 * 60 -> "${minutes / 60} h"
        else -> "${minutes / (24 * 60)} d"
    }
}
