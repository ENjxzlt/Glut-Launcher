package de.glut.launcher.data

import android.content.Context
import android.text.Html
import de.glut.launcher.ui.CustomBackgrounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Ein Foto mit der Quellenangabe, die Wikimedia Commons für die Lizenz verlangt. */
data class StockImage(val file: File, val credit: String?)

/**
 * Quellen für den Bildschirmschoner: eigene Dateien im app-eigenen Ordner
 * (/sdcard/Android/data/de.glut.launcher/files/screensaver) oder Naturfotos aus den
 * „ausgewählten Bildern“ von Wikimedia Commons (freie Lizenzen, Quellenangabe wird eingeblendet).
 * Geladene Fotos bleiben zwischengespeichert und laufen auch offline weiter.
 */
object ScreensaverMedia {
    private const val API = "https://commons.wikimedia.org/w/api.php"
    private const val USER_AGENT = "GlutLauncher (https://github.com/ENjxzlt/Glut-Launcher)"
    private const val STOCK_CACHE_MAX = 30

    /** Kategorien „Featured pictures of …“: nur Natur. */
    private val categories = listOf("forests", "mountains", "beaches", "waterfalls", "lakes", "sunsets", "flowers")
    private val used = mutableSetOf<String>()

    fun dir(context: Context): File =
        (context.getExternalFilesDir("screensaver") ?: File(context.filesDir, "screensaver")).also { it.mkdirs() }

    fun list(context: Context): List<File> =
        dir(context).listFiles { file -> file.isFile && (CustomBackgrounds.isImage(file) || CustomBackgrounds.isVideo(file)) }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()

    private fun stockDir(context: Context) = File(context.cacheDir, "screensaver-stock").also { it.mkdirs() }

    /** Neues Naturfoto laden; ohne Verbindung ein bereits geladenes. */
    suspend fun nextStock(context: Context): StockImage? = withContext(Dispatchers.IO) {
        val dir = stockDir(context)
        val fresh = runCatching { download(dir) }.getOrNull()
        if (fresh != null) {
            prune(dir)
            fresh
        } else {
            dir.listFiles { f -> f.extension == "jpg" && f.length() > 0 }?.randomOrNull()?.let { StockImage(it, creditOf(it)) }
        }
    }

    private fun download(dir: File): StockImage {
        repeat(4) {
            val category = categories.random()
            val letter = ('A'..'Z').random()
            val candidates = query(category, letter).filter { it.title !in used }
            val pick = candidates.randomOrNull() ?: return@repeat
            val target = File(dir, "stock-${System.currentTimeMillis()}.jpg")
            open(pick.url).use { input -> target.outputStream().use { input.copyTo(it) } }
            if (target.length() == 0L) { target.delete(); return@repeat }
            used += pick.title
            File(dir, target.nameWithoutExtension + ".txt").writeText(pick.credit)
            return StockImage(target, pick.credit)
        }
        error("Kein Foto gefunden")
    }

    private class Candidate(val title: String, val url: String, val credit: String)

    /** Bis zu 50 Fotos einer Kategorie ab einem zufälligen Anfangsbuchstaben, nur Querformat in ausreichender Größe. */
    private fun query(category: String, letter: Char): List<Candidate> {
        val params = mapOf(
            "action" to "query", "format" to "json", "generator" to "categorymembers",
            "gcmtitle" to "Category:Featured_pictures_of_$category", "gcmtype" to "file", "gcmlimit" to "50",
            "gcmsort" to "sortkey", "gcmstartsortkeyprefix" to letter.toString(),
            "prop" to "imageinfo", "iiprop" to "url|size|mime|extmetadata", "iiurlwidth" to "1920",
            "iiextmetadatafilter" to "Artist|LicenseShortName|ObjectName",
        )
        val body = open("$API?" + params.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" })
            .use { it.bufferedReader().readText() }
        val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
        return pages.keys().asSequence().mapNotNull { key ->
            val page = pages.getJSONObject(key)
            val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: return@mapNotNull null
            val width = info.optInt("width")
            val height = info.optInt("height").coerceAtLeast(1)
            val ratio = width.toFloat() / height
            val url = info.optString("thumburl")
            if (info.optString("mime") != "image/jpeg" || width < 1920 || ratio < 1.3f || ratio > 2.0f || url.isBlank()) return@mapNotNull null
            val meta = info.optJSONObject("extmetadata")
            fun field(name: String) = meta?.optJSONObject(name)?.optString("value").orEmpty().let(::plain)
            val name = field("ObjectName").ifBlank { page.optString("title").removePrefix("File:").substringBeforeLast('.') }
            val credit = listOf(name, field("Artist"), field("LicenseShortName"), "Wikimedia Commons")
                .filter { it.isNotBlank() }.joinToString(" · ")
            Candidate(page.optString("title"), url, credit.take(160))
        }.toList()
    }

    private fun plain(html: String) = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().trim().replace(Regex("\\s+"), " ")

    private fun open(url: String): java.io.InputStream {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 20000
        connection.setRequestProperty("User-Agent", USER_AGENT)
        check(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
        return connection.inputStream
    }

    private fun creditOf(image: File): String? =
        File(image.parentFile, image.nameWithoutExtension + ".txt").takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }

    private fun prune(dir: File) {
        dir.listFiles { f -> f.extension == "jpg" }?.sortedByDescending { it.lastModified() }?.drop(STOCK_CACHE_MAX)?.forEach {
            File(dir, it.nameWithoutExtension + ".txt").delete()
            it.delete()
        }
    }
}
