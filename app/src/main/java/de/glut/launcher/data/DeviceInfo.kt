package de.glut.launcher.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.view.WindowManager
import de.glut.launcher.t
import java.util.Locale

/** Angaben für „System › Geräteinfo“, ohne Amazon-Einstellungen. */
object DeviceInfo {
    fun collect(context: Context, ip: String?, versionName: String): List<Pair<String, String>> {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes
        val used = total - stat.availableBytes
        val memory = ActivityManager.MemoryInfo().also {
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)
        }
        val metrics = context.resources.displayMetrics
        @Suppress("DEPRECATION")
        val refresh = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.refreshRate
        return listOf(
            t("Modell", "Model") to "${Build.MANUFACTURER} ${Build.MODEL}",
            "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            t("Systemversion", "Build") to Build.DISPLAY,
            t("Speicher", "Storage") to "${gb(used)} / ${gb(total)}",
            t("Arbeitsspeicher", "Memory") to gb(memory.totalMem),
            t("Bildschirm", "Display") to "${metrics.widthPixels}×${metrics.heightPixels} · ${"%.0f".format(Locale.ROOT, refresh)} Hz",
            t("IP-Adresse", "IP address") to (ip ?: "–"),
            t("Laufzeit", "Uptime") to uptime(),
            "Glut Launcher" to versionName,
        )
    }

    private fun gb(bytes: Long) = "%.1f GB".format(Locale.ROOT, bytes / 1_073_741_824.0)

    private fun uptime(): String {
        val minutes = SystemClock.elapsedRealtime() / 60_000
        val days = minutes / 1440
        val hours = minutes / 60 % 24
        return if (days > 0) t("$days T $hours Std.", "${days}d ${hours}h") else t("$hours Std. ${minutes % 60} Min.", "${hours}h ${minutes % 60}m")
    }
}
