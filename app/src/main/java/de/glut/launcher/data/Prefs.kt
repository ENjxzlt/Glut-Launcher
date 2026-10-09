package de.glut.launcher.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import de.glut.launcher.Language
import de.glut.launcher.ui.Accent
import de.glut.launcher.ui.ColorTheme
import de.glut.launcher.ui.FontChoice
import de.glut.launcher.ui.Ground
import de.glut.launcher.ui.TileStyle
import de.glut.launcher.ui.Wallpaper
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

data class Usage(val lastUsed: Long, val count: Int)

/**
 * Einstellungen und Nutzungsdaten. Jeder Wert liegt zusätzlich in einem
 * Compose-State, damit die Oberfläche bei Änderungen neu zeichnet.
 */
class Prefs(context: Context) {
    private val sp: SharedPreferences = prefs(context)
    private val usageSp: SharedPreferences = context.getSharedPreferences("usage", Context.MODE_PRIVATE)

    // Aussehen
    var tileStyle by enumPref("tileStyle", TileStyle.TINTED)
    var wallpaper by enumPref("wallpaper", Wallpaper.GLUT)
    var ground by enumPref("ground", Ground.MEDIUM)
    var accent by enumPref("accent", Accent.YELLOW)
    var colorTheme by enumPref("colorTheme", ColorTheme.GRUVBOX_DARK)
    var font by enumPref("font", FontChoice.GEIST)
    var language by enumPref("language", Language.SYSTEM)

    // Musik-Widget und Updates
    var musicWidget by boolPref("musicWidget", true)
    var autoUpdateCheck by boolPref("autoUpdateCheck", true)
    var lastUpdateCheck by pref("lastUpdateCheck", 0L, { it.toLongOrNull() }, { it.toString() })

    // Startseite
    var redirectHome by boolPref("redirectHome", true)
    var fastHome by boolPref("fastHome", true)
    var instantCover by boolPref("instantCover", true)
    var favorites by listPref("favorites")
    var hidden by pref("hidden", emptySet<String>(), { s -> s.split('\n').filter { it.isNotBlank() }.toSet() }, { it.joinToString("\n") })
    var foldersEnabled by boolPref("foldersEnabled", true)
    var hideSystemApps by boolPref("hideSystemApps", true)
    var folders by pref("folders", emptyList<Folder>(), { Folder.decodeList(it) }, { Folder.encodeList(it) })

    // Einschalt-App: leer = Startseite
    var startupEnabled by boolPref("startupEnabled", false)
    var startupDay by stringPref("startupDay")
    var startupEvening by stringPref("startupEvening")

    // Home Assistant
    var haEnabled by boolPref("haEnabled", false)
    var haUrl by stringPref("haUrl")
    var haAccessToken by stringPref("haAccessToken")
    var haRefreshToken by stringPref("haRefreshToken")
    var haTokenExpiry by pref("haTokenExpiry", 0L, { it.toLongOrNull() }, { it.toString() })
    var haTilesEnabled by boolPref("haTilesEnabled", true)
    var haTiles by listPref("haTiles")
    var kinoEnabled by boolPref("kinoEnabled", false)
    var kinoApps by pref("kinoApps", emptySet<String>(), { s -> s.split('\n').filter { it.isNotBlank() }.toSet() }, { it.joinToString("\n") })
    var kinoStart by stringPref("kinoStart")
    var kinoEnd by stringPref("kinoEnd")
    var kinoActive by boolPref("kinoActive", false)

    val usage = mutableStateMapOf<String, Usage>().apply {
        usageSp.all.keys.filter { it.startsWith("last:") }.forEach { key ->
            val pkg = key.removePrefix("last:")
            put(pkg, Usage(usageSp.getLong(key, 0L), usageSp.getInt("count:$pkg", 0)))
        }
    }

    fun recordUse(packageName: String) {
        val now = System.currentTimeMillis()
        val count = (usage[packageName]?.count ?: 0) + 1
        usage[packageName] = Usage(now, count)
        usageSp.edit().putLong("last:$packageName", now).putInt("count:$packageName", count).apply()
    }

