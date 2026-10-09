package de.glut.launcher.data

import android.graphics.Bitmap

/**
 * Bild der zuletzt gezeigten Startseite. Liegt nur im Arbeitsspeicher; Dienst
 * und Activity laufen im selben Prozess.
 */
object LauncherSnapshot {
    @Volatile
    var bitmap: Bitmap? = null
}
