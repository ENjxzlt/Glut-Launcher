package de.glut.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Choreographer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import de.glut.launcher.data.AppEntry
import de.glut.launcher.data.AppRepository
import de.glut.launcher.data.Folder
import de.glut.launcher.data.HaEntity
import de.glut.launcher.data.HomeAssistant
import de.glut.launcher.data.LauncherSnapshot
import de.glut.launcher.data.LoginStep
import de.glut.launcher.data.MediaWatcher
import de.glut.launcher.data.Prefs
import de.glut.launcher.data.Updater
import de.glut.launcher.service.HomeRedirectService
import de.glut.launcher.ui.AppOptions
import de.glut.launcher.ui.AppsScreen
import de.glut.launcher.ui.ColorTheme
import de.glut.launcher.ui.FolderOptions
import de.glut.launcher.ui.FolderView
import de.glut.launcher.ui.FontChoice
import de.glut.launcher.ui.HomeScreen
import de.glut.launcher.ui.ListPicker
import de.glut.launcher.ui.LocalAccent
import de.glut.launcher.ui.PickItem
import de.glut.launcher.ui.QuickActionsDialog
import de.glut.launcher.ui.SearchScreen
import de.glut.launcher.ui.SettingsAction
import de.glut.launcher.ui.SettingsScreen
import de.glut.launcher.ui.TextInputDialog
import de.glut.launcher.ui.Theme
import de.glut.launcher.ui.WallpaperBackground
import de.glut.launcher.ui.rememberNow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

private enum class Screen { HOME, APPS, SEARCH, SETTINGS }

/** Fenster über dem aktuellen Bildschirm. [back] ist das Fenster, zu dem „Zurück“ führt. */
private sealed interface Overlay {
    val back: Overlay? get() = null

    data class AppMenu(val app: AppEntry, override val back: Overlay? = null) : Overlay
    data object QuickActions : Overlay
    data class FolderOpen(val folderId: String) : Overlay
    data class FolderMenu(val folderId: String, override val back: Overlay? = null) : Overlay
    data class MoveToFolder(val app: AppEntry, override val back: Overlay? = null) : Overlay
    class Input(
        val title: String,
        val initial: String,
        val hint: String,
        val password: Boolean = false,
        val subtitle: String? = null,
        override val back: Overlay? = null,
        val onDone: (String) -> Unit,
    ) : Overlay
    class Picker(
        val title: String,
        val items: List<PickItem>,
        val isSelected: (String) -> Boolean,
        val onSelect: (String) -> Unit,
        val multi: Boolean = false,
        val subtitle: String? = null,
        override val back: Overlay? = null,
    ) : Overlay
}

class MainActivity : ComponentActivity() {
    private lateinit var prefs: Prefs
    private lateinit var repository: AppRepository
    private lateinit var homeAssistant: HomeAssistant
    private lateinit var media: MediaWatcher
    private lateinit var updater: Updater

    private var apps by mutableStateOf(AppRepository.cachedApps)
    private var screen by mutableStateOf(Screen.HOME)
    private var overlay by mutableStateOf<Overlay?>(null)
    private var serviceEnabled by mutableStateOf(false)
    private var keyFilterSupported by mutableStateOf(false)
    private var focusKey by mutableIntStateOf(0)
    private val smartHome = mutableStateListOf<HaEntity>()
    private var haEntities by mutableStateOf<List<HaEntity>>(emptyList())
    private var haStatus by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log("onCreate")
        prefs = Prefs(this)
        Theme.prefs = prefs
        repository = AppRepository(this)
        homeAssistant = HomeAssistant(prefs)
        media = MediaWatcher(this)
        updater = Updater(this)

        setContent {
            val now = rememberNow()
            val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
            CompositionLocalProvider(LocalAccent provides prefs.accent.color) {
                Box(Modifier.fillMaxSize()) {
                    WallpaperBackground(prefs.wallpaper, prefs.ground, prefs.accent.color, hour, Modifier.fillMaxSize())
                    ScreenContent()
                    // key: jedes Fenster bekommt frischen Zustand (z. B. leeres Eingabefeld)
                    overlay?.let { key(it) { OverlayContent(it) } }

                    // Zurück schließt erst Fenster, dann den Bildschirm. Auf der
                    // Startseite passiert nichts – ein Launcher hat kein „dahinter“.
                    BackHandler {
                        when {
                            overlay != null -> closeOverlay()
                            screen != Screen.HOME -> goHome()
                        }
                    }
                }
            }
        }
        handleWake(intent)
        handleQuickActions(intent)

