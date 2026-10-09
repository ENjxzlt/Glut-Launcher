package de.glut.launcher.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import de.glut.launcher.MainActivity
import de.glut.launcher.data.AppRepository
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
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        handler.removeCallbacks(longPress)
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

    /** Fire TV schlafen legen (Android 9+). */
    fun sleep(): Boolean = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.packageName?.toString() != AppRepository.AMAZON_LAUNCHER) return
        if (!Prefs.shouldRedirect(this)) return

        // Kommt die Amazon-Startseite kurz nach dem Einschalten, darf die Einschalt-App starten.
        val justWoke = wokeAt > 0 && System.currentTimeMillis() - wokeAt < WAKE_WINDOW_MS
        wokeAt = 0L

        openLauncher(woke = justWoke)
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

        /** Laufender Dienst, z. B. für „Schlafen“ aus dem Schnellmenü. */
        @Volatile
        var instance: HomeRedirectService? = null
            private set

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
