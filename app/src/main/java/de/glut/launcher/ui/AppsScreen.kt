package de.glut.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.glut.launcher.data.AppEntry
import de.glut.launcher.data.AppRepository
import de.glut.launcher.data.Prefs

enum class SortMode(val label: String) {
    ALPHA("A–Z"),
    RECENT("Zuletzt genutzt"),
    FREQUENT("Am häufigsten"),
    HIDDEN("Ausgeblendete"),
}

@Composable
fun AppsScreen(
    apps: List<AppEntry>,
    prefs: Prefs,
    repository: AppRepository,
    focusKey: Int,
    onLaunch: (AppEntry) -> Unit,
    onOptions: (AppEntry) -> Unit,
) {
    var sort by rememberSaveable { mutableStateOf(SortMode.ALPHA) }
    val now = rememberNow()
    val hiddenCount = apps.count { it.packageName in prefs.hidden }
    val shown = when (sort) {
        SortMode.ALPHA -> apps.filter { it.packageName !in prefs.hidden }
        SortMode.RECENT -> apps.filter { it.packageName !in prefs.hidden }
            .sortedByDescending { prefs.usage[it.packageName]?.lastUsed ?: 0L }
        SortMode.FREQUENT -> apps.filter { it.packageName !in prefs.hidden }
            .sortedByDescending { prefs.usage[it.packageName]?.count ?: 0 }
        SortMode.HIDDEN -> apps.filter { it.packageName in prefs.hidden }
    }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(focusKey) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt("Alle Apps", size = 28.sp, color = Gruv.fg0, weight = FontWeight.SemiBold)
                Txt(
                    "${apps.size - hiddenCount} installiert · $hiddenCount ausgeblendet",
                    size = 11.sp,
                    color = Gruv.fg4,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SortMode.entries.forEach { mode ->
                    Pill(mode.label, onClick = { sort = mode }, selected = mode == sort)
                }
            }
        }

        if (shown.isEmpty()) {
            Txt(
                if (sort == SortMode.HIDDEN) "Keine ausgeblendeten Apps." else "Keine Apps gefunden.",
                size = 12.sp,
                color = Gruv.fg4,
            )
        }

        AppGrid(
            apps = shown,
            prefs = prefs,
            repository = repository,
            onLaunch = onLaunch,
            onOptions = onOptions,
            firstFocus = firstFocus,
            meta = { app ->
                val usage = prefs.usage[app.packageName]
                when (sort) {
                    SortMode.RECENT -> usage?.let { formatRelative(it.lastUsed, now) }
                    SortMode.FREQUENT -> usage?.let { "${it.count}× geöffnet" }
                    else -> null
                }
            },
            modifier = Modifier.weight(1f),
        )
    }
}

/** Raster mit sechs App-Kacheln pro Zeile, für „Alle Apps“ und die Suche. */
@Composable
fun AppGrid(
    apps: List<AppEntry>,
    prefs: Prefs,
    repository: AppRepository,
    onLaunch: (AppEntry) -> Unit,
    onOptions: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
    firstFocus: FocusRequester? = null,
    meta: (AppEntry) -> String? = { null },
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 40.dp),
        modifier = modifier,
    ) {
        itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
            AppTile(
                app = app,
                repository = repository,
                style = prefs.tileStyle,
                width = 124.dp,
                meta = meta(app),
                onClick = { onLaunch(app) },
                onMenu = { onOptions(app) },
                modifier = if (index == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier,
            )
        }
    }
}
