package de.glut.launcher.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import de.glut.launcher.ui.Accent
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

    var tileStyle by enumPref("tileStyle", TileStyle.TINTED)
    var wallpaper by enumPref("wallpaper", Wallpaper.GLUT)
    var ground by enumPref("ground", Ground.MEDIUM)
    var accent by enumPref("accent", Accent.YELLOW)
    var redirectHome by pref("redirectHome", true, { it.toBooleanStrictOrNull() }, { it.toString() })
    var favorites by pref("favorites", emptyList<String>(), { s -> s.split('\n').filter { it.isNotBlank() } }, { it.joinToString("\n") })
    var hidden by pref("hidden", emptySet<String>(), { s -> s.split('\n').filter { it.isNotBlank() }.toSet() }, { it.joinToString("\n") })

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

    fun toggleFavorite(packageName: String) {
        favorites = if (packageName in favorites) favorites - packageName else favorites + packageName
    }

    fun moveFavorite(packageName: String, delta: Int) {
        val list = favorites.toMutableList()
        val from = list.indexOf(packageName)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, list.lastIndex)
        list.removeAt(from)
        list.add(to, packageName)
        favorites = list
    }

    fun toggleHidden(packageName: String) {
        hidden = if (packageName in hidden) hidden - packageName else hidden + packageName
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

    private inline fun <reified E : Enum<E>> enumPref(key: String, default: E) =
        pref(key, default, { name -> enumValues<E>().firstOrNull { it.name == name } }, { it.name })

    companion object {
        private const val KEY_BYPASS_UNTIL = "bypassUntil"

        private fun prefs(context: Context): SharedPreferences =
            context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

        /** Direkter Lesezugriff für den Dienst, ohne Compose-State. */
        fun shouldRedirect(context: Context): Boolean {
            val sp = prefs(context)
            if (sp.getString("redirectHome", "true") == "false") return false
            return System.currentTimeMillis() > sp.getLong(KEY_BYPASS_UNTIL, 0L)
        }
    }
}
