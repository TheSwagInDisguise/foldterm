package dev.foldterm.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.MediaStore
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap

class AppsRepository(private val ctx: Context) {
    private val launcherApps = ctx.getSystemService(LauncherApps::class.java)
    private val userManager = ctx.getSystemService(UserManager::class.java)
    private val iconCache = LruCache<String, Bitmap>(300)

    fun load(): List<AppEntry> {
        val me = Process.myUserHandle()
        val pm = ctx.packageManager
        return userManager.userProfiles.flatMap { user ->
            runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList()).map {
                val pkg = it.componentName.packageName
                AppEntry(
                    label = it.label?.toString() ?: pkg,
                    packageName = pkg,
                    className = it.componentName.className,
                    user = user,
                    isWork = user != me,
                    category = runCatching { it.applicationInfo.category }.getOrDefault(-1),
                    installedAt = runCatching {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo(pkg, 0).firstInstallTime
                    }.getOrDefault(0L),
                )
            }
        }.filter { it.packageName != ctx.packageName }
            .sortedWith(compareBy({ it.label.lowercase() }, { it.isWork }))
    }

    fun icon(app: AppEntry, sizePx: Int = 144): Bitmap? {
        iconCache.get(app.uniqueKey)?.let { return it }
        val bmp = runCatching {
            launcherApps.getActivityList(app.packageName, app.user)
                .firstOrNull { it.componentName.className == app.className }
                ?.getBadgedIcon(0)
                ?.toBitmap(sizePx, sizePx)
        }.getOrNull() ?: return null
        iconCache.put(app.uniqueKey, bmp)
        return bmp
    }

    fun launch(app: AppEntry) {
        launcherApps.startMainActivity(app.component, app.user, null, null)
    }

    fun appInfo(app: AppEntry) {
        launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
    }

    fun uninstallIntent(app: AppEntry): Intent =
        Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
            .putExtra(Intent.EXTRA_USER, app.user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun registerCallback(onChange: () -> Unit): LauncherApps.Callback {
        val cb = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) = onChange()
            override fun onPackageAdded(packageName: String?, user: UserHandle?) = onChange()
            override fun onPackageChanged(packageName: String?, user: UserHandle?) {
                iconCache.evictAll(); onChange()
            }
            override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = onChange()
            override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = onChange()
        }
        launcherApps.registerCallback(cb, Handler(Looper.getMainLooper()))
        return cb
    }

    fun unregister(cb: LauncherApps.Callback) = launcherApps.unregisterCallback(cb)

    /** Default dock: phone, messages, browser, camera, gallery, mail — whichever resolve on this device. */
    fun defaultDock(apps: List<AppEntry>): List<String> {
        val pm = ctx.packageManager
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY),
            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")),
        )
        val owner = apps.filter { !it.isWork }
        return intents.mapNotNull { intent ->
            val pkg = runCatching {
                pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            }.getOrNull()
            val candidates = if (pkg == null || pkg == "android") {
                runCatching { pm.queryIntentActivities(intent, 0).map { it.activityInfo.packageName } }.getOrDefault(emptyList())
            } else listOf(pkg)
            candidates.firstNotNullOfOrNull { p -> owner.firstOrNull { it.packageName == p } }?.key
        }.distinct()
    }
}
