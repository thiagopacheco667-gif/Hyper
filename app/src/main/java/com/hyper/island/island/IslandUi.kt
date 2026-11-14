package com.hyper.island.island

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateSizeAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyper.island.integration.MediaInfo
import com.hyper.island.system.BatteryInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun IslandRoot(
    state: IslandStateHolder,
    battery: StateFlow<BatteryInfo>,
    actions: IslandActions,
) {
    val screen by state.screen.collectAsState()
    val geometry by state.geometry.collectAsState()
    val media by state.media.collectAsState()
    val notifications by state.notifications.collectAsState()
    val bat by battery.collectAsState()
    val density = LocalDensity.current
    val view = LocalView.current

    val target = IslandLayout.size(geometry, screen)
    val sizePx by animateSizeAsState(
        targetValue = Size(target.width.toFloat(), target.height.toFloat()),
        animationSpec = islandSpring(),
        label = "islandSize",
        finishedListener = { actions.onAnimationSettled() },
    )
    val size = with(density) { DpSize(sizePx.width.toDp(), sizePx.height.toDp()) }
    val isPanel = screen is IslandScreen.Alert || screen is IslandScreen.Clock ||
        screen is IslandScreen.Dashboard || screen is IslandScreen.Detail
    val corner by animateDpAsState(
        targetValue = if (isPanel) 34.dp else with(density) { (target.height / 2f).toDp() },
        animationSpec = islandSpring(),
        label = "islandCorner",
    )

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(corner))
                .background(IslandColors.Surface)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            actions.onTap()
                        },
                        onLongPress = {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            actions.onLongPress()
                        },
                    )
                },
        ) {
            AnimatedContent(
                targetState = screen,
                contentKey = { it.key },
                transitionSpec = {
                    (fadeIn(tween(200, delayMillis = 170)) + scaleIn(tween(260, delayMillis = 140), initialScale = 0.94f))
                        .togetherWith(fadeOut(tween(90)))
                },
                label = "islandContent",
            ) { s ->
                val full = IslandLayout.size(geometry, s).let { with(density) { DpSize(it.width.toDp(), it.height.toDp()) } }
                // Cada tela é desenhada no tamanho final: durante a animação só é recortada, não reflui.
                FixedFrame(full) {
                    when (s) {
                        IslandScreen.Compact -> CompactContent(bat)
                        IslandScreen.CompactMedia -> CompactMediaContent(media)
                        IslandScreen.Peek -> PeekContent(bat)
                        IslandScreen.Clock -> ClockContent(bat)
                        is IslandScreen.Alert -> notifications.firstOrNull { it.key == s.notificationKey }
                            ?.let { AlertContent(it) }
                        is IslandScreen.Dashboard -> DashboardContent(bat, media, notifications, actions)
                        is IslandScreen.Detail -> notifications.firstOrNull { it.key == s.notificationKey }
                            ?.let { NotificationDetail(it, actions) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FixedFrame(size: DpSize, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier
                .wrapContentSize(Alignment.TopCenter, unbounded = true)
                .size(size),
            content = content,
        )
    }
}

@Composable
private fun CompactContent(battery: BatteryInfo) {
    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.size(8.dp))
        when {
            // A animação infinita só existe enquanto carrega: sem redesenho contínuo no resto do tempo.
            battery.charging -> {
                val pulse by rememberInfiniteTransition(label = "charge").animateFloat(
                    initialValue = 0.45f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
                    label = "chargeAlpha",
                )
                BatteryGlyph(battery, Modifier.alpha(pulse))
            }
            battery.low -> Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(IslandColors.Red),
            )
            else -> Spacer(Modifier.size(8.dp))
        }
    }
}

/** Cápsula com música tocando: capa à esquerda do furo, equalizador à direita. */
@Composable
private fun CompactMediaContent(media: MediaInfo?) {
    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(media?.art, size = 18.dp, corner = 6.dp)
        if (media?.isPlaying == true) EqualizerBars(Modifier.padding(end = 3.dp)) else Spacer(Modifier.size(16.dp))
    }
}

@Composable
private fun PeekContent(battery: BatteryInfo) {
    val accent = if (battery.charging) IslandColors.Green else IslandColors.Red
    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // O furo da câmera fica no meio: texto à esquerda, bateria à direita.
        Text(
            text = if (battery.charging) "Carregando" else "Bateria baixa",
            color = IslandColors.Primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${battery.level}%",
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Spacer(Modifier.width(6.dp))
            BatteryGlyph(battery)
        }
    }
}

