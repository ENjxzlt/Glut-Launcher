package de.glut.launcher.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import de.glut.launcher.R
import de.glut.launcher.t
import de.glut.launcher.data.Prefs

/**
 * Farbrollen nach dem Vorbild von Gruvbox: bg0h (am dunkelsten) … bg3 für Flächen,
 * fg0 (am hellsten) … fg4 für Text, dazu sieben Akzentfarben.
 */
data class Palette(
    val bg0h: Color, val bg0: Color, val bg0s: Color, val bg1: Color, val bg2: Color, val bg3: Color,
    val fg0: Color, val fg1: Color, val fg2: Color, val fg3: Color, val fg4: Color,
    val red: Color, val green: Color, val yellow: Color, val blue: Color,
    val purple: Color, val aqua: Color, val orange: Color,
    val isLight: Boolean = false,
)

private fun c(hex: Long) = Color(0xFF000000 or hex)

/** Freie Farbschemata (alle unter MIT- oder ähnlicher Lizenz veröffentlicht). */
enum class ColorTheme(private val de: String, val palette: Palette) {
    GRUVBOX_DARK(
        "Gruvbox Dunkel",
        Palette(
            c(0x1D2021), c(0x282828), c(0x32302F), c(0x3C3836), c(0x504945), c(0x665C54),
            c(0xFBF1C7), c(0xEBDBB2), c(0xD5C4A1), c(0xBDAE93), c(0xA89984),
            c(0xFB4934), c(0xB8BB26), c(0xFABD2F), c(0x83A598), c(0xD3869B), c(0x8EC07C), c(0xFE8019),
        ),
    ),
    GRUVBOX_LIGHT(
        "Gruvbox Hell",
        Palette(
            c(0xF9F5D7), c(0xFBF1C7), c(0xF2E5BC), c(0xEBDBB2), c(0xD5C4A1), c(0xBDAE93),
            c(0x282828), c(0x3C3836), c(0x504945), c(0x665C54), c(0x7C6F64),
            c(0x9D0006), c(0x79740E), c(0xB57614), c(0x076678), c(0x8F3F71), c(0x427B58), c(0xAF3A03),
            isLight = true,
        ),
    ),
    NORD(
        "Nord",
        Palette(
            c(0x242933), c(0x2E3440), c(0x333A47), c(0x3B4252), c(0x434C5E), c(0x4C566A),
            c(0xECEFF4), c(0xE5E9F0), c(0xD8DEE9), c(0xC2CAD8), c(0x9CA6B9),
            c(0xBF616A), c(0xA3BE8C), c(0xEBCB8B), c(0x81A1C1), c(0xB48EAD), c(0x88C0D0), c(0xD08770),
        ),
    ),
    CATPPUCCIN(
        "Catppuccin Mocha",
        Palette(
            c(0x11111B), c(0x1E1E2E), c(0x262637), c(0x313244), c(0x45475A), c(0x585B70),
            c(0xE4E9FB), c(0xCDD6F4), c(0xBAC2DE), c(0xA6ADC8), c(0x9399B2),
            c(0xF38BA8), c(0xA6E3A1), c(0xF9E2AF), c(0x89B4FA), c(0xCBA6F7), c(0x94E2D5), c(0xFAB387),
        ),
    ),
    DRACULA(
        "Dracula",
        Palette(
            c(0x21222C), c(0x282A36), c(0x2E3040), c(0x343746), c(0x44475A), c(0x565970),
            c(0xF8F8F2), c(0xEDEDE6), c(0xD6D6D0), c(0xB8BCD0), c(0x9EA4C4),
            c(0xFF5555), c(0x50FA7B), c(0xF1FA8C), c(0xBD93F9), c(0xFF79C6), c(0x8BE9FD), c(0xFFB86C),
        ),
    ),
    TOKYO_NIGHT(
        "Tokyo Night",
        Palette(
            c(0x16161E), c(0x1A1B26), c(0x1F2335), c(0x292E42), c(0x3B4261), c(0x414868),
            c(0xD5DCFA), c(0xC0CAF5), c(0xA9B1D6), c(0x9AA5CE), c(0x8089B3),
            c(0xF7768E), c(0x9ECE6A), c(0xE0AF68), c(0x7AA2F7), c(0xBB9AF7), c(0x73DACA), c(0xFF9E64),
        ),
    ),
    ROSE_PINE(
        "Rosé Pine",
        Palette(
            c(0x12101B), c(0x191724), c(0x1F1D2E), c(0x26233A), c(0x403D52), c(0x524F67),
            c(0xEDEBFA), c(0xE0DEF4), c(0xC9C6E0), c(0xAEAAC6), c(0x9893B2),
            c(0xEB6F92), c(0x9CCFD8), c(0xF6C177), c(0x569FBA), c(0xC4A7E7), c(0x9CCFD8), c(0xEBBCBA),
        ),
    ),
    EVERFOREST(
        "Everforest",
        Palette(
            c(0x232A2E), c(0x2D353B), c(0x343F44), c(0x3D484D), c(0x475258), c(0x4F585E),
            c(0xE4D9BE), c(0xD3C6AA), c(0xC5B99E), c(0xAFB3A4), c(0x9DA9A0),
            c(0xE67E80), c(0xA7C080), c(0xDBBC7F), c(0x7FBBB3), c(0xD699B6), c(0x83C092), c(0xE69875),
        ),
    ),
    SOLARIZED(
        "Solarized Dunkel",
        Palette(
            c(0x00212B), c(0x002B36), c(0x04313D), c(0x073642), c(0x0E4552), c(0x2E5560),
            c(0xEEE8D5), c(0xC9CFC9), c(0xA5B0AE), c(0x93A1A1), c(0x8A9C9F),
            c(0xDC322F), c(0x859900), c(0xB58900), c(0x268BD2), c(0xD33682), c(0x2AA198), c(0xCB4B16),
        ),
    );

