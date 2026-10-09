package de.glut.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.glut.launcher.data.AppEntry
import de.glut.launcher.data.AppRepository
import de.glut.launcher.data.Folder
import de.glut.launcher.data.Prefs

/** Eine Zeile in Einstellungen, Optionen oder Auswahllisten. */
@Composable
fun MenuRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    subtitle: String? = null,
    titleColor: Color = Theme.fg1,
    checked: Boolean = false,
) {
    Focusable(
        onClick = onClick,
        shape = RoundedCornerShape(7.dp),
        color = Color.Transparent,
        focusedColor = Theme.bg2,
        focusedScale = 1.02f,
        modifier = modifier.fillMaxWidth().heightIn(min = 38.dp),
    ) { focused ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt(
                    title,
                    size = 12.sp,
                    color = if (focused) Theme.fg0 else titleColor,
                    weight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                )
                if (subtitle != null) Txt(subtitle, size = 9.sp, color = if (focused) Theme.fg2 else Theme.fg4, maxLines = 2)
            }
            if (value != null) Txt(value, size = 11.sp, color = if (focused) Theme.fg2 else Theme.fg4)
            if (checked) Icon(Icons.check, size = 15.dp, tint = LocalAccent.current)
        }
    }
}

/** Hält den Fokus im Overlay, damit das Steuerkreuz nicht auf die Kacheln dahinter springt. */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.trapFocus(): Modifier =
    focusProperties { exit = { FocusRequester.Cancel } }.focusGroup()

@Composable
fun rememberInitialFocus(): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { requester.requestFocus() }
    }
    return requester
}

/** Abgedunkelter Hintergrund mit einem Fenster in der Mitte. */
@Composable
fun DialogPanel(
    title: String,
    width: Dp = 360.dp,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Theme.bg0h.copy(alpha = 0.65f)), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .width(width)
                .heightIn(max = 480.dp)
                .background(Theme.bg0, RoundedCornerShape(12.dp))
                .trapFocus()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Txt(title, size = 18.sp, color = Theme.fg0, weight = FontWeight.SemiBold)
            if (subtitle != null) Txt(subtitle, size = 10.sp, color = Theme.fg4, maxLines = 3)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
fun AppOptions(
    app: AppEntry,
    prefs: Prefs,
    onClose: () -> Unit,
    onMoveToFolder: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
) {
    val first = rememberInitialFocus()
    val isFavorite = app.packageName in prefs.favorites
    val isHidden = app.packageName in prefs.hidden
    val folder = prefs.folderOf(app.packageName)
    DialogPanel(app.label, subtitle = folder?.let { "Im Ordner „${it.name}“" }) {
        MenuRow(
            if (isFavorite) "Aus Favoriten entfernen" else "Zu Favoriten hinzufügen",
            { prefs.toggleFavorite(app.packageName); onClose() },
            Modifier.focusRequester(first),
        )
        if (isFavorite) {
            MenuRow("In Favoriten nach links", { prefs.moveFavorite(app.packageName, -1) })
            MenuRow("In Favoriten nach rechts", { prefs.moveFavorite(app.packageName, 1) })
        }
        if (prefs.foldersEnabled) {
            MenuRow(if (folder == null) "In Ordner verschieben …" else "In anderen Ordner …", onMoveToFolder)
            if (folder != null) MenuRow("Aus Ordner nehmen", { prefs.removeFromFolder(app.packageName); onClose() })
        }
        MenuRow(
            if (isHidden) "Wieder einblenden" else "Ausblenden",
            { prefs.toggleHidden(app.packageName); onClose() },
        )
        MenuRow("App-Info", { onAppInfo(); onClose() })
        MenuRow("Deinstallieren", { onUninstall(); onClose() }, titleColor = Theme.red)
        MenuRow("Abbrechen", onClose)
    }
}

@Composable
fun FolderOptions(
    folder: Folder,
    prefs: Prefs,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onClose: () -> Unit,
) {
    val first = rememberInitialFocus()
    val isFavorite = folder.favoriteKey in prefs.favorites
    DialogPanel(folder.name, subtitle = "Ordner · ${folder.apps.size} Apps") {
        MenuRow("Öffnen", onOpen, Modifier.focusRequester(first))
        MenuRow("Umbenennen …", onRename)
        MenuRow(
            if (isFavorite) "Aus Favoriten entfernen" else "Zu Favoriten hinzufügen",
            { prefs.toggleFavorite(folder.favoriteKey); onClose() },
        )
        if (isFavorite) {
            MenuRow("In Favoriten nach links", { prefs.moveFavorite(folder.favoriteKey, -1) })
            MenuRow("In Favoriten nach rechts", { prefs.moveFavorite(folder.favoriteKey, 1) })
        }
        MenuRow(
            "Ordner auflösen",
            { prefs.deleteFolder(folder.id); onClose() },
            subtitle = "Die Apps bleiben installiert.",
            titleColor = Theme.red,
        )
        MenuRow("Abbrechen", onClose)
    }
}

