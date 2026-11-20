package dev.foldterm.launcher.data

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle

/** Thin wrapper over AppWidgetHost so the Widgets pane can host real home-screen widgets. */
class Widgets(private val ctx: Context) {
    val manager: AppWidgetManager = AppWidgetManager.getInstance(ctx)
    val host = AppWidgetHost(ctx, HOST_ID)

    fun providers(): List<AppWidgetProviderInfo> =
        runCatching { manager.installedProviders }.getOrDefault(emptyList())
            .sortedWith(compareBy({ appLabel(it).lowercase() }, { label(it).lowercase() }))

    fun label(info: AppWidgetProviderInfo): String =
        runCatching { info.loadLabel(ctx.packageManager) }.getOrNull() ?: info.provider.className.substringAfterLast('.')

    fun appLabel(info: AppWidgetProviderInfo): String = runCatching {
        @Suppress("DEPRECATION")
        val ai = ctx.packageManager.getApplicationInfo(info.provider.packageName, 0)
        ctx.packageManager.getApplicationLabel(ai).toString()
    }.getOrDefault(info.provider.packageName)

    fun info(id: Int): AppWidgetProviderInfo? = runCatching { manager.getAppWidgetInfo(id) }.getOrNull()

    fun allocate(): Int = host.allocateAppWidgetId()

    /** True when we may bind without asking (rare for third-party launchers). */
    fun bindIfAllowed(id: Int, info: AppWidgetProviderInfo): Boolean =
        runCatching { manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null) }.getOrDefault(false)

    fun delete(id: Int) { runCatching { host.deleteAppWidgetId(id) } }

    /** Tells the widget how much room it has, in dp, so it can pick a layout. */
    fun updateSize(id: Int, widthDp: Int, heightDp: Int) {
        runCatching {
            manager.updateAppWidgetOptions(id, Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
            })
        }
    }

    fun startListening() { runCatching { host.startListening() } }
    fun stopListening() { runCatching { host.stopListening() } }

    companion object {
        const val HOST_ID = 4127
    }
}