    val label: String
        get() = when (this) {
            GRUVBOX_DARK -> t(de, "Gruvbox Dark")
            GRUVBOX_LIGHT -> t(de, "Gruvbox Light")
            SOLARIZED -> t(de, "Solarized Dark")
            else -> de
        }
}

/** Freie Schriften (SIL Open Font License). [clock] ist die Schrift der Uhr. */
enum class FontChoice(val label: String, val family: FontFamily, val clock: FontFamily) {
    GEIST("Geist", Geist, GeistMono),
    INTER("Inter", Inter, Inter),
    PLEX("IBM Plex Sans", Plex, Plex),
    JETBRAINS("JetBrains Mono", JetBrains, JetBrains),
    ATKINSON("Atkinson Hyperlegible", Atkinson, Atkinson),
    LEXEND("Lexend", Lexend, Lexend),
    NUNITO("Nunito", Nunito, Nunito),
}

/**
 * Das aktive Farbschema. Liest aus den Einstellungen (Compose-State), daher
 * zeichnet die Oberfläche bei einem Wechsel automatisch neu.
 */
object Theme {
    var prefs: Prefs? = null

    private val p: Palette get() = (prefs?.colorTheme ?: ColorTheme.GRUVBOX_DARK).palette

    val bg0h get() = p.bg0h
    val bg0 get() = p.bg0
    val bg0s get() = p.bg0s
    val bg1 get() = p.bg1
    val bg2 get() = p.bg2
    val bg3 get() = p.bg3
    val fg0 get() = p.fg0
    val fg1 get() = p.fg1
    val fg2 get() = p.fg2
    val fg3 get() = p.fg3
    val fg4 get() = p.fg4
    val red get() = p.red
    val green get() = p.green
    val yellow get() = p.yellow
    val blue get() = p.blue
    val purple get() = p.purple
    val aqua get() = p.aqua
    val orange get() = p.orange
    val isLight get() = p.isLight

