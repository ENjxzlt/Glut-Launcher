package de.glut.launcher.data

import android.content.Context
import de.glut.launcher.ui.CustomBackgrounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Quellen für den Bildschirmschoner: eigene Dateien im app-eigenen Ordner
 * (/sdcard/Android/data/de.glut.launcher/files/screensaver) oder zufällige Fotos von
 * picsum.photos (Unsplash-Lizenz, keine Namensnennung nötig), zwischengespeichert für offline.
 */
object ScreensaverMedia {
    private const val STOCK_URL = "https://picsum.photos/1920/1080"
    private const val STOCK_CACHE_MAX = 30

    fun dir(context: Context): File =
        (context.getExternalFilesDir("screensaver") ?: File(context.filesDir, "screensaver")).also { it.mkdirs() }

    fun list(context: Context): List<File> =
        dir(context).listFiles { file -> file.isFile && (CustomBackgrounds.isImage(file) || CustomBackgrounds.isVideo(file)) }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()

    private fun stockDir(context: Context) = File(context.cacheDir, "screensaver-stock").also { it.mkdirs() }

    /** Neues Foto laden; ohne Verbindung ein bereits geladenes. */
    suspend fun nextStock(context: Context): File? = withContext(Dispatchers.IO) {
        val dir = stockDir(context)
        val fresh = runCatching { download(dir) }.getOrNull()
        if (fresh != null) {
            dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(STOCK_CACHE_MAX)?.forEach { it.delete() }
            fresh
        } else {
            dir.listFiles()?.filter { it.length() > 0 }?.randomOrNull()
        }
    }

    private fun download(dir: File): File {
        val connection = URL(STOCK_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8000
            connection.readTimeout = 15000
            check(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
            val target = File(dir, "stock-${System.currentTimeMillis()}.jpg")
            connection.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
            if (target.length() == 0L) { target.delete(); error("leer") }
            return target
        } finally {
            connection.disconnect()
        }
    }
}
