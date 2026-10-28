package de.glut.launcher.ui

import de.glut.launcher.Language
import de.glut.launcher.t
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.glut.launcher.data.Prefs
import de.glut.launcher.data.Updater

/** Aktionen, die mehr brauchen als einen Schalter (Auswahlliste, Eingabe, Systemaufruf). */
enum class SettingsAction {
    THEME, FONT,
    STARTUP_DAY, STARTUP_EVENING,
    HA_URL, HA_LOGIN, HA_TOKEN, HA_LOGOUT, HA_TILES,
    KINO_APPS, KINO_START, KINO_END,
    UPDATE_CHECK, UPDATE_INSTALL, UNKNOWN_SOURCES,
    FIRE_SETTINGS, AMAZON_HOME, REBOOT, SLEEP, BACKGROUND_FILE,
    OPEN_WIFI, OPEN_BLUETOOTH, OPEN_USERS,
    TOGGLE_ROOT_MODE, TOGGLE_ADB_NET, TOGGLE_ADB_DEBUG, TOGGLE_SOUND_FX, CYCLE_VOLUME,
    PICK_MANAGED_APP, CHECK_ROOT,
    SCREENSAVER_TOGGLE, SCREENSAVER_TIMEOUT, SCREENSAVER_PREVIEW,
    JELLYFIN_URL, JELLYFIN_QUICK, JELLYFIN_LOGIN, JELLYFIN_LOGOUT, ICON_PACK,
}

/** Seiten der Einstellungen. Jede Seite kennt ihre übergeordnete, „Zurück“ führt dorthin. */
enum class SettingsPage(private val de: String, private val en: String, val parent: SettingsPage?) {
    ROOT("Einstellungen", "Settings", null),
    LOOK("Aussehen", "Appearance", ROOT),
    HOME_APPS("Startseite & Apps", "Home & apps", ROOT),
    WAKE("Beim Einschalten", "On wake-up", ROOT),
    SMART_HOME("Smart Home", "Smart home", ROOT),
    JELLYFIN("Jellyfin", "Jellyfin", ROOT),
    HOME_BUTTON("Home-Taste", "Home button", ROOT),
    SCREENSAVER("Bildschirmschoner", "Screensaver", ROOT),
    UPDATES("Updates & Info", "Updates & info", ROOT),
    SYSTEM("System", "System", ROOT),
    NETWORK("Netzwerk", "Network", SYSTEM),
    SOUND("Ton", "Sound", SYSTEM),
    APPS("Apps verwalten", "Manage apps", SYSTEM),
    ENERGY("Energie & Root", "Power & root", SYSTEM),
    INFO("Geräteinfo", "Device info", SYSTEM),
    DEVELOPER("Entwickler", "Developer", SYSTEM);

    val label: String get() = t(de, en)

    /** „Einstellungen › System › Netzwerk“ */
    val breadcrumb: String get() = generateSequence(this) { it.parent }.toList().asReversed().joinToString(" › ") { it.label }
}

/** Geräte- und Systemzustand für die System-Seiten (nur mit Root sinnvoll). */
data class SystemState(
    val rootAvailable: Boolean = false,
    val rootMode: Boolean = false,
    val wifiSummary: String = "–",
    val bluetoothSummary: String = "–",
    val userSummary: String = "–",
    val adbNetwork: Boolean = false,
    val adbDebugging: Boolean = false,
    val volumePercent: Int = 0,
    val soundEffects: Boolean = true,
    val info: List<Pair<String, String>> = emptyList(),
    val status: String? = null,
    val screensaverActive: Boolean = false,
    val screensaverFiles: Int = 0,
    val screensaverFolder: String = "",
    val screenTimeoutMinutes: Int = 5,
)

private fun onOff(value: Boolean) = if (value) t("An", "On") else t("Aus", "Off")

