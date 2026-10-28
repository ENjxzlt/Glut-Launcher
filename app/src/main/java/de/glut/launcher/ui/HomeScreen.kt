package de.glut.launcher.ui

import de.glut.launcher.t
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.glut.launcher.data.AppEntry
import de.glut.launcher.data.AppRepository
import de.glut.launcher.data.Folder
import de.glut.launcher.data.HaEntity
import de.glut.launcher.data.JellyfinFeed
import de.glut.launcher.data.JellyfinImages
import de.glut.launcher.data.JellyfinItem
import de.glut.launcher.data.NowPlaying
import de.glut.launcher.data.Prefs

/** Was die Jellyfin-Leiste auf der Startseite gerade zeigt. */
data class JellyfinState(
    val enabled: Boolean = false,
    val connected: Boolean = false,
    val feeds: List<JellyfinFeed> = emptyList(),
    val feed: JellyfinFeed = JellyfinFeed.CONTINUE,
    val items: List<JellyfinItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

private fun feedLabel(feed: JellyfinFeed) = when (feed) {
    JellyfinFeed.CONTINUE -> t("Weiter schauen", "Continue watching")
    JellyfinFeed.NEW -> t("Neu hinzugefügt", "Recently added")
    JellyfinFeed.RECOMMENDED -> t("Empfohlen", "Suggested")
}

@Composable
fun HomeScreen(
    apps: List<AppEntry>,
    prefs: Prefs,
    repository: AppRepository,
    focusKey: Int,
    smartHome: List<HaEntity>,
    nowPlaying: NowPlaying?,
    jellyfin: JellyfinState,
    showAmazonHome: Boolean,
    onMediaOpen: () -> Unit,
    onMediaPlayPause: () -> Unit,
    onMediaNext: () -> Unit,
    onMediaPrevious: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onOptions: (AppEntry) -> Unit,
    onOpenFolder: (Folder) -> Unit,
    onFolderMenu: (Folder) -> Unit,
    onSmartHome: (HaEntity) -> Unit,
    onJellyfinOpen: (JellyfinItem) -> Unit,
    onJellyfinFeed: (JellyfinFeed) -> Unit,
    onJellyfinSetup: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenWifi: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAmazonHome: () -> Unit,
) {
    val now = rememberNow()
    val visible = visibleApps(apps, prefs)
    val byPackage = apps.filter { it.packageName !in prefs.hidden }.associateBy { it.packageName }
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
    // Ist eine Jellyfin-Karte markiert, ersetzt ihre Beschreibung die große Uhr.
    var focusedItem by remember { mutableStateOf<JellyfinItem?>(null) }

    LaunchedEffect(apps.isNotEmpty(), focusKey, jellyfin.items.isNotEmpty()) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }

    val focusJelly = jellyfin.items.isNotEmpty()
    val focusFavorite = !focusJelly && favorites.isNotEmpty()
    val firstFocusModifier = Modifier.focusRequester(firstFocus)

    Column(
        modifier = Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Kopfzeile: Reiter in der Mitte, Symbole rechts
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (showAmazonHome) Pill(t("Amazon-Startseite", "Amazon home"), onOpenAmazonHome, icon = Icons.home)
            }
            Row(
                modifier = Modifier.glass(RoundedCornerShape(50), strong = true).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Pill(t("Start", "Home"), {}, selected = true)
                Pill(t("Apps", "Apps"), onOpenApps)
                Pill(t("Suche", "Search"), onOpenSearch, icon = Icons.search)
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                IconButton(Icons.wifi, "WLAN", onOpenWifi)
                IconButton(Icons.profile, t("Profil", "Profile"), onOpenProfile)
                IconButton(Icons.settings, t("Fire-TV-Einstellungen", "Fire TV settings"), onOpenSettings)
                IconButton(Icons.sliders, t("Launcher-Einstellungen", "Launcher settings"), onOpenMenu)
            }
        }

        // Große Uhr (oder die markierte Jellyfin-Karte) links, Smart Home und Musik rechts
        Row(Modifier.fillMaxWidth().height(158.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                val item = focusedItem
                if (item == null) BigClock(now) else JellyfinDetails(item, jellyfin.feed)
            }
            if (showSmartHome || (nowPlaying != null && prefs.musicWidget)) {
                Column(Modifier.width(250.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showSmartHome) SmartHomeCard(smartHome, onSmartHome)
                    if (nowPlaying != null && prefs.musicWidget) {
                        NowPlayingCard(nowPlaying, onMediaOpen, onMediaPlayPause, onMediaNext, onMediaPrevious)
                    }
                }
            }
        }

        if (jellyfin.enabled) {
            JellyfinStrip(
                state = jellyfin,
                firstFocus = if (focusJelly) firstFocusModifier else Modifier,
                onFocused = { item, focused ->
                    if (focused) focusedItem = item else if (focusedItem?.id == item.id) focusedItem = null
                },
                onOpen = onJellyfinOpen,
                onFeed = onJellyfinFeed,
                onSetup = onJellyfinSetup,
            )
        }

        Spacer(Modifier.weight(1f))

        // Dock: Favoriten und Ordner in einer Glasleiste
        Row(
            modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp), strong = true),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
            ) {
                itemsIndexed(favorites, key = { _, item -> if (item is Folder) item.favoriteKey else (item as AppEntry).packageName }) { index, item ->
                    val focus = if (index == 0 && focusFavorite) firstFocusModifier else Modifier
                    when (item) {
                        is Folder -> SquareFolderTile(
                            folder = item,
                            size = 54.dp,
                            onClick = { onOpenFolder(item) },
                            onMenu = { onFolderMenu(item) },
                            modifier = focus,
                        )
                        is AppEntry -> SquareAppTile(
                            app = item,
                            repository = repository,
                            size = 54.dp,
                            onClick = { onLaunch(item) },
                            onMenu = { onOptions(item) },
                            modifier = focus,
                        )
                    }
                }
                item(key = "add") { SquareAddTile(size = 54.dp, onClick = onOpenApps) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            KeyHint("OK", t("Öffnen", "Open"))
            KeyHint(t("MENÜ", "MENU"), t("Favorit, Ordner, Ausblenden", "Favourite, folder, hide"))
        }
    }
}

