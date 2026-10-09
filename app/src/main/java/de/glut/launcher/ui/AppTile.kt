package de.glut.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.glut.launcher.data.AppArt
import de.glut.launcher.data.AppEntry
import de.glut.launcher.data.AppRepository

/** Entsättigt und wärmt Banner an, damit sie zu Gruvbox passen. */
private val gruvTint: ColorFilter = ColorFilter.colorMatrix(
    ColorMatrix().apply {
        setToSaturation(0.45f)
        timesAssign(
            ColorMatrix(
                floatArrayOf(
                    1.00f, 0f, 0f, 0f, 6f,
                    0f, 0.93f, 0f, 0f, 2f,
                    0f, 0f, 0.78f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    },
)

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
) {
    val art by produceState<AppArt?>(null, app.packageName) { value = repository.loadArt(app) }
    val shape = RoundedCornerShape(if (large) 10.dp else 8.dp)
    val showBanner = style != TileStyle.PLAIN && art?.banner != null

    Column(modifier.size(width = width, height = width * 9 / 16 + if (showBanner) 22.dp else 0.dp)) {
        Focusable(
            onClick = onClick,
            onMenu = onMenu,
            shape = shape,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        ) { focused ->
            val banner = art?.banner
            if (showBanner && banner != null) {
                Image(
                    bitmap = banner,
                    contentDescription = app.label,
                    contentScale = ContentScale.Crop,
                    colorFilter = if (style == TileStyle.TINTED) gruvTint else null,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                PlainTileContent(app, art, style, focused, meta, large)
            }
        }
        if (showBanner) {
            Spacer(Modifier.height(5.dp))
            Txt(
                text = if (meta != null) "${app.label} · $meta" else app.label,
                size = if (large) 9.sp else 8.5.sp,
                color = Gruv.fg4,
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
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = if (large) 14.dp else 10.dp, vertical = if (large) 12.dp else 9.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .size(if (large) 7.dp else 6.dp)
                    .background(Gruv.swatchFor(app.packageName), RoundedCornerShape(2.dp)),
            )
            if (meta != null) Txt(meta, size = 10.sp, color = if (focused) Gruv.fg2 else Gruv.fg3)
        }
        // Ohne Banner (z. B. Handy-Apps) zeigen wir im Banner-Stil das Symbol.
        val icon = art?.icon
        if (style != TileStyle.PLAIN && icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                colorFilter = if (style == TileStyle.TINTED) gruvTint else null,
                modifier = Modifier.weight(1f).fillMaxHeight().align(Alignment.CenterHorizontally).padding(vertical = 2.dp),
            )
        }
        Txt(
            text = app.label,
            size = if (large) 20.sp else 11.5.sp,
            weight = FontWeight.SemiBold,
            color = if (focused) Gruv.fg0 else Gruv.fg1,
        )
    }
}
