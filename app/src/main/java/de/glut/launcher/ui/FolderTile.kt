package de.glut.launcher.ui

import de.glut.launcher.t
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.glut.launcher.data.Folder

/**
 * Ordner-Kachel in derselben Größe wie eine App-Kachel. Bei Banner-Stilen
 * steht darunter eine Zeile, damit sie neben App-Kacheln bündig bleibt.
 */
@Composable
fun FolderTile(
    folder: Folder,
    style: TileStyle,
    width: Dp,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val withLabel = style != TileStyle.PLAIN
    Column(modifier.size(width = width, height = width * 9 / 16 + if (withLabel) 22.dp else 0.dp)) {
        Focusable(
            onClick = onClick,
            onMenu = onMenu,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        ) { focused ->
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.folder, size = 13.dp, tint = if (focused) Theme.fg0 else Theme.fg3)
                    // Farbquadrate der ersten Apps als Vorschau
                    folder.apps.take(4).forEach { pkg ->
                        Box(Modifier.size(5.dp).background(Theme.swatchFor(pkg), RoundedCornerShape(1.5.dp)))
                    }
                }
                Txt(
                    folder.name,
                    size = 11.5.sp,
                    weight = FontWeight.SemiBold,
                    color = if (focused) Theme.fg0 else Theme.fg1,
                )
            }
        }
        if (withLabel) {
            Spacer(Modifier.height(5.dp))
            Txt(t("Ordner · ${folder.apps.size} Apps", "Folder · ${folder.apps.size} apps"), size = 8.5.sp, color = Theme.fg4)
        }
    }
}
