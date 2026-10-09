package de.glut.launcher.ui

import de.glut.launcher.t
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.glut.launcher.data.AppEntry
import de.glut.launcher.data.AppRepository
import de.glut.launcher.data.Folder
import de.glut.launcher.data.Prefs

enum class SortMode(private val de: String, private val en: String) {
    ALPHA("A–Z", "A–Z"),
    RECENT("Zuletzt genutzt", "Recently used"),
    FREQUENT("Am häufigsten", "Most used"),
    SYSTEM("System", "System"),
    HIDDEN("Ausgeblendete", "Hidden");

    val label: String get() = t(de, en)
}

/** Apps, die in „Alle Apps“ und auf der Startseite erscheinen. */
fun visibleApps(apps: List<AppEntry>, prefs: Prefs): List<AppEntry> =
    apps.filter { it.packageName !in prefs.hidden && !(prefs.hideSystemApps && it.isSystem) }

@Composable
fun AppsScreen(
    apps: List<AppEntry>,
    prefs: Prefs,
    repository: AppRepository,
    focusKey: Int,
    onLaunch: (AppEntry) -> Unit,
    onOptions: (AppEntry) -> Unit,
    onOpenFolder: (Folder) -> Unit,
    onFolderMenu: (Folder) -> Unit,
) {
    var sort by rememberSaveable { mutableStateOf(SortMode.ALPHA) }
    val now = rememberNow()
    val visible = visibleApps(apps, prefs)
    val hiddenCount = apps.count { it.packageName in prefs.hidden }
    val installed = apps.map { it.packageName }.toSet()
    val folders = if (prefs.foldersEnabled && sort == SortMode.ALPHA) {
        prefs.folders.map { f -> f.copy(apps = f.apps.filter { it in installed }) }
    } else {
        emptyList()
    }
    val inFolders = folders.flatMap { it.apps }.toSet()
    val shown = when (sort) {
        SortMode.ALPHA -> visible.filter { it.packageName !in inFolders }
        SortMode.RECENT -> visible.sortedByDescending { prefs.usage[it.packageName]?.lastUsed ?: 0L }
        SortMode.FREQUENT -> visible.sortedByDescending { prefs.usage[it.packageName]?.count ?: 0 }
        SortMode.SYSTEM -> apps.filter { it.isSystem && it.packageName !in prefs.hidden }
        SortMode.HIDDEN -> apps.filter { it.packageName in prefs.hidden }
    }
    val modes = SortMode.entries.filter { it != SortMode.SYSTEM || prefs.hideSystemApps }
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
                Txt(t("Alle Apps", "All apps"), size = 28.sp, color = Theme.fg0, weight = FontWeight.SemiBold)
                Txt(
                    t("${visible.size} Apps · $hiddenCount ausgeblendet", "${visible.size} apps · $hiddenCount hidden") +
                        if (prefs.hideSystemApps) " · ${apps.count { it.isSystem }} System" else "",
                    size = 11.sp,
                    color = Theme.fg4,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                modes.forEach { mode ->
                    Pill(mode.label, onClick = { sort = mode }, selected = mode == sort)
                }
            }
        }

        if (shown.isEmpty() && folders.isEmpty()) {
            Txt(
                when (sort) {
                    SortMode.HIDDEN -> t("Keine ausgeblendeten Apps.", "No hidden apps.")
                    SortMode.SYSTEM -> t("Keine Systemapps gefunden.", "No system apps found.")
                    else -> t("Keine Apps gefunden.", "No apps found.")
                },
                size = 12.sp,
                color = Theme.fg4,
            )
        }

        AppGrid(
            apps = shown,
            prefs = prefs,
            repository = repository,
            onLaunch = onLaunch,
            onOptions = onOptions,
            firstFocus = firstFocus,
            folders = folders,
            onOpenFolder = onOpenFolder,
            onFolderMenu = onFolderMenu,
            meta = { app ->
                val usage = prefs.usage[app.packageName]
                when (sort) {
                    SortMode.RECENT -> usage?.let { formatRelative(it.lastUsed, now) }
                    SortMode.FREQUENT -> usage?.let { t("${it.count}× geöffnet", "opened ${it.count}×") }
                    else -> null
                }
            },
            modifier = Modifier.weight(1f),
        )
    }
}

/** Raster aus Ordnern (zuerst) und App-Kacheln, für „Alle Apps“, Suche und Ordner. */
@Composable
fun AppGrid(
    apps: List<AppEntry>,
    prefs: Prefs,
    repository: AppRepository,
    onLaunch: (AppEntry) -> Unit,
    onOptions: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
    firstFocus: FocusRequester? = null,
    folders: List<Folder> = emptyList(),
    onOpenFolder: (Folder) -> Unit = {},
    onFolderMenu: (Folder) -> Unit = {},
    columns: Int = 6,
    tileWidth: Dp = 124.dp,
    meta: (AppEntry) -> String? = { null },
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 40.dp),
        modifier = modifier,
    ) {
        itemsIndexed(folders, key = { _, f -> "folder:" + f.id }) { index, folder ->
            FolderTile(
                folder = folder,
                style = prefs.tileStyle,
                width = tileWidth,
                onClick = { onOpenFolder(folder) },
                onMenu = { onFolderMenu(folder) },
                modifier = if (index == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier,
            )
        }
        itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
            AppTile(
                app = app,
                repository = repository,
                style = prefs.tileStyle,
                width = tileWidth,
                meta = meta(app),
                onClick = { onLaunch(app) },
                onMenu = { onOptions(app) },
                modifier = if (index == 0 && folders.isEmpty() && firstFocus != null) {
                    Modifier.focusRequester(firstFocus)
                } else {
                    Modifier
                },
            )
        }
    }
}
