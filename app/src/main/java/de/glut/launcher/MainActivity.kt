package de.glut.launcher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import de.glut.launcher.data.AppEntry
import de.glut.launcher.data.AppRepository
import de.glut.launcher.data.Prefs
import de.glut.launcher.service.HomeRedirectService
import de.glut.launcher.ui.AppOptions
import de.glut.launcher.ui.AppsScreen
import de.glut.launcher.ui.HomeScreen
import de.glut.launcher.ui.LocalAccent
import de.glut.launcher.ui.QuickMenu
import de.glut.launcher.ui.WallpaperBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Screen { HOME, APPS }

class MainActivity : ComponentActivity() {
    private lateinit var prefs: Prefs
    private lateinit var repository: AppRepository

    private var apps by mutableStateOf<List<AppEntry>>(emptyList())
    private var screen by mutableStateOf(Screen.HOME)
    private var menuOpen by mutableStateOf(false)
    private var optionsFor by mutableStateOf<AppEntry?>(null)
    private var serviceEnabled by mutableStateOf(false)
    private var focusKey by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        repository = AppRepository(this)

        setContent {
            CompositionLocalProvider(LocalAccent provides prefs.accent.color) {
                Box(Modifier.fillMaxSize()) {
                    WallpaperBackground(prefs.wallpaper, prefs.ground, prefs.accent.color, Modifier.fillMaxSize())

                    when (screen) {
                        Screen.HOME -> HomeScreen(
                            apps = apps,
                            prefs = prefs,
                            repository = repository,
                            focusKey = focusKey,
                            onLaunch = ::launchApp,
                            onOptions = { optionsFor = it },
                            onOpenApps = { screen = Screen.APPS },
                            onOpenMenu = { menuOpen = true },
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
                            onOptions = { optionsFor = it },
                        )
                    }

                    if (menuOpen) {
                        QuickMenu(
                            prefs = prefs,
                            serviceEnabled = serviceEnabled,
                            onEditHome = { closeOverlays(); screen = Screen.APPS },
                            onOpenSettings = { closeOverlays(); startSafely(Intent(Settings.ACTION_SETTINGS)) },
                            onOpenAmazonHome = { closeOverlays(); openAmazonHome() },
                        )
                    }

                    optionsFor?.let { app ->
                        AppOptions(
                            app = app,
                            prefs = prefs,
                            onClose = ::closeOverlays,
                            onAppInfo = {
                                startSafely(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                        .setData(Uri.fromParts("package", app.packageName, null)),
                                )
                            },
                            onUninstall = {
                                startSafely(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null)))
                            },
                        )
                    }

                    // Zurück schließt erst Overlays, dann die App-Übersicht. Auf der
                    // Startseite passiert nichts – ein Launcher hat kein „dahinter“.
                    BackHandler {
                        when {
                            optionsFor != null || menuOpen -> closeOverlays()
                            screen != Screen.HOME -> { screen = Screen.HOME; focusKey++ }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        prefs.clearAmazonHomeBypass()
        serviceEnabled = HomeRedirectService.isEnabled(this)
        refreshApps()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home gedrückt, während der Launcher schon offen ist: zurück zur Startseite.
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            optionsFor = null
            menuOpen = false
            screen = Screen.HOME
            focusKey++
        }
    }

    private fun refreshApps() {
        lifecycleScope.launch {
            repository.clearArtCache()
            apps = withContext(Dispatchers.IO) { repository.loadApps() }
        }
    }

    private fun closeOverlays() {
        optionsFor = null
        menuOpen = false
        focusKey++
    }

    private fun launchApp(app: AppEntry) {
        if (startSafely(repository.launchIntent(app))) prefs.recordUse(app.packageName)
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

    private fun startSafely(intent: Intent, quiet: Boolean = false): Boolean =
        try {
            startActivity(intent)
            true
        } catch (e: Exception) {
            if (!quiet) Toast.makeText(this, "Lässt sich auf diesem Gerät nicht öffnen.", Toast.LENGTH_SHORT).show()
            false
        }
}
