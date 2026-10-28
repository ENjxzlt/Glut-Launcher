package de.glut.launcher.data

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import de.glut.launcher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/** Ein Vorschlag aus Jellyfin für die Leiste auf der Startseite. */
data class JellyfinItem(
    val id: String,
    val title: String,
    val subtitle: String?,
    val type: String,
    val year: Int?,
    val rating: Float?,
    val overview: String?,
    /** Wiedergabefortschritt in Prozent, nur bei „Weiter schauen“. */
    val progress: Int?,
    val imageUrl: String?,
    val minutesLeft: Int?,
)

enum class JellyfinFeed { CONTINUE, NEW, RECOMMENDED }

class QuickConnectSession(val code: String, val secret: String)

class JellyfinException(message: String, val code: Int = 0) : IOException(message)

/**
 * Minimaler Jellyfin-Client: Anmeldung per Benutzername/Passwort oder Quick Connect
 * und die Listen „Weiter schauen“, „Neu hinzugefügt“ und „Empfohlen“.
 */
class Jellyfin(private val prefs: Prefs) {

    // --- Anmeldung ---

    /** Prüft die Adresse und liefert den Servernamen. */
    suspend fun ping(url: String): String = io {
        JSONObject(request("GET", "${normalize(url)}/System/Info/Public", null, token = null)).optString("ServerName", "Jellyfin")
    }

    suspend fun login(url: String, username: String, password: String) = io {
        val base = normalize(url)
        val result = JSONObject(
            request("POST", "$base/Users/AuthenticateByName", JSONObject().put("Username", username).put("Pw", password), token = null),
        )
        store(base, result)
    }

    suspend fun quickConnectStart(url: String): QuickConnectSession = io {
        val json = JSONObject(request("POST", "${normalize(url)}/QuickConnect/Initiate", JSONObject(), token = null))
        QuickConnectSession(json.getString("Code"), json.getString("Secret"))
    }

    /** true, sobald der Code in einer angemeldeten Jellyfin-App bestätigt wurde (dann ist man angemeldet). */
    suspend fun quickConnectPoll(url: String, session: QuickConnectSession): Boolean = io {
        val base = normalize(url)
        val state = JSONObject(request("GET", "$base/QuickConnect/Connect?secret=${enc(session.secret)}", null, token = null))
        if (!state.optBoolean("Authenticated")) return@io false
        val result = JSONObject(
            request("POST", "$base/Users/AuthenticateWithQuickConnect", JSONObject().put("Secret", session.secret), token = null),
        )
        store(base, result)
        true
    }

    private fun store(base: String, result: JSONObject) {
        val user = result.getJSONObject("User")
        prefs.jellyfinUrl = base
        prefs.jellyfinToken = result.getString("AccessToken")
        prefs.jellyfinUserId = user.getString("Id")
        prefs.jellyfinUserName = user.optString("Name")
    }

    // --- Vorschläge ---

    suspend fun items(feed: JellyfinFeed, limit: Int = 12): List<JellyfinItem> = io {
        val uid = prefs.jellyfinUserId
        val common = "enableImageTypes=Primary,Backdrop,Thumb&imageTypeLimit=1&fields=Overview&enableUserData=true&limit=$limit"
        val body = when (feed) {
            JellyfinFeed.CONTINUE -> firstOf(
                "/UserItems/Resume?userId=$uid&mediaTypes=Video&$common",
                "/Users/$uid/Items/Resume?mediaTypes=Video&$common",
            )
            JellyfinFeed.NEW -> firstOf(
                "/Items/Latest?userId=$uid&includeItemTypes=Movie,Series&$common",
                "/Users/$uid/Items/Latest?includeItemTypes=Movie,Series&$common",
            )
            JellyfinFeed.RECOMMENDED -> firstOf(
                "/Items/Suggestions?userId=$uid&mediaType=Video&type=Movie,Series&$common",
                "/Users/$uid/Suggestions?mediaType=Video&type=Movie,Series&$common",
                "/Items?userId=$uid&recursive=true&includeItemTypes=Movie,Series&sortBy=Random&$common",
            )
        }
        val array = if (body.trimStart().startsWith("[")) JSONArray(body) else JSONObject(body).optJSONArray("Items") ?: JSONArray()
        (0 until array.length()).mapNotNull { runCatching { parse(array.getJSONObject(it)) }.getOrNull() }
    }

    /** Probiert die Pfade der Reihe nach: neuere und ältere Jellyfin-Versionen nennen sie unterschiedlich. */
    private fun firstOf(vararg paths: String): String {
        var last: Exception? = null
        for (path in paths) {
            try {
                return request("GET", prefs.jellyfinUrl + path, null, token = prefs.jellyfinToken)
            } catch (e: JellyfinException) {
                if (e.code != 404 && e.code != 405 && e.code != 400) throw e
                last = e
            }
        }
        throw last ?: JellyfinException("Keine Antwort")
    }