/** Sehr große Uhr mit Datum, in Lexend und mit hellem Verlauf. */
@Composable
private fun BigClock(now: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        BasicText(
            text = formatClock(now),
            style = TextStyle(
                brush = Brush.verticalGradient(listOf(Theme.fg0, Theme.fg0.copy(alpha = 0.6f))),
                fontSize = 100.sp,
                fontFamily = Lexend,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-3).sp,
            ),
            maxLines = 1,
        )
        Txt(formatDate(now), size = 15.sp, color = Theme.fg1, family = Lexend, weight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
    }
}

/** Beschreibung der markierten Jellyfin-Karte, an Stelle der Uhr. */
@Composable
private fun JellyfinDetails(item: JellyfinItem, feed: JellyfinFeed) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(end = 12.dp)) {
        Txt(feedLabel(feed).uppercase(), size = 9.sp, color = Theme.fg2, weight = FontWeight.SemiBold)
        Txt(item.title, size = 30.sp, color = Theme.fg0, weight = FontWeight.Bold, family = Lexend)
        val meta = listOfNotNull(
            item.subtitle,
            item.year?.toString(),
            item.rating?.let { "★ %.1f".format(java.util.Locale.ROOT, it) },
            item.minutesLeft?.takeIf { it > 0 }?.let { t("$it Min. übrig", "$it min left") },
        ).joinToString("  ·  ")
        if (meta.isNotBlank()) Txt(meta, size = 11.sp, color = Theme.fg1)
        item.overview?.let { Txt(it, size = 10.5.sp, color = Theme.fg2, maxLines = 3) }
    }
}

/** „Für dich aus Jellyfin“: Karten zum Weiterschauen, Neues und Empfehlungen. */
@Composable
private fun JellyfinStrip(
    state: JellyfinState,
    firstFocus: Modifier,
    onFocused: (JellyfinItem, Boolean) -> Unit,
    onOpen: (JellyfinItem) -> Unit,
    onFeed: (JellyfinFeed) -> Unit,
    onSetup: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(20.dp).background(Brush.linearGradient(listOf(Color(0xFF8A63F0), Color(0xFF3FA7E0))), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.play, size = 10.dp, tint = Color.White) }
            Txt(t("Für dich aus Jellyfin", "For you from Jellyfin"), size = 13.sp, color = Theme.fg0, weight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            if (state.connected) {
                state.feeds.forEach { feed -> Pill(feedLabel(feed), { onFeed(feed) }, selected = feed == state.feed) }
            }
        }
        when {
            !state.connected -> MessageCard(
                title = t("Jellyfin verbinden", "Connect Jellyfin"),
                text = t("Adresse und Anmeldung in den Einstellungen eingeben", "Enter the address and sign in under Settings"),
                modifier = firstFocus,
                onClick = onSetup,
            )
            state.items.isEmpty() -> MessageCard(
                title = if (state.loading) t("Lade Vorschläge …", "Loading suggestions …") else state.error ?: t("Keine Vorschläge", "No suggestions"),
                text = if (state.loading) "" else t("OK öffnet die Jellyfin-Einstellungen", "OK opens the Jellyfin settings"),
                modifier = firstFocus,
                onClick = onSetup,
            )
            else -> LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(horizontal = 8.dp + ShadowBleedX, vertical = 6.dp + ShadowBleedY),
                modifier = Modifier.bleed(ShadowBleedX, ShadowBleedY),
            ) {
                itemsIndexed(state.items, key = { _, item -> state.feed.name + item.id }) { index, item ->
                    JellyfinCard(item, if (index == 0) firstFocus else Modifier, onFocused = { onFocused(item, it) }, onClick = { onOpen(item) })
                }
            }
        }
    }
}