    // Favoriten: Paketnamen oder "folder:<id>"

    fun toggleFavorite(key: String) {
        favorites = if (key in favorites) favorites - key else favorites + key
    }

    fun moveFavorite(key: String, delta: Int) {
        val list = favorites.toMutableList()
        val from = list.indexOf(key)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, list.lastIndex)
        list.removeAt(from)
        list.add(to, key)
        favorites = list
    }

    fun toggleHidden(packageName: String) {
        hidden = if (packageName in hidden) hidden - packageName else hidden + packageName
    }

    // Ordner

    fun folderOf(packageName: String): Folder? = folders.firstOrNull { packageName in it.apps }

    fun createFolder(name: String, firstApp: String?): Folder {
        val folder = Folder(id = System.currentTimeMillis().toString(36), name = name, apps = emptyList())
        folders = folders + folder
        if (firstApp != null) moveToFolder(firstApp, folder.id)
        return folder
    }

    fun renameFolder(id: String, name: String) {
        folders = folders.map { if (it.id == id) it.copy(name = name) else it }
    }

    fun deleteFolder(id: String) {
        folders = folders.filterNot { it.id == id }
        favorites = favorites - (Folder.FAVORITE_PREFIX + id)
    }

    fun moveToFolder(packageName: String, folderId: String) {
        folders = folders.map { folder ->
            val without = folder.apps - packageName
            if (folder.id == folderId) folder.copy(apps = without + packageName) else folder.copy(apps = without)
        }
    }

    fun removeFromFolder(packageName: String) {
        folders = folders.map { it.copy(apps = it.apps - packageName) }
    }

    // Home Assistant

    val haConnected: Boolean get() = haUrl.isNotBlank() && haAccessToken.isNotBlank()

    fun haLogout() {
        haAccessToken = ""
        haRefreshToken = ""
        haTokenExpiry = 0L
    }

    /** Lässt die Amazon-Startseite für kurze Zeit in Ruhe (für Einstellungen und Updates). */
    fun allowAmazonHomeFor(millis: Long) {
        sp.edit().putLong(KEY_BYPASS_UNTIL, System.currentTimeMillis() + millis).apply()
    }

    fun clearAmazonHomeBypass() {
        sp.edit().remove(KEY_BYPASS_UNTIL).apply()
    }

    private fun <T> pref(
        key: String,
        default: T,
        read: (String) -> T?,
        write: (T) -> String,
    ) = object : ReadWriteProperty<Any?, T> {
        private val state = mutableStateOf(sp.getString(key, null)?.let(read) ?: default)
        override fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            state.value = value
            sp.edit().putString(key, write(value)).apply()
        }
    }

    private fun boolPref(key: String, default: Boolean) =
        pref(key, default, { it.toBooleanStrictOrNull() }, { it.toString() })

    private fun stringPref(key: String) = pref(key, "", { it }, { it })

    private fun listPref(key: String) =
        pref(key, emptyList<String>(), { s -> s.split('\n').filter { it.isNotBlank() } }, { it.joinToString("\n") })

    private inline fun <reified E : Enum<E>> enumPref(key: String, default: E) =
        pref(key, default, { name -> enumValues<E>().firstOrNull { it.name == name } }, { it.name })

    companion object {
        private const val KEY_BYPASS_UNTIL = "bypassUntil"

        private fun prefs(context: Context): SharedPreferences =
            context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

        /** Sofortbild der Startseite zeigen, solange Android App-Starts sperrt. */
        fun instantCover(context: Context): Boolean = prefs(context).getString("instantCover", "true") != "false"

        /** Home-Taste direkt abfangen statt auf die Amazon-Startseite zu warten. */
        fun fastHome(context: Context): Boolean = prefs(context).getString("fastHome", "true") != "false"

        /** Direkter Lesezugriff für den Dienst, ohne Compose-State. */
        fun shouldRedirect(context: Context): Boolean {
            val sp = prefs(context)
            if (sp.getString("redirectHome", "true") == "false") return false
            return System.currentTimeMillis() > sp.getLong(KEY_BYPASS_UNTIL, 0L)
        }
    }
}
