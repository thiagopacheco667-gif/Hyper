package com.hyper.island.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hyper.island.data.SettingsRepository
import com.hyper.island.service.IslandAccessibilityService
import com.hyper.island.system.Permissions
import com.hyper.island.ui.theme.HyperColors
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(settings: SettingsRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Reavalia as permissões sempre que o usuário volta das telas de ajustes.
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val accessibilityOn = remember(refresh) { Permissions.isAccessibilityEnabled(context) }
    val batteryOk = remember(refresh) { Permissions.isIgnoringBatteryOptimizations(context) }
    val notificationsOn = remember(refresh) { Permissions.isNotificationListenerEnabled(context) }
    val serviceRunning by IslandAccessibilityService.running.collectAsState()
    val islandEnabled by settings.islandEnabled.collectAsState(initial = true)
    val yieldDuringMedia by settings.yieldDuringMedia.collectAsState(initial = true)

    Box(
        Modifier
            .fillMaxSize()
            .background(HyperColors.Background),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(36.dp))
            Text("Dynamic Island 2.0", color = HyperColors.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(
                "Etapa 2 · Música e notificações",
                color = HyperColors.TextSecondary,
                fontSize = 14.sp,
            )

            StatusCard(
                running = serviceRunning && accessibilityOn,
                islandEnabled = islandEnabled,
                onToggle = { value -> scope.launch { settings.setIslandEnabled(value) } },
            )

            SectionTitle("Permissões")

            PermissionCard(
                title = "Serviço da ilha",
                description = "Ative \"Dynamic Island 2.0\" em Acessibilidade → Apps baixados. " +
                    "É o que permite desenhar a ilha sobre a barra de status. O serviço não lê a tela.",
                granted = accessibilityOn,
                actionLabel = "Abrir Acessibilidade",
                onAction = { Permissions.openAccessibilitySettings(context) },
            )

            PermissionCard(
                title = "Acesso às notificações",
                description = "Mostra notificações novas na ilha e identifica a música tocando (capa, título e controles). " +
                    "O conteúdo fica só no aparelho.",
                granted = notificationsOn,
                actionLabel = "Permitir acesso",
                onAction = { Permissions.openNotificationListenerSettings(context) },
            )

            if (!accessibilityOn || !notificationsOn) {
                InfoCard(
                    title = "A opção aparece bloqueada?",
                    body = "Apps instalados por APK têm \"configurações restritas\". Abra as informações do app, " +
                        "toque nos três pontos (⋮) no canto superior e escolha \"Permitir configurações restritas\". " +
                        "Depois volte e ative a permissão.",
                    actionLabel = "Informações do app",
                    onAction = { Permissions.openAppDetails(context) },
                )
            }

            PermissionCard(
                title = "Bateria sem restrições",
                description = "Impede que o sistema pause a ilha para economizar bateria.",
                granted = batteryOk,
                actionLabel = "Permitir",
                onAction = { Permissions.requestIgnoreBatteryOptimizations(context) },
            )

            if (Permissions.isXiaomiFamily) {
                SectionTitle("Ajustes do HyperOS")
                InfoCard(
                    title = "Inicialização automática",
                    body = "Ative para o app na lista. Sem isso, o HyperOS pode impedir a ilha de voltar após o app ser fechado.",
                    actionLabel = "Abrir",
                    onAction = { Permissions.openXiaomiAutostart(context) },
                )
                InfoCard(
                    title = "Outras permissões",
                    body = "Ative \"Exibir janelas pop-up enquanto executa em segundo plano\". " +
                        "Ela é necessária para abrir os ajustes pela ilha e, na Etapa 3, as janelas flutuantes.",
                    actionLabel = "Abrir",
                    onAction = { Permissions.openXiaomiOtherPermissions(context) },
                )
                InfoCard(
                    title = "Travar nos recentes",
                    body = "Abra a tela de apps recentes, segure o card do Dynamic Island 2.0 e toque no cadeado.",
                )
                ToggleCard(
                    title = "Ceder lugar ao Hyper Island",
                    body = "Enquanto uma música ou vídeo toca, o Hyper Island do sistema aparece. " +
                        "Com esta opção, a Dynamic Island 2.0 se esconde nesses momentos e volta quando o player para. " +
                        "Requer o acesso às notificações.",
                    checked = yieldDuringMedia,
                    onCheckedChange = { value -> scope.launch { settings.setYieldDuringMedia(value) } },
                )
            }

            SectionTitle("Como usar")
            InfoCard(
                title = "Gestos",
                body = "Toque na ilha para abrir o painel com música e notificações. Toque numa área vazia ou fora dela para recolher. " +
                    "Quando chega uma notificação, a ilha mostra uma prévia; toque nela para ver tudo, abrir ou dispensar. " +
                    "Segure a ilha para voltar a esta tela.",
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatusCard(running: Boolean, islandEnabled: Boolean, onToggle: (Boolean) -> Unit) {
    HyperCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ilha", color = HyperColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(if (running && islandEnabled) HyperColors.Ok else HyperColors.Warn)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when {
                            !running -> "Serviço desligado: ative a permissão abaixo"
                            !islandEnabled -> "Pausada"
                            else -> "Ativa sobre todos os apps"
                        },
                        color = HyperColors.TextSecondary,
                        fontSize = 13.sp,
                    )
                }
            }
            Switch(
                checked = islandEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = HyperColors.Ok),
            )
        }
    }
}

@Composable
private fun ToggleCard(title: String, body: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    HyperCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                color = HyperColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedTrackColor = HyperColors.Ok),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(body, color = HyperColors.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    HyperCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(if (granted) HyperColors.Ok else HyperColors.Warn)
            Spacer(Modifier.width(10.dp))
            Text(title, color = HyperColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(
                if (granted) "Concedida" else "Pendente",
                color = if (granted) HyperColors.Ok else HyperColors.Warn,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(description, color = HyperColors.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        if (!granted) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = HyperColors.Accent),
            ) { Text(actionLabel) }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    HyperCard {
        Text(title, color = HyperColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(body, color = HyperColors.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onAction,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, HyperColors.CardBorder),
            ) { Text(actionLabel, color = HyperColors.TextPrimary) }
        }
    }
}

@Composable
private fun HyperCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = HyperColors.Card),
        border = BorderStroke(1.dp, HyperColors.CardBorder),
    ) {
        Column(Modifier.padding(18.dp)) { content() }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = HyperColors.TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 10.dp, start = 4.dp),
    )
}

@Composable
private fun StatusDot(color: Color) {
    Box(
        Modifier
            .size(9.dp)
            .clip(CircleShape)
            .background(color),
    )
}
