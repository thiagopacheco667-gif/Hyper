package com.hyper.island.system

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.hyper.island.service.IslandAccessibilityService

/** Verificações de permissão e atalhos para as telas certas do Android e do HyperOS. */
object Permissions {

    val isXiaomiFamily: Boolean
        get() = listOf("xiaomi", "poco", "redmi").any {
            Build.MANUFACTURER.equals(it, ignoreCase = true) || Build.BRAND.equals(it, ignoreCase = true)
        }

    fun isAccessibilityEnabled(context: Context): Boolean {
        val am = context.getSystemService(AccessibilityManager::class.java) ?: return false
        val target = IslandAccessibilityService::class.java.name
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
            val info = it.resolveInfo?.serviceInfo ?: return@any false
            info.packageName == context.packageName && info.name == target
        }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    fun openAccessibilitySettings(context: Context) =
        launch(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun openAppDetails(context: Context) = launch(context, appDetailsIntent(context))

    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context) = launch(
        context,
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
        fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    )

    /** Central de segurança do HyperOS → Inicialização automática. */
    fun openXiaomiAutostart(context: Context) = launch(
        context,
        Intent().setComponent(
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            ),
        ),
    )

    /** HyperOS → Outras permissões (janelas pop-up em segundo plano, tela de bloqueio). */
    fun openXiaomiOtherPermissions(context: Context) = launch(
        context,
        Intent("miui.intent.action.APP_PERM_EDITOR")
            .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
            .putExtra("extra_pkgname", context.packageName),
    )

    private fun appDetailsIntent(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

    /** Abre a tela pedida; se o fabricante não tiver essa tela, cai na tela do app. */
    private fun launch(context: Context, intent: Intent, fallback: Intent? = null) {
        val candidates = listOfNotNull(intent, fallback, appDetailsIntent(context))
        for (candidate in candidates) {
            try {
                context.startActivity(candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: ActivityNotFoundException) {
                // tenta o próximo
            } catch (e: SecurityException) {
                // tenta o próximo
            }
        }
    }
}
