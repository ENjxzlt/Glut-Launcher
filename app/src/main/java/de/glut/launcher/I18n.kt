package de.glut.launcher

import android.content.Context
import de.glut.launcher.ui.Theme
import java.util.Locale

/** Sprache der Oberfläche. SYSTEM folgt der Gerätesprache (Deutsch, sonst Englisch). */
enum class Language(val label: String) {
    SYSTEM("System"),
    GERMAN("Deutsch"),
    ENGLISH("English"),
}

private fun systemIsGerman(): Boolean = Locale.getDefault().language == "de"

/** True, wenn die Oberfläche englisch ist. Liest den Compose-State, Änderungen wirken sofort. */
val isEnglish: Boolean
    get() = when (Theme.prefs?.language ?: Language.SYSTEM) {
        Language.GERMAN -> false
        Language.ENGLISH -> true
        Language.SYSTEM -> !systemIsGerman()
    }

/** Text in der aktiven Sprache. */
fun t(de: String, en: String): String = if (isEnglish) en else de

/** Locale für Datums- und Wochentagsnamen. */
val uiLocale: Locale get() = if (isEnglish) Locale.UK else Locale.GERMANY

/** Für Code ohne Zugriff auf die Einstellungen im Speicher (z. B. Broadcast-Empfänger). */
fun t(context: Context, de: String, en: String): String {
    val stored = context.getSharedPreferences("launcher", Context.MODE_PRIVATE).getString("language", null)
    val english = when (stored) {
        Language.GERMAN.name -> false
        Language.ENGLISH.name -> true
        else -> !systemIsGerman()
    }
    return if (english) en else de
}
