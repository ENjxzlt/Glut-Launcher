package de.glut.launcher.ui

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp

/**
 * Fokussierbares Element für die Fernbedienung: OK löst [onClick] aus,
 * die Menü-Taste [onMenu]. Im Fokus wird es größer und bekommt einen Rahmen
 * in der Akzentfarbe.
 */
@Composable
fun Focusable(
    onClick: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    onMenu: (() -> Unit)? = null,
    color: Color = Gruv.bg1,
    focusedColor: Color = Gruv.bg2,
    focusedScale: Float = 1.06f,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusedScale else 1f, label = "focusScale")
    val accent = LocalAccent.current
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (focused) 24f else 0f
                this.shape = shape
            }
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (onMenu != null && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MENU) {
                    if (event.type == KeyEventType.KeyUp) onMenu()
                    true
                } else {
                    false
                }
            }
            .clip(shape)
            .background(if (focused) focusedColor else color)
            .then(if (focused) Modifier.border(2.dp, accent, shape) else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        content(focused)
    }
}