    private fun parse(o: JSONObject): JellyfinItem {
        val id = o.getString("Id")
        val type = o.optString("Type")
        val episode = type == "Episode"
        val userData = o.optJSONObject("UserData")
        val percent = userData?.optDouble("PlayedPercentage", Double.NaN)?.takeIf { !it.isNaN() && it > 0.0 }?.toInt()
        val ticks = o.optLong("RunTimeTicks", 0L)
        val left = if (percent != null && ticks > 0L) (ticks / 600_000_000L * (100 - percent) / 100).toInt() else null
        return JellyfinItem(
            id = id,
            title = if (episode) o.optString("SeriesName").ifBlank { o.optString("Name") } else o.optString("Name"),
            subtitle = if (episode) {
                val season = o.optInt("ParentIndexNumber", -1)
                val number = o.optInt("IndexNumber", -1)
                listOfNotNull(
                    if (season >= 0 && number >= 0) "S$season E$number" else null,
                    o.optString("Name").takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { null }
            } else {
                null
            },
            type = type,
            year = o.optInt("ProductionYear", 0).takeIf { it > 0 },
            rating = o.optDouble("CommunityRating", Double.NaN).takeIf { !it.isNaN() }?.toFloat(),
            overview = o.optString("Overview").takeIf { it.isNotBlank() },
            progress = percent,
            imageUrl = imageFor(o, id, episode),
            minutesLeft = left,
        )
    }

    /** Querformat bevorzugt: Thumb, dann Hintergrundbild, bei Folgen das der Serie, zuletzt das Plakat. */
    private fun imageFor(o: JSONObject, id: String, episode: Boolean): String? {
        fun url(item: String, kind: String, tag: String?) =
            "${prefs.jellyfinUrl}/Items/$item/Images/$kind?maxWidth=640&quality=85" +
                (if (tag.isNullOrBlank()) "" else "&tag=${enc(tag)}") + "&api_key=${enc(prefs.jellyfinToken)}"
        val tags = o.optJSONObject("ImageTags")
        val backdrops = o.optJSONArray("BackdropImageTags")
        return when {
            tags?.has("Thumb") == true -> url(id, "Thumb", tags.optString("Thumb"))
            backdrops != null && backdrops.length() > 0 -> url(id, "Backdrop", backdrops.optString(0))
            episode && o.has("ParentThumbItemId") -> url(o.getString("ParentThumbItemId"), "Thumb", o.optString("ParentThumbImageTag"))
            episode && o.has("ParentBackdropItemId") ->
                url(o.getString("ParentBackdropItemId"), "Backdrop", o.optJSONArray("ParentBackdropImageTags")?.optString(0))
            tags?.has("Primary") == true -> url(id, "Primary", tags.optString("Primary"))
            episode && o.has("SeriesId") -> url(o.getString("SeriesId"), "Primary", o.optString("SeriesPrimaryImageTag"))
            else -> null
        }
    }

    // --- HTTP ---

    private fun request(method: String, url: String, body: JSONObject?, token: String?): String {
        if (prefs.jellyfinDeviceId.isBlank()) prefs.jellyfinDeviceId = UUID.randomUUID().toString()
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 8000
            connection.readTimeout = 15000
            val auth = "MediaBrowser Client=\"Glut Launcher\", Device=\"Fire TV\", DeviceId=\"${prefs.jellyfinDeviceId}\", " +
                "Version=\"${BuildConfig.VERSION_NAME}\"" + (if (token.isNullOrBlank()) "" else ", Token=\"$token\"")
            connection.setRequestProperty("Authorization", auth)
            connection.setRequestProperty("X-Emby-Authorization", auth)
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                throw JellyfinException(
                    when (code) {
                        401 -> "Anmeldung abgelehnt (HTTP 401)"
                        else -> "Jellyfin: HTTP $code"
                    },
                    code,
                )
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    companion object {
        fun normalize(url: String): String {
            var u = url.trim().trimEnd('/')
            if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://$u"
            if (!u.substringAfter("://").contains(':') && u.startsWith("http://")) u += ":8096"
            return u
        }
    }
}

/** Lädt Vorschaubilder und merkt sich die letzten, damit Scrollen und Rückkehr nicht neu laden. */
object JellyfinImages {
    private val cache = LruCache<String, ImageBitmap>(40)

    suspend fun load(url: String): ImageBitmap? {
        cache.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 15000
                try {
                    if (connection.responseCode !in 200..299) return@runCatching null
                    val bytes = connection.inputStream.use { it.readBytes() }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    while (bounds.outWidth / (sample * 2) >= 640) sample *= 2
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                        ?.asImageBitmap()
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()?.also { cache.put(url, it) }
        }
    }
}
