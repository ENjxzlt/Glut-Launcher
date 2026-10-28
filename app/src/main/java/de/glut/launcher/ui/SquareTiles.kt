package de.glut.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.glut.launcher.t
import de.glut.launcher.data.AppArt
import de.glut.launcher.data.AppEntry
import de.glut.launcher.data.AppRepository
import de.glut.launcher.data.Folder

/** App als Quadrat (1:1) mit abgerundeten Ecken, wie im Dock von Apple TV. */
@Composable
fun SquareAppTile(
    app: AppEntry,
    repository: AppRepository,
    size: Dp,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    onFocusChange: ((Boolean) -> Unit)? = null,
) {
    val art by produceState<AppArt?>(null, app.packageName, repository.packVersion) { value = repository.loadArt(app) }
    Column(modifier.width(size), horizontalAlignment = Alignment.CenterHorizontally) {
        Focusable(
            onClick = onClick,
            onMenu = onMenu,
            shape = RoundedCornerShape(size * 0.27f),
            focusedScale = 1.14f,
            onFocusChange = onFocusChange,
            modifier = Modifier.size(size),
        ) { _ ->
            val current = art
            val base = current?.iconColor ?: Theme.swatchFor(app.packageName)
            Box(Modifier.fillMaxSize().background(lerp(Theme.bg1, base, 0.55f)), contentAlignment = Alignment.Center) {
                val icon = current?.icon
                val logo = current?.logo
                val banner = current?.banner
                val plate = current?.iconBackground
                when {
                    icon != null && plate != null -> Box(Modifier.fillMaxSize().background(plate), contentAlignment = Alignment.Center) {
                        Image(icon, app.label, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(size * (current?.iconPadding ?: 0.16f)))
                    }
                    icon != null -> Image(icon, app.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    logo != null -> Image(logo, app.label, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(size * 0.14f))
                    banner != null -> Image(banner, app.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else -> Txt(app.label.take(1).uppercase(), size = (size.value * 0.42f).sp, color = Theme.fg0, weight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Ordner als Quadrat: vier kleine Farbfelder für die ersten Apps darin. */
@Composable
fun SquareFolderTile(
    folder: Folder,
    size: Dp,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.width(size), horizontalAlignment = Alignment.CenterHorizontally) {
        Focusable(
            onClick = onClick,
            onMenu = onMenu,
            shape = RoundedCornerShape(size * 0.27f),
            color = Theme.fg0.copy(alpha = 0.14f),
            focusedScale = 1.14f,
            modifier = Modifier.size(size),
        ) { _ ->
            Column(Modifier.fillMaxSize().padding(size * 0.17f), verticalArrangement = Arrangement.spacedBy(size * 0.08f)) {
                folder.apps.take(4).chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(size * 0.08f)) {
                        pair.forEach { pkg ->
                            Box(Modifier.weight(1f).fillMaxSize().background(Theme.swatchFor(pkg), RoundedCornerShape(size * 0.07f)))
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** „+“-Quadrat am Ende des Docks. */
@Composable
fun SquareAddTile(size: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.width(size), horizontalAlignment = Alignment.CenterHorizontally) {
        Focusable(
            onClick = onClick,
            shape = RoundedCornerShape(size * 0.27f),
            color = androidx.compose.ui.graphics.Color.Transparent,
            focusedScale = 1.14f,
            modifier = Modifier
                .size(size)
                .drawBehind {
                    drawRoundRect(
                        color = Theme.fg0.copy(alpha = 0.45f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius((size * 0.27f).toPx()),
                        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 6f))),
                    )
                },
        ) { _ ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.plus, size = size * 0.4f, tint = Theme.fg0) }
        }
    }
}
