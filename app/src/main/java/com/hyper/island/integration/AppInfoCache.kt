package com.hyper.island.integration

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap

/** Nome e ícone dos apps, em cache para não recarregar a cada notificação. */
object AppInfoCache {
    private const val ICON_PX = 96

    private val labels = LruCache<String, String>(64)
    private val icons = LruCache<String, Bitmap>(32)

    fun label(context: Context, packageName: String): String {
        labels.get(packageName)?.let { return it }
        val pm = context.packageManager
        val label = try {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
        labels.put(packageName, label)
        return label
    }

    fun icon(context: Context, packageName: String): Bitmap? {
        icons.get(packageName)?.let { return it }
        val bitmap = try {
            context.packageManager.getApplicationIcon(packageName).toBitmap(ICON_PX, ICON_PX)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } ?: return null
        icons.put(packageName, bitmap)
        return bitmap
    }
}