@Composable
private fun JellyfinCard(item: JellyfinItem, modifier: Modifier, onFocused: (Boolean) -> Unit, onClick: () -> Unit) {
    val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, item.imageUrl) {
        value = item.imageUrl?.let { JellyfinImages.load(it) }
    }
    Focusable(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Theme.bg2,
        focusedScale = 1.1f,
        onFocusChange = onFocused,
        modifier = modifier.size(width = 164.dp, height = 92.dp),
    ) { _ ->
        Box(Modifier.fillMaxSize()) {
            image?.let { Image(it, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(52.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))),
            )
            Txt(
                item.title,
                size = 10.5.sp,
                color = Color.White,
                weight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = if (item.progress != null) 14.dp else 8.dp, end = 10.dp),
            )
            item.progress?.let { percent ->
                Box(
                    Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 10.dp, bottom = 7.dp).fillMaxWidth().height(3.dp)
                        .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp)),
                ) {
                    Box(Modifier.fillMaxWidth(percent.coerceIn(0, 100) / 100f).fillMaxHeight().background(Color.White, RoundedCornerShape(2.dp)))
                }
            }
        }
    }
}

@Composable
private fun MessageCard(title: String, text: String, modifier: Modifier, onClick: () -> Unit) {
    Focusable(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        focusedColor = Theme.fg0.copy(alpha = 0.18f),
        focusedScale = 1.02f,
        modifier = modifier.fillMaxWidth().height(92.dp).glass(RoundedCornerShape(16.dp)),
    ) { _ ->
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.Center) {
            Txt(title, size = 13.sp, color = Theme.fg0, weight = FontWeight.SemiBold)
            if (text.isNotBlank()) Txt(text, size = 10.5.sp, color = Theme.fg2)
        }
    }
}

/** Glaskarte mit bis zu vier Smart-Home-Kacheln. */
@Composable
private fun SmartHomeCard(entities: List<HaEntity>, onClick: (HaEntity) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Txt("SMART HOME", size = 8.5.sp, color = Theme.fg2, weight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
        entities.take(4).chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pair.forEach { entity -> Box(Modifier.weight(1f)) { SmartHomeTile(entity, onClick = { onClick(entity) }) } }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Musik-Widget als schmale Glaskarte. */
@Composable
private fun NowPlayingCard(
    media: NowPlaying,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Focusable(
            onClick = onOpen,
            shape = RoundedCornerShape(8.dp),
            color = Theme.bg2,
            focusedScale = 1.05f,
            modifier = Modifier.size(38.dp),
        ) { _ ->
            val art = media.art
            if (art != null) {
                Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.music, size = 18.dp, tint = Theme.fg3) }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Txt(media.title, size = 10.5.sp, color = Theme.fg0, weight = FontWeight.SemiBold)
            if (media.artist.isNotBlank()) Txt(media.artist, size = 9.sp, color = Theme.fg2)
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
        modifier = Modifier.fillMaxWidth().height(40.dp),
    ) { focused ->
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 9.dp),
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
                Txt(entity.name, size = 9.5.sp, weight = FontWeight.SemiBold, color = if (focused) Theme.fg0 else Theme.fg1)
                Txt(
                    when {
                        entity.isAction -> t("Ausführen", "Run")
                        entity.isOn -> t("An", "On")
                        entity.state == "off" -> t("Aus", "Off")
                        entity.state == "unavailable" -> t("Nicht erreichbar", "Unavailable")
                        entity.state == "unknown" -> t("Unbekannt", "Unknown")
                        else -> entity.state
                    },
                    size = 8.sp,
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
            .size(width = 84.dp, height = 47.dp)
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
