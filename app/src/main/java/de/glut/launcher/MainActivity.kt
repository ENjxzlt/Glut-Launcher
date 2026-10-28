package de.glut.launcher

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import de.glut.launcher.data.AppEntry
import de.glut.launcher.data.AppRepository
import de.glut.launcher.data.Folder
import de.glut.launcher.data.HaEntity
import de.glut.launcher.data.HomeAssistant
import de.glut.launcher.data.IconPack
import de.glut.launcher.data.Jellyfin
import de.glut.launcher.data.JellyfinFeed
import de.glut.launcher.data.JellyfinItem
import de.glut.launcher.data.QuickConnectSession
import de.glut.launcher.data.LauncherSnapshot
import de.glut.launcher.data.LoginStep
import de.glut.launcher.data.MediaWatcher
import de.glut.launcher.data.BluetoothControl
import de.glut.launcher.data.BtDevice
import de.glut.launcher.data.DeviceInfo
import de.glut.launcher.data.Prefs
import de.glut.launcher.data.ScreensaverMedia
import de.glut.launcher.data.SystemControl
import de.glut.launcher.data.RootShell
import de.glut.launcher.data.SystemUser
import de.glut.launcher.data.SystemUsers
import de.glut.launcher.data.WifiControl
import de.glut.launcher.data.WifiNetwork
import de.glut.launcher.data.WifiState
import de.glut.launcher.data.Updater
import de.glut.launcher.service.HomeRedirectService
import de.glut.launcher.ui.AppOptions
import de.glut.launcher.ui.AppsScreen
import de.glut.launcher.ui.ColorTheme
import de.glut.launcher.ui.CustomBackgrounds
import de.glut.launcher.ui.FolderOptions
import de.glut.launcher.ui.FolderView
import de.glut.launcher.ui.FontChoice
import de.glut.launcher.ui.HomeScreen
import de.glut.launcher.ui.JellyfinState
import de.glut.launcher.ui.ListPicker
import de.glut.launcher.ui.LocalAccent
import de.glut.launcher.ui.PickItem
import de.glut.launcher.ui.QuickActionsDialog
import de.glut.launcher.ui.SearchScreen
import de.glut.launcher.ui.SettingsAction
import de.glut.launcher.ui.SettingsScreen
import de.glut.launcher.ui.BluetoothScreen
import de.glut.launcher.ui.SettingsPage
import de.glut.launcher.ui.SystemState
import de.glut.launcher.ui.UsersScreen
import de.glut.launcher.ui.WifiScreen
import de.glut.launcher.ui.TextInputDialog
import de.glut.launcher.ui.Theme
import de.glut.launcher.ui.WallpaperBackground
import de.glut.launcher.ui.rememberNow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

