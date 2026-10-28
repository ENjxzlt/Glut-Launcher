package de.glut.launcher.ui

import de.glut.launcher.t
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.glut.launcher.data.AppArt
import de.glut.launcher.data.AppEntry
import de.glut.launcher.data.AppRepository
import de.glut.launcher.data.Folder
import de.glut.launcher.data.HaEntity
import de.glut.launcher.data.NowPlaying
import de.glut.launcher.data.Prefs

@Composable
fun HomeScreen(
    apps: List<AppEntry>,
    prefs: Prefs,
    repository: AppRepository,
    focusKey: Int,
    smartHome: List<HaEntity>,
    nowPlaying: NowPlaying?,
    onMediaOpen: () -> Unit,
    onMediaPlayPause: () -> Unit,
    onMediaNext: () -> Unit,
    onMediaPrevious: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onOptions: (AppEntry) -> Unit,
    onOpenFolder: (Folder) -> Unit,
    onFolderMenu: (Folder) -> Unit,
    onSmartHome: (HaEntity) -> Unit,
    onOpenApps: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenWifi: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBluetooth: () -> Unit,
    onOpenAmazonHome: () -> Unit,
) {
    val now = rememberNow()
    val visible = visibleApps(apps, prefs)
    val byPackage = apps.filter { it.packageName !in prefs.hidden }.associateBy { it.packageName }
    val recents = visible
        .filter { (prefs.usage[it.packageName]?.lastUsed ?: 0L) > 0L }
        .sortedByDescending { prefs.usage[it.packageName]?.lastUsed ?: 0L }
        .take(4)
    val folderById = if (prefs.foldersEnabled) prefs.folders.associateBy { it.id } else emptyMap()
    val installed = apps.map { it.packageName }.toSet()
    // Favoriten: Apps oder Ordner, in gespeicherter Reihenfolge
    val favorites: List<Any> = prefs.favorites.mapNotNull { key ->
        if (key.startsWith(Folder.FAVORITE_PREFIX)) {
            folderById[key.removePrefix(Folder.FAVORITE_PREFIX)]?.let { f -> f.copy(apps = f.apps.filter { it in installed }) }
        } else {
            byPackage[key]
        }
    }
    val showSmartHome = prefs.haEnabled && prefs.haTilesEnabled && smartHome.isNotEmpty()
    val firstFocus = remember { FocusRequester() }
    // Die große Fläche oben zeigt die App, die gerade markiert ist (Apple-TV-Art).
    var focusedPackage by remember { mutableStateOf<String?>(null) }
    val heroApp: AppEntry? = focusedPackage?.let { byPackage[it] }
        ?: recents.firstOrNull()
        ?: favorites.filterIsInstance<AppEntry>().firstOrNull()

    LaunchedEffect(apps.isNotEmpty(), focusKey) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Kopfzeile: Uhr, Reiter, Symbole
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(220.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt(formatClock(now), size = 30.sp, color = Theme.fg0, family = Theme.clockFont, weight = FontWeight.Medium)
                Txt(formatDate(now), size = 11.sp, color = Theme.fg4)
            }
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.background(Theme.bg1.copy(alpha = 0.7f), RoundedCornerShape(50)).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Pill(t("Start", "Home"), {}, selected = true)
                Pill(t("Apps", "Apps"), onOpenApps)
                Pill(t("Suche", "Search"), onOpenSearch, icon = Icons.search)
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(Icons.wifi, "WLAN", onOpenWifi)
                IconButton(Icons.profile, t("Profil", "Profile"), onOpenProfile)
                IconButton(Icons.settings, t("Fire-TV-Einstellungen", "Fire TV settings"), onOpenSettings)
                IconButton(Icons.sliders, t("Launcher-Einstellungen", "Launcher settings"), onOpenMenu)
            }
        }

        // Der erste Fokus landet auf der ersten Kachel, die es gibt.
        val focusRecent = recents.isNotEmpty()
        val focusFavorite = !focusRecent && favorites.isNotEmpty()
        val focusAllApps = !focusRecent && !focusFavorite
        val firstFocusModifier = Modifier.focusRequester(firstFocus)

        // Die große Fläche bleibt stehen, nur die Reihen darunter scrollen.
        if (heroApp != null || (nowPlaying != null && prefs.musicWidget)) {
            HeroShelf(
                repository = repository,
                app = heroApp,
                meta = heroApp?.let { app -> prefs.usage[app.packageName]?.lastUsed?.takeIf { it > 0L }?.let { formatRelative(it, now) } },
                nowPlaying = nowPlaying.takeIf { prefs.musicWidget },
                onLaunch = onLaunch,
                onOptions = onOptions,
                onMediaOpen = onMediaOpen,
                onMediaPlayPause = onMediaPlayPause,
                onMediaNext = onMediaNext,
                onMediaPrevious = onMediaPrevious,
            )
        }


        // Der Inhalt scrollt, wenn Smart Home & Co. nicht mehr auf den Bildschirm passen.
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (recents.isNotEmpty()) {
                Section(t("Zuletzt genutzt", "Recently used")) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp + ShadowBleedX, vertical = 6.dp + ShadowBleedY),
                        modifier = Modifier.bleed(ShadowBleedX, ShadowBleedY),
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
                                onFocusChange = { if (it) focusedPackage = app.packageName },
                                modifier = if (index == 0 && focusRecent) firstFocusModifier else Modifier,
                            )
                        }
                    }
                }
            }

            Section(t("Favoriten", "Favourites")) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp + ShadowBleedX, vertical = 6.dp + ShadowBleedY),
                    modifier = Modifier.bleed(ShadowBleedX, ShadowBleedY),
                ) {
                    itemsIndexed(favorites, key = { _, item -> if (item is Folder) item.favoriteKey else (item as AppEntry).packageName }) { index, item ->
                        val focus = if (index == 0 && focusFavorite) firstFocusModifier else Modifier
                        when (item) {
                            is Folder -> FolderTile(
                                folder = item,
                                style = prefs.tileStyle,
                                width = 108.dp,
                                onClick = { onOpenFolder(item) },
                                onMenu = { onFolderMenu(item) },
                                modifier = focus,
                            )
                            is AppEntry -> AppTile(
                                app = item,
                                repository = repository,
                                style = prefs.tileStyle,
                                width = 108.dp,
                                onClick = { onLaunch(item) },
                                onMenu = { onOptions(item) },
                                onFocusChange = { if (it) focusedPackage = item.packageName },
                                modifier = focus,
                            )
                        }
                    }
                    item(key = "add") { AddTile(onClick = onOpenApps) }
                }
            }

            if (showSmartHome) {
                Section("Smart Home") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp + ShadowBleedX, vertical = 4.dp + ShadowBleedY),
                        modifier = Modifier.bleed(ShadowBleedX, ShadowBleedY),
                    ) {
                        itemsIndexed(smartHome, key = { _, e -> e.id }) { _, entity ->
                            SmartHomeTile(entity, onClick = { onSmartHome(entity) })
                        }
                    }
                }
            }

            Section(t("Geräte & System", "Devices & system")) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Pill(t("Alle Apps (${visible.size})", "All apps (${visible.size})"), onOpenApps, if (focusAllApps) firstFocusModifier else Modifier, icon = Icons.grid)
                    Pill("Bluetooth", onOpenBluetooth, icon = Icons.bluetooth)
                    Pill(t("Amazon-Startseite", "Amazon home"), onOpenAmazonHome, icon = Icons.home)
                }
            }
        }

    }
    // Unten blendet der Inhalt weich aus, darüber die Tastenhinweise.
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(96.dp)
            .background(Brush.verticalGradient(0f to Color.Transparent, 0.55f to Theme.bg0h.copy(alpha = 0.92f), 1f to Theme.bg0h.copy(alpha = 0.98f))),
    )
    Row(
        modifier = Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        KeyHint("OK", t("Öffnen", "Open"))
        KeyHint(t("MENÜ", "MENU"), t("Favorit, Ordner, Ausblenden", "Favourite, folder, hide"))
    }
    }
}

