package de.glut.launcher.data

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import de.glut.launcher.service.MediaListenerService

data class NowPlaying(
    val packageName: String,
    val title: String,
    val artist: String,
    val art: ImageBitmap?,
    val isPlaying: Boolean,
)

/** Beobachtet laufende Musik/Videos anderer Apps über deren Mediensitzung. */
class MediaWatcher(private val context: Context) {
    var nowPlaying by mutableStateOf<NowPlaying?>(null)
        private set

    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(context, MediaListenerService::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var controllers: List<MediaController> = emptyList()
    private var started = false

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = update()
        override fun onMetadataChanged(metadata: MediaMetadata?) = update()
        override fun onSessionDestroyed() = update()
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        watch(list.orEmpty())
    }

    val hasAccess: Boolean
        get() = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    fun start() {
        if (started || !hasAccess || manager == null) return
        runCatching {
            manager.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
            watch(manager.getActiveSessions(component))
            started = true
        }
    }

    fun stop() {
        if (!started) return
        runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        controllers.forEach { it.unregisterCallback(controllerCallback) }
        controllers = emptyList()
        started = false
    }

    fun playPause() {
        val controller = current() ?: return
        if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
            controller.transportControls.pause()
        } else {
            controller.transportControls.play()
        }
    }

    fun next() = current()?.transportControls?.skipToNext()

    fun previous() = current()?.transportControls?.skipToPrevious()

    private fun watch(list: List<MediaController>) {
        controllers.forEach { it.unregisterCallback(controllerCallback) }
        controllers = list.filter { it.packageName != context.packageName }
        controllers.forEach { it.registerCallback(controllerCallback, handler) }
        update()
    }

    /** Bevorzugt eine spielende Sitzung, sonst eine pausierte mit Titel. */
    private fun current(): MediaController? =
        controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull { it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) != null }

    private fun update() {
        val controller = current()
        val metadata = controller?.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        nowPlaying = if (controller == null || metadata == null || title.isNullOrBlank()) {
            null
        } else {
            val art: Bitmap? = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            NowPlaying(
                packageName = controller.packageName,
                title = title,
                artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                    ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE).orEmpty(),
                art = art?.let { runCatching { it.asImageBitmap() }.getOrNull() },
                isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING,
            )
        }
    }
}
