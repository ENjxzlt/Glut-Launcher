package de.glut.launcher.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.glut.launcher.MainActivity
import de.glut.launcher.data.Prefs

/**
 * Zweiter Weg zum Autostart nach dem Hochfahren und nach einem Update von Glut. Auf Fire OS 7 (Android 9) darf ein
 * Boot-Empfänger noch eine Activity starten; auf neueren Versionen übernimmt das
 * der Bedienungshilfen-Dienst.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val booted = intent.action == Intent.ACTION_BOOT_COMPLETED
        val updated = intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (!booted && !updated) return
        if (!Prefs.shouldRedirect(context)) return
        val start = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .putExtra(MainActivity.EXTRA_WOKE, booted)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(start) }
    }
}
