package de.glut.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.glut.launcher.data.AppEntry
import de.glut.launcher.data.AppRepository
import de.glut.launcher.data.Prefs

/**
 * Sucht unter den installierten Apps (auch ausgeblendeten). Eine Inhaltssuche
 * über alle Streaming-Apps wie bei Amazon ist für fremde Apps nicht möglich.
 */
@Composable
fun SearchScreen(
    apps: List<AppEntry>,
    prefs: Prefs,
    repository: AppRepository,
    focusKey: Int,
    onLaunch: (AppEntry) -> Unit,
    onOptions: (AppEntry) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var fieldFocused by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val accent = LocalAccent.current

    LaunchedEffect(focusKey) {
        withFrameNanos { }
        runCatching { fieldFocus.requestFocus() }
        keyboard?.show()
    }

    val term = query.trim()
    val results = if (term.isEmpty()) {
        apps.filter { it.packageName !in prefs.hidden }
    } else {
        // Treffer am Wortanfang zuerst, dann alle anderen.
        apps.filter { it.label.contains(term, ignoreCase = true) }
            .sortedBy { if (it.label.startsWith(term, ignoreCase = true)) 0 else 1 }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(if (fieldFocused) Gruv.bg2 else Gruv.bg1, RoundedCornerShape(22.dp))
                .then(if (fieldFocused) Modifier.border(2.dp, accent, RoundedCornerShape(22.dp)) else Modifier)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.search, size = 18.dp, tint = Gruv.fg2)
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = Gruv.fg0, fontSize = 15.sp, fontFamily = Geist),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboard?.hide()
                    focusManager.moveFocus(FocusDirection.Down)
                }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(fieldFocus)
                    .onFocusChanged { fieldFocused = it.isFocused },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Txt("App-Name eingeben …", size = 15.sp, color = Gruv.fg4)
                        inner()
                    }
                },
            )
        }

        Txt(
            when {
                term.isEmpty() -> "Alle Apps"
                results.isEmpty() -> "Keine App gefunden für „$term“"
                else -> "${results.size} Treffer für „$term“"
            },
            size = 11.sp,
            color = Gruv.fg4,
        )

        AppGrid(
            apps = results,
            prefs = prefs,
            repository = repository,
            onLaunch = onLaunch,
            onOptions = onOptions,
            modifier = Modifier.weight(1f),
        )
    }
}