/** Große Fläche oben: die markierte App mit Öffnen/Optionen, rechts das Musik-Widget. */
@Composable
private fun HeroShelf(
    repository: AppRepository,
    app: AppEntry?,
    meta: String?,
    nowPlaying: NowPlaying?,
    onLaunch: (AppEntry) -> Unit,
    onOptions: (AppEntry) -> Unit,
    onMediaOpen: () -> Unit,
    onMediaPlayPause: () -> Unit,
    onMediaNext: () -> Unit,
    onMediaPrevious: () -> Unit,
) {
    val tint = if (app != null) Theme.swatchFor(app.packageName) else LocalAccent.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.horizontalGradient(listOf(tint.copy(alpha = 0.5f), Theme.bg1.copy(alpha = 0.55f))))
            .padding(horizontal = 28.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (app != null) {
                Txt(
                    if (meta != null) t("ZULETZT GENUTZT · ", "RECENTLY USED · ") + meta.uppercase() else t("FAVORIT", "FAVOURITE"),
                    size = 9.sp,
                    color = Theme.fg2,
                    weight = FontWeight.SemiBold,
                )
                Txt(app.label, size = 30.sp, color = Theme.fg0, weight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(t("Öffnen", "Open"), { onLaunch(app) }, selected = true)
                    Pill(t("Optionen", "Options"), { onOptions(app) })
                }
            }
        }
        if (app != null) {
            val art by produceState<AppArt?>(null, app.packageName) { value = repository.loadArt(app) }
            val image = art?.banner ?: art?.logo ?: art?.icon
            if (image != null) {
                Box(
                    modifier = Modifier
                        .padding(start = 20.dp)
                        .fillMaxHeight()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Theme.bg0h.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = image,
                        contentDescription = app.label,
                        contentScale = if (art?.banner != null) ContentScale.Crop else ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(if (art?.banner != null) 0.dp else 14.dp),
                    )
                }
            }
        }
        if (nowPlaying != null) {
            Spacer(Modifier.width(20.dp))
            NowPlayingCard(nowPlaying, onMediaOpen, onMediaPlayPause, onMediaNext, onMediaPrevious)
        }
    }
}

