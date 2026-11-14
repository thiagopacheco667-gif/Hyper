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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
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

/** Altura da linha de cabeçalho dos painéis (app à esquerda, hora à direita). */
private val HeaderHeight = 34.dp

/** Cabeçalho dos painéis. Eles ficam abaixo da barra de status, então não há furo da câmera por cima. */
@Composable
private fun TopBand(
    start: @Composable RowScope.() -> Unit,
    end: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(HeaderHeight)
            .padding(start = 20.dp, end = 20.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, content = start)
        Spacer(Modifier.width(12.dp))
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

/** Painel principal: player (no layout do Hyper Island) e notificações recentes. */
@Composable
internal fun DashboardContent(
    battery: BatteryInfo,
    media: MediaInfo?,
    notifications: List<IslandNotification>,
    actions: IslandActions,
) {
    Column(Modifier.fillMaxSize()) {
        if (media != null) MediaPanel(media, actions)
        val visible = notifications.take(IslandLayout.MAX_ROWS)
        if (visible.isNotEmpty()) {
            Spacer(Modifier.height(if (media == null) IslandLayout.LIST_PADDING.dp else 0.dp))
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

/**
 * Player expandido com as mesmas medidas do Hyper Island de fábrica
 * (valores em px na tela de referência de 1220 px, convertidos por [ref]).
 */
@Composable
private fun MediaPanel(media: MediaInfo, actions: IslandActions) {
    val accent = media.accentColor?.let { Color(it) } ?: IslandColors.Tertiary
    Box(
        Modifier
            .fillMaxWidth()
            .height(ref(IslandLayout.REF_MEDIA_PANEL))
            .drawBehind {
                // Brilho na cor da capa, vindo do canto inferior esquerdo.
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.5f), Color.Transparent),
                        center = Offset(size.width * 0.12f, size.height * 1.05f),
                        radius = size.width * 0.62f,
                    ),
                )
            },
    ) {
        // Capa + título/artista.
        Row(
            Modifier
                .padding(start = ref(50f), top = ref(49f), end = ref(50f))
                .height(ref(171f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Artwork(media.art, size = ref(171f), corner = ref(34f))
                // Selo do app de origem no canto da capa.
                val appIcon = remember(media.packageName) { media.packageName }
                AppBadge(appIcon, Modifier.align(Alignment.BottomEnd).padding(ref(10f)))
            }
            Spacer(Modifier.width(ref(42f)))
            Column(Modifier.weight(1f)) {
                Text(
                    media.title,
                    color = IslandColors.Primary,
                    fontSize = refSp(54f),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(ref(14f)))
                Text(
                    media.artist.ifBlank { media.appLabel },
                    color = IslandColors.Secondary,
                    fontSize = refSp(38f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Controles centralizados (centros a 220 px uns dos outros, como no original).
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = ref(296f))
                .height(ref(80f)),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlButton(ref(100f), enabled = media.canSkipPrevious, onClick = actions::onPrevious) {
                SkipGlyph(ref(56f), forward = false)
            }
            Spacer(Modifier.width(ref(120f)))
            ControlButton(ref(100f), enabled = true, onClick = actions::onPlayPause) {
                if (media.isPlaying) PauseGlyph(ref(68f)) else PlayGlyph(ref(68f))
            }
            Spacer(Modifier.width(ref(120f)))
            ControlButton(ref(100f), enabled = media.canSkipNext, onClick = actions::onNext) {
                SkipGlyph(ref(56f), forward = true)
            }
        }

        // Barra de progresso com os tempos nas laterais.
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = ref(418f), start = ref(74f), end = ref(78f))
                .height(ref(60f)),
        ) {
            ProgressRow(media, actions)
        }
    }
}

@Composable
private fun AppBadge(packageName: String, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val icon = remember(packageName) { com.hyper.island.integration.AppInfoCache.icon(context, packageName) }
    if (icon != null) {
        Box(
            modifier
                .size(ref(40f))
                .clip(CircleShape)
                .background(IslandColors.Surface.copy(alpha = 0.5f))
                .padding(ref(3f)),
        ) {
            Artwork(icon, size = ref(34f), corner = ref(17f))
        }
    }
}

@Composable
private fun ControlButton(size: Dp, enabled: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val modifier = Modifier.size(size)
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
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        if (duration > 0) {
            val position = media.currentPosition(now)
            val fraction = (position.toFloat() / duration).coerceIn(0f, 1f)
            val canSeek = media.canSeek
            val latestSeek by rememberUpdatedState(actions::onSeek)
            Box(Modifier.width(ref(187f))) {
                Text(formatDuration(position), color = IslandColors.Tertiary, fontSize = refSp(34f))
            }
            Canvas(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(canSeek, duration) {
                        if (!canSeek) return@pointerInput
                        detectTapGestures { offset ->
                            val f = (offset.x / size.width).coerceIn(0f, 1f)
                            latestSeek((f * duration).toLong())
                        }
                    },
            ) {
                val barH = size.height * 0.2f
                val y = (size.height - barH) / 2
                val r = CornerRadius(barH / 2)
                drawRoundRect(IslandColors.Track, Offset(0f, y), Size(size.width, barH), r)
                drawRoundRect(IslandColors.Primary, Offset(0f, y), Size(size.width * fraction, barH), r)
            }
            Box(Modifier.width(ref(187f)), contentAlignment = Alignment.CenterEnd) {
                Text(formatDuration(duration), color = IslandColors.Tertiary, fontSize = refSp(34f))
            }
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
