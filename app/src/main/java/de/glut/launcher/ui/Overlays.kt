package de.glut.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.glut.launcher.data.AppEntry
import de.glut.launcher.data.Prefs

/** Eine Zeile in Schnellmenü oder App-Optionen. */
@Composable
fun MenuRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    subtitle: String? = null,
    titleColor: Color = Gruv.fg1,
) {
    Focusable(
        onClick = onClick,
        shape = RoundedCornerShape(7.dp),
        color = Color.Transparent,
        focusedColor = Gruv.bg2,
        focusedScale = 1.02f,
        modifier = modifier.fillMaxWidth().heightIn(min = 38.dp),
    ) { focused ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt(
                    title,
                    size = 12.sp,
                    color = if (focused) Gruv.fg0 else titleColor,
                    weight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                )
                if (subtitle != null) Txt(subtitle, size = 9.sp, color = if (focused) Gruv.fg2 else Gruv.fg4, maxLines = 2)
            }
            if (value != null) Txt(value, size = 11.sp, color = if (focused) Gruv.fg2 else Gruv.fg4)
        }
    }
}

/** Hält den Fokus im Overlay, damit das Steuerkreuz nicht auf die Kacheln dahinter springt. */
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.trapFocus(): Modifier =
    focusProperties { exit = { FocusRequester.Cancel } }.focusGroup()

@Composable
private fun rememberInitialFocus(): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { requester.requestFocus() }
    }
    return requester
}

@Composable
fun QuickMenu(
    prefs: Prefs,
    serviceEnabled: Boolean,
    onEditHome: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAmazonHome: () -> Unit,
) {
    val first = rememberInitialFocus()
    Box(Modifier.fillMaxSize().background(Gruv.bg0h.copy(alpha = 0.6f))) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(320.dp)
                .fillMaxHeight()
                .background(Gruv.bg0)
                .trapFocus()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp, vertical = 30.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Txt("Schnellmenü", size = 22.sp, color = Gruv.fg0, weight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))

            MenuRow("Startseite bearbeiten", onEditHome, Modifier.focusRequester(first), value = "›")
            MenuRow("Kachelstil", { prefs.tileStyle = prefs.tileStyle.next(TileStyle.entries.toTypedArray()) }, value = prefs.tileStyle.label)
            MenuRow("Hintergrund", { prefs.wallpaper = prefs.wallpaper.next(Wallpaper.entries.toTypedArray()) }, value = prefs.wallpaper.label)
            MenuRow("Kontrast", { prefs.ground = prefs.ground.next(Ground.entries.toTypedArray()) }, value = prefs.ground.label)
            MenuRow("Akzentfarbe", { prefs.accent = prefs.accent.next(Accent.entries.toTypedArray()) }, value = prefs.accent.label)
            MenuRow(
                title = "Home-Taste umleiten",
                onClick = { prefs.redirectHome = !prefs.redirectHome },
                value = if (prefs.redirectHome) "An" else "Aus",
                subtitle = if (serviceEnabled) "Dienst aktiv" else "Dienst nicht aktiviert – siehe Anleitung (ADB)",
            )

            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Gruv.bg1))
            Spacer(Modifier.height(8.dp))

            MenuRow("Fire-TV-Einstellungen", onOpenSettings)
            MenuRow(
                title = "Amazon-Startseite öffnen",
                onClick = onOpenAmazonHome,
                subtitle = "10 Minuten ohne Umleitung, z. B. für Updates",
            )

            Spacer(Modifier.height(16.dp))
            KeyHint("ZURÜCK", "Schließen")
        }
    }
}

@Composable
fun AppOptions(
    app: AppEntry,
    prefs: Prefs,
    onClose: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
) {
    val first = rememberInitialFocus()
    val isFavorite = app.packageName in prefs.favorites
    val isHidden = app.packageName in prefs.hidden
    Box(Modifier.fillMaxSize().background(Gruv.bg0h.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .width(340.dp)
                .background(Gruv.bg0, RoundedCornerShape(12.dp))
                .trapFocus()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Txt(app.label, size = 18.sp, color = Gruv.fg0, weight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            MenuRow(
                if (isFavorite) "Aus Favoriten entfernen" else "Zu Favoriten hinzufügen",
                { prefs.toggleFavorite(app.packageName); onClose() },
                Modifier.focusRequester(first),
            )
            if (isFavorite) {
                MenuRow("In Favoriten nach links", { prefs.moveFavorite(app.packageName, -1) })
                MenuRow("In Favoriten nach rechts", { prefs.moveFavorite(app.packageName, 1) })
            }
            MenuRow(
                if (isHidden) "Wieder einblenden" else "Ausblenden",
                { prefs.toggleHidden(app.packageName); onClose() },
            )
            MenuRow("App-Info", { onAppInfo(); onClose() })
            MenuRow("Deinstallieren", { onUninstall(); onClose() }, titleColor = Gruv.red)
            MenuRow("Abbrechen", onClose)
        }
    }
}
