package de.glut.launcher.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.glut.launcher.BuildConfig
import de.glut.launcher.t
import de.glut.launcher.service.InstallResultReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val versionCode: Int, val versionName: String, val apkUrl: String)

/**
 * Sucht auf GitHub nach einer neueren Version und installiert sie über den
 * Paket-Installer. Android fragt vor der Installation nach Bestätigung.
 */
class Updater(private val context: Context, private val beta: () -> Boolean = { false }) {
    var available by mutableStateOf<UpdateInfo?>(null)
        private set
    var status by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    val currentVersion: String get() = "${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})"

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        status = null
        runCatching {
            val json = JSONObject(get(if (beta()) BETA_API else STABLE_API))
            val body = json.optString("body")
            val code = Regex("versionCode=(\\d+)").find(body)?.groupValues?.get(1)?.toInt() ?: return@runCatching null
            val name = Regex("versionName=([\\w.\\-]+)").find(body)?.groupValues?.get(1) ?: code.toString()
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") }
                ?.getString("browser_download_url") ?: return@runCatching null
            UpdateInfo(code, name, apk).takeIf { code > BuildConfig.VERSION_CODE }
        }.onFailure {
            status = t("Update-Prüfung fehlgeschlagen: keine Verbindung zu GitHub.", "Update check failed: no connection to GitHub.")
        }.getOrNull().also {
            available = it
            if (it == null && status == null) status = t("Glut Launcher ist aktuell.", "Glut Launcher is up to date.")
        }
    }

    /** Lädt die APK herunter und übergibt sie an den Installer. */
    suspend fun install(info: UpdateInfo) {
        if (busy) return
        busy = true
        status = t("Lade Version ${info.versionName} …", "Downloading version ${info.versionName} …")
        try {
            val file = withContext(Dispatchers.IO) { download(info.apkUrl) }
            status = t("Installiere …", "Installing …")
            withContext(Dispatchers.IO) { commit(file) }
        } catch (e: Exception) {
            status = t("Update fehlgeschlagen: ", "Update failed: ") + (e.message ?: t("unbekannter Fehler", "unknown error"))
        } finally {
            busy = false
        }
    }

    fun clearStatus() {
        status = null
    }

    /** Darf diese App Pakete installieren? (Fire OS: „Apps unbekannter Herkunft“) */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    private fun download(url: String): File {
        val target = File(context.cacheDir, "update.apk")
        var connection = URL(url).openConnection() as HttpURLConnection
        // GitHub leitet auf einen anderen Host um; HttpURLConnection folgt nur innerhalb des Protokolls.
        repeat(5) {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            val code = connection.responseCode
            if (code in 300..399) {
                val next = connection.getHeaderField("Location")
                connection.disconnect()
                connection = URL(URL(url), next).openConnection() as HttpURLConnection
            } else {
                if (code !in 200..299) throw IllegalStateException(t("Download-Fehler $code", "Download error $code"))
                connection.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
                connection.disconnect()
                return target
            }
        }
        throw IllegalStateException(t("Zu viele Weiterleitungen", "Too many redirects"))
    }

    private fun commit(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("glut-launcher.apk", 0, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val intent = Intent(context, InstallResultReceiver::class.java)
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pending.intentSender)
        }
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            if (connection.responseCode !in 200..299) throw IllegalStateException("HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val REPO_API = "https://api.github.com/repos/ENjxzlt/Glut-Launcher"

        /** Neueste stabile Version (GitHub „latest“ überspringt Vorabversionen). */
        private const val STABLE_API = "$REPO_API/releases/latest"

        /** Automatischer Build jeder Änderung, als Vorabversion veröffentlicht. */
        private const val BETA_API = "$REPO_API/releases/tags/neuester-build"
    }
}
