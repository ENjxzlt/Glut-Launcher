package de.glut.launcher.ui

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val german = Locale.GERMANY

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

fun formatClock(time: Long): String = SimpleDateFormat("HH:mm", german).format(Date(time))

fun formatDate(time: Long): String = SimpleDateFormat("EEEE, d. MMMM", german).format(Date(time))

/** „vor 5 Min.“, „vor 2 Std.“, „gestern“, „Mittwoch“, „12. Sept.“ */
fun formatRelative(time: Long, now: Long): String {
    val minutes = (now - time) / 60_000L
    if (minutes < 1) return "gerade eben"
    if (minutes < 60) return "vor $minutes Min."
    val then = Calendar.getInstance().apply { timeInMillis = time }
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val days = daysBetween(then, today)
    return when {
        days == 0 -> "vor ${minutes / 60} Std."
        days == 1 -> "gestern"
        days < 7 -> SimpleDateFormat("EEEE", german).format(Date(time))
        else -> SimpleDateFormat("d. MMM", german).format(Date(time))
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
