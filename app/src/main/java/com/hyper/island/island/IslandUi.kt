package com.hyper.island.island

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateSizeAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyper.island.system.BatteryInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private object IslandColors {
    val Surface = Color(0xFF000000)
    val Primary = Color(0xFFFFFFFF)
    val Secondary = Color(0xFF8E8E93)
    val Track = Color(0xFF2C2C2E)
    val Green = Color(0xFF30D158)
    val Red = Color(0xFFFF453A)
}

private fun <T> islandSpring() = spring<T>(dampingRatio = 0.78f, stiffness = 420f)

@Composable
fun IslandRoot(
    state: IslandStateHolder,
    battery: StateFlow<BatteryInfo>,
    actions: IslandActions,
) {
    val mode by state.mode.collectAsState()
    val geometry by state.geometry.collectAsState()
    val bat by battery.collectAsState()
    val density = LocalDensity.current
    val view = LocalView.current

    fun IntSize.toDp(): DpSize = with(density) { DpSize(width.toDp(), height.toDp()) }

    val target = geometry.sizeFor(mode).toDp()
    val expandedSize = geometry.expanded.toDp()

    val targetPx = geometry.sizeFor(mode).let { Size(it.width.toFloat(), it.height.toFloat()) }
    val sizePx by animateSizeAsState(
        targetValue = targetPx,
        animationSpec = islandSpring(),
        label = "islandSize",
        finishedListener = { actions.onAnimationSettled() },
    )
    val size = with(density) { DpSize(sizePx.width.toDp(), sizePx.height.toDp()) }
    val corner by animateDpAsState(
        targetValue = if (mode == IslandMode.EXPANDED) 34.dp else target.height / 2,
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
                targetState = mode,
                transitionSpec = {
                    (fadeIn(tween(220, delayMillis = 110)) + scaleIn(tween(260, delayMillis = 80), initialScale = 0.92f))
                        .togetherWith(fadeOut(tween(90)))
                },
                label = "islandContent",
            ) { m ->
                when (m) {
                    IslandMode.COMPACT -> CompactContent(bat)
                    IslandMode.PEEK -> PeekContent(bat)
                    IslandMode.EXPANDED -> ExpandedContent(bat, expandedSize)
                }
            }
        }
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

@Composable
private fun ExpandedContent(battery: BatteryInfo, fullSize: DpSize) {
    // Relógio atualizado uma vez por minuto, só enquanto o painel está aberto.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L - (now % 60_000L))
        }
    }
    val dateTime = remember(now) { LocalDateTime.now() }
    val time = remember(dateTime) { dateTime.format(DateTimeFormatter.ofPattern("HH:mm")) }
    val date = remember(dateTime) {
        dateTime.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM")).replaceFirstChar { it.uppercase() }
    }

    // Layout fixo no tamanho final: durante a animação o conteúdo é só recortado, não reflui.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .wrapContentSize(Alignment.TopCenter, unbounded = true)
                .size(fullSize)
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
private fun BatteryGlyph(battery: BatteryInfo, modifier: Modifier = Modifier) {
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