private enum class Screen { HOME, APPS, SEARCH, SETTINGS, WIFI, USERS, BLUETOOTH }

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
    /** Glut ist selbst der Standard-Launcher (z. B. Amazons Launcher deaktiviert, Root/ADB). */
    private var onlyLauncher by mutableStateOf(false)
    private var focusKey by mutableIntStateOf(0)
    private val smartHome = mutableStateListOf<HaEntity>()
    private var haEntities by mutableStateOf<List<HaEntity>>(emptyList())
    private var haStatus by mutableStateOf<String?>(null)

    private lateinit var wifi: WifiControl
    private var rootAvailable by mutableStateOf(false)
    private var wifiState by mutableStateOf<WifiState?>(null)
    private var wifiNetworks by mutableStateOf<List<WifiNetwork>>(emptyList())
    private var wifiScanning by mutableStateOf(false)
    private var systemUsers by mutableStateOf<List<SystemUser>?>(null)
    private var adbNetwork by mutableStateOf(false)
    private var systemStatus by mutableStateOf<String?>(null)
    private var settingsPage by mutableStateOf(SettingsPage.ROOT)
    private var rootChecked = false
    private lateinit var jellyfin: Jellyfin
    private var jellyfinFeed by mutableStateOf(JellyfinFeed.CONTINUE)
    private var jellyfinItems by mutableStateOf<Map<JellyfinFeed, List<JellyfinItem>>>(emptyMap())
    private var jellyfinLoading by mutableStateOf(false)
    private var jellyfinError by mutableStateOf<String?>(null)
    private var jellyfinLoadedAt = 0L
    private var quickConnectJob: kotlinx.coroutines.Job? = null
    private var screensaverActive by mutableStateOf(false)
    private var screenTimeoutMinutes by mutableStateOf(5)
    private var screensaverFiles by mutableStateOf(0)
    /** Seiten wie WLAN öffnen sich auch von den oberen Tasten; „Zurück“ führt dann zur Startseite. */
    private var subReturnsHome = false
    private lateinit var bluetooth: BluetoothControl
    private var btEnabled by mutableStateOf(false)
    private var btScanning by mutableStateOf(false)
    private var btDevices by mutableStateOf<List<BtDevice>>(emptyList())
    private var adbDebugging by mutableStateOf(false)
    private var soundEffects by mutableStateOf(true)
    private var volumePercent by mutableStateOf(0)
    private var deviceInfo by mutableStateOf<List<Pair<String, String>>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log("onCreate")
        prefs = Prefs(this)
        Theme.prefs = prefs
        repository = AppRepository(this)
        homeAssistant = HomeAssistant(prefs)
        wifi = WifiControl(this)
        jellyfin = Jellyfin(prefs)
        applyIconPack()
        bluetooth = BluetoothControl(this) {
            btEnabled = bluetooth.enabled
            btScanning = bluetooth.scanning
            btDevices = bluetooth.devices()
        }
        media = MediaWatcher(this)
        updater = Updater(this) { prefs.betaUpdates }

        setContent {
            val now = rememberNow()
            val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
            CompositionLocalProvider(LocalAccent provides prefs.accent.color) {
                Box(Modifier.fillMaxSize()) {
                    val customFile = remember(prefs.customBackground, prefs.wallpaper) { CustomBackgrounds.resolve(this@MainActivity, prefs.customBackground) }
                    WallpaperBackground(prefs.wallpaper, prefs.ground, prefs.accent.color, hour, Modifier.fillMaxSize(), customFile, prefs.backgroundDim)
                    ScreenContent()
                    // key: jedes Fenster bekommt frischen Zustand (z. B. leeres Eingabefeld)
                    overlay?.let { key(it) { OverlayContent(it) } }

                    // Zurück schließt erst Fenster, dann den Bildschirm. Auf der
                    // Startseite passiert nichts – ein Launcher hat kein „dahinter“.
                    BackHandler {
                        when {
                            overlay != null -> closeOverlay()
                            screen == Screen.WIFI || screen == Screen.BLUETOOTH || screen == Screen.USERS -> leaveSubPage()
                            screen == Screen.SETTINGS && settingsPage.parent != null -> navigateSettings(settingsPage.parent!!)
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
                jellyfin = jellyfinState(),
                showAmazonHome = !onlyLauncher,
                onMediaOpen = { media.nowPlaying?.let { launchPackage(it.packageName) } },
                onMediaPlayPause = { media.playPause() },
                onMediaNext = { media.next() },
                onMediaPrevious = { media.previous() },
                onLaunch = ::launchApp,
                onOptions = { overlay = Overlay.AppMenu(it) },
                onOpenFolder = { overlay = Overlay.FolderOpen(it.id) },
                onFolderMenu = { overlay = Overlay.FolderMenu(it.id) },
                onSmartHome = ::activateEntity,
                onJellyfinOpen = ::openJellyfin,
                onJellyfinFeed = { jellyfinFeed = it },
                onJellyfinSetup = { openSettings(SettingsPage.JELLYFIN) },
                onOpenApps = { screen = Screen.APPS },
                onOpenMenu = { openSettings() },
                onOpenSearch = { screen = Screen.SEARCH },
                // Die Systemseiten brauchen Root und sind nur sinnvoll, wenn Glut der Standard-Launcher ist.
                onOpenWifi = { if (onlyLauncher) openWifiPage() else openWifi() },
                onOpenProfile = { if (onlyLauncher) openUsersPage() else openProfiles() },
                onOpenSettings = { if (onlyLauncher) openSettings(SettingsPage.SYSTEM) else startAmazonSettings(Intent(Settings.ACTION_SETTINGS)) },
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
            Screen.WIFI -> WifiScreen(
                focusKey = focusKey,
                state = wifiState,
                networks = wifiNetworks,
                scanning = wifiScanning,
                status = systemStatus,
                onToggle = {
                    wifi.setEnabled(wifiState?.enabled != true)
                    lifecycleScope.launch { kotlinx.coroutines.delay(1500); refreshWifi() }
                },
                onRescan = { lifecycleScope.launch { refreshWifi() } },
                onNetwork = ::onWifiNetwork,
                onForget = { network ->
                    systemStatus = if (wifi.forget(network.ssid)) t("„${network.ssid}“ vergessen.", "Forgot “${network.ssid}”.")
                    else t("Lässt sich nicht vergessen.", "Can't be forgotten.")
                    lifecycleScope.launch { refreshWifi() }
                },
            )
            Screen.USERS -> UsersScreen(
                focusKey = focusKey,
                users = systemUsers,
                rootAvailable = rootAvailable,
                rootMode = prefs.rootMode,
                status = systemStatus,
                onUser = { user ->
                    if (user.current) {
                        systemStatus = t("${user.name} ist schon aktiv.", "${user.name} is already active.")
                    } else {
                        lifecycleScope.launch {
                            systemStatus = if (SystemUsers.switchTo(user.id)) t("Wechsle zu ${user.name} …", "Switching to ${user.name} …")
                            else t("Wechsel fehlgeschlagen.", "Switch failed.")
                        }
                    }
                },
                onRoot = {
                    prefs.rootMode = !prefs.rootMode
                    systemStatus = if (prefs.rootMode) t("Root-Modus an.", "Root mode on.") else t("Root-Modus aus.", "Root mode off.")
                },
            )
            Screen.BLUETOOTH -> BluetoothScreen(
                focusKey = focusKey,
                available = bluetooth.available,
                enabled = btEnabled,
                scanning = btScanning,
                devices = btDevices,
                status = systemStatus,
                onToggle = {
                    bluetooth.setEnabled(!bluetooth.enabled)
                    lifecycleScope.launch { kotlinx.coroutines.delay(1500); btEnabled = bluetooth.enabled; btDevices = bluetooth.devices() }
                },
                onScan = { lifecycleScope.launch { wifi.ensureAccess(); bluetooth.startScan() } },
                onDevice = ::onBluetoothDevice,
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
                page = settingsPage,
                prefs = prefs,
                focusKey = focusKey,
                serviceEnabled = serviceEnabled,
                keyFilterSupported = keyFilterSupported,
                onlyLauncher = onlyLauncher,
                mediaAccess = media.hasAccess,
                haStatus = haStatus,
                updater = updater,
                system = SystemState(
                    rootAvailable = rootAvailable,
                    rootMode = prefs.rootMode,
                    wifiSummary = wifiState?.ssid ?: if (wifiState?.enabled == false) t("WLAN aus", "Wi-Fi off") else t("Nicht verbunden", "Not connected"),
                    bluetoothSummary = if (!btEnabled) t("Aus", "Off") else t("${btDevices.count { it.bonded }} gekoppelt", "${btDevices.count { it.bonded }} paired"),
                    userSummary = systemUsers?.firstOrNull { it.current }?.name ?: "–",
                    adbNetwork = adbNetwork,
                    adbDebugging = adbDebugging,
                    volumePercent = volumePercent,
                    soundEffects = soundEffects,
                    info = deviceInfo,
                    status = systemStatus,
                    screensaverActive = screensaverActive,
                    screensaverFiles = screensaverFiles,
                    screensaverFolder = ScreensaverMedia.dir(this@MainActivity).path.removePrefix("/storage/emulated/0").removePrefix("/sdcard"),
                    screenTimeoutMinutes = screenTimeoutMinutes,
                ),
                appLabel = { pkg -> if (pkg.isBlank()) t("Startseite", "Home") else apps.firstOrNull { it.packageName == pkg }?.label ?: pkg },
                entityLabel = { id -> if (id.isBlank()) t("Nichts", "Nothing") else haEntities.firstOrNull { it.id == id }?.name ?: id },
                onNavigate = ::navigateSettings,
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
                    if (onlyLauncher) manageApp(current.app) else startSafely(
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
                onlyLauncher = onlyLauncher,
                onSleep = {
                    closeOverlay()
                    sleepNow()
                },
                onSettings = { overlay = null; openSettings() },
                onFireSettings = { closeOverlay(); if (onlyLauncher) openSettings(SettingsPage.SYSTEM) else startAmazonSettings(Intent(Settings.ACTION_SETTINGS)) },
                onAmazonHome = { closeOverlay(); openAmazonHome() },
                onRestart = { closeOverlay(); restart() },
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
        onlyLauncher = isDefaultHome()
        // Ohne Amazons Einstellungen läuft alles Systemnahe über Root: einmal früh prüfen.
        if (onlyLauncher && !rootChecked) {
            rootChecked = true
            lifecycleScope.launch { rootAvailable = RootShell.available() }
        }
        if (appsDirty) refreshApps()
        media.start()
        endKinoMode()
        refreshSmartHome()
        refreshJellyfin()
        autoCheckUpdate()
    }

    private fun isDefaultHome(): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val info = packageManager.resolveActivity(home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        return info?.activityInfo?.packageName == packageName
    }

    /**
     * Ist Glut der einzige Launcher, bekommt das System die lange Home-Taste und das
     * Schnellmenü ist sonst nicht erreichbar: Menü-Taste (☰) öffnet es stattdessen.
     * Kacheln behandeln ☰ selbst (App-Optionen); hier landet nur, was frei ist.
     */
    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_MENU && onlyLauncher && overlay == null) {
            overlay = Overlay.QuickActions
            focusKey++
            return true
        }
        return super.onKeyUp(keyCode, event)
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
        bluetooth.stop()
        quickConnectJob?.cancel()
        media.stop()
        super.onDestroy()
    }

    // --- Navigation ---

    private fun goHome() {
        // Nach Änderungen in den Einstellungen (z. B. Geräteauswahl) die Smart-Home-Leiste neu laden.
        if (screen == Screen.SETTINGS) refreshSmartHome()
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
        // Nach dem Hochfahren können Dienst und Boot-Empfänger beide melden; nur einmal reagieren.
        val now = SystemClock.elapsedRealtime()
        if (now - lastWakeHandled < 30_000L) return
        lastWakeHandled = now
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
        if (!startAmazonSettings(Intent(Settings.ACTION_SYNC_SETTINGS), quiet = true)) {
            startAmazonSettings(Intent(Settings.ACTION_SETTINGS))
        }
    }

    /**
     * Apps dürfen nicht selbst neu starten. Wir öffnen das System-Einschaltmenü über den
     * Bedienungshilfen-Dienst, sonst „Mein Fire TV“ mit dem Neustart-Eintrag.
     */
    private fun restart() {
        if (onlyLauncher && prefs.rootMode && rootAvailable) {
            lifecycleScope.launch { RootShell.run("reboot") }
            return
        }
        if (HomeRedirectService.instance?.powerDialog() == true) return
        openSettingsPage(
            action = "",
            keywords = listOf("MyFireTv", "MyFireTV", "DeviceAndSoftware", "AboutActivity", "SystemActivity"),
            hint = t("Neu starten: Mein Fire TV → Neu starten.", "To restart: My Fire TV → Restart."),
            alwaysHint = true,
        )
    }

    private fun sleepNow() {
        if (onlyLauncher && prefs.rootMode && rootAvailable) {
            lifecycleScope.launch { RootShell.run("input keyevent 223") }
        } else if (HomeRedirectService.instance?.sleep() != true) {
            toast(t("Schlafen wird auf diesem Gerät nicht unterstützt.", "Sleep is not supported on this device."))
        }
    }

    // --- System (Root) ---

    private val systemPages = setOf(
        SettingsPage.SYSTEM, SettingsPage.NETWORK, SettingsPage.SOUND, SettingsPage.APPS,
        SettingsPage.ENERGY, SettingsPage.INFO, SettingsPage.DEVELOPER, SettingsPage.SCREENSAVER,
    )

    private fun openSettings(page: SettingsPage = SettingsPage.ROOT) {
        screen = Screen.SETTINGS
        navigateSettings(page)
    }

    private fun navigateSettings(page: SettingsPage) {
        settingsPage = page
        systemStatus = null
        focusKey++
        if (onlyLauncher && page in systemPages) refreshSystem()
    }

    /** Liest Root, Benutzer, Lautstärke usw. neu ein, wenn eine Systemseite aufgeht. */
    private fun refreshSystem() {
        volumePercent = SystemControl.volumePercent(this)
        adbDebugging = SystemControl.adbDebugging(this)
        soundEffects = SystemControl.soundEffects(this)
        screensaverActive = SystemControl.screensaverComponent(this)?.contains(packageName) == true
        screenTimeoutMinutes = SystemControl.screenTimeoutMinutes(this)
        screensaverFiles = ScreensaverMedia.list(this).size
        btEnabled = bluetooth.enabled
        btDevices = bluetooth.devices()
        lifecycleScope.launch {
            rootAvailable = RootShell.available()
            if (rootAvailable) {
                systemUsers = SystemUsers.list()
                adbNetwork = SystemUsers.adbOverNetwork()
            }
            wifi.ensureAccess()
            wifiState = wifi.state()
            deviceInfo = DeviceInfo.collect(this@MainActivity, wifiState?.ip, updater.currentVersion)
        }
    }

    private fun leaveSubPage() {
        bluetooth.stop()
        if (subReturnsHome) {
            goHome()
        } else {
            screen = Screen.SETTINGS
            focusKey++
            if (onlyLauncher) refreshSystem()
        }
    }

    private fun openWifiPage() {
        systemStatus = null
        subReturnsHome = screen == Screen.HOME
        settingsPage = SettingsPage.NETWORK
        screen = Screen.WIFI
        focusKey++
        lifecycleScope.launch { refreshWifi() }
    }

    private suspend fun refreshWifi() {
        wifiScanning = true
        wifiState = wifi.state()
        wifiNetworks = if (wifiState?.enabled == true) wifi.scan() else emptyList()
        wifiState = wifi.state()
        wifiScanning = false
    }

    private fun onWifiNetwork(network: WifiNetwork) {
        if (network.connected) return
        if (network.secure && !network.saved) {
            overlay = Overlay.Input(
                title = t("Passwort für „${network.ssid}“", "Password for “${network.ssid}”"),
                initial = "",
                hint = t("WLAN-Passwort", "Wi-Fi password"),
                password = true,
            ) { password -> if (password.isNotBlank()) connectWifi(network, password) }
        } else {
            connectWifi(network, null)
        }
    }

    private fun connectWifi(network: WifiNetwork, password: String?) {
        systemStatus = t("Verbinde mit „${network.ssid}“ …", "Connecting to “${network.ssid}” …")
        lifecycleScope.launch {
            val started = wifi.connect(network, password)
            kotlinx.coroutines.delay(5000)
            refreshWifi()
            systemStatus = if (wifiState?.ssid == network.ssid) t("Verbunden.", "Connected.")
            else if (started) t("Noch nicht verbunden – Passwort prüfen.", "Not connected yet – check the password.")
            else t("Verbindung nicht möglich.", "Couldn't connect.")
        }
    }

    private fun openUsersPage() {
        systemStatus = null
        subReturnsHome = screen == Screen.HOME
        settingsPage = SettingsPage.SYSTEM
        screen = Screen.USERS
        focusKey++
        lifecycleScope.launch {
            rootAvailable = RootShell.available()
            if (rootAvailable) systemUsers = SystemUsers.list()
        }
    }

    private fun openBluetoothPage() {
        systemStatus = null
        subReturnsHome = screen == Screen.HOME
        settingsPage = SettingsPage.NETWORK
        screen = Screen.BLUETOOTH
        focusKey++
        btEnabled = bluetooth.enabled
        btDevices = bluetooth.devices()
        lifecycleScope.launch { wifi.ensureAccess(); bluetooth.startScan() }
    }

    private fun onBluetoothDevice(device: BtDevice) {
        systemStatus = if (device.bonded) {
            if (bluetooth.unpair(device.address)) t("„${device.name}“ entkoppelt.", "Unpaired “${device.name}”.")
            else t("Entkoppeln nicht möglich.", "Couldn't unpair.")
        } else {
            if (bluetooth.pair(device.address)) t("Koppeln mit „${device.name}“ … am Gerät bestätigen.", "Pairing with “${device.name}” … confirm on the device.")
            else t("Koppeln nicht möglich.", "Couldn't pair.")
        }
    }

    // --- Apps verwalten (ersetzt die App-Info von Amazons Einstellungen) ---

    private fun manageApp(app: AppEntry) {
        if (!rootAvailable) {
            toast(t("Apps verwalten braucht Root.", "Managing apps needs root."))
            return
        }
        overlay = Overlay.Picker(
            title = app.label,
            items = listOf(
                PickItem("stop", t("Beenden", "Force stop")),
                PickItem("cache", t("Cache löschen", "Clear cache")),
                PickItem("data", t("Daten löschen", "Clear data"), t("Setzt die App zurück", "Resets the app")),
                PickItem("uninstall", t("Deinstallieren", "Uninstall")),
            ),
            isSelected = { false },
            onSelect = { action ->
                overlay = null
                val pkg = app.packageName
                lifecycleScope.launch {
                    val ok = when (action) {
                        "stop" -> SystemControl.forceStop(pkg)
                        "cache" -> SystemControl.clearCache(pkg)
                        "data" -> SystemControl.clearData(pkg)
                        else -> SystemControl.uninstall(pkg)
                    }
                    systemStatus = if (ok) t("„${app.label}“: erledigt.", "“${app.label}”: done.") else t("„${app.label}“: fehlgeschlagen.", "“${app.label}”: failed.")
                    toast(systemStatus.orEmpty())
                }
            },
        )
    }

    // --- Jellyfin ---

    private fun applyIconPack() {
        val pack = prefs.iconPack.takeIf { it.isNotBlank() }?.let { runCatching { IconPack(this, it) }.getOrNull() }
        repository.setIconPack(pack)
    }

    private fun jellyfinFeeds() = listOfNotNull(
        JellyfinFeed.CONTINUE.takeIf { prefs.jellyfinContinue },
        JellyfinFeed.NEW.takeIf { prefs.jellyfinNew },
        JellyfinFeed.RECOMMENDED.takeIf { prefs.jellyfinRecommended },
    )

    private fun jellyfinState(): JellyfinState {
        val feeds = jellyfinFeeds()
        // Ist die gewählte Liste leer (z. B. nichts zum Weiterschauen), zeigen wir die erste mit Inhalt.
        val feed = feeds.firstOrNull { it == jellyfinFeed && jellyfinItems[it].orEmpty().isNotEmpty() }
            ?: feeds.firstOrNull { jellyfinItems[it].orEmpty().isNotEmpty() }
            ?: feeds.firstOrNull()
            ?: jellyfinFeed
        return JellyfinState(
            enabled = prefs.jellyfinEnabled,
            connected = prefs.jellyfinConnected,
            feeds = feeds,
            feed = feed,
            items = jellyfinItems[feed].orEmpty(),
            loading = jellyfinLoading,
            error = jellyfinError,
        )
    }

    /** Lädt die Vorschläge neu, höchstens alle fünf Minuten (oder sofort mit [force]). */
    private fun refreshJellyfin(force: Boolean = false) {
        if (!prefs.jellyfinEnabled || !prefs.jellyfinConnected || jellyfinLoading) return
        val fresh = SystemClock.elapsedRealtime() - jellyfinLoadedAt < 5 * 60_000L
        if (!force && fresh && jellyfinItems.values.any { it.isNotEmpty() }) return
        jellyfinLoading = true
        jellyfinError = null
        lifecycleScope.launch {
            val loaded = mutableMapOf<JellyfinFeed, List<JellyfinItem>>()
            var failure: String? = null
            for (feed in jellyfinFeeds()) {
                runCatching { jellyfin.items(feed, prefs.jellyfinCount) }
                    .onSuccess { loaded[feed] = it }
                    .onFailure { failure = it.message ?: t("Jellyfin nicht erreichbar", "Jellyfin not reachable") }
            }
            jellyfinItems = loaded
            jellyfinError = if (loaded.values.all { it.isEmpty() }) failure else null
            jellyfinLoadedAt = SystemClock.elapsedRealtime()
            jellyfinLoading = false
        }
    }

    /** Öffnet die Jellyfin-App (eine feste Verknüpfung zu einem Titel gibt es dort nicht). */
    private fun openJellyfin(item: JellyfinItem) {
        val pkg = apps.firstOrNull { it.packageName.contains("jellyfin", ignoreCase = true) || it.label.contains("Jellyfin", ignoreCase = true) }
        if (pkg != null) launchApp(pkg) else toast(t("Jellyfin-App nicht gefunden: „${item.title}“", "Jellyfin app not found: “${item.title}”"))
    }

    private fun startJellyfinLogin() {
        if (prefs.jellyfinUrl.isBlank()) {
            toast(t("Bitte zuerst die Server-Adresse eingeben.", "Please enter the server address first."))
            return
        }
        overlay = Overlay.Input(t("Benutzername", "Username"), "", t("Jellyfin-Benutzer", "Jellyfin user")) { user ->
            overlay = Overlay.Input(t("Passwort", "Password"), "", t("Passwort", "Password"), password = true) { password ->
                systemStatus = t("Melde an …", "Signing in …")
                lifecycleScope.launch {
                    systemStatus = runCatching { jellyfin.login(prefs.jellyfinUrl, user, password) }
                        .fold({ t("Angemeldet.", "Signed in.") }, { it.message ?: t("Anmeldung fehlgeschlagen.", "Sign-in failed.") })
                    refreshJellyfin(force = true)
                }
            }
        }
    }

    /** Quick Connect: Code anzeigen, im Hintergrund auf die Bestätigung warten (bis zu drei Minuten). */
    private fun startQuickConnect() {
        if (prefs.jellyfinUrl.isBlank()) {
            toast(t("Bitte zuerst die Server-Adresse eingeben.", "Please enter the server address first."))
            return
        }
        quickConnectJob?.cancel()
        quickConnectJob = lifecycleScope.launch {
            val session: QuickConnectSession = runCatching { jellyfin.quickConnectStart(prefs.jellyfinUrl) }.getOrElse {
                systemStatus = t("Quick Connect nicht möglich: ", "Quick Connect failed: ") + (it.message ?: "")
                return@launch
            }
            val dialog = Overlay.Picker(
                title = t("Quick-Connect-Code", "Quick Connect code") + ": " + session.code,
                subtitle = t(
                    "In einer angemeldeten Jellyfin-App: Profil → Quick Connect, dann diesen Code eingeben.",
                    "In a signed-in Jellyfin app: profile → Quick Connect, then enter this code.",
                ),
                items = listOf(PickItem("cancel", t("Abbrechen", "Cancel"))),
                isSelected = { false },
                onSelect = { quickConnectJob?.cancel(); overlay = null },
            )
            overlay = dialog
            repeat(60) {
                kotlinx.coroutines.delay(3000)
                val done = runCatching { jellyfin.quickConnectPoll(prefs.jellyfinUrl, session) }.getOrDefault(false)
                if (done) {
                    if (overlay === dialog) overlay = null
                    systemStatus = t("Angemeldet als ${prefs.jellyfinUserName}.", "Signed in as ${prefs.jellyfinUserName}.")
                    refreshJellyfin(force = true)
                    return@launch
                }
            }
            if (overlay === dialog) overlay = null
            systemStatus = t("Quick Connect abgelaufen.", "Quick Connect expired.")
        }
    }

    private fun toggleScreensaver() {
        if (!rootAvailable) {
            systemStatus = t("Den Bildschirmschoner ersetzen braucht Root.", "Replacing the screensaver needs root.")
            return
        }
        lifecycleScope.launch {
            val current = SystemControl.screensaverComponent(this@MainActivity).orEmpty()
            val ok = if (current.contains(packageName)) {
                SystemControl.setScreensaver(prefs.screensaverPrevious.ifBlank { SystemControl.AMAZON_DREAM })
            } else {
                prefs.screensaverPrevious = current
                SystemControl.setScreensaver(SystemControl.DREAM_COMPONENT)
            }
            screensaverActive = SystemControl.screensaverComponent(this@MainActivity)?.contains(packageName) == true
            if (!ok) systemStatus = t("Umstellen fehlgeschlagen.", "Switching failed.")
        }
    }

    private fun toggleAdbNetwork() {
        if (!rootAvailable) {
            systemStatus = t("ADB über Netzwerk braucht Root.", "ADB over network needs root.")
            return
        }
        lifecycleScope.launch {
            val target = !adbNetwork
            SystemUsers.setAdbOverNetwork(target)
            adbNetwork = SystemUsers.adbOverNetwork()
        }
    }

    private fun openBluetooth() = openSettingsPage(
        action = Settings.ACTION_BLUETOOTH_SETTINGS,
        keywords = listOf("ControllersAndBluetooth", "BluetoothSettings", "Bluetooth", "Controllers"),
        hint = t(
            "Bluetooth findest du in den Fire-TV-Einstellungen unter „Controller und Bluetooth-Geräte“.",
            "Bluetooth is under “Controllers & Bluetooth devices” in the Fire TV settings.",
        ),
    )

    private fun openWifi() = openSettingsPage(
        action = Settings.ACTION_WIFI_SETTINGS,
        keywords = listOf("NetworkActivity", "WifiSettings", "Network", "Wifi"),
        hint = t(
            "WLAN findest du in den Fire-TV-Einstellungen unter „Netzwerk“.",
            "Wi-Fi is under “Network” in the Fire TV settings.",
        ),
    )

    /**
     * Fire OS kennt viele Standard-Android-Einstellungsseiten nicht. Wir versuchen zuerst
     * die Standard-Adresse, suchen dann in Amazons Einstellungs-Paketen nach einer passenden
     * öffentlichen Seite und öffnen sonst die allgemeinen Einstellungen mit einem Hinweis.
     */
    private fun openSettingsPage(action: String, keywords: List<String>, hint: String, alwaysHint: Boolean = false) {
        if (alwaysHint) toast(hint)
        if (action.isNotEmpty()) {
            val standard = Intent(action)
            if (standard.resolveActivity(packageManager) != null && startAmazonSettings(standard, quiet = true)) return
        }
        val component = findAmazonSettingsActivity(keywords)
        if (component != null && startAmazonSettings(Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), quiet = true)) return
        if (alwaysHint) {
            startAmazonSettings(Intent(Settings.ACTION_SETTINGS))
            return
        }
        toast(hint)
        startAmazonSettings(Intent(Settings.ACTION_SETTINGS))
    }

    private fun findAmazonSettingsActivity(keywords: List<String>): ComponentName? {
        val activities = AMAZON_SETTINGS_PACKAGES.flatMap { pkg ->
            runCatching { packageManager.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES).activities?.toList() }
                .getOrNull().orEmpty()
                .filter { it.exported && it.enabled }
                .map { pkg to it.name }
        }
        // Stichwörter in Reihenfolge: das spezifischste zuerst.
        for (keyword in keywords) {
            val match = activities.firstOrNull { (_, name) ->
                name.substringAfterLast('.').contains(keyword, ignoreCase = true) && !name.contains("Dialog", ignoreCase = true)
            } ?: continue
            Log.i(HomeRedirectService.TAG, "Settings page for $keyword: ${match.first}/${match.second}")
            return ComponentName(match.first, match.second)
        }
        return null
    }

    /**
     * Amazon öffnet seine Einstellungen teils über die eigene Startseite (HomeActivity).
     * Damit die Umleitung das nicht sofort wieder schließt, pausiert sie kurz.
     */
    private fun startAmazonSettings(intent: Intent, quiet: Boolean = false): Boolean {
        prefs.allowAmazonHomeFor(SETTINGS_GRACE_MS)
        val started = startSafely(intent, quiet)
        if (!started) prefs.clearAmazonHomeBypass()
        return started
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
            SettingsAction.OPEN_WIFI -> openWifiPage()
            SettingsAction.OPEN_BLUETOOTH -> openBluetoothPage()
            SettingsAction.OPEN_USERS -> openUsersPage()
            SettingsAction.SLEEP -> sleepNow()
            SettingsAction.TOGGLE_ROOT_MODE -> {
                prefs.rootMode = !prefs.rootMode
                if (prefs.rootMode && !rootAvailable) systemStatus = t("Kein Root gefunden – der Modus greift erst mit Root.", "No root found – the mode only works with root.")
            }
            SettingsAction.TOGGLE_ADB_NET -> toggleAdbNetwork()
            SettingsAction.TOGGLE_ADB_DEBUG -> lifecycleScope.launch {
                if (!rootAvailable) {
                    systemStatus = t("ADB-Debugging ändern braucht Root.", "Changing ADB debugging needs root.")
                } else {
                    SystemControl.setAdbDebugging(!adbDebugging)
                    adbDebugging = SystemControl.adbDebugging(this@MainActivity)
                }
            }
            SettingsAction.TOGGLE_SOUND_FX -> lifecycleScope.launch {
                if (!rootAvailable) {
                    systemStatus = t("Systemtöne ändern braucht Root.", "Changing system sounds needs root.")
                } else {
                    SystemControl.setSoundEffects(!soundEffects)
                    soundEffects = SystemControl.soundEffects(this@MainActivity)
                }
            }
            SettingsAction.ICON_PACK -> {
                val packs = IconPack.installed(this)
                overlay = Overlay.Picker(
                    title = t("Icon-Pack", "Icon pack"),
                    subtitle = if (packs.isEmpty()) t("Kein Icon-Pack installiert. Installiere eines (ADW/Nova-Format), dann erscheint es hier.", "No icon pack installed. Install one (ADW/Nova format) and it shows up here.") else null,
                    items = listOf(PickItem("", t("Standard (Symbole der Apps)", "Default (app icons)"))) + packs.map { PickItem(it.packageName, it.label) },
                    isSelected = { it == prefs.iconPack },
                    onSelect = { pkg ->
                        prefs.iconPack = pkg
                        applyIconPack()
                        overlay = null
                    },
                )
            }
            SettingsAction.JELLYFIN_URL -> overlay = Overlay.Input(
                title = t("Jellyfin-Adresse", "Jellyfin address"),
                initial = prefs.jellyfinUrl,
                hint = t("z. B. 192.168.1.20:8096", "e.g. 192.168.1.20:8096"),
            ) { url ->
                if (url.isNotBlank()) lifecycleScope.launch {
                    systemStatus = t("Prüfe Server …", "Checking server …")
                    systemStatus = runCatching { jellyfin.ping(url) }
                        .onSuccess { prefs.jellyfinUrl = Jellyfin.normalize(url) }
                        .fold({ t("Erreicht: $it.", "Reached: $it.") }, { it.message ?: t("Server nicht erreichbar.", "Server not reachable.") })
                }
            }
            SettingsAction.JELLYFIN_LOGIN -> startJellyfinLogin()
            SettingsAction.JELLYFIN_QUICK -> startQuickConnect()
            SettingsAction.JELLYFIN_LOGOUT -> {
                prefs.jellyfinLogout()
                jellyfinItems = emptyMap()
                systemStatus = t("Abgemeldet.", "Signed out.")
            }
            SettingsAction.SCREENSAVER_TOGGLE -> toggleScreensaver()
            SettingsAction.SCREENSAVER_TIMEOUT -> lifecycleScope.launch {
                if (!rootAvailable) {
                    systemStatus = t("Die Zeit ändern braucht Root.", "Changing the delay needs root.")
                } else {
                    val steps = listOf(1, 2, 5, 10, 15, 30)
                    val next = steps.firstOrNull { it > screenTimeoutMinutes } ?: steps.first()
                    SystemControl.setScreenTimeoutMinutes(next)
                    screenTimeoutMinutes = SystemControl.screenTimeoutMinutes(this@MainActivity)
                }
            }
            SettingsAction.SCREENSAVER_PREVIEW -> lifecycleScope.launch {
                if (!rootAvailable) systemStatus = t("Die Vorschau braucht Root.", "The preview needs root.")
                else if (!SystemControl.previewScreensaver()) systemStatus = t("Vorschau fehlgeschlagen.", "Preview failed.")
            }
            SettingsAction.CYCLE_VOLUME -> volumePercent = SystemControl.cycleVolume(this)
            SettingsAction.CHECK_ROOT -> lifecycleScope.launch {
                rootAvailable = RootShell.available()
                systemStatus = if (rootAvailable) t("Root-Zugriff funktioniert.", "Root access works.")
                else t("Kein Root-Zugriff. Hat dein Root-Manager Glut freigegeben?", "No root access. Did your root manager grant Glut?")
            }
            SettingsAction.PICK_MANAGED_APP -> overlay = appPicker(t("App verwalten", "Manage app"), allowNone = false, { "" }) { pkg ->
                apps.firstOrNull { it.packageName == pkg }?.let(::manageApp)
            }
            SettingsAction.BACKGROUND_FILE -> overlay = Overlay.Picker(
                title = t("Eigener Hintergrund", "Custom background"),
                items = listOf(PickItem("", t("Keiner (Glut-Hintergrund)", "None (Glut background)"))) +
                    CustomBackgrounds.list(this).map {
                        PickItem(it.name, it.name, if (CustomBackgrounds.isVideo(it)) t("Video", "Video") else t("Bild", "Image"))
                    },
                isSelected = { it == prefs.customBackground },
                onSelect = { name -> prefs.customBackground = name },
                subtitle = t("Dateien liegen in ${CustomBackgrounds.dir(this).path}", "Files go in ${CustomBackgrounds.dir(this).path}"),
            )
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
                    onSelect = { id ->
                        prefs.haTiles = if (id in prefs.haTiles) prefs.haTiles - id else prefs.haTiles + id
                        refreshSmartHome()
                    },
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
            SettingsAction.UNKNOWN_SOURCES -> if (onlyLauncher && rootAvailable) {
                lifecycleScope.launch {
                    systemStatus = if (SystemControl.allowInstalls(packageName)) t("Installation erlaubt.", "Installing allowed.") else t("Fehlgeschlagen.", "Failed.")
                }
            } else {
                val ok = startSafely(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")),
                    quiet = true,
                )
                if (!ok) toast(t("Fire-TV-Einstellungen → Mein Fire TV → Entwickleroptionen → Apps unbekannter Herkunft → Glut Launcher erlauben.", "Fire TV settings → My Fire TV → Developer options → Apps from unknown sources → allow Glut Launcher."))
            }
            SettingsAction.FIRE_SETTINGS -> startAmazonSettings(Intent(Settings.ACTION_SETTINGS))
            SettingsAction.AMAZON_HOME -> openAmazonHome()
            SettingsAction.REBOOT -> restart()
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
            }.onFailure {
                haStatus = it.message ?: t("Home Assistant nicht erreichbar.", "Home Assistant not reachable.")
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

        private var lastWakeHandled = -60_000L
        private const val SETTINGS_GRACE_MS = 5_000L

        private val AMAZON_SETTINGS_PACKAGES = listOf(
            "com.amazon.tv.settings.v2",
            "com.amazon.tv.settings",
            AppRepository.AMAZON_LAUNCHER,
        )
        private const val NEW_FOLDER = "\u0000new"
    }
}
