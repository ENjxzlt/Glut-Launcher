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

/** Gruvbox Dark, https://github.com/morhetz/gruvbox */
object Gruv {
    val bg0h = Color(0xFF1D2021)
    val bg0 = Color(0xFF282828)
    val bg0s = Color(0xFF32302F)
    val bg1 = Color(0xFF3C3836)
    val bg2 = Color(0xFF504945)
    val bg3 = Color(0xFF665C54)
    val fg0 = Color(0xFFFBF1C7)
    val fg1 = Color(0xFFEBDBB2)
    val fg2 = Color(0xFFD5C4A1)
    val fg3 = Color(0xFFBDAE93)
    val fg4 = Color(0xFFA89984)
    val red = Color(0xFFFB4934)
    val green = Color(0xFFB8BB26)
    val yellow = Color(0xFFFABD2F)
    val blue = Color(0xFF83A598)
    val purple = Color(0xFFD3869B)
    val aqua = Color(0xFF8EC07C)
    val orange = Color(0xFFFE8019)

    val swatches = listOf(red, orange, yellow, green, aqua, blue, purple)

    /** Feste Farbe pro App, damit ein Paket immer dieselbe Farbe bekommt. */
    fun swatchFor(packageName: String): Color =
        swatches[Math.floorMod(packageName.hashCode(), swatches.size)]
}

enum class TileStyle(val label: String) {
    TINTED("Eingefärbt"),
    PLAIN("Schlicht"),
    ORIGINAL("Original"),
}

enum class Ground(val label: String, val color: Color) {
    HARD("Hart", Gruv.bg0h),
    MEDIUM("Mittel", Gruv.bg0),
    SOFT("Weich", Gruv.bg0s),
}

enum class Wallpaper(val label: String) {
    GLUT("Glut"),
    CONTOURS("Konturen"),
    PLAIN("Schlicht"),
}

enum class Accent(val label: String, val color: Color) {
    YELLOW("Gelb", Gruv.yellow),
    ORANGE("Orange", Gruv.orange),
    AQUA("Aqua", Gruv.aqua),
    BLUE("Blau", Gruv.blue),
}

fun <E : Enum<E>> E.next(values: Array<E>): E = values[(ordinal + 1) % values.size]

val LocalAccent = staticCompositionLocalOf { Gruv.yellow }

val Geist = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
)

val GeistMono = FontFamily(Font(R.font.geist_mono_medium, FontWeight.Medium))

@Composable
fun Txt(
    text: String,
    size: TextUnit = 11.sp,
    color: Color = Gruv.fg1,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    family: FontFamily = Geist,
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