/** Ein geöffneter Ordner als Fenster mit App-Raster. */
@Composable
fun FolderView(
    folder: Folder,
    apps: List<AppEntry>,
    prefs: Prefs,
    repository: AppRepository,
    onLaunch: (AppEntry) -> Unit,
    onOptions: (AppEntry) -> Unit,
) {
    val first = rememberInitialFocus()
    Box(Modifier.fillMaxSize().background(Theme.bg0h.copy(alpha = 0.65f)), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .width(720.dp)
                .height(420.dp)
                .background(Theme.bg0, RoundedCornerShape(14.dp))
                .trapFocus()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.folder, size = 20.dp, tint = LocalAccent.current)
                Txt(folder.name, size = 20.sp, color = Theme.fg0, weight = FontWeight.SemiBold)
                Txt("${apps.size} Apps", size = 11.sp, color = Theme.fg4)
            }
            if (apps.isEmpty()) {
                Txt("Noch leer. Menü-Taste auf einer App → „In Ordner verschieben“.", size = 11.sp, color = Theme.fg4)
            }
            AppGrid(
                apps = apps,
                prefs = prefs,
                repository = repository,
                onLaunch = onLaunch,
                onOptions = onOptions,
                firstFocus = first,
                columns = 5,
                tileWidth = 118.dp,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

data class PickItem(val key: String, val label: String, val subtitle: String? = null)

/**
 * Auswahlliste. Bei [multi] bleibt sie offen und wird mit „Fertig“
 * geschlossen, sonst schließt eine Auswahl sie sofort.
 */
@Composable
fun ListPicker(
    title: String,
    items: List<PickItem>,
    isSelected: (String) -> Boolean,
    onSelect: (String) -> Unit,
    onDone: () -> Unit,
    multi: Boolean = false,
    subtitle: String? = null,
) {
    val first = rememberInitialFocus()
    DialogPanel(title, width = 420.dp, subtitle = subtitle) {
        if (multi) MenuRow("Fertig", onDone, Modifier.focusRequester(first), titleColor = LocalAccent.current)
        items.forEachIndexed { index, item ->
            MenuRow(
                title = item.label,
                subtitle = item.subtitle,
                checked = isSelected(item.key),
                onClick = { onSelect(item.key); if (!multi) onDone() },
                modifier = if (!multi && index == 0) Modifier.focusRequester(first) else Modifier,
            )
        }
        if (items.isEmpty()) Txt("Nichts zur Auswahl.", size = 11.sp, color = Theme.fg4)
        if (!multi) MenuRow("Abbrechen", onDone)
    }
}

/** Eingabefeld mit Bildschirmtastatur. */
@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    hint: String,
    onDone: (String) -> Unit,
    onCancel: () -> Unit,
    password: Boolean = false,
    subtitle: String? = null,
) {
    var text by remember { mutableStateOf(initial) }
    var focused by remember { mutableStateOf(false) }
    val field = rememberInitialFocus()
    val keyboard = LocalSoftwareKeyboardController.current
    val accent = LocalAccent.current
    LaunchedEffect(Unit) { keyboard?.show() }

    DialogPanel(title, width = 460.dp, subtitle = subtitle) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = TextStyle(color = Theme.fg0, fontSize = 14.sp, fontFamily = Theme.font),
            cursorBrush = SolidColor(accent),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
                keyboardType = if (password) KeyboardType.Password else KeyboardType.Uri,
            ),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); onDone(text.trim()) }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(field)
                .onFocusChanged { focused = it.isFocused }
                .background(if (focused) Theme.bg2 else Theme.bg1, RoundedCornerShape(8.dp))
                .then(if (focused) Modifier.border(2.dp, accent, RoundedCornerShape(8.dp)) else Modifier)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) Txt(hint, size = 14.sp, color = Theme.fg4)
                    inner()
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        MenuRow("Übernehmen", { keyboard?.hide(); onDone(text.trim()) }, titleColor = accent)
        MenuRow("Abbrechen", { keyboard?.hide(); onCancel() })
    }
}

/** Schnellmenü bei langem Druck auf die Home-Taste. */
@Composable
fun QuickActionsDialog(
    onSleep: () -> Unit,
    onSettings: () -> Unit,
    onFireSettings: () -> Unit,
    onAmazonHome: () -> Unit,
    onClose: () -> Unit,
) {
    val first = rememberInitialFocus()
    DialogPanel("Schnellmenü") {
        MenuRow("Schlafen", onSleep, Modifier.focusRequester(first))
        MenuRow("Launcher-Einstellungen", onSettings)
        MenuRow("Fire-TV-Einstellungen", onFireSettings)
        MenuRow("Amazon-Startseite öffnen", onAmazonHome, subtitle = "10 Minuten ohne Umleitung")
        MenuRow("Abbrechen", onClose)
    }
}
