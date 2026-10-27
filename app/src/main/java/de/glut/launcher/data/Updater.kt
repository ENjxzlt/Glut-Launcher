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
        // Die GitHub-API erlaubt ohne Anmeldung nur 60 Anfragen pro Stunde und IP-Adresse
        // (alle Geräte im Haushalt zusammen). Dann weichen wir auf die normale Release-Seite aus.
        val result = runCatching { fromApi() }.recoverCatching { apiError ->
            runCatching { fromReleasePage() }.getOrElse { throw apiError }
        }
        result.onFailure {
            status = t("Update-Prüfung fehlgeschlagen: ", "Update check failed: ") + (it.message ?: t("keine Verbindung zu GitHub", "no connection to GitHub"))
        }
        result.getOrNull().also {
            available = it
            if (it == null && status == null) status = t("Glut Launcher ist aktuell.", "Glut Launcher is up to date.")
        }
    }

    private fun fromApi(): UpdateInfo? {
        val json = JSONObject(get(if (beta()) BETA_API else STABLE_API))
        val assets = json.getJSONArray("assets")
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.getString("name").endsWith(".apk") }
            ?.getString("browser_download_url") ?: return null
        return parse(json.optString("body"), apk)
    }

    private fun fromReleasePage(): UpdateInfo? {
        val tag = if (beta()) BETA_TAG else latestTag()
        val page = get("$WEB/releases/tag/$tag", accept = "text/html")
        return parse(page, "$WEB/releases/download/$tag/$APK_NAME")
    }

    /** /releases/latest leitet auf die Seite der neuesten stabilen Version weiter. */
    private fun latestTag(): String {
        val connection = URL("$WEB/releases/latest").openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            val location = connection.getHeaderField("Location") ?: throw IllegalStateException("HTTP ${connection.responseCode}")
            return location.substringAfterLast("/tag/")
        } finally {
            connection.disconnect()
        }
    }

    /** Der Release-Text trägt „versionCode=…“ und „versionName=…“ (vom Build eingetragen). */
    private fun parse(text: String, apkUrl: String): UpdateInfo? {
        val code = Regex("versionCode=(\\d+)").find(text)?.groupValues?.get(1)?.toInt() ?: return null
        val name = Regex("versionName=([\\w.\\-]+)").find(text)?.groupValues?.get(1) ?: code.toString()
        return UpdateInfo(code, name, apkUrl).takeIf { code > BuildConfig.VERSION_CODE }
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

    private fun get(url: String, accept: String = "application/vnd.github+json"): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("Accept", accept)
            val code = connection.responseCode
            if (code == 403 || code == 429) throw IllegalStateException(t("GitHub-Limit erreicht (HTTP $code), später erneut versuchen", "GitHub rate limit reached (HTTP $code), try again later"))
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val REPO_API = "https://api.github.com/repos/ENjxzlt/Glut-Launcher"
        private const val WEB = "https://github.com/ENjxzlt/Glut-Launcher"
        private const val BETA_TAG = "neuester-build"
        private const val APK_NAME = "glut-launcher.apk"

        /** Neueste stabile Version (GitHub „latest“ überspringt Vorabversionen). */
        private const val STABLE_API = "$REPO_API/releases/latest"

        /** Automatischer Build jeder Änderung, als Vorabversion veröffentlicht. */
        private const val BETA_API = "$REPO_API/releases/tags/$BETA_TAG"
    }
}