/** Painel sem música nem notificações: relógio grande e anel de bateria. */
@Composable
private fun ClockContent(battery: BatteryInfo) {
    val dateTime = rememberMinuteClock()
    val time = remember(dateTime) { dateTime.format(DateTimeFormatter.ofPattern("HH:mm")) }
    val date = remember(dateTime) {
        dateTime.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM")).replaceFirstChar { it.uppercase() }
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 22.dp, end = 20.dp, top = 18.dp, bottom = 14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text(time, color = IslandColors.Primary, fontSize = 38.sp, fontWeight = FontWeight.Light)
                Text(date, color = IslandColors.Secondary, fontSize = 13.sp, maxLines = 1)
            }
            BatteryRing(battery, ringSize = 62.dp)
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "Toque para recolher · segure para abrir os ajustes",
            color = IslandColors.Secondary.copy(alpha = 0.6f),
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}

/** Hora local, atualizada uma vez por minuto enquanto a tela está visível. */
@Composable
internal fun rememberMinuteClock(): LocalDateTime {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L - (now % 60_000L))
        }
    }
    return remember(now) { LocalDateTime.now() }
}

@Composable
private fun BatteryRing(battery: BatteryInfo, ringSize: Dp) {
    val color = when {
        battery.charging -> IslandColors.Green
        battery.low -> IslandColors.Red
        else -> IslandColors.Primary
    }
    val glow = if (battery.charging) {
        val t by rememberInfiniteTransition(label = "ring").animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
            label = "ringAlpha",
        )
        t
    } else {
        1f
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(ringSize), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 6.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    color = IslandColors.Track,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke),
                )
                drawArc(
                    color = color.copy(alpha = glow),
                    startAngle = -90f,
                    sweepAngle = 360f * battery.level / 100f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Text("${battery.level}%", color = IslandColors.Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = when {
                battery.charging -> "Carregando"
                battery.low -> "Bateria baixa"
                else -> "Bateria"
            },
            color = if (battery.low) IslandColors.Red else IslandColors.Secondary,
            fontSize = 11.sp,
        )
    }
}

/** Ícone de bateria desenhado à mão (sem dependência de pacotes de ícones). */
@Composable
internal fun BatteryGlyph(battery: BatteryInfo, modifier: Modifier = Modifier) {
    val fill = when {
        battery.charging -> IslandColors.Green
        battery.low -> IslandColors.Red
        else -> IslandColors.Primary
    }
    Canvas(modifier.size(width = 20.dp, height = 10.dp)) {
        val stroke = 1.2.dp.toPx()
        val nub = 2.dp.toPx()
        val bodyW = size.width - nub - 1.dp.toPx()
        val r = CornerRadius(3.dp.toPx())
        drawRoundRect(
            color = IslandColors.Secondary,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(bodyW - stroke, size.height - stroke),
            cornerRadius = r,
            style = Stroke(stroke),
        )
        drawRoundRect(
            color = IslandColors.Secondary,
            topLeft = Offset(bodyW + 1.dp.toPx() - nub / 2, size.height * 0.32f),
            size = Size(nub, size.height * 0.36f),
            cornerRadius = CornerRadius(1.dp.toPx()),
        )
        val pad = 2.2.dp.toPx()
        val innerW = (bodyW - pad * 2) * (battery.level.coerceIn(4, 100) / 100f)
        drawRoundRect(
            color = fill,
            topLeft = Offset(pad, pad),
            size = Size(innerW, size.height - pad * 2),
            cornerRadius = CornerRadius(1.5.dp.toPx()),
        )
        if (battery.charging) {
            val cx = bodyW / 2
            val h = size.height
            val bolt = Path().apply {
                moveTo(cx + h * 0.10f, h * 0.10f)
                lineTo(cx - h * 0.22f, h * 0.56f)
                lineTo(cx - h * 0.02f, h * 0.56f)
                lineTo(cx - h * 0.10f, h * 0.90f)
                lineTo(cx + h * 0.22f, h * 0.44f)
                lineTo(cx + h * 0.02f, h * 0.44f)
                close()
            }
            drawPath(bolt, Color.Black)
        }
    }
}
