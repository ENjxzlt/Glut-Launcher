package de.glut.launcher.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Eigene Hintergründe liegen im app-eigenen Ordner, den man per ADB oder USB füllen kann
 * (kein Speicherzugriff nötig): /sdcard/Android/data/de.glut.launcher/files/backgrounds
 */
object CustomBackgrounds {
    private val imageTypes = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")
    private val videoTypes = setOf("mp4", "m4v", "webm", "mkv", "3gp")

    fun dir(context: Context): File =
        (context.getExternalFilesDir("backgrounds") ?: File(context.filesDir, "backgrounds")).also { it.mkdirs() }

    fun list(context: Context): List<File> =
        dir(context).listFiles { file -> file.isFile && (isImage(file) || isVideo(file)) }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()

    fun resolve(context: Context, name: String): File? =
        name.takeIf { it.isNotBlank() }?.let { File(dir(context), it) }?.takeIf { it.isFile }

    fun isVideo(file: File) = file.extension.lowercase() in videoTypes
    fun isImage(file: File) = file.extension.lowercase() in imageTypes
}

/** Bild oder stummes, endlos laufendes Video – abgedunkelt, damit Text lesbar bleibt. */
@Composable
fun CustomBackgroundLayer(file: File, dim: Int, modifier: Modifier = Modifier) {
    Box(modifier) {
        if (CustomBackgrounds.isVideo(file)) VideoLayer(file, Modifier.fillMaxSize()) else ImageLayer(file, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim.coerceIn(0, 90) / 100f)))
    }
}

@Composable
private fun ImageLayer(file: File, modifier: Modifier) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, file.path, file.lastModified()) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                // Auf Bildschirmgröße verkleinert laden, sonst sprengt ein 4K-Foto den Speicher.
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 1920 && bounds.outHeight / (sample * 2) >= 1080) sample *= 2
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()
        }
    }
    bitmap?.let { Image(it, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop) }
}

@Composable
private fun VideoLayer(file: File, modifier: Modifier) {
    val context = LocalContext.current
    val player = remember(file.path) { MediaPlayer() }
    DisposableEffect(player) {
        // Im Hintergrund nicht weiterspielen: spart Akku-/CPU-Zeit des Sticks.
        val lifecycle = (context as? ComponentActivity)?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            runCatching {
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> if (player.isPlaying) player.pause()
                    Lifecycle.Event.ON_RESUME -> player.start()
                    else -> Unit
                }
            }
        }
        lifecycle?.addObserver(observer)
        onDispose {
            lifecycle?.removeObserver(observer)
            runCatching { player.release() }
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                        runCatching {
                            player.setDataSource(file.path)
                            player.setSurface(Surface(texture))
                            player.isLooping = true
                            player.setVolume(0f, 0f)
                            player.setOnVideoSizeChangedListener { _, w, h -> cover(this@apply, w, h) }
                            player.setOnPreparedListener { it.start() }
                            player.prepareAsync()
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit
                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture) = true
                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                }
            }
        },
    )
}

/** TextureView streckt das Video; so füllt es die Fläche wie „Crop“ ohne Verzerrung. */
private fun cover(view: TextureView, videoWidth: Int, videoHeight: Int) {
    if (videoWidth <= 0 || videoHeight <= 0 || view.width == 0 || view.height == 0) return
    val videoAspect = videoWidth.toFloat() / videoHeight
    val viewAspect = view.width.toFloat() / view.height
    val scaleX = if (videoAspect > viewAspect) videoAspect / viewAspect else 1f
    val scaleY = if (videoAspect > viewAspect) 1f else viewAspect / videoAspect
    view.setTransform(Matrix().apply { setScale(scaleX, scaleY, view.width / 2f, view.height / 2f) })
}
