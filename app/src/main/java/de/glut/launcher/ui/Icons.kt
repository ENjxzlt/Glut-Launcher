package de.glut.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Einfache Linien-Symbole (24er Raster), Farbe kommt per Tint. */
private fun lineIcon(path: String): ImageVector =
    ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(path),
        stroke = SolidColor(Color.White),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()

object Icons {
    val search = lineIcon("M18 11a7 7 0 1 1-14 0a7 7 0 1 1 14 0z M20 20l-4-4")
    val wifi = lineIcon("M2 9a15 15 0 0 1 20 0 M5 12.5a10 10 0 0 1 14 0 M8.5 16a5 5 0 0 1 7 0 M12 19.5h0.01")
    val profile = lineIcon("M16 8a4 4 0 1 1-8 0a4 4 0 1 1 8 0z M4 21v-1a6 6 0 0 1 6-6h4a6 6 0 0 1 6 6v1")
    val settings = lineIcon(
        "M15 12a3 3 0 1 1-6 0a3 3 0 1 1 6 0z " +
            "M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3a1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1" +
            "a1.7 1.7 0 0 0-1.1-1.5a1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8a1.7 1.7 0 0 0-1.5-1H3" +
            "a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1a1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9" +
            "a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5a1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1" +
            "a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z",
    )
    val sliders = lineIcon("M4 6h9 M17 6h3 M15 4v4 M4 12h3 M11 12h9 M9 10v4 M4 18h11 M19 18h1 M17 16v4")
    val grid = lineIcon("M4 4h6v6h-6z M14 4h6v6h-6z M4 14h6v6h-6z M14 14h6v6h-6z")
    val bluetooth = lineIcon("M7 7l10 10l-5 5V2l5 5L7 17")
    val home = lineIcon("M3 11l9-7l9 7 M5 10v10h14V10")
    val play = lineIcon("M7 5l12 7l-12 7z")
    val pause = lineIcon("M8 5v14 M16 5v14")
    val previous = lineIcon("M18 6l-8 6l8 6z M6 6v12")
    val next = lineIcon("M6 6l8 6l-8 6z M18 6v12")
    val music = lineIcon("M9 18V5l11-2v13 M9 18a3 3 0 1 1-6 0a3 3 0 1 1 6 0z M20 16a3 3 0 1 1-6 0a3 3 0 1 1 6 0z")
    val fan = lineIcon("M12 12m-1.5 0a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0 M12 10.5c0-4 1-7 3.5-7s2 3.5-2 6 M13.5 12c4 0 7 1 7 3.5s-3.5 2-6-2 M12 13.5c0 4-1 7-3.5 7s-2-3.5 2-6 M10.5 12c-4 0-7-1-7-3.5s3.5-2 6 2")
    val power = lineIcon("M12 3v8 M7 6.5a7 7 0 1 0 10 0")
    val check = lineIcon("M5 12l5 5l9-10")
    val folder = lineIcon("M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z")
    val bulb = lineIcon("M9 18h6 M10 21h4 M12 3a6 6 0 0 0-4 10.5c.8.8 1 1.5 1 2.5h6c0-1 .2-1.7 1-2.5A6 6 0 0 0 12 3z")
}

@Composable
fun Icon(vector: ImageVector, size: Dp = 16.dp, tint: Color = Theme.fg1) {
    Image(
        painter = rememberVectorPainter(vector),
        contentDescription = null,
        modifier = Modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

/** Runder Symbol-Knopf für die Kopfzeile. */
@Composable
fun IconButton(vector: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Focusable(
        onClick = onClick,
        shape = CircleShape,
        focusedScale = 1.1f,
        modifier = modifier.size(36.dp).semantics { contentDescription = description },
    ) { focused ->
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            Icon(vector, size = 17.dp, tint = if (focused) Theme.fg0 else Theme.fg1)
        }
    }
}
