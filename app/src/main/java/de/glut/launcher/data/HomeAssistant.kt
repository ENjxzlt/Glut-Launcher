package de.glut.launcher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class HaEntity(val id: String, val name: String, val state: String) {
    val domain: String get() = id.substringBefore('.')
    val isOn: Boolean get() = state == "on"
    val isAction: Boolean get() = domain == "scene" || domain == "script"
}

/** Ergebnis eines Anmeldeschritts: weiteres Formular (z. B. 2FA) oder fertig. */
sealed interface LoginStep {
    data class NeedsCode(val flowId: String) : LoginStep
    data object Done : LoginStep
    data class Failed(val message: String) : LoginStep
}

class HaException(message: String) : IOException(message)

/**
 * Minimaler Home-Assistant-Client über die REST-API.
 * Anmeldung per Benutzername/Passwort (Login-Flow) oder mit einem
 * langlebigen Zugriffstoken aus dem HA-Profil.
 */
class HomeAssistant(private val prefs: Prefs) {

    suspend fun login(url: String, username: String, password: String): LoginStep = io {
        val base = normalize(url)
        val start = post(
            "$base/auth/login_flow",
            JSONObject()
                .put("client_id", CLIENT_ID)
                .put("handler", JSONArray().put("homeassistant").put(JSONObject.NULL))
                .put("redirect_uri", REDIRECT_URI),
        )
        val flowId = start.getString("flow_id")
        val step = post(
            "$base/auth/login_flow/$flowId",
            JSONObject().put("client_id", CLIENT_ID).put("username", username).put("password", password),
        )
        finishStep(base, flowId, step)
    }

    suspend fun submitCode(url: String, flowId: String, code: String): LoginStep = io {
        val base = normalize(url)
        val step = post("$base/auth/login_flow/$flowId", JSONObject().put("client_id", CLIENT_ID).put("code", code))
        finishStep(base, flowId, step)
    }

    /** Langlebiges Token aus dem HA-Profil; läuft nicht ab. */
    suspend fun useLongLivedToken(url: String, token: String) = io {
        val base = normalize(url)
        request("GET", "$base/api/", null, token.trim()) // prüft, ob Token und Adresse stimmen
        prefs.haUrl = base
        prefs.haAccessToken = token.trim()
        prefs.haRefreshToken = ""
        prefs.haTokenExpiry = Long.MAX_VALUE
    }

    suspend fun states(): List<HaEntity> = io {
        val array = JSONArray(authed("GET", "/api/states", null))
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            val id = obj.getString("entity_id")
            val name = obj.optJSONObject("attributes")?.optString("friendly_name").orEmpty().ifBlank { id }
            HaEntity(id, name, obj.optString("state"))
        }.filter { it.domain in DOMAINS }.sortedBy { it.name.lowercase() }
    }

    /** Schaltet um (Licht, Schalter …) oder führt aus (Szene, Skript). */
    suspend fun activate(entityId: String) = io {
        val domain = entityId.substringBefore('.')
        val service = if (domain == "scene" || domain == "script") "$domain/turn_on" else "homeassistant/toggle"
        authed("POST", "/api/services/$service", JSONObject().put("entity_id", entityId).toString())
    }

    /** Szene/Skript nur auslösen, nicht umschalten. */
    suspend fun turnOn(entityId: String) = io {
        val domain = entityId.substringBefore('.')
        val service = if (domain == "scene" || domain == "script") "$domain/turn_on" else "homeassistant/turn_on"
        authed("POST", "/api/services/$service", JSONObject().put("entity_id", entityId).toString())
    }

    private fun finishStep(base: String, flowId: String, step: JSONObject): LoginStep {
        if (step.optString("type") == "create_entry") {
            val code = step.getString("result")
            val tokens = postForm(
                "$base/auth/token",
                mapOf("grant_type" to "authorization_code", "code" to code, "client_id" to CLIENT_ID),
            )
            storeTokens(base, tokens)
            return LoginStep.Done
        }
        val errors = step.optJSONObject("errors")
        if (errors != null && errors.length() > 0) {
            return LoginStep.Failed(
                when (errors.optString("base")) {
                    "invalid_auth" -> "Benutzername oder Passwort falsch."
                    "invalid_code" -> "Code falsch."
                    else -> "Anmeldung abgelehnt."
                },
            )
        }
        return if (step.optString("step_id") == "mfa") LoginStep.NeedsCode(flowId) else LoginStep.Failed("Unerwartete Antwort von Home Assistant.")
    }

    private fun storeTokens(base: String, tokens: JSONObject) {
        prefs.haUrl = base
        prefs.haAccessToken = tokens.getString("access_token")
        tokens.optString("refresh_token").takeIf { it.isNotBlank() }?.let { prefs.haRefreshToken = it }
        prefs.haTokenExpiry = System.currentTimeMillis() + tokens.optLong("expires_in", 1800L) * 1000L - 60_000L
    }

    private fun authed(method: String, path: String, body: String?): String {
        if (!prefs.haConnected) throw HaException("Nicht mit Home Assistant verbunden.")
        if (System.currentTimeMillis() > prefs.haTokenExpiry && prefs.haRefreshToken.isNotBlank()) refresh()
        return request(method, prefs.haUrl + path, body, prefs.haAccessToken)
    }

    private fun refresh() {
        val tokens = postForm(
            "${prefs.haUrl}/auth/token",
            mapOf("grant_type" to "refresh_token", "refresh_token" to prefs.haRefreshToken, "client_id" to CLIENT_ID),
        )
        storeTokens(prefs.haUrl, tokens)
    }

    private fun post(url: String, body: JSONObject): JSONObject = JSONObject(request("POST", url, body.toString(), null))

    private fun postForm(url: String, fields: Map<String, String>): JSONObject {
        val body = fields.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        return JSONObject(request("POST", url, body, null, "application/x-www-form-urlencoded"))
    }

    private fun request(
        method: String,
        url: String,
        body: String?,
        token: String?,
        contentType: String = "application/json",
    ): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 5000
            connection.readTimeout = 8000
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            when {
                code == 401 -> throw HaException("Zugriff verweigert – bitte neu anmelden.")
                code !in 200..299 -> throw HaException("Home Assistant antwortet mit Fehler $code.")
            }
            return text
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    companion object {
        // Gleicher Host für client_id und redirect_uri: HA akzeptiert das ohne Abruf der Seite.
        private const val CLIENT_ID = "https://glut-launcher.app/"
        private const val REDIRECT_URI = "https://glut-launcher.app/auth"

        val DOMAINS = setOf("light", "switch", "input_boolean", "fan", "scene", "script")

        fun normalize(url: String): String {
            var u = url.trim().trimEnd('/')
            if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://$u"
            if (!u.substringAfter("://").contains(':') && u.startsWith("http://")) u += ":8123"
            return u
        }
    }
}
