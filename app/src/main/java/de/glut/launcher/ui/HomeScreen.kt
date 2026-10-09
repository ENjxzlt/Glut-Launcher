package de.glut.launcher.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.glut.launcher.data.AppEntry
import de.glut.launcher.data.AppRepository
import de.glut.launcher.data.Prefs

@Composable
fun HomeScreen(
    apps: List<AppEntry>,
    prefs: Prefs,
    repository: AppRepository,
    focusKey: Int,
    onLaunch: (AppEntry) -> Unit,
    onOptions: (AppEntry) -> Unit,
    onOpenApps: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBluetooth: () -> Unit,
    onOpenAmazonHome: () -> Unit,
) {
    val now = rememberNow()
    val visible = apps.filter { it.packageName !in prefs.hidden }
    val byPackage = visible.associateBy { it.packageName }
    val recents = visible
        .filter { (prefs.usage[it.packageName]?.lastUsed ?: 0L) > 0L }
        .sortedByDescending { prefs.usage[it.packageName]?.lastUsed ?: 0L }
        .take(4)
    val favorites = prefs.favorites.mapNotNull { byPackage[it] }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(apps.isNotEmpty(), focusKey) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 28.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Kopfzeile: Uhr links, Menü rechts
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Txt(formatClock(now), size = 36.sp, color = Gruv.fg0, family = GeistMono, weight = FontWeight.Medium)
                Txt(formatDate(now), size = 12.sp, color = Gruv.fg4)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Einstellungen", onOpenSettings)
                Pill("Menü", onOpenMenu)
            }
        }

        // Der erste Fokus landet auf der ersten Kachel, die es gibt.
        val focusRecent = recents.isNotEmpty()
        val focusFavorite = !focusRecent && favorites.isNotEmpty()
        val focusAllApps = !focusRecent && !focusFavorite
        val firstFocusModifier = Modifier.focusRequester(firstFocus)

        if (recents.isNotEmpty()) {
            Section("Zuletzt genutzt") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    itemsIndexed(recents, key = { _, app -> app.packageName }) { index, app ->
                        AppTile(
                            app = app,
                            repository = repository,
                            style = prefs.tileStyle,
                            width = 200.dp,
                            large = true,
                            meta = formatRelative(prefs.usage[app.packageName]?.lastUsed ?: now, now),
                            onClick = { onLaunch(app) },
                            onMenu = { onOptions(app) },
                            modifier = if (index == 0 && focusRecent) firstFocusModifier else Modifier,
                        )
                    }
                }
            }
        }

        Section("Favoriten") {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            ) {
                itemsIndexed(favorites, key = { _, app -> app.packageName }) { index, app ->
                    AppTile(
                        app = app,
                        repository = repository,
                        style = prefs.tileStyle,
                        width = 108.dp,
                        onClick = { onLaunch(app) },
                        onMenu = { onOptions(app) },
                        modifier = if (index == 0 && focusFavorite) firstFocusModifier else Modifier,
                    )
                }
                item(key = "add") { AddTile(onClick = onOpenApps) }
            }
        }

        Section("Geräte & System") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Alle Apps (${visible.size})", onOpenApps, if (focusAllApps) firstFocusModifier else Modifier)
                Pill("Bluetooth", onOpenBluetooth)
                Pill("Amazon-Startseite", onOpenAmazonHome)
            }
        }

        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            KeyHint("OK", "Öffnen")
            KeyHint("MENÜ", "Favorit, Reihenfolge, Ausblenden")
            KeyHint("ZURÜCK", "Startseite")
        }
    }
}

@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Txt(title, size = 13.sp, weight = FontWeight.SemiBold)
        content()
    }
}

@Composable
fun Pill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    Focusable(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) Gruv.fg1 else Gruv.bg1,
        focusedColor = if (selected) Gruv.fg0 else Gruv.bg2,
        focusedScale = 1.04f,
        modifier = modifier.height(32.dp),
    ) { _ ->
        Box(Modifier.fillMaxSize().padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Txt(
                text,
                size = 11.sp,
                color = if (selected) Gruv.bg0 else Gruv.fg1,
                weight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    Focusable(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier
            .size(width = 108.dp, height = 61.dp)
            .drawBehind {
                drawRoundRect(
                    color = Gruv.bg3,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))),
                )
            },
    ) { _ ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Txt("+ Hinzufügen", size = 11.sp, color = Gruv.fg3)
        }
    }
}
