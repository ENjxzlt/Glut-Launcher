package de.glut.launcher.ui

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
    FIRE_SETTINGS, AMAZON_HOME,
}

private fun onOff(value: Boolean) = if (value) "An" else "Aus"

@Composable
fun SettingsScreen(
    prefs: Prefs,
    focusKey: Int,
    serviceEnabled: Boolean,
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
            Txt("Einstellungen", size = 28.sp, color = Theme.fg0, weight = FontWeight.SemiBold)
            Txt("Glut Launcher ${updater.currentVersion}", size = 11.sp, color = Theme.fg4)

            Header("Aussehen")
            MenuRow("Farbschema", { onAction(SettingsAction.THEME) }, Modifier.focusRequester(first), value = prefs.colorTheme.label)
            MenuRow("Schriftart", { onAction(SettingsAction.FONT) }, value = prefs.font.label)
            MenuRow("Akzentfarbe", { prefs.accent = prefs.accent.next(Accent.entries.toTypedArray()) }, value = prefs.accent.label)
            MenuRow(
                "Hintergrund",
                { prefs.wallpaper = prefs.wallpaper.next(Wallpaper.entries.toTypedArray()) },
                value = prefs.wallpaper.label,
                subtitle = if (prefs.wallpaper == Wallpaper.TIME) "Morgens kühl, tagsüber hell, abends warm, nachts dunkel" else null,
            )
            MenuRow("Kontrast", { prefs.ground = prefs.ground.next(Ground.entries.toTypedArray()) }, value = prefs.ground.label)
            MenuRow("Kachelstil", { prefs.tileStyle = prefs.tileStyle.next(TileStyle.entries.toTypedArray()) }, value = prefs.tileStyle.label)

            Header("Startseite & Apps")
            MenuRow(
                "Ordner",
                { prefs.foldersEnabled = !prefs.foldersEnabled },
                value = onOff(prefs.foldersEnabled),
                subtitle = "Menü-Taste auf einer App → „In Ordner verschieben“",
            )
            MenuRow(
                "Systemapps in „Alle Apps“ ausblenden",
                { prefs.hideSystemApps = !prefs.hideSystemApps },
                value = onOff(prefs.hideSystemApps),
                subtitle = "Bleiben über die Suche und den Filter „System“ erreichbar",
            )
            MenuRow(
                "Musik-Widget",
                { prefs.musicWidget = !prefs.musicWidget },
                value = onOff(prefs.musicWidget),
                subtitle = if (mediaAccess) "Zeigt, was im Hintergrund läuft" else "Braucht Zugriff auf Benachrichtigungen (ADB, siehe Anleitung)",
            )

            Header("Beim Einschalten")
            MenuRow(
                "Einschalt-App",
                { prefs.startupEnabled = !prefs.startupEnabled },
                value = onOff(prefs.startupEnabled),
                subtitle = if (serviceEnabled) "Öffnet nach dem Aufwachen direkt eine App" else "Braucht die Home-Umleitung (ADB, siehe Anleitung)",
            )
            if (prefs.startupEnabled) {
                MenuRow("Tagsüber (6–18 Uhr)", { onAction(SettingsAction.STARTUP_DAY) }, value = appLabel(prefs.startupDay))
                MenuRow("Abends (18–6 Uhr)", { onAction(SettingsAction.STARTUP_EVENING) }, value = appLabel(prefs.startupEvening))
            }

            Header("Smart Home")
            MenuRow("Home Assistant", { prefs.haEnabled = !prefs.haEnabled }, value = onOff(prefs.haEnabled))
            if (prefs.haEnabled) {
                MenuRow("Server-Adresse", { onAction(SettingsAction.HA_URL) }, value = prefs.haUrl.ifBlank { "nicht gesetzt" })
                if (prefs.haConnected) {
                    MenuRow("Verbunden", { onAction(SettingsAction.HA_LOGOUT) }, subtitle = haStatus ?: "OK auswählen zum Abmelden")
                } else {
                    MenuRow("Anmelden mit Benutzername & Passwort", { onAction(SettingsAction.HA_LOGIN) }, subtitle = haStatus)
                    MenuRow("Langlebiges Token eingeben", { onAction(SettingsAction.HA_TOKEN) }, subtitle = "HA → Profil → Sicherheit → Token erstellen")
                }
                if (prefs.haConnected) {
                    MenuRow("Smart-Home-Leiste auf der Startseite", { prefs.haTilesEnabled = !prefs.haTilesEnabled }, value = onOff(prefs.haTilesEnabled))
                    MenuRow("Geräte und Szenen wählen", { onAction(SettingsAction.HA_TILES) }, value = "${prefs.haTiles.size} gewählt")
                    MenuRow(
                        "Kino-Modus",
                        { prefs.kinoEnabled = !prefs.kinoEnabled },
                        value = onOff(prefs.kinoEnabled),
                        subtitle = "Startet eine Szene, wenn du eine Video-App öffnest",
                    )
                    if (prefs.kinoEnabled) {
                        MenuRow("Apps für den Kino-Modus", { onAction(SettingsAction.KINO_APPS) }, value = "${prefs.kinoApps.size} gewählt")
                        MenuRow("Beim Start der App", { onAction(SettingsAction.KINO_START) }, value = entityLabel(prefs.kinoStart))
                        MenuRow("Zurück auf der Startseite", { onAction(SettingsAction.KINO_END) }, value = entityLabel(prefs.kinoEnd))
                    }
                }
            }

            Header("System")
            MenuRow(
                "Home-Taste umleiten",
                { prefs.redirectHome = !prefs.redirectHome },
                value = onOff(prefs.redirectHome),
                subtitle = if (serviceEnabled) "Dienst aktiv" else "Dienst nicht aktiviert – siehe Anleitung (ADB)",
            )
            MenuRow(
                "Nach Updates suchen",
                { onAction(SettingsAction.UPDATE_CHECK) },
                subtitle = updater.status ?: "Installiert: ${updater.currentVersion}",
            )
            updater.available?.let { info ->
                MenuRow(
                    "Version ${info.versionName} installieren",
                    { onAction(SettingsAction.UPDATE_INSTALL) },
                    titleColor = LocalAccent.current,
                    subtitle = if (updater.canInstall()) null else "Erst „Apps unbekannter Herkunft“ für Glut Launcher erlauben",
                )
                if (!updater.canInstall()) MenuRow("Installation erlauben …", { onAction(SettingsAction.UNKNOWN_SOURCES) })
            }
            MenuRow("Automatisch nach Updates suchen", { prefs.autoUpdateCheck = !prefs.autoUpdateCheck }, value = onOff(prefs.autoUpdateCheck))
            MenuRow("Fire-TV-Einstellungen", { onAction(SettingsAction.FIRE_SETTINGS) })
            MenuRow(
                "Amazon-Startseite öffnen",
                { onAction(SettingsAction.AMAZON_HOME) },
                subtitle = "10 Minuten ohne Umleitung, z. B. für Updates",
            )
            Spacer(Modifier.height(16.dp))
            KeyHint("ZURÜCK", "Startseite")
        }
    }
}

@Composable
private fun Header(title: String) {
    Spacer(Modifier.height(18.dp))
    Txt(title, size = 13.sp, color = LocalAccent.current, weight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp, bottom = 4.dp))
}
