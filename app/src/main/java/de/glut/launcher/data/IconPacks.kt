package de.glut.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class IconPackInfo(val packageName: String, val label: String)

/**
 * Unterstützt die üblichen Icon-Packs aus dem Play Store (ADW/Nova-Format mit appfilter.xml),
 * damit Apps wie auf dem Handy aussehen. Apps ohne Eintrag im Pack bleiben bei ihrem Symbol,
 * bekommen aber – falls das Pack eines mitbringt – dessen Hintergrund und Maske.
 */
class IconPack(private val context: Context, val packageName: String) {
    private val res: Resources = context.packageManager.getResourcesForApplication(packageName)
    private val byComponent = HashMap<String, String>()
    private val byPackage = HashMap<String, String>()
    private val backs = mutableListOf<String>()
    private var mask: String? = null
    private var upon: String? = null
    private var scale = 1f

    init {
        parse()
    }

    private fun parser(): XmlPullParser? {
        val id = res.getIdentifier("appfilter", "xml", packageName)
        if (id != 0) return res.getXml(id)
        return runCatching {
            val other = context.createPackageContext(packageName, 0)
            XmlPullParserFactory.newInstance().newPullParser().apply { setInput(other.assets.open("appfilter.xml"), null) }
        }.getOrNull()
    }

    private fun parse() {
        val xml = parser() ?: return
        runCatching {
            var event = xml.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (xml.name) {
                        "item" -> {
                            val component = xml.getAttributeValue(null, "component")
                            val drawable = xml.getAttributeValue(null, "drawable")
                            if (component != null && drawable != null) add(component, drawable)
                        }
                        "iconback" -> for (i in 0 until xml.attributeCount) if (xml.getAttributeName(i).startsWith("img")) backs += xml.getAttributeValue(i)
                        "iconmask" -> mask = xml.getAttributeValue(null, "img1")
                        "iconupon" -> upon = xml.getAttributeValue(null, "img1")
                        "scale" -> scale = xml.getAttributeValue(null, "factor")?.toFloatOrNull() ?: 1f
                    }
                }
                event = xml.next()
            }
        }
    }

    /** component="ComponentInfo{paket/Klasse}" */
    private fun add(component: String, drawable: String) {
        val inner = component.substringAfter("{", "").substringBefore("}")
        if (!inner.contains('/')) return
        val pkg = inner.substringBefore('/')
        val cls = inner.substringAfter('/').let { if (it.startsWith(".")) pkg + it else it }
        byComponent.putIfAbsent("$pkg/$cls", drawable)
        byPackage.putIfAbsent(pkg, drawable)
    }

    private fun drawable(name: String): Drawable? {
        val id = res.getIdentifier(name, "drawable", packageName).takeIf { it != 0 } ?: res.getIdentifier(name, "mipmap", packageName)
        if (id == 0) return null
        return runCatching { res.getDrawable(id, null) }.getOrNull()
    }

    private fun bitmap(name: String?, size: Int): Bitmap? = name?.let { drawable(it) }?.let { runCatching { it.toBitmap(size, size) }.getOrNull() }

    /** Das Symbol des Packs für diese App, sonst null. */
    fun iconFor(component: ComponentName, size: Int = 144): Bitmap? {
        val name = byComponent["${component.packageName}/${component.className}"] ?: byPackage[component.packageName] ?: return null
        return bitmap(name, size)
    }

    /** Für Apps ohne Eintrag: das eigene Symbol auf den Hintergrund des Packs, mit Maske. Ohne Hintergrund null. */
    fun fallback(icon: Drawable, key: String, size: Int = 144): Bitmap? {
        if (backs.isEmpty()) return null
        val back = bitmap(backs[(key.hashCode() and Int.MAX_VALUE) % backs.size], size) ?: return null
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(back, 0f, 0f, null)
        val inner = (size * scale.coerceIn(0.3f, 1f)).toInt()
        val offset = (size - inner) / 2
        runCatching { icon.toBitmap(inner, inner) }.getOrNull()?.let { canvas.drawBitmap(it, offset.toFloat(), offset.toFloat(), null) }
        bitmap(upon, size)?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        bitmap(mask, size)?.let {
            canvas.drawBitmap(it, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
        }
        return out
    }

    companion object {
        private val actions = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.anddoes.launcher.THEME",
            "com.gau.go.launcherex.theme",
            "fr.neamar.kiss.THEME",
        )

        fun installed(context: Context): List<IconPackInfo> {
            val pm = context.packageManager
            return actions.flatMap { action -> pm.queryIntentActivities(Intent(action), 0) }
                .map { it.activityInfo.packageName }
                .distinct()
                .mapNotNull { pkg -> runCatching { IconPackInfo(pkg, pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()) }.getOrNull() }
                .sortedBy { it.label.lowercase() }
        }
    }
}
