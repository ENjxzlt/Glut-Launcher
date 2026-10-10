package de.glut.launcher.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import de.glut.launcher.MainActivity
import de.glut.launcher.data.AppRepository
import de.glut.launcher.data.LauncherSnapshot
import de.glut.launcher.data.Prefs

/**
 * Fire OS lässt keinen anderen Standard-Launcher zu. Dieser Dienst bemerkt,
 * wenn die Amazon-Startseite in den Vordergrund kommt (Home-Taste, Einschalten,
 * Aufwachen), und öffnet sofort Glut Launcher darüber.
 *
 * Er bekommt laut Konfiguration nur Ereignisse von com.amazon.tv.launcher und
 * liest keine Bildschirminhalte.
 */
class HomeRedirectService : AccessibilityService() {

    /** Zeitpunkt des letzten Einschaltens; 0 = schon verarbeitet. */
    private var wokeAt = 0L

    private val handler = Handler(Looper.getMainLooper())
    private var homeLongPressed = false
    private val longPress = Runnable {
        homeLongPressed = true
        openLauncher(quickActions = true)
    }

    /** Sofortbild über der Amazon-Startseite, bis Glut wirklich da ist. */
    private var cover: View? = null
    private val hideCoverRunnable = Runnable { hideCover() }
    private var lastLaunchAt = 0L

    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            wokeAt = System.currentTimeMillis()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        wokeAt = System.currentTimeMillis() // Dienst startet beim Hochfahren
        ContextCompat.registerReceiver(this, screenOn, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED)
        instance = this

