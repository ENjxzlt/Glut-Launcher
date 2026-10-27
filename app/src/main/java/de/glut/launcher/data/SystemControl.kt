package de.glut.launcher.data

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import kotlin.math.ceil

/** Systemeinstellungen, die sonst nur Amazons Einstellungs-App ändert. Schreiben geht über Root. */
object SystemControl {
    private fun audio(context: Context) = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun volumePercent(context: Context): Int {
        val am = audio(context)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
    }

    /** Erhöht in fünf Schritten, springt am Ende auf leise zurück. */
    fun cycleVolume(context: Context): Int {
        val am = audio(context)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val step = ceil(max / 5.0).toInt().coerceAtLeast(1)
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, if (current >= max) 0 else (current + step).coerceAtMost(max), 0)
        return volumePercent(context)
    }

    fun adbDebugging(context: Context) = Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1

    suspend fun setAdbDebugging(on: Boolean) = RootShell.run("settings put global adb_enabled ${if (on) 1 else 0}").isSuccess

    fun soundEffects(context: Context) = Settings.System.getInt(context.contentResolver, "sound_effects_enabled", 1) == 1

    suspend fun setSoundEffects(on: Boolean) = RootShell.run("settings put system sound_effects_enabled ${if (on) 1 else 0}").isSuccess

    suspend fun allowInstalls(packageName: String) = RootShell.run("appops set $packageName REQUEST_INSTALL_PACKAGES allow").isSuccess

    // --- Apps verwalten ---

    suspend fun forceStop(pkg: String) = RootShell.run("am force-stop ${quote(pkg)}").isSuccess
    suspend fun clearData(pkg: String) = RootShell.run("pm clear ${quote(pkg)}").isSuccess
    suspend fun clearCache(pkg: String) =
        RootShell.run("rm -rf /data/data/${quote(pkg)}/cache/* /sdcard/Android/data/${quote(pkg)}/cache/*").isSuccess
    suspend fun uninstall(pkg: String) = RootShell.run("pm uninstall ${quote(pkg)}").isSuccess

    /** Paketnamen kommen aus dem Gerät, trotzdem nie ungeprüft in eine Shell. */
    private fun quote(pkg: String): String {
        require(pkg.matches(Regex("[A-Za-z0-9._]+"))) { "Ungültiger Paketname" }
        return pkg
    }
}
