package dev.foldterm.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.util.LruCache
import android.util.Xml
import androidx.core.graphics.drawable.toBitmap
import org.xmlpull.v1.XmlPullParser

/**
 * Reads standard Android icon packs (the ADW/Nova `appfilter.xml` format that nearly every
 * pack on the Play Store ships) and serves their icons by component.
 */
class IconPacks(private val ctx: Context) {
    data class Pack(val pkg: String, val label: String)

    private val actions = listOf(
        "org.adw.launcher.THEMES", "com.gau.go.launcherex.theme", "com.novalauncher.THEME",
        "com.teslacoilsw.launcher.THEME", "com.anddoes.launcher.THEME",
    )
    private var loadedPkg: String? = null
    private var res: Resources? = null
    private var byComponent: Map<String, String> = emptyMap()
    private var byPackage: Map<String, String> = emptyMap()
    private val cache = LruCache<String, Bitmap>(300)

    fun installed(): List<Pack> {
        val pm = ctx.packageManager
        return actions.flatMap { action ->
            runCatching { pm.queryIntentActivities(Intent(action), 0) }.getOrDefault(emptyList())
        }.map { it.activityInfo.packageName }.distinct().map { pkg ->
            val label = runCatching {
                @Suppress("DEPRECATION")
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrDefault(pkg)
            Pack(pkg, label)
        }.sortedBy { it.label.lowercase() }
    }

    @Synchronized
    private fun ensure(pkg: String): Boolean {
        if (pkg == loadedPkg) return res != null
        loadedPkg = pkg
        res = null; byComponent = emptyMap(); byPackage = emptyMap(); cache.evictAll()
        val r = runCatching { ctx.packageManager.getResourcesForApplication(pkg) }.getOrNull() ?: return false
        val comp = HashMap<String, String>()
        val pkgOnly = HashMap<String, String>()
        runCatching {
            val id = r.getIdentifier("appfilter", "xml", pkg)
            val parser: XmlPullParser = if (id != 0) r.getXml(id) else Xml.newPullParser().apply {
                setInput(r.assets.open("appfilter.xml"), null)
            }
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG || parser.name != "item") continue
                val raw = parser.getAttributeValue(null, "component") ?: continue
                val drawable = parser.getAttributeValue(null, "drawable") ?: continue
                val flat = raw.removePrefix("ComponentInfo{").removeSuffix("}")
                val p = flat.substringBefore('/')
                var cls = flat.substringAfter('/', "")
                if (p.isEmpty() || cls.isEmpty()) continue
                if (cls.startsWith(".")) cls = p + cls
                comp.putIfAbsent("$p/$cls", drawable)
                pkgOnly.putIfAbsent(p, drawable)
            }
        }
        res = r
        byComponent = comp
        byPackage = pkgOnly
        return true
    }

    /** The pack's icon for [cn], or null when the pack doesn't cover it. */
    fun icon(packPkg: String, cn: ComponentName, sizePx: Int = 144): Bitmap? {
        if (!ensure(packPkg)) return null
        val key = cn.flattenToString()
        cache.get(key)?.let { return it }
        val name = byComponent["${cn.packageName}/${cn.className}"] ?: byPackage[cn.packageName] ?: return null
        val r = res ?: return null
        val id = r.getIdentifier(name, "drawable", packPkg).takeIf { it != 0 } ?: r.getIdentifier(name, "mipmap", packPkg)
        if (id == 0) return null
        val bmp = runCatching {
            @Suppress("DEPRECATION")
            r.getDrawable(id, null).toBitmap(sizePx, sizePx)
        }.getOrNull() ?: return null
        cache.put(key, bmp)
        return bmp
    }

    /** Share of [apps] the pack has an icon for, 0–100. */
    fun coverage(packPkg: String, apps: List<AppEntry>): Int {
        if (!ensure(packPkg) || apps.isEmpty()) return 0
        val hit = apps.count { byComponent.containsKey("${it.packageName}/${it.className}") || byPackage.containsKey(it.packageName) }
        return hit * 100 / apps.size
    }
}
