package de.glut.launcher.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
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

    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            wokeAt = System.currentTimeMillis()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        wokeAt = System.currentTimeMillis() // Dienst startet beim Hochfahren
        ContextCompat.registerReceiver(this, screenOn, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOn) }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.packageName?.toString() != AppRepository.AMAZON_LAUNCHER) return
        if (!Prefs.shouldRedirect(this)) return

        // Kommt die Amazon-Startseite kurz nach dem Einschalten, darf die Einschalt-App starten.
        val justWoke = wokeAt > 0 && System.currentTimeMillis() - wokeAt < WAKE_WINDOW_MS
        wokeAt = 0L

        val intent = Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .putExtra(MainActivity.EXTRA_WOKE, justWoke)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        runCatching { startActivity(intent) }
    }

    override fun onInterrupt() = Unit

    companion object {
        private const val WAKE_WINDOW_MS = 60_000L

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