@Composable
fun SettingsScreen(
    page: SettingsPage,
    prefs: Prefs,
    focusKey: Int,
    serviceEnabled: Boolean,
    keyFilterSupported: Boolean,
    onlyLauncher: Boolean,
    mediaAccess: Boolean,
    haStatus: String?,
    updater: Updater,
    system: SystemState,
    appLabel: (String) -> String,
    entityLabel: (String) -> String,
    onNavigate: (SettingsPage) -> Unit,
    onAction: (SettingsAction) -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(focusKey, page) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    val firstRow = Modifier.focusRequester(first)

    // Zweigeteilt wie bei Apple TV: links der Titel der Seite, rechts die Einträge.
    Row(Modifier.fillMaxSize().padding(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Column(
            modifier = Modifier.width(280.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Txt(page.label, size = 30.sp, color = Theme.fg0, weight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Txt(
                if (page == SettingsPage.ROOT) "Glut Launcher ${updater.currentVersion}" else page.breadcrumb,
                size = 11.sp,
                color = Theme.fg4,
                maxLines = 2,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(top = 44.dp, bottom = 60.dp, end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {

            when (page) {
                SettingsPage.ROOT -> {
                    SubMenuRow(SettingsPage.LOOK, "${prefs.colorTheme.label} · ${prefs.font.label}", firstRow, onNavigate)
                    SubMenuRow(SettingsPage.HOME_APPS, t("Ordner, Systemapps, Musik-Widget", "Folders, system apps, now-playing widget"), Modifier, onNavigate)
                    SubMenuRow(SettingsPage.WAKE, t("Einschalt-App: ", "Startup app: ") + onOff(prefs.startupEnabled), Modifier, onNavigate)
                    SubMenuRow(SettingsPage.SMART_HOME, "Home Assistant: " + onOff(prefs.haEnabled), Modifier, onNavigate)
                    SubMenuRow(
                        SettingsPage.JELLYFIN,
                        if (prefs.jellyfinConnected) t("Angemeldet als ${prefs.jellyfinUserName}", "Signed in as ${prefs.jellyfinUserName}") else t("Nicht verbunden", "Not connected"),
                        Modifier,
                        onNavigate,
                    )
                    if (!onlyLauncher) SubMenuRow(SettingsPage.HOME_BUTTON, t("Umleitung: ", "Redirect: ") + onOff(prefs.redirectHome), Modifier, onNavigate)
                    if (onlyLauncher) SubMenuRow(SettingsPage.SCREENSAVER, if (system.screensaverActive) t("Glut-Bildschirmschoner aktiv", "Glut screensaver active") else t("Amazon-Bildschirmschoner", "Amazon screensaver"), Modifier, onNavigate)
                    SubMenuRow(SettingsPage.UPDATES, t("Version ", "Version ") + updater.currentVersion, Modifier, onNavigate)
                    if (onlyLauncher) SubMenuRow(SettingsPage.SYSTEM, t("WLAN, Bluetooth, Ton, Apps, Energie", "Wi-Fi, Bluetooth, sound, apps, power"), Modifier, onNavigate)
                }

                SettingsPage.LOOK -> {
                    MenuRow(
                        "Sprache / Language",
                        { prefs.language = prefs.language.next(Language.entries.toTypedArray()) },
                        firstRow,
                        value = prefs.language.label,
                    )
                    MenuRow(t("Farbschema", "Colour theme"), { onAction(SettingsAction.THEME) }, value = prefs.colorTheme.label)
                    MenuRow(t("Schriftart", "Font"), { onAction(SettingsAction.FONT) }, value = prefs.font.label)
                    MenuRow(t("Akzentfarbe", "Accent colour"), { prefs.accent = prefs.accent.next(Accent.entries.toTypedArray()) }, value = prefs.accent.label)
                    MenuRow(
                        t("Hintergrund", "Background"),
                        { prefs.wallpaper = prefs.wallpaper.next(Wallpaper.entries.toTypedArray()) },
                        value = prefs.wallpaper.label,
                        subtitle = if (prefs.wallpaper == Wallpaper.TIME) t("Morgens kühl, tagsüber hell, abends warm, nachts dunkel", "Cool in the morning, bright by day, warm in the evening, dark at night") else null,
                    )
                    if (prefs.wallpaper == Wallpaper.CUSTOM) {
                        MenuRow(
                            t("Bild oder Video wählen", "Choose image or video"),
                            { onAction(SettingsAction.BACKGROUND_FILE) },
                            value = prefs.customBackground.ifBlank { t("keine Datei", "no file") },
                            subtitle = t("Per ADB oder USB in den Ordner „backgrounds“ legen", "Put files in the “backgrounds” folder via ADB or USB"),
                        )
                        MenuRow(
                            t("Abdunkeln", "Dim"),
                            { prefs.backgroundDim = if (prefs.backgroundDim >= 80) 0 else prefs.backgroundDim + 20 },
                            value = "${prefs.backgroundDim} %",
                        )
                    }
                    MenuRow(t("Kontrast", "Contrast"), { prefs.ground = prefs.ground.next(Ground.entries.toTypedArray()) }, value = prefs.ground.label)
                    MenuRow(
                        t("Icon-Pack", "Icon pack"),
                        { onAction(SettingsAction.ICON_PACK) },
                        value = if (prefs.iconPack.isBlank()) t("Standard", "Default") else appLabel(prefs.iconPack),
                        subtitle = t("Apps sehen aus wie auf dem Handy (Pack aus dem Play Store)", "Apps look like on a phone (pack from the Play Store)"),
                    )
                    MenuRow(t("Kachelstil", "Tile style"), { prefs.tileStyle = prefs.tileStyle.next(TileStyle.entries.toTypedArray()) }, value = prefs.tileStyle.label)
                }

                SettingsPage.HOME_APPS -> {
                    MenuRow(
                        t("Ordner", "Folders"),
                        { prefs.foldersEnabled = !prefs.foldersEnabled },
                        firstRow,
                        value = onOff(prefs.foldersEnabled),
                        subtitle = t("Menü-Taste auf einer App → „In Ordner verschieben“", "Menu button on an app → “Move to folder”"),
                    )
                    MenuRow(
                        t("Systemapps in „Alle Apps“ ausblenden", "Hide system apps in “All apps”"),
                        { prefs.hideSystemApps = !prefs.hideSystemApps },
                        value = onOff(prefs.hideSystemApps),
                        subtitle = t("Bleiben über die Suche und den Filter „System“ erreichbar", "Still available via search and the “System” filter"),
                    )
                    MenuRow(
                        t("Musik-Widget", "Now-playing widget"),
                        { prefs.musicWidget = !prefs.musicWidget },
                        value = onOff(prefs.musicWidget),
                        subtitle = if (mediaAccess) t("Zeigt, was im Hintergrund läuft", "Shows what is playing in the background") else t("Braucht Zugriff auf Benachrichtigungen (ADB, siehe Anleitung)", "Needs notification access (ADB, see README)"),
                    )
                }

                SettingsPage.WAKE -> {
                    MenuRow(
                        t("Einschalt-App", "Startup app"),
                        { prefs.startupEnabled = !prefs.startupEnabled },
                        firstRow,
                        value = onOff(prefs.startupEnabled),
                        subtitle = if (serviceEnabled) t("Öffnet nach dem Aufwachen direkt eine App", "Opens an app right after the TV wakes up") else t("Braucht die Home-Umleitung (ADB, siehe Anleitung)", "Needs the Home redirect (ADB, see README)"),
                    )
                    if (prefs.startupEnabled) {
                        MenuRow(t("Tagsüber (6–18 Uhr)", "Daytime (6 am – 6 pm)"), { onAction(SettingsAction.STARTUP_DAY) }, value = appLabel(prefs.startupDay))
                        MenuRow(t("Abends (18–6 Uhr)", "Evening (6 pm – 6 am)"), { onAction(SettingsAction.STARTUP_EVENING) }, value = appLabel(prefs.startupEvening))
                    }
                }

                SettingsPage.SMART_HOME -> {
                    MenuRow("Home Assistant", { prefs.haEnabled = !prefs.haEnabled }, firstRow, value = onOff(prefs.haEnabled))
                    if (prefs.haEnabled) {
                        MenuRow(t("Server-Adresse", "Server address"), { onAction(SettingsAction.HA_URL) }, value = prefs.haUrl.ifBlank { t("nicht gesetzt", "not set") })
                        if (prefs.haConnected) {
                            MenuRow(t("Verbunden", "Connected"), { onAction(SettingsAction.HA_LOGOUT) }, subtitle = haStatus ?: t("OK auswählen zum Abmelden", "Press OK to sign out"))
                        } else {
                            MenuRow(t("Anmelden mit Benutzername & Passwort", "Sign in with username & password"), { onAction(SettingsAction.HA_LOGIN) }, subtitle = haStatus)
                            MenuRow(t("Langlebiges Token eingeben", "Enter long-lived token"), { onAction(SettingsAction.HA_TOKEN) }, subtitle = t("HA → Profil → Sicherheit → Token erstellen", "HA → Profile → Security → Create token"))
                        }
                        if (prefs.haConnected) {
                            MenuRow(t("Smart-Home-Leiste auf der Startseite", "Smart home row on the home screen"), { prefs.haTilesEnabled = !prefs.haTilesEnabled }, value = onOff(prefs.haTilesEnabled))
                            MenuRow(t("Geräte und Szenen wählen", "Choose devices and scenes"), { onAction(SettingsAction.HA_TILES) }, value = t("${prefs.haTiles.size} gewählt", "${prefs.haTiles.size} selected"))
                            MenuRow(
                                t("Kino-Modus", "Cinema mode"),
                                { prefs.kinoEnabled = !prefs.kinoEnabled },
                                value = onOff(prefs.kinoEnabled),
                                subtitle = t("Startet eine Szene, wenn du eine Video-App öffnest", "Runs a scene when you open a video app"),
                            )
                            if (prefs.kinoEnabled) {
                                MenuRow(t("Apps für den Kino-Modus", "Apps for cinema mode"), { onAction(SettingsAction.KINO_APPS) }, value = t("${prefs.kinoApps.size} gewählt", "${prefs.kinoApps.size} selected"))
                                MenuRow(t("Beim Start der App", "When the app starts"), { onAction(SettingsAction.KINO_START) }, value = entityLabel(prefs.kinoStart))
                                MenuRow(t("Zurück auf der Startseite", "Back on the home screen"), { onAction(SettingsAction.KINO_END) }, value = entityLabel(prefs.kinoEnd))
                            }
                        }
                    }
                }

                SettingsPage.JELLYFIN -> {
                    MenuRow(
                        t("Vorschläge auf der Startseite", "Suggestions on the home screen"),
                        { prefs.jellyfinEnabled = !prefs.jellyfinEnabled },
                        firstRow,
                        value = onOff(prefs.jellyfinEnabled),
                        subtitle = t("Die Leiste unter der Uhr", "The strip below the clock"),
                    )
                    MenuRow(t("Server", "Server"), { onAction(SettingsAction.JELLYFIN_URL) }, value = prefs.jellyfinUrl.ifBlank { t("nicht gesetzt", "not set") })
                    if (prefs.jellyfinConnected) {
                        MenuRow(
                            t("Konto", "Account"),
                            { onAction(SettingsAction.JELLYFIN_LOGOUT) },
                            value = prefs.jellyfinUserName,
                            subtitle = t("OK zum Abmelden", "Press OK to sign out"),
                        )
                    } else {
                        MenuRow(
                            t("Mit Quick Connect anmelden", "Sign in with Quick Connect"),
                            { onAction(SettingsAction.JELLYFIN_QUICK) },
                            subtitle = t("Code in einer angemeldeten Jellyfin-App bestätigen, kein Passwort nötig", "Confirm a code in a signed-in Jellyfin app, no password needed"),
                        )
                        MenuRow(t("Mit Benutzername & Passwort anmelden", "Sign in with username & password"), { onAction(SettingsAction.JELLYFIN_LOGIN) })
                    }
                    MenuRow(t("Weiter schauen", "Continue watching"), { prefs.jellyfinContinue = !prefs.jellyfinContinue }, value = onOff(prefs.jellyfinContinue))
                    MenuRow(t("Neu hinzugefügt", "Recently added"), { prefs.jellyfinNew = !prefs.jellyfinNew }, value = onOff(prefs.jellyfinNew))
                    MenuRow(t("Empfohlen", "Suggested"), { prefs.jellyfinRecommended = !prefs.jellyfinRecommended }, value = onOff(prefs.jellyfinRecommended))
                    MenuRow(
                        t("Anzahl", "Count"),
                        { prefs.jellyfinCount = when { prefs.jellyfinCount < 8 -> 8; prefs.jellyfinCount < 12 -> 12; prefs.jellyfinCount < 16 -> 16; prefs.jellyfinCount < 20 -> 20; else -> 8 } },
                        value = "${prefs.jellyfinCount}",
                    )
                }

                SettingsPage.HOME_BUTTON -> {
                    MenuRow(
                        t("Home-Taste umleiten", "Redirect Home button"),
                        { prefs.redirectHome = !prefs.redirectHome },
                        firstRow,
                        value = onOff(prefs.redirectHome),
                        subtitle = if (serviceEnabled) t("Dienst aktiv", "Service active") else t("Dienst nicht aktiviert – siehe Anleitung (ADB)", "Service not enabled – see README (ADB)"),
                    )
                    MenuRow(
                        t("Sofortbild beim Home-Druck", "Instant snapshot on Home"),
                        { prefs.instantCover = !prefs.instantCover },
                        value = onOff(prefs.instantCover),
                        subtitle = t("Android sperrt nach der Home-Taste App-Starts für einige Sekunden. Bis dahin zeigt Glut ein Bild der Startseite", "Android blocks app launches for a few seconds after Home. Until then Glut shows a snapshot of its home screen"),
                    )
                    if (keyFilterSupported) {
                        MenuRow(
                            t("Schnelle Home-Taste", "Fast Home button"),
                            { prefs.fastHome = !prefs.fastHome },
                            value = onOff(prefs.fastHome),
                            subtitle = t("Öffnet Glut sofort, ohne Amazon-Startseite. Lang drücken: Glut-Schnellmenü statt Amazons Menü", "Opens Glut immediately, skipping Amazon home. Long press: Glut quick menu instead of Amazon's"),
                        )
                    } else if (serviceEnabled) {
                        MenuRow(
                            t("Schnelle Home-Taste", "Fast Home button"),
                            {},
                            value = t("Nicht verfügbar", "Not available"),
                            subtitle = t("Fire OS gibt die Home-Taste auf diesem Gerät nicht an Apps weiter", "Fire OS does not pass the Home button to apps on this device"),
                        )
                    }
                    MenuRow(
                        t("Amazon-Startseite öffnen", "Open Amazon home"),
                        { onAction(SettingsAction.AMAZON_HOME) },
                        subtitle = t("10 Minuten ohne Umleitung, z. B. für Updates", "10 minutes without redirect, e.g. for updates"),
                    )
                    MenuRow(t("Fire-TV-Einstellungen", "Fire TV settings"), { onAction(SettingsAction.FIRE_SETTINGS) })
                    MenuRow(
                        t("Neu starten", "Restart"),
                        { onAction(SettingsAction.REBOOT) },
                        subtitle = t("Öffnet das Einschaltmenü oder „Mein Fire TV“", "Opens the power menu or “My Fire TV”"),
                    )
                }

                SettingsPage.SCREENSAVER -> {
                    MenuRow(
                        t("Glut-Bildschirmschoner", "Glut screensaver"),
                        { onAction(SettingsAction.SCREENSAVER_TOGGLE) },
                        firstRow,
                        value = onOff(system.screensaverActive),
                        subtitle = if (system.rootAvailable) t("Ersetzt den Bildschirmschoner von Amazon", "Replaces Amazon's screensaver") else t("Braucht Root", "Needs root"),
                    )
                    MenuRow(
                        t("Quelle", "Source"),
                        { prefs.screensaverSource = prefs.screensaverSource.next(ScreensaverSource.entries.toTypedArray()) },
                        value = prefs.screensaverSource.label,
                        subtitle = if (prefs.screensaverSource == ScreensaverSource.STOCK) {
                            t("Naturfotos von Wikimedia Commons (ausgewählte Bilder, mit Quellenangabe), braucht Internet", "Nature photos from Wikimedia Commons (featured pictures, with credit), needs internet")
                        } else {
                            t("${system.screensaverFiles} Dateien in ${system.screensaverFolder}", "${system.screensaverFiles} files in ${system.screensaverFolder}")
                        },
                    )
                    MenuRow(
                        t("Wechselzeit", "Change every"),
                        { prefs.screensaverInterval = when { prefs.screensaverInterval < 10 -> 10; prefs.screensaverInterval < 15 -> 15; prefs.screensaverInterval < 30 -> 30; prefs.screensaverInterval < 60 -> 60; else -> 10 } },
                        value = "${prefs.screensaverInterval} s",
                        subtitle = t("Videos laufen bis zum Ende", "Videos play to the end"),
                    )
                    MenuRow(t("Uhr anzeigen", "Show clock"), { prefs.screensaverClock = !prefs.screensaverClock }, value = onOff(prefs.screensaverClock))
                    MenuRow(
                        t("Startet nach", "Starts after"),
                        { onAction(SettingsAction.SCREENSAVER_TIMEOUT) },
                        value = t("${system.screenTimeoutMinutes} Min.", "${system.screenTimeoutMinutes} min"),
                        subtitle = if (system.rootAvailable) null else t("Braucht Root", "Needs root"),
                    )
                    MenuRow(t("Vorschau", "Preview"), { onAction(SettingsAction.SCREENSAVER_PREVIEW) }, subtitle = t("Startet den Bildschirmschoner jetzt", "Starts the screensaver now"))
                }

                SettingsPage.UPDATES -> {
                    MenuRow(
                        t("Nach Updates suchen", "Check for updates"),
                        { onAction(SettingsAction.UPDATE_CHECK) },
                        firstRow,
                        subtitle = updater.status ?: t("Installiert: ${updater.currentVersion}", "Installed: ${updater.currentVersion}"),
                    )
                    updater.available?.let { info ->
                        MenuRow(
                            t("Version ${info.versionName} installieren", "Install version ${info.versionName}"),
                            { onAction(SettingsAction.UPDATE_INSTALL) },
                            titleColor = LocalAccent.current,
                            subtitle = if (updater.canInstall()) null else t("Erst „Apps unbekannter Herkunft“ für Glut Launcher erlauben", "First allow “Apps from unknown sources” for Glut Launcher"),
                        )
                        if (!updater.canInstall()) MenuRow(t("Installation erlauben …", "Allow installation …"), { onAction(SettingsAction.UNKNOWN_SOURCES) })
                    }
                    MenuRow(t("Automatisch nach Updates suchen", "Check for updates automatically"), { prefs.autoUpdateCheck = !prefs.autoUpdateCheck }, value = onOff(prefs.autoUpdateCheck))
                    MenuRow(
                        t("Beta-Versionen", "Beta versions"),
                        { prefs.betaUpdates = !prefs.betaUpdates; updater.clearStatus() },
                        value = onOff(prefs.betaUpdates),
                        subtitle = t(
                            "Jede Änderung sofort, ungetestet. Aus: nur stabile Versionen",
                            "Every change right away, untested. Off: stable releases only",
                        ),
                    )
                    if (onlyLauncher) MenuRow(
                        t("Einziger Launcher", "Only launcher"),
                        {},
                        value = t("Aktiv", "Active"),
                        subtitle = t("Glut ist der Standard-Launcher. Menü-Taste (☰) auf der Startseite öffnet das Schnellmenü", "Glut is the default launcher. The Menu button (☰) on the home screen opens the quick menu"),
                    )
                }

                SettingsPage.SYSTEM -> {
                    SubMenuRow(SettingsPage.NETWORK, system.wifiSummary, firstRow, onNavigate)
                    MenuRow(t("Benutzer", "Users"), { onAction(SettingsAction.OPEN_USERS) }, value = system.userSummary, subtitle = t("Konten wechseln, Root-Modus", "Switch accounts, root mode"))
                    SubMenuRow(SettingsPage.SOUND, "${system.volumePercent} %", Modifier, onNavigate)
                    SubMenuRow(SettingsPage.APPS, t("Beenden, Daten löschen, deinstallieren", "Stop, clear data, uninstall"), Modifier, onNavigate)
                    SubMenuRow(SettingsPage.ENERGY, t("Schlafen, Neustart", "Sleep, restart"), Modifier, onNavigate)
                    SubMenuRow(SettingsPage.INFO, t("Modell, Speicher, Version", "Model, storage, version"), Modifier, onNavigate)
                    SubMenuRow(SettingsPage.DEVELOPER, "ADB: " + onOff(system.adbDebugging), Modifier, onNavigate)
                }

                SettingsPage.NETWORK -> {
                    MenuRow("WLAN", { onAction(SettingsAction.OPEN_WIFI) }, firstRow, value = system.wifiSummary)
                    MenuRow("Bluetooth", { onAction(SettingsAction.OPEN_BLUETOOTH) }, value = system.bluetoothSummary, subtitle = t("Fernbedienung, Controller, Kopfhörer", "Remote, controllers, headphones"))
                    MenuRow(
                        t("ADB über Netzwerk", "ADB over network"),
                        { onAction(SettingsAction.TOGGLE_ADB_NET) },
                        value = onOff(system.adbNetwork),
                        subtitle = if (system.rootAvailable) t("Port 5555, nur im lokalen Netz", "Port 5555, local network only") else t("Braucht Root", "Needs root"),
                    )
                }

                SettingsPage.SOUND -> {
                    MenuRow(
                        t("Lautstärke", "Volume"),
                        { onAction(SettingsAction.CYCLE_VOLUME) },
                        firstRow,
                        value = "${system.volumePercent} %",
                        subtitle = t("OK erhöht in Schritten, am Ende wieder leise", "OK raises it in steps, wraps around at the top"),
                    )
                    MenuRow(
                        t("Systemtöne", "System sounds"),
                        { onAction(SettingsAction.TOGGLE_SOUND_FX) },
                        value = onOff(system.soundEffects),
                        subtitle = if (system.rootAvailable) null else t("Braucht Root", "Needs root"),
                    )
                }

                SettingsPage.APPS -> {
                    MenuRow(
                        t("App wählen …", "Choose an app …"),
                        { onAction(SettingsAction.PICK_MANAGED_APP) },
                        firstRow,
                        subtitle = t("Beenden, Cache und Daten löschen, deinstallieren", "Stop, clear cache and data, uninstall"),
                    )
                    MenuRow(
                        t("Installation von Apps erlauben", "Allow installing apps"),
                        { onAction(SettingsAction.UNKNOWN_SOURCES) },
                        subtitle = t("Für Updates von Glut und eigene APKs", "For Glut updates and your own APKs"),
                    )
                }

                SettingsPage.ENERGY -> {
                    MenuRow(t("Schlafen", "Sleep"), { onAction(SettingsAction.SLEEP) }, firstRow)
                    MenuRow(
                        t("Neu starten", "Restart"),
                        { onAction(SettingsAction.REBOOT) },
                        subtitle = if (system.rootMode) t("Sofort, ohne Rückfrage", "Immediately, no confirmation") else t("Öffnet das Einschaltmenü", "Opens the power menu"),
                    )
                    MenuRow(
                        t("Root-Modus", "Root mode"),
                        { onAction(SettingsAction.TOGGLE_ROOT_MODE) },
                        value = onOff(system.rootMode),
                        subtitle = t("Schlafen und Neustart laufen direkt, ohne Menüs", "Sleep and restart run directly, without menus"),
                    )
                }

                SettingsPage.INFO -> {
                    system.info.forEachIndexed { index, (label, value) ->
                        MenuRow(label, {}, if (index == 0) firstRow else Modifier, value = value)
                    }
                }

                SettingsPage.DEVELOPER -> {
                    MenuRow(
                        t("Root-Zugriff prüfen", "Check root access"),
                        { onAction(SettingsAction.CHECK_ROOT) },
                        firstRow,
                        value = if (system.rootAvailable) t("Root aktiv", "Root active") else t("Kein Root", "No root"),
                    )
                    MenuRow(
                        t("ADB-Debugging", "ADB debugging"),
                        { onAction(SettingsAction.TOGGLE_ADB_DEBUG) },
                        value = onOff(system.adbDebugging),
                        subtitle = if (system.rootAvailable) null else t("Braucht Root", "Needs root"),
                    )
                }
            }

            if (system.status != null && page != SettingsPage.ROOT) {
                Spacer(Modifier.height(8.dp))
                Txt(system.status, size = 10.sp, color = Theme.fg3, maxLines = 3, modifier = Modifier.padding(start = 12.dp))
            }
            Spacer(Modifier.height(16.dp))
            KeyHint(t("ZURÜCK", "BACK"), page.parent?.label ?: t("Startseite", "Home"))
        }
    }
}

@Composable
private fun SubMenuRow(page: SettingsPage, summary: String, modifier: Modifier, onNavigate: (SettingsPage) -> Unit) {
    MenuRow(page.label, { onNavigate(page) }, modifier, subtitle = summary, value = "›")
}