/** Musik-Widget: zeigt, was im Hintergrund läuft, mit Tasten für Pause und Titelwechsel. */
@Composable
private fun NowPlayingCard(
    media: NowPlaying,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    Row(
        modifier = Modifier
            .background(Theme.bg0h.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Focusable(
            onClick = onOpen,
            shape = RoundedCornerShape(8.dp),
            color = Theme.bg2,
            focusedScale = 1.05f,
            modifier = Modifier.size(56.dp),
        ) { _ ->
            val art = media.art
            if (art != null) {
                Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.music, size = 22.dp, tint = Theme.fg3) }
            }
        }
        Column(Modifier.width(170.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(if (media.isPlaying) t("Läuft gerade", "Now playing") else t("Pausiert", "Paused"), size = 9.sp, color = LocalAccent.current)
            Txt(media.title, size = 13.sp, color = Theme.fg0, weight = FontWeight.SemiBold)
            if (media.artist.isNotBlank()) Txt(media.artist, size = 10.5.sp, color = Theme.fg3)
        }
        IconButton(Icons.previous, t("Vorheriger Titel", "Previous track"), onPrevious)
        IconButton(if (media.isPlaying) Icons.pause else Icons.play, if (media.isPlaying) "Pause" else t("Abspielen", "Play"), onPlayPause)
        IconButton(Icons.next, t("Nächster Titel", "Next track"), onNext)
    }
}

/** Kachel für Licht, Schalter, Szene oder Skript aus Home Assistant. */
@Composable
private fun SmartHomeTile(entity: HaEntity, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Focusable(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (entity.isOn) Theme.bg2 else Theme.bg1,
        focusedScale = 1.05f,
        modifier = Modifier.size(width = 150.dp, height = 50.dp),
    ) { focused ->
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Icon(
                when (entity.domain) {
                    "scene", "script" -> Icons.sliders
                    "fan" -> Icons.fan
                    "switch", "input_boolean" -> Icons.power
                    else -> Icons.bulb
                },
                size = 17.dp,
                tint = if (entity.isOn) accent else if (focused) Theme.fg1 else Theme.fg4,
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Txt(entity.name, size = 10.5.sp, weight = FontWeight.SemiBold, color = if (focused) Theme.fg0 else Theme.fg1)
                Txt(
                    when {
                        entity.isAction -> t("Ausführen", "Run")
                        entity.isOn -> t("An", "On")
                        entity.state == "off" -> t("Aus", "Off")
                        entity.state == "unavailable" -> t("Nicht erreichbar", "Unavailable")
                        entity.state == "unknown" -> t("Unbekannt", "Unknown")
                        else -> entity.state
                    },
                    size = 9.sp,
                    color = Theme.fg4,
                )
            }
        }
    }
}

@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Txt(title, size = 15.sp, weight = FontWeight.SemiBold, color = Theme.fg0)
        content()
    }
}

@Composable
fun Pill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    icon: ImageVector? = null,
) {
    Focusable(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) Theme.fg1 else Theme.bg1,
        focusedColor = if (selected) Theme.fg0 else Theme.bg2,
        focusedScale = 1.04f,
        modifier = modifier.height(32.dp),
    ) { _ ->
        // Nur so breit wie der Inhalt – nicht fillMaxSize, sonst nimmt die Pille die ganze Zeile.
        Row(
            modifier = Modifier.fillMaxHeight().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            val color = if (selected) Theme.bg0 else Theme.fg1
            if (icon != null) Icon(icon, size = 14.dp, tint = color)
            Txt(text, size = 11.sp, color = color, weight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

/** Sieht aus wie ein Eingabefeld, öffnet aber die Such-Ansicht (dort tippt man). */
@Composable
fun SearchBar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Focusable(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        focusedScale = 1.02f,
        modifier = modifier.height(36.dp),
    ) { focused ->
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.search, size = 15.dp, tint = if (focused) Theme.fg1 else Theme.fg4)
            Txt(t("Apps durchsuchen", "Search apps"), size = 11.5.sp, color = if (focused) Theme.fg2 else Theme.fg4)
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
                    color = Theme.bg3,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))),
                )
            },
    ) { _ ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Txt(t("+ Hinzufügen", "+ Add"), size = 11.sp, color = Theme.fg3)
        }
    }
}
