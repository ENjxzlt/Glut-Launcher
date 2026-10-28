package de.glut.launcher.ui

import de.glut.launcher.t
import de.glut.launcher.uiLocale
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


/** Aktuelle Zeit, aktualisiert zu jeder vollen Minute. */
@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L - now % 60_000L)
        }
    }
    return now
}

fun formatClock(time: Long): String = SimpleDateFormat("HH:mm", uiLocale).format(Date(time))

fun formatDate(time: Long): String =
    SimpleDateFormat(t("EEEE, d. MMMM", "EEEE, d MMMM"), uiLocale).format(Date(time))

/** „vor 5 Min.“ / "5 min ago", „gestern“ / "yesterday", Wochentag, Datum */
fun formatRelative(time: Long, now: Long): String {
    val minutes = (now - time) / 60_000L
    if (minutes < 1) return t("gerade eben", "just now")
    if (minutes < 60) return t("vor $minutes Min.", "$minutes min ago")
    val then = Calendar.getInstance().apply { timeInMillis = time }
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val days = daysBetween(then, today)
    return when {
        days == 0 -> t("vor ${minutes / 60} Std.", "${minutes / 60} h ago")
        days == 1 -> t("gestern", "yesterday")
        days < 7 -> SimpleDateFormat("EEEE", uiLocale).format(Date(time))
        else -> SimpleDateFormat(t("d. MMM", "d MMM"), uiLocale).format(Date(time))
    }
}

private fun daysBetween(a: Calendar, b: Calendar): Int {
    fun Calendar.dayStart(): Long = (clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return ((b.dayStart() - a.dayStart()) / 86_400_000L).toInt()
}

/** Tastenhinweis wie „[OK] Öffnen“ in der Fußzeile. */
@Composable
fun KeyHint(key: String, label: String) {
    val accent = LocalAccent.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Txt(
            text = key,
            size = 9.sp,
            color = accent,
            family = Theme.clockFont,
            modifier = Modifier
                .border(0.75.dp, accent, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Txt(label, size = 10.sp, color = Theme.fg4)
    }
}

/**
 * Vergrößert den Zeichenbereich einer Reihe oder eines Rasters um [horizontal]/[vertical],
 * ohne das Layout zu verschieben. Zusammen mit gleich großem contentPadding haben
 * Fokus-Schatten und Vergrößerung Platz, statt an der Kante abgeschnitten zu werden.
 */
fun Modifier.bleed(horizontal: Dp, vertical: Dp): Modifier = layout { measurable, constraints ->
    val h = horizontal.roundToPx()
    val v = vertical.roundToPx()
    val widened = constraints.copy(
        minWidth = if (constraints.hasBoundedWidth) constraints.minWidth + 2 * h else constraints.minWidth,
        maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + 2 * h else constraints.maxWidth,
        minHeight = if (constraints.hasBoundedHeight) constraints.minHeight + 2 * v else constraints.minHeight,
        maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight + 2 * v else constraints.maxHeight,
    )
    val placeable = measurable.measure(widened)
    layout(
        (placeable.width - 2 * h).coerceAtLeast(0),
        (placeable.height - 2 * v).coerceAtLeast(0),
    ) { placeable.place(-h, -v) }
}

/** Platz für Schatten rund um Kachelreihen. */
val ShadowBleedX = 24.dp
val ShadowBleedY = 20.dp

/**
 * „Liquid Glass“: halbdurchsichtige Fläche mit hellem Rand oben. Echte Hintergrund-Unschärfe gibt es
 * auf dem Stick nicht, die Farbflächen des Hintergrunds schimmern aber durch.
 */
fun Modifier.glass(shape: Shape, strong: Boolean = false): Modifier {
    val tint = if (Theme.isLight) Color.Black else Color.White
    return this
        .background(Brush.linearGradient(listOf(tint.copy(alpha = if (strong) 0.26f else 0.18f), tint.copy(alpha = 0.06f))), shape)
        .border(1.dp, Brush.verticalGradient(listOf(tint.copy(alpha = 0.5f), tint.copy(alpha = 0.08f))), shape)
}
