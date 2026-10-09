package de.glut.launcher.ui

import androidx.compose.foundation.Canvas
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
fun WallpaperBackground(wallpaper: Wallpaper, ground: Ground, accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(ground.color)
        val w = size.width
        val h = size.height
        val m = size.maxDimension
        when (wallpaper) {
            Wallpaper.GLUT -> {
                glow(Color(0xFFD65D0E), 0.30f, Offset(w * 0.88f, h * 0.08f), m * 0.55f)
                glow(Color(0xFF689D6A), 0.24f, Offset(w * 0.05f, h * 0.95f), m * 0.45f)
                glow(Color(0xFFB16286), 0.14f, Offset(w * 0.60f, h * 0.60f), m * 0.35f)
                drawRect(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.35f),
                    ),
                )
            }
            Wallpaper.CONTOURS -> {
                glow(accent, 0.14f, Offset(w * 0.80f, h * 0.20f), m * 0.50f)
                contours()
            }
            Wallpaper.PLAIN -> Unit
        }
    }
}

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
    drawPath(path, Gruv.fg1.copy(alpha = 0.06f), style = Stroke(width = 1.5f * sx))
}
