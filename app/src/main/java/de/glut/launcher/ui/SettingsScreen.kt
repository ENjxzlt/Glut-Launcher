package de.glut.launcher.ui

import de.glut.launcher.Language
import de.glut.launcher.t
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
    FIRE_SETTINGS, AMAZON_HOME, REBOOT,
}

private fun onOff(value: Boolean) = if (value) t("An", "On") else t("Aus", "Off")

@Composable
fun SettingsScreen(
    prefs: Prefs,
    focusKey: Int,
    serviceEnabled: Boolean,
    keyFilterSupported: Boolean,
    mediaAccess: Boolean,
    haStatus: String?,
    updater: Updater,
    appLabel: (String) -> String,
    entityLabel: (String) -> String,
    onAction: (SettingsAction) -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(focusKey) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .width(640.dp)
                .verticalScroll(rememberScrollState())
                .padding(top = 28.dp, bottom = 60.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Txt(t("Einstellungen", "Settings"), size = 28.sp, color = Theme.fg0, weight = FontWeight.SemiBold)
            Txt("Glut Launcher ${updater.currentVersion}", size = 11.sp, color = Theme.fg4)

            Header(t("Aussehen", "Appearance"))
            MenuRow(
                "Sprache / Language",
                { prefs.language = prefs.language.next(Language.entries.toTypedArray()) },
                Modifier.focusRequester(first),
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
            MenuRow(t("Kontrast", "Contrast"), { prefs.ground = prefs.ground.next(Ground.entries.toTypedArray()) }, value = prefs.ground.label)
            MenuRow(t("Kachelstil", "Tile style"), { prefs.tileStyle = prefs.tileStyle.next(TileStyle.entries.toTypedArray()) }, value = prefs.tileStyle.label)

            Header(t("Startseite & Apps", "Home & apps"))
            MenuRow(
                t("Ordner", "Folders"),
                { prefs.foldersEnabled = !prefs.foldersEnabled },
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

            Header(t("Beim Einschalten", "On wake-up"))
            MenuRow(
                t("Einschalt-App", "Startup app"),
                { prefs.startupEnabled = !prefs.startupEnabled },
                value = onOff(prefs.startupEnabled),
                subtitle = if (serviceEnabled) t("Öffnet nach dem Aufwachen direkt eine App", "Opens an app right after the TV wakes up") else t("Braucht die Home-Umleitung (ADB, siehe Anleitung)", "Needs the Home redirect (ADB, see README)"),
            )
            if (prefs.startupEnabled) {
                MenuRow(t("Tagsüber (6–18 Uhr)", "Daytime (6 am – 6 pm)"), { onAction(SettingsAction.STARTUP_DAY) }, value = appLabel(prefs.startupDay))
                MenuRow(t("Abends (18–6 Uhr)", "Evening (6 pm – 6 am)"), { onAction(SettingsAction.STARTUP_EVENING) }, value = appLabel(prefs.startupEvening))
            }

            Header("Smart Home")
            MenuRow("Home Assistant", { prefs.haEnabled = !prefs.haEnabled }, value = onOff(prefs.haEnabled))
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

            Header("System")
            MenuRow(
                t("Home-Taste umleiten", "Redirect Home button"),
                { prefs.redirectHome = !prefs.redirectHome },
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
                t("Nach Updates suchen", "Check for updates"),
                { onAction(SettingsAction.UPDATE_CHECK) },
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
            MenuRow(t("Fire-TV-Einstellungen", "Fire TV settings"), { onAction(SettingsAction.FIRE_SETTINGS) })
            MenuRow(
                t("Neu starten", "Restart"),
                { onAction(SettingsAction.REBOOT) },
                subtitle = t("Öffnet das Einschaltmenü oder „Mein Fire TV“", "Opens the power menu or “My Fire TV”"),
            )
            MenuRow(
                t("Amazon-Startseite öffnen", "Open Amazon home"),
                { onAction(SettingsAction.AMAZON_HOME) },
                subtitle = t("10 Minuten ohne Umleitung, z. B. für Updates", "10 minutes without redirect, e.g. for updates"),
            )
            Spacer(Modifier.height(16.dp))
            KeyHint(t("ZURÜCK", "BACK"), t("Startseite", "Home"))
        }
    }
}

@Composable
private fun Header(title: String) {
    Spacer(Modifier.height(18.dp))
    Txt(title, size = 13.sp, color = LocalAccent.current, weight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp, bottom = 4.dp))
}
