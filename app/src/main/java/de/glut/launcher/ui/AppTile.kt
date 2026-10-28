package de.glut.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.glut.launcher.data.AppArt
import de.glut.launcher.t
import de.glut.launcher.data.AppEntry
import de.glut.launcher.data.AppRepository

/**
 * Entsättigt Banner und tönt sie in Richtung der Textfarbe des Farbschemas
 * (bei Gruvbox warm, bei Nord kühl), damit sie zum Schema passen.
 */
private fun themeTint(tone: Color): ColorFilter {
    val max = maxOf(tone.red, tone.green, tone.blue, 0.01f)
    return ColorFilter.colorMatrix(
        ColorMatrix().apply {
            setToSaturation(0.45f)
            timesAssign(
                ColorMatrix(
                    floatArrayOf(
                        tone.red / max, 0f, 0f, 0f, 0f,
                        0f, tone.green / max, 0f, 0f, 0f,
                        0f, 0f, tone.blue / max, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
        },
    )
}

/**
 * Eine App-Kachel im Format 16:9. Bei [TileStyle.PLAIN] stehen Name und
 * Farbquadrat in der Kachel, sonst das Banner der App mit dem Namen darunter.
 * [large] zeigt zusätzlich [meta] (z. B. „vor 2 Std.“) für „Zuletzt genutzt“.
 */
@Composable
fun AppTile(
    app: AppEntry,
    repository: AppRepository,
    style: TileStyle,
    width: Dp,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    meta: String? = null,
    large: Boolean = false,
    onFocusChange: ((Boolean) -> Unit)? = null,
) {
    var tileFocused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val art by produceState<AppArt?>(null, app.packageName) { value = repository.loadArt(app) }
    val shape = RoundedCornerShape(if (large) 16.dp else 14.dp)
    // Banner-Stile: echtes Banner, sonst ein aus Logo oder Symbol erzeugtes.
    val showBanner = style != TileStyle.PLAIN && (art?.banner != null || art?.logo != null || art?.icon != null)
    val tone = Theme.fg1
    val tint = remember(tone) { themeTint(tone) }

    Column(modifier.size(width = width, height = width * 9 / 16 + if (showBanner) 22.dp else 0.dp)) {
        Focusable(
            onClick = onClick,
            onMenu = onMenu,
            shape = shape,
            focusedScale = 1.08f,
            onFocusChange = { tileFocused = it; onFocusChange?.invoke(it) },
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        ) { focused ->
            val currentArt = art
            val banner = currentArt?.banner
            if (showBanner && banner != null) {
                Image(
                    bitmap = banner,
                    contentDescription = app.label,
                    contentScale = ContentScale.Crop,
                    colorFilter = if (style == TileStyle.TINTED) tint else null,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (showBanner && currentArt != null) {
                GeneratedBanner(app, currentArt, style, tint)
            } else {
                PlainTileContent(app, currentArt, style, focused, meta, large, tint)
            }
        }
        if (showBanner) {
            Spacer(Modifier.height(if (tileFocused) 9.dp else 6.dp))
            Txt(
                text = if (meta != null) "${app.label} · $meta" else app.label,
                size = if (large) 9.5.sp else 9.sp,
                color = if (tileFocused) Theme.fg0 else Theme.fg4,
                weight = if (tileFocused) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

/** Eigene Kachel für vorinstallierte Apps ohne Banner (ohne fremde Logos). */
private data class CustomTile(val icon: ImageVector, val title: String, val color: () -> Color)

private fun customTileFor(label: String): CustomTile? {
    // Kurze Titel, damit sie auch in kleine Kacheln passen; der volle Name steht darunter.
    val l = label.lowercase()
    return when {
        "game" in l || "spiele" in l -> CustomTile(Icons.gamepad, t("Spiele", "Games"), { Theme.purple })
        "kids" in l || "kinder" in l -> CustomTile(Icons.kids, "Kids", { Theme.yellow })
        "photo" in l || "foto" in l -> CustomTile(Icons.photo, t("Fotos", "Photos"), { Theme.blue })
        "silk" in l || "browser" in l || "internet" in l -> CustomTile(Icons.globe, "Silk", { Theme.aqua })
        else -> null
    }
}

@Composable
private fun CustomTileContent(tile: CustomTile, style: TileStyle) {
    val color = tile.color()
    val background = lerp(Theme.bg1, color, if (style == TileStyle.TINTED) 0.28f else 0.5f)
    BoxWithConstraints(Modifier.fillMaxSize().background(background), contentAlignment = Alignment.Center) {
        val h = maxHeight
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(h * 0.08f),
            modifier = Modifier.padding(horizontal = h * 0.14f),
        ) {
            Icon(tile.icon, size = h * 0.36f, tint = color)
            Txt(
                tile.title,
                size = (h.value * 0.2f).sp,
                weight = FontWeight.SemiBold,
                color = Theme.fg0,
            )
        }
    }
}

/** Ersatz-Banner für Apps ohne eigenes: eigene Kachel, Logo oder Symbol auf seiner Hauptfarbe. */
@Composable
private fun GeneratedBanner(app: AppEntry, art: AppArt, style: TileStyle, tint: ColorFilter) {
    val custom = customTileFor(app.label)
    if (custom != null) {
        CustomTileContent(custom, style)
        return
    }
    val base = art.iconColor ?: Theme.swatchFor(app.packageName)
    // Eingefärbt: Farbe stark Richtung Schema-Fläche ziehen, Original: kräftiger lassen.
    val background = lerp(Theme.bg1, base, if (style == TileStyle.TINTED) 0.45f else 0.8f)
    val filter = if (style == TileStyle.TINTED) tint else null
    Box(Modifier.fillMaxSize().background(background), contentAlignment = Alignment.Center) {
        val logo = art.logo
        val icon = art.icon
        when {
            logo != null -> Image(
                bitmap = logo,
                contentDescription = app.label,
                contentScale = ContentScale.Fit,
                colorFilter = filter,
                modifier = Modifier.fillMaxWidth(0.78f).fillMaxHeight(0.55f),
            )
            icon != null -> Image(
                bitmap = icon,
                contentDescription = app.label,
                contentScale = ContentScale.Fit,
                colorFilter = filter,
                modifier = Modifier.fillMaxHeight(0.58f).aspectRatio(1f),
            )
        }
    }
}

@Composable
private fun PlainTileContent(
    app: AppEntry,
    art: AppArt?,
    style: TileStyle,
    focused: Boolean,
    meta: String?,
    large: Boolean,
    tint: ColorFilter,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = if (large) 14.dp else 10.dp, vertical = if (large) 12.dp else 9.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .size(if (large) 7.dp else 6.dp)
                    .background(Theme.swatchFor(app.packageName), RoundedCornerShape(2.dp)),
            )
            if (meta != null) Txt(meta, size = 10.sp, color = if (focused) Theme.fg2 else Theme.fg3)
        }
        // Ohne Banner (z. B. Handy-Apps) zeigen wir im Banner-Stil das Symbol.
        val icon = art?.icon
        if (style != TileStyle.PLAIN && icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                colorFilter = if (style == TileStyle.TINTED) tint else null,
                modifier = Modifier.weight(1f).fillMaxHeight().align(Alignment.CenterHorizontally).padding(vertical = 2.dp),
            )
        }
        Txt(
            text = app.label,
            size = if (large) 20.sp else 11.5.sp,
            weight = FontWeight.SemiBold,
            color = if (focused) Theme.fg0 else Theme.fg1,
        )
    }
}
