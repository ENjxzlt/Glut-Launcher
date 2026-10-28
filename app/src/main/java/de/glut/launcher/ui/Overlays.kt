package de.glut.launcher.ui

import de.glut.launcher.t
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
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        focusedColor = Theme.fg0,
        focusedScale = 1.02f,
        modifier = modifier.fillMaxWidth().heightIn(min = 44.dp),
    ) { focused ->
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt(
                    title,
                    size = 12.5.sp,
                    color = if (focused) Theme.bg0 else titleColor,
                    weight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                )
                if (subtitle != null) Txt(subtitle, size = 9.sp, color = if (focused) Theme.bg3 else Theme.fg4, maxLines = 2)
            }
            if (value != null) Txt(value, size = 11.sp, color = if (focused) Theme.bg3 else Theme.fg4)
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
                .background(Theme.bg1.copy(alpha = 0.94f), RoundedCornerShape(22.dp))
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
    DialogPanel(app.label, subtitle = folder?.let { t("Im Ordner „${it.name}“", "In folder “${it.name}”") }) {
        MenuRow(
            if (isFavorite) t("Aus Favoriten entfernen", "Remove from favourites") else t("Zu Favoriten hinzufügen", "Add to favourites"),
            { prefs.toggleFavorite(app.packageName); onClose() },
            Modifier.focusRequester(first),
        )
        if (isFavorite) {
            MenuRow(t("In Favoriten nach links", "Move left in favourites"), { prefs.moveFavorite(app.packageName, -1) })
            MenuRow(t("In Favoriten nach rechts", "Move right in favourites"), { prefs.moveFavorite(app.packageName, 1) })
        }
        if (prefs.foldersEnabled) {
            MenuRow(if (folder == null) t("In Ordner verschieben …", "Move to folder …") else t("In anderen Ordner …", "Move to another folder …"), onMoveToFolder)
            if (folder != null) MenuRow(t("Aus Ordner nehmen", "Remove from folder"), { prefs.removeFromFolder(app.packageName); onClose() })
        }
        MenuRow(
            if (isHidden) t("Wieder einblenden", "Unhide") else t("Ausblenden", "Hide"),
            { prefs.toggleHidden(app.packageName); onClose() },
        )
        MenuRow(t("App-Info", "App info"), { onAppInfo(); onClose() })
        MenuRow(t("Deinstallieren", "Uninstall"), { onUninstall(); onClose() }, titleColor = Theme.red)
        MenuRow(t("Abbrechen", "Cancel"), onClose)
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
    DialogPanel(folder.name, subtitle = t("Ordner · ${folder.apps.size} Apps", "Folder · ${folder.apps.size} apps")) {
        MenuRow(t("Öffnen", "Open"), onOpen, Modifier.focusRequester(first))
        MenuRow(t("Umbenennen …", "Rename …"), onRename)
        MenuRow(
            if (isFavorite) t("Aus Favoriten entfernen", "Remove from favourites") else t("Zu Favoriten hinzufügen", "Add to favourites"),
            { prefs.toggleFavorite(folder.favoriteKey); onClose() },
        )
        if (isFavorite) {
            MenuRow(t("In Favoriten nach links", "Move left in favourites"), { prefs.moveFavorite(folder.favoriteKey, -1) })
            MenuRow(t("In Favoriten nach rechts", "Move right in favourites"), { prefs.moveFavorite(folder.favoriteKey, 1) })
        }
        MenuRow(
            t("Ordner auflösen", "Delete folder"),
            { prefs.deleteFolder(folder.id); onClose() },
            subtitle = t("Die Apps bleiben installiert.", "The apps stay installed."),
            titleColor = Theme.red,
        )
        MenuRow(t("Abbrechen", "Cancel"), onClose)
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
                Txt(t("${apps.size} Apps", "${apps.size} apps"), size = 11.sp, color = Theme.fg4)
            }
            if (apps.isEmpty()) {
                Txt(t("Noch leer. Menü-Taste auf einer App → „In Ordner verschieben“.", "Empty. Menu button on an app → “Move to folder”."), size = 11.sp, color = Theme.fg4)
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
        if (multi) MenuRow(t("Fertig", "Done"), onDone, Modifier.focusRequester(first), titleColor = LocalAccent.current)
        items.forEachIndexed { index, item ->
            MenuRow(
                title = item.label,
                subtitle = item.subtitle,
                checked = isSelected(item.key),
                onClick = { onSelect(item.key); if (!multi) onDone() },
                modifier = if (!multi && index == 0) Modifier.focusRequester(first) else Modifier,
            )
        }
        if (items.isEmpty()) Txt(t("Nichts zur Auswahl.", "Nothing to choose from."), size = 11.sp, color = Theme.fg4)
        if (!multi) MenuRow(t("Abbrechen", "Cancel"), onDone)
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
        MenuRow(t("Übernehmen", "Apply"), { keyboard?.hide(); onDone(text.trim()) }, titleColor = accent)
        MenuRow(t("Abbrechen", "Cancel"), { keyboard?.hide(); onCancel() })
    }
}

/** Schnellmenü bei langem Druck auf die Home-Taste (als einziger Launcher: Menü-Taste). */
@Composable
fun QuickActionsDialog(
    onlyLauncher: Boolean,
    onSleep: () -> Unit,
    onSettings: () -> Unit,
    onFireSettings: () -> Unit,
    onAmazonHome: () -> Unit,
    onRestart: () -> Unit,
    onClose: () -> Unit,
) {
    val first = rememberInitialFocus()
    DialogPanel(t("Schnellmenü", "Quick menu")) {
        MenuRow(t("Schlafen", "Sleep"), onSleep, Modifier.focusRequester(first))
        MenuRow(t("Neu starten", "Restart"), onRestart)
        MenuRow(t("Launcher-Einstellungen", "Launcher settings"), onSettings)
        MenuRow(if (onlyLauncher) t("Systemeinstellungen", "System settings") else t("Fire-TV-Einstellungen", "Fire TV settings"), onFireSettings)
        if (!onlyLauncher) MenuRow(t("Amazon-Startseite öffnen", "Open Amazon home"), onAmazonHome, subtitle = t("10 Minuten ohne Umleitung", "10 minutes without redirect"))
        MenuRow(t("Abbrechen", "Cancel"), onClose)
    }
}