        // App-Liste nur neu laden, wenn sich installierte Apps ändern – nicht bei jedem Home-Druck.
        val packages = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, packages, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    // Beim Start einmal im Hintergrund prüfen; bis dahin zeigt die Liste den Stand aus dem Speicher.
    private var appsDirty = true
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            appsDirty = true
            repository.clearArtCache()
            refreshApps()
        }
    }

    @androidx.compose.runtime.Composable
    private fun ScreenContent() {
        when (screen) {
            Screen.HOME -> HomeScreen(
                apps = apps,
                prefs = prefs,
                repository = repository,
                focusKey = focusKey,
                smartHome = smartHome,
                nowPlaying = media.nowPlaying,
                onMediaOpen = { media.nowPlaying?.let { launchPackage(it.packageName) } },
                onMediaPlayPause = { media.playPause() },
                onMediaNext = { media.next() },
                onMediaPrevious = { media.previous() },
                onLaunch = ::launchApp,
                onOptions = { overlay = Overlay.AppMenu(it) },
                onOpenFolder = { overlay = Overlay.FolderOpen(it.id) },
                onFolderMenu = { overlay = Overlay.FolderMenu(it.id) },
                onSmartHome = ::activateEntity,
                onOpenApps = { screen = Screen.APPS },
                onOpenMenu = { screen = Screen.SETTINGS },
                onOpenSearch = { screen = Screen.SEARCH },
                onOpenWifi = { startSafely(Intent(Settings.ACTION_WIFI_SETTINGS)) },
                onOpenProfile = ::openProfiles,
                onOpenSettings = { startSafely(Intent(Settings.ACTION_SETTINGS)) },
                onOpenBluetooth = { startSafely(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                onOpenAmazonHome = ::openAmazonHome,
            )
            Screen.APPS -> AppsScreen(
                apps = apps,
                prefs = prefs,
                repository = repository,
                focusKey = focusKey,
                onLaunch = ::launchApp,
                onOptions = { overlay = Overlay.AppMenu(it) },
                onOpenFolder = { overlay = Overlay.FolderOpen(it.id) },
                onFolderMenu = { overlay = Overlay.FolderMenu(it.id) },
            )
            Screen.SEARCH -> SearchScreen(
                apps = apps,
                prefs = prefs,
                repository = repository,
                focusKey = focusKey,
                onLaunch = ::launchApp,
                onOptions = { overlay = Overlay.AppMenu(it) },
            )
            Screen.SETTINGS -> SettingsScreen(
                prefs = prefs,
                focusKey = focusKey,
                serviceEnabled = serviceEnabled,
                keyFilterSupported = keyFilterSupported,
                mediaAccess = media.hasAccess,
                haStatus = haStatus,
                updater = updater,
                appLabel = { pkg -> if (pkg.isBlank()) t("Startseite", "Home") else apps.firstOrNull { it.packageName == pkg }?.label ?: pkg },
                entityLabel = { id -> if (id.isBlank()) t("Nichts", "Nothing") else haEntities.firstOrNull { it.id == id }?.name ?: id },
                onAction = ::onSettingsAction,
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun OverlayContent(current: Overlay) {
        when (current) {
            is Overlay.AppMenu -> AppOptions(
                app = current.app,
                prefs = prefs,
                onClose = ::closeOverlay,
                onMoveToFolder = { overlay = Overlay.MoveToFolder(current.app, current.back) },
                onAppInfo = {
                    startSafely(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.fromParts("package", current.app.packageName, null)),
                    )
                },
                onUninstall = {
                    startSafely(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", current.app.packageName, null)))
                },
            )
            is Overlay.FolderOpen -> {
                val folder = prefs.folders.firstOrNull { it.id == current.folderId }
                if (folder == null) {
                    LaunchedEffect(current) { closeOverlay() }
                } else {
                    val byPackage = apps.associateBy { it.packageName }
                    FolderView(
                        folder = folder,
                        apps = folder.apps.mapNotNull { byPackage[it] },
                        prefs = prefs,
                        repository = repository,
                        onLaunch = ::launchApp,
                        onOptions = { overlay = Overlay.AppMenu(it, back = current) },
                    )
                }
            }
            is Overlay.FolderMenu -> {
                val folder = prefs.folders.firstOrNull { it.id == current.folderId }
                if (folder == null) {
                    LaunchedEffect(current) { closeOverlay() }
                } else {
                    FolderOptions(
                        folder = folder,
                        prefs = prefs,
                        onOpen = { overlay = Overlay.FolderOpen(folder.id) },
                        onRename = { overlay = renameFolder(folder, back = null) },
                        onClose = ::closeOverlay,
                    )
                }
            }
            is Overlay.MoveToFolder -> {
                val currentFolder = prefs.folderOf(current.app.packageName)
                val items = prefs.folders.map { PickItem(it.id, it.name, "${it.apps.size} Apps") } +
                    PickItem(NEW_FOLDER, t("Neuer Ordner …", "New folder …"))
                ListPicker(
                    title = t("„${current.app.label}“ verschieben", "Move “${current.app.label}”"),
                    items = items,
                    isSelected = { it == currentFolder?.id },
                    onSelect = { key ->
                        if (key == NEW_FOLDER) {
                            val folder = prefs.createFolder(t("Neuer Ordner", "New folder"), current.app.packageName)
                            overlay = renameFolder(folder, back = current.back)
                        } else {
                            prefs.moveToFolder(current.app.packageName, key)
                            overlay = current.back
                        }
                    },
                    onDone = { if (overlay === current) closeOverlay() },
                )
            }
            Overlay.QuickActions -> QuickActionsDialog(
                onSleep = {
                    closeOverlay()
                    if (HomeRedirectService.instance?.sleep() != true) toast(t("Schlafen wird auf diesem Gerät nicht unterstützt.", "Sleep is not supported on this device."))
                },
                onSettings = { overlay = null; screen = Screen.SETTINGS; focusKey++ },
                onFireSettings = { closeOverlay(); startSafely(Intent(Settings.ACTION_SETTINGS)) },
                onAmazonHome = { closeOverlay(); openAmazonHome() },
                onClose = ::closeOverlay,
            )
            is Overlay.Input -> TextInputDialog(
                title = current.title,
                initial = current.initial,
                hint = current.hint,
                password = current.password,
                subtitle = current.subtitle,
                onDone = { text -> overlay = current.back; focusKey++; current.onDone(text) },
                onCancel = ::closeOverlay,
            )
            is Overlay.Picker -> ListPicker(
                title = current.title,
                items = current.items,
                isSelected = current.isSelected,
                onSelect = current.onSelect,
                multi = current.multi,
                subtitle = current.subtitle,
                onDone = { if (overlay === current) closeOverlay() },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        log("onNewIntent")
        // Home gedrückt, während der Launcher schon offen ist: zurück zur Startseite.
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            overlay = null
            goHome()
        }
        handleWake(intent)
        handleQuickActions(intent)
    }

    /** Langes Drücken der Home-Taste (schnelle Home-Taste). */
    private fun handleQuickActions(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_QUICK_ACTIONS, false) != true) return
        intent.removeExtra(EXTRA_QUICK_ACTIONS)
        overlay = Overlay.QuickActions
    }

    override fun onResume() {
        super.onResume()
        log("onResume")
        Choreographer.getInstance().postFrameCallback {
            log("first frame")
            // Ein Bild später ist Glut sicher sichtbar: Sofortbild entfernen.
            window.decorView.post { HomeRedirectService.instance?.hideCover() }
        }
        window.decorView.removeCallbacks(captureSnapshot)
        window.decorView.postDelayed(captureSnapshot, 1500)
        prefs.clearAmazonHomeBypass()
        serviceEnabled = HomeRedirectService.isEnabled(this)
        keyFilterSupported = HomeRedirectService.canFilterKeys()
        if (appsDirty) refreshApps()
        media.start()
        endKinoMode()
        refreshSmartHome()
        autoCheckUpdate()
    }

    override fun onPause() {
        window.decorView.removeCallbacks(captureSnapshot)
        super.onPause()
    }

    /** Merkt sich das Aussehen der Startseite für das Sofortbild beim nächsten Home-Druck. */
    private val captureSnapshot = Runnable {
        if (screen != Screen.HOME || overlay != null || !prefs.instantCover) return@Runnable
        val view = window.decorView
        if (view.width == 0 || view.height == 0) return@Runnable
        runCatching {
            val reuse = LauncherSnapshot.bitmap?.takeIf { it.width == view.width && it.height == view.height }
            val bitmap = reuse ?: Bitmap.createBitmap(view.width, view.height, Bitmap.Config.RGB_565)
            view.draw(Canvas(bitmap))
            LauncherSnapshot.bitmap = bitmap
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(packageReceiver) }
        media.stop()
        super.onDestroy()
    }

    // --- Navigation ---

    private fun goHome() {
        screen = Screen.HOME
        focusKey++
    }

    private fun closeOverlay() {
        overlay = overlay?.back
        focusKey++
    }

    private fun renameFolder(folder: Folder, back: Overlay?) = Overlay.Input(
        title = t("Ordner benennen", "Name folder"),
        initial = folder.name,
        hint = t("z. B. Streaming", "e.g. Streaming"),
        back = back,
    ) { name -> if (name.isNotBlank()) prefs.renameFolder(folder.id, name) }

    // --- Apps starten ---

    private fun refreshApps() {
        appsDirty = false
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { repository.loadApps() }
            if (loaded != apps) apps = loaded
        }
    }

    private fun launchApp(app: AppEntry) {
        if (!startSafely(repository.launchIntent(app))) return
        prefs.recordUse(app.packageName)
        startKinoMode(app.packageName)
    }

    private fun launchPackage(packageName: String) {
        val app = apps.firstOrNull { it.packageName == packageName }
        if (app != null) {
            launchApp(app)
        } else {
            packageManager.getLaunchIntentForPackage(packageName)?.let { startSafely(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    /** Nach dem Einschalten: gewählte App für die Tageszeit öffnen. */
    private fun handleWake(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_WOKE, false) != true) return
        intent.removeExtra(EXTRA_WOKE)
        if (!prefs.startupEnabled) return
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val pkg = if (hour in 6..17) prefs.startupDay else prefs.startupEvening
        if (pkg.isNotBlank()) launchPackage(pkg)
    }

    private fun openAmazonHome() {
        prefs.allowAmazonHomeFor(10 * 60 * 1000L)
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setPackage(AppRepository.AMAZON_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!startSafely(home, quiet = true)) {
            val fallback = packageManager.getLaunchIntentForPackage(AppRepository.AMAZON_LAUNCHER)
            if (fallback == null || !startSafely(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
                prefs.clearAmazonHomeBypass()
            }
        }
    }

    /**
     * Amazon bietet für den Profilwechsel keine öffentliche Schnittstelle.
     * Wir versuchen die Konto-Einstellungen und fallen auf die Fire-TV-Einstellungen zurück.
     */
    private fun openProfiles() {
        if (!startSafely(Intent(Settings.ACTION_SYNC_SETTINGS), quiet = true)) {
            startSafely(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun startSafely(intent: Intent, quiet: Boolean = false): Boolean =
        try {
            startActivity(intent)
            true
        } catch (e: Exception) {
            if (!quiet) toast(t("Lässt sich auf diesem Gerät nicht öffnen.", "Can't be opened on this device."))
            false
        }

    private fun log(step: String) = Log.i(HomeRedirectService.TAG, "$step uptime=${SystemClock.uptimeMillis()}")

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    // --- Einstellungen ---

    private fun onSettingsAction(action: SettingsAction) {
        when (action) {
            SettingsAction.THEME -> overlay = Overlay.Picker(
                title = t("Farbschema", "Colour theme"),
                items = ColorTheme.entries.map { PickItem(it.name, it.label) },
                isSelected = { it == prefs.colorTheme.name },
                onSelect = { key -> prefs.colorTheme = ColorTheme.valueOf(key) },
            )
            SettingsAction.FONT -> overlay = Overlay.Picker(
                title = t("Schriftart", "Font"),
                items = FontChoice.entries.map { PickItem(it.name, it.label) },
                isSelected = { it == prefs.font.name },
                onSelect = { key -> prefs.font = FontChoice.valueOf(key) },
            )
            SettingsAction.STARTUP_DAY -> overlay = appPicker(t("Tagsüber öffnen", "Open during the day"), allowNone = true, { prefs.startupDay }) { prefs.startupDay = it }
            SettingsAction.STARTUP_EVENING -> overlay = appPicker(t("Abends öffnen", "Open in the evening"), allowNone = true, { prefs.startupEvening }) { prefs.startupEvening = it }
            SettingsAction.HA_URL -> overlay = Overlay.Input(
                title = t("Home-Assistant-Adresse", "Home Assistant address"),
                initial = prefs.haUrl,
                hint = t("z. B. 192.168.1.20:8123 oder homeassistant.local", "e.g. 192.168.1.20:8123 or homeassistant.local"),
            ) { url -> if (url.isNotBlank()) prefs.haUrl = HomeAssistant.normalize(url) }
            SettingsAction.HA_LOGIN -> startHaLogin()
            SettingsAction.HA_TOKEN -> overlay = Overlay.Input(
                title = t("Langlebiges Token", "Long-lived token"),
                initial = "",
                hint = t("Token einfügen", "Paste token"),
                password = true,
                subtitle = t("Mit der Fernbedienung mühsam – am einfachsten per ADB vom Handy: adb shell input text TOKEN", "Tedious with the remote – easiest via ADB from your phone: adb shell input text TOKEN"),
            ) { token ->
                if (token.isNotBlank() && prefs.haUrl.isNotBlank()) {
                    haStatus = t("Prüfe Token …", "Checking token …")
                    lifecycleScope.launch {
                        haStatus = runCatching { homeAssistant.useLongLivedToken(prefs.haUrl, token); t("Verbunden.", "Connected.") }
                            .getOrElse { it.message ?: t("Verbindung fehlgeschlagen.", "Connection failed.") }
                        refreshSmartHome()
                    }
                }
            }
            SettingsAction.HA_LOGOUT -> {
                prefs.haLogout()
                smartHome.clear()
                haStatus = t("Abgemeldet.", "Signed out.")
            }
            SettingsAction.HA_TILES -> withEntities { entities ->
                overlay = Overlay.Picker(
                    title = t("Geräte für die Startseite", "Devices for the home screen"),
                    items = entities.map { PickItem(it.id, it.name, it.id) },
                    isSelected = { it in prefs.haTiles },
                    onSelect = { id -> prefs.haTiles = if (id in prefs.haTiles) prefs.haTiles - id else prefs.haTiles + id },
                    multi = true,
                )
            }
            SettingsAction.KINO_APPS -> overlay = Overlay.Picker(
                title = t("Apps für den Kino-Modus", "Apps for cinema mode"),
                items = apps.map { PickItem(it.packageName, it.label) },
                isSelected = { it in prefs.kinoApps },
                onSelect = { pkg -> prefs.kinoApps = if (pkg in prefs.kinoApps) prefs.kinoApps - pkg else prefs.kinoApps + pkg },
                multi = true,
            )
            SettingsAction.KINO_START -> withEntities { entities ->
                overlay = entityPicker(t("Beim Start der App", "When the app starts"), entities, { prefs.kinoStart }) { prefs.kinoStart = it }
            }
            SettingsAction.KINO_END -> withEntities { entities ->
                overlay = entityPicker(t("Zurück auf der Startseite", "Back on the home screen"), entities, { prefs.kinoEnd }) { prefs.kinoEnd = it }
            }
            SettingsAction.UPDATE_CHECK -> lifecycleScope.launch { updater.check() }
            SettingsAction.UPDATE_INSTALL -> updater.available?.let { info ->
                if (!updater.canInstall()) {
                    onSettingsAction(SettingsAction.UNKNOWN_SOURCES)
                } else {
                    lifecycleScope.launch { updater.install(info) }
                }
            }
            SettingsAction.UNKNOWN_SOURCES -> {
                val ok = startSafely(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")),
                    quiet = true,
                )
                if (!ok) toast(t("Fire-TV-Einstellungen → Mein Fire TV → Entwickleroptionen → Apps unbekannter Herkunft → Glut Launcher erlauben.", "Fire TV settings → My Fire TV → Developer options → Apps from unknown sources → allow Glut Launcher."))
            }
            SettingsAction.FIRE_SETTINGS -> startSafely(Intent(Settings.ACTION_SETTINGS))
            SettingsAction.AMAZON_HOME -> openAmazonHome()
        }
    }

    private fun appPicker(title: String, allowNone: Boolean, current: () -> String, set: (String) -> Unit) = Overlay.Picker(
        title = title,
        items = (if (allowNone) listOf(PickItem("", t("Startseite (keine App)", "Home screen (no app)"))) else emptyList()) +
            apps.map { PickItem(it.packageName, it.label) },
        isSelected = { it == current() },
        onSelect = set,
    )

    private fun entityPicker(title: String, entities: List<HaEntity>, current: () -> String, set: (String) -> Unit) = Overlay.Picker(
        title = title,
        subtitle = t("Szenen und Skripte aus Home Assistant", "Scenes and scripts from Home Assistant"),
        items = listOf(PickItem("", t("Nichts", "Nothing"))) + entities.filter { it.isAction }.map { PickItem(it.id, it.name, it.id) },
        isSelected = { it == current() },
        onSelect = set,
    )

    // --- Home Assistant ---

    private fun startHaLogin() {
        if (prefs.haUrl.isBlank()) {
            toast(t("Bitte zuerst die Server-Adresse eingeben.", "Please enter the server address first."))
            return
        }
        overlay = Overlay.Input(t("Benutzername", "Username"), "", t("Home-Assistant-Benutzer", "Home Assistant user")) { user ->
            overlay = Overlay.Input(t("Passwort", "Password"), "", t("Passwort", "Password"), password = true) { password ->
                haStatus = t("Melde an …", "Signing in …")
                lifecycleScope.launch { handleLogin { homeAssistant.login(prefs.haUrl, user, password) } }
            }
        }
    }

    private suspend fun handleLogin(step: suspend () -> LoginStep) {
        val result = runCatching { step() }.getOrElse { LoginStep.Failed(it.message ?: t("Keine Verbindung.", "No connection.")) }
        when (result) {
            LoginStep.Done -> {
                haStatus = t("Verbunden.", "Connected.")
                refreshSmartHome()
            }
            is LoginStep.Failed -> haStatus = result.message
            is LoginStep.NeedsCode -> overlay = Overlay.Input(
                title = t("Bestätigungscode", "Verification code"),
                initial = "",
                hint = t("6-stelliger Code aus der Authenticator-App", "6-digit code from your authenticator app"),
            ) { code ->
                lifecycleScope.launch { handleLogin { homeAssistant.submitCode(prefs.haUrl, result.flowId, code) } }
            }
        }
    }

    /** Lädt die Entitäten aus Home Assistant und ruft dann [then] auf. */
    private fun withEntities(then: (List<HaEntity>) -> Unit) {
        lifecycleScope.launch {
            runCatching { homeAssistant.states() }
                .onSuccess { haEntities = it; then(it) }
                .onFailure { toast(it.message ?: t("Home Assistant nicht erreichbar.", "Home Assistant not reachable.")) }
        }
    }

    private fun refreshSmartHome() {
        if (!prefs.haEnabled || !prefs.haConnected) return
        lifecycleScope.launch {
            runCatching { homeAssistant.states() }.onSuccess { all ->
                haEntities = all
                val byId = all.associateBy { it.id }
                smartHome.clear()
                smartHome.addAll(prefs.haTiles.mapNotNull { byId[it] })
            }
        }
    }

    private fun activateEntity(entity: HaEntity) {
        lifecycleScope.launch {
            runCatching { homeAssistant.activate(entity.id) }
                .onFailure { toast(it.message ?: t("Home Assistant nicht erreichbar.", "Home Assistant not reachable.")) }
            refreshSmartHome()
        }
    }

    /** Kino-Modus: Szene beim Start einer gewählten App … */
    private fun startKinoMode(packageName: String) {
        if (!prefs.haEnabled || !prefs.kinoEnabled || packageName !in prefs.kinoApps) return
        prefs.kinoActive = true
        if (prefs.kinoStart.isNotBlank()) {
            lifecycleScope.launch { runCatching { homeAssistant.turnOn(prefs.kinoStart) } }
        }
    }

    /** … und wieder zurück, sobald der Launcher wieder vorne ist. */
    private fun endKinoMode() {
        if (!prefs.kinoActive) return
        prefs.kinoActive = false
        if (prefs.haEnabled && prefs.kinoEnd.isNotBlank()) {
            lifecycleScope.launch { runCatching { homeAssistant.turnOn(prefs.kinoEnd) } }
        }
    }

    // --- Updates ---

    private fun autoCheckUpdate() {
        if (!prefs.autoUpdateCheck) return
        val now = System.currentTimeMillis()
        if (now - prefs.lastUpdateCheck < 6 * 60 * 60 * 1000L) return
        prefs.lastUpdateCheck = now
        lifecycleScope.launch {
            updater.check()?.let { toast(t("Update auf Version ${it.versionName} verfügbar – Einstellungen → System.", "Update to version ${it.versionName} available – Settings → System.")) }
        }
    }

    companion object {
        const val EXTRA_WOKE = "de.glut.launcher.WOKE"
        const val EXTRA_QUICK_ACTIONS = "de.glut.launcher.QUICK_ACTIONS"
        private const val NEW_FOLDER = "\u0000new"
    }
}