    val font: FontFamily get() = (prefs?.font ?: FontChoice.GEIST).family
    val clockFont: FontFamily get() = (prefs?.font ?: FontChoice.GEIST).clock

    /** Feste Farbe pro App, damit ein Paket immer dieselbe Farbe bekommt. */
    fun swatchFor(packageName: String): Color {
        val swatches = listOf(red, orange, yellow, green, aqua, blue, purple)
        return swatches[Math.floorMod(packageName.hashCode(), swatches.size)]
    }
}

enum class ScreensaverSource(private val de: String, private val en: String) {
    FILES("Eigene Dateien", "Own files"),
    STOCK("Stock-Bilder", "Stock photos");

    val label: String get() = t(de, en)
}

enum class TileStyle(private val de: String, private val en: String) {
    TINTED("Eingefärbt", "Tinted"),
    PLAIN("Schlicht", "Plain"),
    ORIGINAL("Original", "Original");

    val label: String get() = t(de, en)
}

enum class Ground(private val de: String, private val en: String) {
    HARD("Hart", "Hard"),
    MEDIUM("Mittel", "Medium"),
    SOFT("Weich", "Soft");

    val label: String get() = t(de, en)

    val color: Color
        get() = when (this) {
            HARD -> Theme.bg0h
            MEDIUM -> Theme.bg0
            SOFT -> Theme.bg0s
        }
}

enum class Wallpaper(private val de: String, private val en: String) {
    GLUT("Glut", "Glow"),
    TIME("Tageszeit", "Time of day"),
    CONTOURS("Konturen", "Contours"),
    PLAIN("Schlicht", "Plain"),
    CUSTOM("Eigenes Bild/Video", "Custom image/video");

    val label: String get() = t(de, en)
}

enum class Accent(private val de: String, private val en: String) {
    YELLOW("Gelb", "Yellow"),
    ORANGE("Orange", "Orange"),
    AQUA("Aqua", "Aqua"),
    BLUE("Blau", "Blue"),
    PURPLE("Violett", "Purple");

    val label: String get() = t(de, en)

    val color: Color
        get() = when (this) {
            YELLOW -> Theme.yellow
            ORANGE -> Theme.orange
            AQUA -> Theme.aqua
            BLUE -> Theme.blue
            PURPLE -> Theme.purple
        }
}

fun <E : Enum<E>> E.next(values: Array<E>): E = values[(ordinal + 1) % values.size]

val LocalAccent = staticCompositionLocalOf { Color(0xFFFABD2F) }

val Geist = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
)
val GeistMono = FontFamily(Font(R.font.geist_mono_medium, FontWeight.Medium))
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)
val Plex = FontFamily(
    Font(R.font.plex_regular, FontWeight.Normal),
    Font(R.font.plex_medium, FontWeight.Medium),
    Font(R.font.plex_semibold, FontWeight.SemiBold),
)
val JetBrains = FontFamily(
    Font(R.font.jetbrains_regular, FontWeight.Normal),
    Font(R.font.jetbrains_medium, FontWeight.Medium),
    Font(R.font.jetbrains_semibold, FontWeight.SemiBold),
)
val Atkinson = FontFamily(
    Font(R.font.atkinson_regular, FontWeight.Normal),
    Font(R.font.atkinson_bold, FontWeight.Bold),
)
val Lexend = FontFamily(
    Font(R.font.lexend_regular, FontWeight.Normal),
    Font(R.font.lexend_medium, FontWeight.Medium),
    Font(R.font.lexend_semibold, FontWeight.SemiBold),
)
val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_medium, FontWeight.Medium),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
)

@Composable
fun Txt(
    text: String,
    size: TextUnit = 11.sp,
    color: Color = Theme.fg1,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    family: FontFamily = Theme.font,
    maxLines: Int = 1,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(color = color, fontSize = size, fontWeight = weight, fontFamily = family),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
