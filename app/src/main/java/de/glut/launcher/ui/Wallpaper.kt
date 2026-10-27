package de.glut.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import java.io.File
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** Statischer Hintergrund. Bewusst ohne Animation, damit das Scrollen flüssig bleibt. */
@Composable
fun WallpaperBackground(
    wallpaper: Wallpaper,
    ground: Ground,
    accent: Color,
    hour: Int,
    modifier: Modifier = Modifier,
    custom: File? = null,
    dim: Int = 40,
) {
    Box(modifier) {
        if (wallpaper == Wallpaper.CUSTOM && custom != null) CustomBackgroundLayer(custom, dim, Modifier.fillMaxSize())
        else GeneratedBackground(wallpaper, ground, accent, hour, Modifier.fillMaxSize())
    }
}

@Composable
private fun GeneratedBackground(
    wallpaper: Wallpaper,
    ground: Ground,
    accent: Color,
    hour: Int,
    modifier: Modifier,
) {
    Canvas(modifier) {
        drawRect(ground.color)
        val w = size.width
        val h = size.height
        val m = size.maxDimension
        when (wallpaper) {
            Wallpaper.GLUT, Wallpaper.CUSTOM -> evening()
            Wallpaper.TIME -> when (hour) {
                in 6..10 -> morning()
                in 11..16 -> day()
                in 17..21 -> evening()
                else -> night()
            }
            Wallpaper.CONTOURS -> {
                glow(accent, 0.14f, Offset(w * 0.80f, h * 0.20f), m * 0.50f)
                contours()
            }
            Wallpaper.PLAIN -> Unit
        }
    }
}


/** Morgens kühl: Aqua und Blau. */
private fun DrawScope.morning() {
    glow(Theme.aqua, 0.22f, Offset(size.width * 0.88f, size.height * 0.08f), size.maxDimension * 0.55f)
    glow(Theme.blue, 0.20f, Offset(size.width * 0.05f, size.height * 0.95f), size.maxDimension * 0.45f)
    glow(Theme.yellow, 0.07f, Offset(size.width * 0.60f, size.height * 0.55f), size.maxDimension * 0.35f)
    drawRect(shade())
}

/** Tagsüber hell und ruhig: Gelb und Aqua. */
private fun DrawScope.day() {
    glow(Theme.yellow, 0.16f, Offset(size.width * 0.88f, size.height * 0.08f), size.maxDimension * 0.55f)
    glow(Theme.aqua, 0.15f, Offset(size.width * 0.05f, size.height * 0.95f), size.maxDimension * 0.45f)
    drawRect(shade())
}

/** Abends warm: die „Glut“. */
private fun DrawScope.evening() {
    glow(Theme.orange, 0.24f, Offset(size.width * 0.88f, size.height * 0.08f), size.maxDimension * 0.55f)
    glow(Theme.aqua, 0.20f, Offset(size.width * 0.05f, size.height * 0.95f), size.maxDimension * 0.45f)
    glow(Theme.purple, 0.12f, Offset(size.width * 0.60f, size.height * 0.60f), size.maxDimension * 0.35f)
    drawRect(shade())
}

/** Nachts fast dunkel, damit der Fernseher nicht blendet. */
private fun DrawScope.night() {
    glow(Theme.purple, 0.08f, Offset(size.width * 0.88f, size.height * 0.08f), size.maxDimension * 0.50f)
    drawRect(Theme.bg0h.copy(alpha = 0.45f))
}

/** Unten etwas dunkler (bzw. bei hellen Schemata heller), damit Text gut lesbar bleibt. */
private fun shade() = Brush.verticalGradient(0.55f to Color.Transparent, 1f to Theme.bg0h.copy(alpha = 0.6f))

private fun DrawScope.glow(color: Color, alpha: Float, center: Offset, radius: Float) {
    drawRect(Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center, radius))
}

/** Geschwungene Linien, entworfen auf 1920×1080 und auf die echte Größe skaliert. */
private fun DrawScope.contours() {
    val sx = size.width / 1920f
    val sy = size.height / 1080f
    val path = Path()
    for (i in 0..10) {
        val y = 100f + i * 90f
        path.moveTo(-40f * sx, y * sy)
        path.cubicTo(380f * sx, (y - 110f) * sy, 760f * sx, (y + 130f) * sy, 1160f * sx, y * sy)
        path.cubicTo(1560f * sx, (y - 130f) * sy, 1680f * sx, (y - 90f) * sy, 1960f * sx, (y + 30f) * sy)
    }
    drawPath(path, Theme.fg1.copy(alpha = 0.06f), style = Stroke(width = 1.5f * sx))
}
