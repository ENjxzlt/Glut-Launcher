package de.glut.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
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

/** Banner (16:9, typisch für TV-Apps) und Symbol als Ersatz. */
data class AppArt(val banner: ImageBitmap?, val icon: ImageBitmap?)

class AppRepository(private val context: Context) {
    private val pm: PackageManager = context.packageManager
    private val artCache = ConcurrentHashMap<String, AppArt>()

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
        return result.values.sortedBy { it.label.lowercase() }
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

    suspend fun loadArt(app: AppEntry): AppArt {
        artCache[app.packageName]?.let { return it }
        return withContext(Dispatchers.IO) {
            val component = ComponentName(app.packageName, app.activityName)
            val banner = runCatching {
                pm.getActivityBanner(component) ?: pm.getApplicationBanner(app.packageName)
            }.getOrNull()
            val icon = runCatching { pm.getActivityIcon(component) }.getOrNull()
            AppArt(
                banner = banner?.render(320, 180),
                icon = icon?.render(144, 144),
            ).also { artCache[app.packageName] = it }
        }
    }

    fun clearArtCache() = artCache.clear()

    private fun Drawable.render(width: Int, height: Int): ImageBitmap? =
        runCatching { toBitmap(width, height).asImageBitmap() }.getOrNull()

    companion object {
        const val AMAZON_LAUNCHER = "com.amazon.tv.launcher"

        /** Vorinstallierte Streaming-Apps, die nie als System-App gelten sollen. */
        private val ALWAYS_USER = setOf(
            "com.amazon.avod",
            "com.amazon.firetv.youtube",
            "com.netflix.ninja",
            "com.amazon.music.tv",
        )
    }
}