        // Beim Hochfahren zeigt Fire OS die Amazon-Startseite, bevor dieser Dienst läuft;
        // das Ereignis dafür verpassen wir. Kurz nach dem Booten öffnen wir Glut daher selbst.
        // Dasselbe nach einem Update von Glut: Android startet den Dienst neu, während
        // schon die Amazon-Startseite zu sehen ist.
        val justBooted = SystemClock.elapsedRealtime() < BOOT_WINDOW_MS
        val justUpdated = runCatching {
            System.currentTimeMillis() - packageManager.getPackageInfo(packageName, 0).lastUpdateTime < UPDATE_WINDOW_MS
        }.getOrDefault(false)
        if ((justBooted || justUpdated) && Prefs.shouldRedirect(this)) {
            handler.postDelayed({
                wokeAt = 0L
                lastLaunchAt = SystemClock.uptimeMillis()
                openLauncher(woke = justBooted)
            }, BOOT_DELAY_MS)
        }
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        handler.removeCallbacks(longPress)
        hideCover()
        runCatching { unregisterReceiver(screenOn) }
        super.onDestroy()
    }

    /**
     * Schnelle Home-Taste: Wir fangen die Taste ab, bevor Fire OS die
     * Amazon-Startseite lädt. Kurz drücken öffnet Glut, lang drücken das
     * Glut-Schnellmenü (Amazons eigenes Menü ist dann nicht mehr erreichbar).
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        if (!Prefs.fastHome(this) || !Prefs.shouldRedirect(this)) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                homeLongPressed = false
                handler.postDelayed(longPress, LONG_PRESS_MS)
            }
            KeyEvent.ACTION_UP -> {
                handler.removeCallbacks(longPress)
                if (!homeLongPressed) openLauncher()
            }
        }
        return true
    }

    /** System-Einschaltmenü (Neu starten / Ausschalten), sofern Fire OS es anbietet. */
    fun powerDialog(): Boolean = performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)

    /** Fire TV schlafen legen (Android 9+). */
    fun sleep(): Boolean = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.packageName?.toString() != AppRepository.AMAZON_LAUNCHER) return
        Log.i(TAG, "Amazon window: class=${event.className} redirect=${Prefs.shouldRedirect(this)}")
        // Im selben Paket stecken auch Amazons Einstellungen; nur die Startseite umleiten.
        if (!isAmazonHome(event.className?.toString())) return
        if (!Prefs.shouldRedirect(this)) return
        // Zeitmessung: eventTime ist der Zeitpunkt des Ereignisses, uptime der Empfang.
        Log.i(TAG, "Amazon home reported: event=${event.eventTime} received=${SystemClock.uptimeMillis()} class=${event.className}")

        // Kommt die Amazon-Startseite kurz nach dem Einschalten, darf die Einschalt-App starten.
        val justWoke = wokeAt > 0 && System.currentTimeMillis() - wokeAt < WAKE_WINDOW_MS
        wokeAt = 0L

        showCover()
        // Fire OS meldet die Amazon-Startseite oft zweimal; ein Start genügt.
        val now = SystemClock.uptimeMillis()
        if (now - lastLaunchAt < APP_SWITCH_BLOCK_MS && !justWoke) return
        lastLaunchAt = now
        openLauncher(woke = justWoke)
    }

    /**
     * Nach der Home-Taste sperrt Android App-Starts für etwa 5 Sekunden
     * (nur die System-Startseite ist ausgenommen). Ein Overlay des Dienstes
     * unterliegt dieser Sperre nicht: Wir zeigen sofort das zuletzt gemerkte
     * Bild der Glut-Startseite, bis die Activity tatsächlich erscheint.
     */
    private fun showCover() {
        if (cover != null || !Prefs.instantCover(this)) return
        val bitmap = LauncherSnapshot.bitmap ?: return
        val view = ImageView(this).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_XY
            isFocusable = true
            isFocusableInTouchMode = true
            // Tasten schlucken, damit nichts versehentlich bei Amazon landet.
            setOnKeyListener { _, _, _ -> true }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.OPAQUE,
        )
        runCatching {
            getSystemService(WindowManager::class.java).addView(view, params)
            view.requestFocus()
            cover = view
            Log.i(TAG, "Snapshot shown uptime=${SystemClock.uptimeMillis()}")
            // Sicherheitsnetz, falls Glut aus irgendeinem Grund nicht erscheint.
            handler.removeCallbacks(hideCoverRunnable)
            handler.postDelayed(hideCoverRunnable, COVER_TIMEOUT_MS)
        }.onFailure { Log.w(TAG, "Snapshot not possible: ${it.message}") }
    }

    fun hideCover() {
        handler.removeCallbacks(hideCoverRunnable)
        val view = cover ?: return
        cover = null
        runCatching { getSystemService(WindowManager::class.java).removeViewImmediate(view) }
    }

    private fun openLauncher(woke: Boolean = false, quickActions: Boolean = false) {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .putExtra(MainActivity.EXTRA_WOKE, woke)
            .putExtra(MainActivity.EXTRA_QUICK_ACTIONS, quickActions)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        runCatching { startActivity(intent) }
    }

    override fun onInterrupt() = Unit

    companion object {
        private const val WAKE_WINDOW_MS = 60_000L
        private const val LONG_PRESS_MS = 650L
        private const val APP_SWITCH_BLOCK_MS = 6_000L
        private const val COVER_TIMEOUT_MS = 8_000L
        private const val BOOT_WINDOW_MS = 3 * 60_000L
        private const val BOOT_DELAY_MS = 1_500L
        private const val UPDATE_WINDOW_MS = 2 * 60_000L

        /**
         * Startseite von Fire OS 7 ist z. B. com.amazon.tv.launcher.ui.HomeActivity_vNext.
         * Einstellungen, Profilauswahl usw. laufen im selben Paket und bleiben unberührt.
         */
        fun isAmazonHome(className: String?): Boolean =
            className != null && className.contains("Home") && !className.contains("Setting", ignoreCase = true)
        const val TAG = "Glut"

        /** Laufender Dienst, z. B. für „Schlafen“ aus dem Schnellmenü. */
        @Volatile
        var instance: HomeRedirectService? = null
            private set

        /**
         * Darf der Dienst Tasten abfangen? Fire OS streicht diese Berechtigung auf
         * manchen Geräten (dumpsys zeigt dann capabilities=0).
         */
        fun canFilterKeys(): Boolean {
            val info = instance?.serviceInfo ?: return false
            return (info.capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_REQUEST_FILTER_KEY_EVENTS) != 0
        }

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val me = ComponentName(context, HomeRedirectService::class.java)
            return enabled.split(':').any { entry ->
                ComponentName.unflattenFromString(entry) == me
            }
        }
    }
}
