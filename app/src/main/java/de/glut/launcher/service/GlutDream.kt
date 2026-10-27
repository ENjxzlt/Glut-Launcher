package de.glut.launcher.service

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.media.MediaPlayer
import android.service.dreams.DreamService
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import de.glut.launcher.data.Prefs
import de.glut.launcher.data.ScreensaverMedia
import de.glut.launcher.ui.CustomBackgrounds
import de.glut.launcher.ui.ScreensaverSource
import de.glut.launcher.ui.cover
import de.glut.launcher.uiLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.coroutines.resume

/**
 * Glut-Bildschirmschoner: wechselt zwischen Bildern (weiche Überblendung, langsamer Zoom)
 * und spielt Videos stumm ab. Quelle: eigene Dateien oder Stock-Fotos. Reine Views statt
 * Compose, weil ein DreamService keinen Lifecycle-Besitzer hat und das auf dem Stick leichter bleibt.
 */
class GlutDream : DreamService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var clockJob: Job? = null
    private var pendingStock: Deferred<File?>? = null
    private var player: MediaPlayer? = null
    private lateinit var prefs: Prefs
    private lateinit var stage: FrameLayout
    private lateinit var imageA: ImageView
    private lateinit var imageB: ImageView
    private lateinit var video: TextureView
    private lateinit var clock: TextView
    private var showingA = true

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true
        prefs = Prefs(this)
        buildStage()
        setContentView(stage)
    }

    private fun buildStage() {
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        fun image() = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0f }
        stage = FrameLayout(this).apply { setBackgroundColor(Color.rgb(29, 32, 33)) }
        imageA = image()
        imageB = image()
        video = TextureView(this).apply { alpha = 0f; visibility = View.GONE }
        clock = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setShadowLayer(14f, 0f, 2f, Color.BLACK)
            gravity = Gravity.END
            visibility = if (prefs.screensaverClock) View.VISIBLE else View.GONE
        }
        val margin = (48 * resources.displayMetrics.density).toInt()
        stage.addView(imageA, FrameLayout.LayoutParams(match, match))
        stage.addView(imageB, FrameLayout.LayoutParams(match, match))
        stage.addView(video, FrameLayout.LayoutParams(match, match))
        stage.addView(
            clock,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END)
                .apply { setMargins(margin, margin, margin, margin) },
        )
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        if (prefs.screensaverClock) clockJob = scope.launch { tickClock() }
        job = scope.launch { play() }
    }

    override fun onDreamingStopped() {
        job?.cancel()
        clockJob?.cancel()
        pendingStock?.cancel()
        releasePlayer()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        scope.cancel()
        super.onDetachedFromWindow()
    }

    private suspend fun tickClock() {
        val time = SimpleDateFormat("HH:mm", uiLocale)
        val date = SimpleDateFormat("EEEE, d. MMMM", uiLocale)
        while (scope.isActive) {
            val now = Date()
            val timeText = time.format(now)
            clock.text = SpannableString("$timeText\n${date.format(now)}").apply {
                setSpan(RelativeSizeSpan(2.2f), 0, timeText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            delay(30_000)
        }
    }

    private suspend fun play() {
        var playlist = emptyList<File>()
        var index = 0
        while (scope.isActive) {
            val interval = prefs.screensaverInterval.coerceIn(5, 300) * 1000L
            val file = when (prefs.screensaverSource) {
                ScreensaverSource.FILES -> {
                    if (index >= playlist.size) {
                        playlist = ScreensaverMedia.list(this).shuffled()
                        index = 0
                    }
                    playlist.getOrNull(index++)
                }
                ScreensaverSource.STOCK -> (pendingStock?.await() ?: ScreensaverMedia.nextStock(this)).also { pendingStock = null }
            }
            if (file == null) {
                // Nichts zu zeigen (Ordner leer, offline ohne Zwischenspeicher): nur die Uhr.
                delay(30_000)
                continue
            }
            if (CustomBackgrounds.isVideo(file)) {
                playVideo(file)
            } else {
                if (prefs.screensaverSource == ScreensaverSource.STOCK) pendingStock = scope.async { ScreensaverMedia.nextStock(this@GlutDream) }
                showImage(file, interval)
                delay(interval)
            }
        }
    }

    private suspend fun showImage(file: File, interval: Long) {
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 1920 && bounds.outHeight / (sample * 2) >= 1080) sample *= 2
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull()
        } ?: return
        val incoming = if (showingA) imageB else imageA
        val outgoing = if (showingA) imageA else imageB
        showingA = !showingA
        hideVideo()
        incoming.setImageBitmap(bitmap)
        incoming.alpha = 0f
        incoming.scaleX = 1f
        incoming.scaleY = 1f
        incoming.bringToFront()
        clock.bringToFront()
        incoming.animate().alpha(1f).setDuration(FADE_MS).withEndAction {
            outgoing.alpha = 0f
            outgoing.setImageDrawable(null)
        }.start()
        ObjectAnimator.ofPropertyValuesHolder(
            incoming,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.08f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.08f),
        ).apply { duration = interval + FADE_MS * 2; interpolator = LinearInterpolator() }.start()
    }

    /** Spielt ein Video bis zum Ende (stumm). Bei Fehlern geht es mit dem nächsten Eintrag weiter. */
    private suspend fun playVideo(file: File) {
        video.visibility = View.VISIBLE
        video.alpha = 0f
        video.bringToFront()
        clock.bringToFront()
        suspendCancellableCoroutine { cont ->
            val mp = MediaPlayer()
            player = mp
            fun finish() { if (cont.isActive) cont.resume(Unit) }
            fun start(texture: SurfaceTexture) {
                try {
                    mp.setDataSource(file.path)
                    mp.setSurface(Surface(texture))
                    mp.setVolume(0f, 0f)
                    mp.setOnVideoSizeChangedListener { _, w, h -> cover(video, w, h) }
                    mp.setOnPreparedListener {
                        it.start()
                        video.animate().alpha(1f).setDuration(FADE_MS).start()
                    }
                    mp.setOnCompletionListener { finish() }
                    mp.setOnErrorListener { _, _, _ -> finish(); true }
                    mp.prepareAsync()
                } catch (e: Exception) {
                    finish()
                }
            }
            cont.invokeOnCancellation { releasePlayer() }
            val texture = video.surfaceTexture
            if (video.isAvailable && texture != null) {
                start(texture)
            } else {
                video.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) = start(surface)
                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture) = true
                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                }
            }
        }
        releasePlayer()
    }

    private fun hideVideo() {
        if (video.visibility != View.VISIBLE) return
        video.animate().alpha(0f).setDuration(FADE_MS).withEndAction {
            video.visibility = View.GONE
            releasePlayer()
        }.start()
    }

    private fun releasePlayer() {
        player?.let { runCatching { it.release() } }
        player = null
    }

    private companion object {
        const val FADE_MS = 1500L
    }
}
