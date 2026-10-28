package de.glut.launcher.data

import android.content.ComponentName
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    /** Vorinstalliert und nie aktualisiert, z. B. Amazon-Dienste. */
    val isSystem: Boolean = false,
)

/**
 * Banner (16:9, typisch für TV-Apps). Fehlt es, nutzt die Kachel ein breites Logo
 * oder das Symbol auf einer Fläche in [iconColor], der Hauptfarbe des Symbols.
 */
/** Untergrund und Randanteil für ein Symbol, das nicht als volles Quadrat aussieht. */
data class IconPlate(val color: Color, val padding: Float)

data class AppArt(
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
    val logo: ImageBitmap? = null,
    val iconColor: Color? = null,
    /** Hintergrund für Symbole mit durchsichtigen Ecken (z. B. Play Store); null = Symbol füllt das Quadrat. */
    val iconBackground: Color? = null,
    val iconPadding: Float = 0f,
)

class AppRepository(private val context: Context) {
    private val pm: PackageManager = context.packageManager

    fun loadApps(): List<AppEntry> {
        val result = LinkedHashMap<String, AppEntry>()
        // TV-Apps zuerst, dann normale Android-Apps, die jemand per Sideload installiert hat.
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (info in pm.queryIntentActivities(intent, 0)) {
                val activity = info.activityInfo ?: continue
                if (activity.packageName == context.packageName) continue
                if (activity.packageName == AMAZON_LAUNCHER) continue
                if (activity.packageName in result) continue
                result[activity.packageName] = AppEntry(
                    packageName = activity.packageName,
                    activityName = activity.name,
                    label = info.loadLabel(pm).toString(),
                    isSystem = isSystemApp(activity.applicationInfo),
                )
            }
        }
        return result.values.sortedBy { it.label.lowercase() }.also { cachedApps = it }
    }

    /**
     * Vorinstallierte Apps, die nie über den Store aktualisiert wurden. Streaming-Apps
     * wie Prime Video sind zwar vorinstalliert, werden aber aktualisiert und bleiben sichtbar.
     */
    private fun isSystemApp(info: ApplicationInfo?): Boolean {
        if (info == null || info.packageName in ALWAYS_USER) return false
        val system = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val updated = (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        return system && !updated
    }

    fun launchIntent(app: AppEntry): Intent =
        Intent(Intent.ACTION_MAIN)
            .setComponent(ComponentName(app.packageName, app.activityName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    /** Gewähltes Icon-Pack; mit [setIconPack] wechseln, dann laden sich die Symbole neu. */
    private var pack: IconPack? = null
    var packVersion by androidx.compose.runtime.mutableIntStateOf(0)
        private set

    fun setIconPack(value: IconPack?) {
        pack = value
        artCache.clear()
        packVersion++
    }

    suspend fun loadArt(app: AppEntry): AppArt {
        artCache[app.packageName]?.let { return it }
        return withContext(Dispatchers.IO) {
            val component = ComponentName(app.packageName, app.activityName)
            val banner = runCatching {
                pm.getActivityBanner(component) ?: pm.getApplicationBanner(app.packageName)
            }.getOrNull()
            val icon = runCatching { pm.getActivityIcon(component) }.getOrNull()
            // Manche Apps (z. B. von Amazon) haben kein Banner, aber ein breites Logo.
            val logo = if (banner != null) null else runCatching {
                pm.getActivityLogo(component) ?: pm.getApplicationLogo(app.packageName)
            }.getOrNull()?.takeIf { it.intrinsicWidth > it.intrinsicHeight * 1.3f }
            val themed = pack?.let { p -> p.iconFor(component) ?: icon?.let { p.fallback(it, app.packageName) } }
            val iconBitmap = themed ?: icon?.let { squareBitmap(it) }
            val iconBackground = iconBitmap?.let { backgroundFor(it) }
            AppArt(
                banner = banner?.render(320, 180),
                icon = iconBitmap?.asImageBitmap(),
                logo = logo?.renderKeepingAspect(maxWidth = 320, maxHeight = 120),
                iconColor = iconBitmap?.let { dominantColor(it) },
                iconBackground = iconBackground?.color,
                iconPadding = iconBackground?.padding ?: 0f,
            ).also { artCache[app.packageName] = it }
        }
    }

    fun clearArtCache() = artCache.clear()

    /**
     * Symbol als Quadrat. Breite Logos (z. B. YouTube TV) werden nicht gestreckt oder abgeschnitten,
     * sondern mit Rand mittig eingepasst.
     */
    private fun squareBitmap(icon: Drawable, size: Int = 144): Bitmap? {
        val w = icon.intrinsicWidth
        val h = icon.intrinsicHeight
        if (w <= 0 || h <= 0 || kotlin.math.abs(w.toFloat() / h - 1f) < 0.12f) return runCatching { icon.toBitmap(size, size) }.getOrNull()
        val scale = minOf(size / w.toFloat(), size / h.toFloat())
        val inner = runCatching { icon.toBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1)) }.getOrNull() ?: return null
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(out).drawBitmap(inner, (size - inner.width) / 2f, (size - inner.height) / 2f, null)
        return out
    }

    /**
     * Manche Symbole sehen als Quadrat schlecht aus: Logos auf durchsichtigem Grund (Play Store) und
     * Symbole, deren Inhalt bis an den Rand reicht (YouTube). Sie bekommen einen Untergrund mit Rand.
     * Normale Symbole mit abgerundeten Ecken füllen das Quadrat weiter ganz aus (null).
     */
    private fun backgroundFor(bitmap: Bitmap): IconPlate? {
        val w = bitmap.width
        val h = bitmap.height
        fun alpha(p: Int) = android.graphics.Color.alpha(p)
        var transparent = 0
        var total = 0
        var sum = 0f
        var visible = 0
        for (y in 0 until h step 3) for (x in 0 until w step 3) {
            val p = bitmap.getPixel(x, y)
            total++
            if (alpha(p) < 128) {
                transparent++
            } else {
                sum += (0.299f * android.graphics.Color.red(p) + 0.587f * android.graphics.Color.green(p) + 0.114f * android.graphics.Color.blue(p)) / 255f
                visible++
            }
        }
        if (transparent.toFloat() / total >= 0.30f) {
            val luminance = if (visible == 0) 0f else sum / visible
            return IconPlate(if (luminance > 0.75f) Color(0xFF202124) else Color(0xFFF4F4F4), 0.16f)
        }
        val corners = listOf(bitmap.getPixel(1, 1), bitmap.getPixel(w - 2, 1), bitmap.getPixel(1, h - 2), bitmap.getPixel(w - 2, h - 2))
        if (corners.any { alpha(it) < 250 }) return null
        fun channel(p: Int, shift: Int) = (p shr shift) and 0xFF
        val base = corners[0]
        if (corners.any { c -> listOf(16, 8, 0).any { kotlin.math.abs(channel(c, it) - channel(base, it)) > 24 } }) return null
        var minX = w; var maxX = -1; var minY = h; var maxY = -1
        for (y in 0 until h) for (x in 0 until w) {
            val p = bitmap.getPixel(x, y)
            val distance = listOf(16, 8, 0).sumOf { kotlin.math.abs(channel(p, it) - channel(base, it)) }
            if (distance > 48) {
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (y < minY) minY = y; if (y > maxY) maxY = y
            }
        }
        if (maxX < 0) return null
        val reachesEdge = (maxX - minX + 1) >= w * 0.88f || (maxY - minY + 1) >= h * 0.88f
        return if (reachesEdge) IconPlate(Color(base), 0.12f) else null
    }

    private fun Drawable.render(width: Int, height: Int): ImageBitmap? =
        runCatching { toBitmap(width, height).asImageBitmap() }.getOrNull()

    private fun Drawable.renderKeepingAspect(maxWidth: Int, maxHeight: Int): ImageBitmap? {
        val w = intrinsicWidth.takeIf { it > 0 } ?: return null
        val h = intrinsicHeight.takeIf { it > 0 } ?: return null
        val scale = minOf(maxWidth / w.toFloat(), maxHeight / h.toFloat())
        return render((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
    }

    /** Kräftigste Farbe des Symbols: Durchschnitt der Pixel, gewichtet nach Sättigung. */
    private fun dominantColor(bitmap: Bitmap): Color? {
        val small = runCatching { Bitmap.createScaledBitmap(bitmap, 24, 24, true) }.getOrNull() ?: return null
        var r = 0f
        var g = 0f
        var b = 0f
        var total = 0f
        val hsv = FloatArray(3)
        for (y in 0 until small.height) for (x in 0 until small.width) {
            val p = small.getPixel(x, y)
            if (android.graphics.Color.alpha(p) < 128) continue
            android.graphics.Color.colorToHSV(p, hsv)
            val weight = 0.05f + hsv[1] * hsv[2]
            r += android.graphics.Color.red(p) * weight
            g += android.graphics.Color.green(p) * weight
            b += android.graphics.Color.blue(p) * weight
            total += weight
        }
        if (total == 0f) return null
        return Color(r / total / 255f, g / total / 255f, b / total / 255f)
    }

    companion object {
        const val AMAZON_LAUNCHER = "com.amazon.tv.launcher"

        // Pro Prozess, nicht pro Activity: Verwirft Fire OS die Activity aus
        // Speichermangel, sind App-Liste und Banner beim nächsten Home-Druck sofort da.
        private val artCache = ConcurrentHashMap<String, AppArt>()

        @Volatile
        var cachedApps: List<AppEntry> = emptyList()
            private set

        /** Vorinstallierte Streaming-Apps, die nie als System-App gelten sollen. */
        private val ALWAYS_USER = setOf(
            "com.amazon.avod",
            "com.amazon.firetv.youtube",
            "com.netflix.ninja",
            "com.amazon.music.tv",
        )
    }
}
