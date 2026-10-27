package de.glut.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.glut.launcher.data.SystemUser
import de.glut.launcher.data.WifiNetwork
import de.glut.launcher.data.WifiState
import de.glut.launcher.t

@Composable
private fun rememberFirstFocus(focusKey: Int): FocusRequester {
    val first = remember { FocusRequester() }
    LaunchedEffect(focusKey) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    return first
}

@Composable
private fun SectionHeader(title: String) {
    Spacer(Modifier.height(18.dp))
    Txt(title, size = 13.sp, color = LocalAccent.current, weight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp, bottom = 4.dp))
}

@Composable
private fun RootBadge(active: Boolean) {
    val color = if (active) Theme.green else Theme.fg4
    Row(
        modifier = Modifier
            .background(Theme.bg1, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Txt(if (active) t("Root aktiv", "Root active") else t("Kein Root", "No root"), size = 10.sp, color = color)
    }
}

/** Einstiegsseite: Netzwerk, Benutzer und Energie an einem Ort. */
@Composable
fun SystemScreen(
    focusKey: Int,
    rootAvailable: Boolean,
    rootMode: Boolean,
    wifiSummary: String,
    userSummary: String,
    adbNetwork: Boolean,
    status: String?,
    onWifi: () -> Unit,
    onUsers: () -> Unit,
    onToggleRootMode: () -> Unit,
    onToggleAdb: () -> Unit,
    onSleep: () -> Unit,
    onRestart: () -> Unit,
    onFireSettings: () -> Unit,
) {
    val first = rememberFirstFocus(focusKey)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .width(640.dp)
                .verticalScroll(rememberScrollState())
                .padding(top = 28.dp, bottom = 60.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Txt(t("System", "System"), size = 28.sp, color = Theme.fg0, weight = FontWeight.SemiBold)
                    Txt(t("Einstellungen › System", "Settings › System"), size = 11.sp, color = Theme.fg4)
                }
                RootBadge(rootAvailable)
            }

            SectionHeader(t("Netzwerk", "Network"))
            MenuRow("WLAN", onWifi, Modifier.focusRequester(first), value = wifiSummary)
            MenuRow(
                t("ADB über Netzwerk", "ADB over network"),
                onToggleAdb,
                value = if (adbNetwork) t("An", "On") else t("Aus", "Off"),
                subtitle = if (rootAvailable) t("Port 5555, nur im lokalen Netz", "Port 5555, local network only") else t("Braucht Root", "Needs root"),
            )

            SectionHeader(t("Benutzer", "Users"))
            MenuRow(t("Benutzer wählen", "Choose user"), onUsers, value = userSummary)
            MenuRow(
                t("Root-Modus", "Root mode"),
                onToggleRootMode,
                value = if (rootMode) t("An", "On") else t("Aus", "Off"),
                subtitle = t(
                    "Schlafen und Neustart laufen direkt, ohne Amazons Menüs",
                    "Sleep and restart run directly, without Amazon's menus",
                ),
            )

            SectionHeader(t("Energie", "Power"))
            MenuRow(t("Schlafen", "Sleep"), onSleep)
            MenuRow(
                t("Neu starten", "Restart"),
                onRestart,
                subtitle = if (rootMode) t("Sofort, ohne Rückfrage", "Immediately, no confirmation") else t("Öffnet das Einschaltmenü oder „Mein Fire TV“", "Opens the power menu or “My Fire TV”"),
            )
            MenuRow(t("Fire-TV-Einstellungen", "Fire TV settings"), onFireSettings)

            if (status != null) {
                Spacer(Modifier.height(10.dp))
                Txt(status, size = 10.sp, color = Theme.fg3, maxLines = 3, modifier = Modifier.padding(start = 12.dp))
            }
            Spacer(Modifier.height(16.dp))
            KeyHint(t("ZURÜCK", "BACK"), t("Einstellungen", "Settings"))
        }
    }
}

private fun signalLabel(level: Int) = when {
    level >= -55 -> t("sehr gut", "excellent")
    level >= -67 -> t("gut", "good")
    level >= -78 -> t("mittel", "fair")
    else -> t("schwach", "weak")
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Txt(label, size = 9.sp, color = Theme.fg4)
        Txt(value, size = 12.sp, color = Theme.fg0)
    }
}

/** WLAN-Seite: Netzwerkliste links, Verbindungsdetails rechts. */
@Composable
fun WifiScreen(
    focusKey: Int,
    state: WifiState?,
    networks: List<WifiNetwork>,
    scanning: Boolean,
    status: String?,
    onToggle: () -> Unit,
    onRescan: () -> Unit,
    onNetwork: (WifiNetwork) -> Unit,
    onForget: (WifiNetwork) -> Unit,
) {
    val first = rememberFirstFocus(focusKey)
    val enabled = state?.enabled == true
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Row(
            modifier = Modifier.width(860.dp).padding(top = 28.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Txt("WLAN", size = 28.sp, color = Theme.fg0, weight = FontWeight.SemiBold)
                Txt(t("System › Netzwerk", "System › Network"), size = 11.sp, color = Theme.fg4)
                Spacer(Modifier.height(10.dp))
                MenuRow(
                    "WLAN",
                    onToggle,
                    Modifier.focusRequester(first),
                    value = if (enabled) t("An", "On") else t("Aus", "Off"),
                )
                if (enabled) {
                    MenuRow(
                        if (scanning) t("Suche läuft …", "Scanning …") else t("Erneut suchen", "Scan again"),
                        onRescan,
                    )
                    val connected = networks.firstOrNull { it.connected }
                    if (connected != null) {
                        SectionHeader(t("Verbunden", "Connected"))
                        MenuRow(
                            connected.ssid,
                            { onNetwork(connected) },
                            subtitle = "${connected.band} · ${signalLabel(connected.level)}",
                            checked = true,
                        )
                    }
                    val others = networks.filter { !it.connected }
                    if (others.isNotEmpty()) SectionHeader(t("Verfügbare Netzwerke", "Available networks"))
                    for (network in others) {
                        MenuRow(
                            network.ssid,
                            { onNetwork(network) },
                            subtitle = buildString {
                                append(network.band).append(" · ").append(signalLabel(network.level))
                                if (network.saved) append(" · ").append(t("gespeichert", "saved"))
                            },
                            value = if (network.secure) t("gesichert", "secured") else t("offen", "open"),
                        )
                    }
                    if (!scanning && networks.isEmpty()) {
                        Txt(
                            t("Keine Netzwerke gefunden. Standortzugriff braucht Root.", "No networks found. Location access needs root."),
                            size = 10.sp,
                            color = Theme.fg4,
                            maxLines = 2,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
                if (status != null) Txt(status, size = 10.sp, color = Theme.fg3, maxLines = 3, modifier = Modifier.padding(start = 12.dp, top = 8.dp))
            }
            Column(Modifier.width(230.dp).padding(top = 52.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Theme.bg1, RoundedCornerShape(10.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Txt(t("Verbindung", "Connection"), size = 11.sp, color = LocalAccent.current, weight = FontWeight.SemiBold)
                    if (state?.ssid != null) {
                        DetailLine(t("Netzwerk", "Network"), state.ssid)
                        DetailLine(t("IP-Adresse", "IP address"), state.ip ?: "–")
                        DetailLine("Gateway", state.gateway ?: "–")
                        DetailLine(t("Signal", "Signal"), "${state.rssi} dBm · ${signalLabel(state.rssi)}")
                        DetailLine(t("Tempo", "Speed"), "${state.speedMbps} Mbit/s · ${state.band ?: ""}")
                    } else {
                        Txt(t("Nicht verbunden", "Not connected"), size = 12.sp, color = Theme.fg3)
                    }
                }
                val connected = networks.firstOrNull { it.connected }
                val saved = connected ?: networks.firstOrNull { it.saved }
                if (saved != null) {
                    MenuRow(t("„${saved.ssid}“ vergessen", "Forget “${saved.ssid}”"), { onForget(saved) })
                }
                Spacer(Modifier.height(4.dp))
                KeyHint("OK", t("Verbinden", "Connect"))
                KeyHint(t("ZURÜCK", "BACK"), t("System", "System"))
            }
        }
    }
}

@Composable
private fun UserCard(
    name: String,
    subtitle: String,
    initial: String,
    tint: Color,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    Focusable(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (highlighted) Theme.bg2 else Theme.bg1,
        focusedColor = Theme.bg2,
        modifier = modifier.width(150.dp).height(190.dp),
    ) { focused ->
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(64.dp).background(Theme.bg0, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Txt(initial, size = 26.sp, color = tint, weight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            Txt(name, size = 13.sp, color = if (focused) Theme.fg0 else Theme.fg1, weight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Txt(subtitle, size = 9.sp, color = Theme.fg4, maxLines = 2)
        }
    }
}

/** Benutzerwahl: vorhandene Konten plus Root. */
@Composable
fun UsersScreen(
    focusKey: Int,
    users: List<SystemUser>?,
    rootAvailable: Boolean,
    rootMode: Boolean,
    status: String?,
    onUser: (SystemUser) -> Unit,
    onRoot: () -> Unit,
) {
    val first = rememberFirstFocus(focusKey)
    val accent = LocalAccent.current
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Txt(t("Wer nutzt Glut?", "Who is using Glut?"), size = 26.sp, color = Theme.fg0, weight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Txt(
            if (rootAvailable) t("Wähle einen Benutzer oder fahre mit Root-Rechten fort.", "Pick a user or continue with root access.")
            else t("Benutzerkonten brauchen Root-Zugriff.", "User accounts need root access."),
            size = 11.sp,
            color = Theme.fg4,
        )
        Spacer(Modifier.height(34.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            val list = users.orEmpty()
            list.forEachIndexed { index, user ->
                UserCard(
                    name = user.name,
                    subtitle = if (user.current) t("Aktiv · Nutzer ${user.id}", "Active · user ${user.id}") else t("Nutzer ${user.id}", "User ${user.id}"),
                    initial = user.name.take(1).uppercase(),
                    tint = if (index % 2 == 0) accent else Theme.blue,
                    modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                    highlighted = user.current,
                    onClick = { onUser(user) },
                )
            }
            UserCard(
                name = "Root",
                subtitle = if (rootMode) t("Superuser · aktiv", "Superuser · active") else t("Superuser-Zugriff", "Superuser access"),
                initial = "#",
                tint = Theme.orange,
                modifier = if (list.isEmpty()) Modifier.focusRequester(first) else Modifier,
                highlighted = rootMode,
                onClick = onRoot,
            )
        }
        Spacer(Modifier.height(26.dp))
        Txt(
            t(
                "Als Root laufen Systemänderungen ohne Rückfrage.",
                "As root, system changes run without confirmation.",
            ),
            size = 10.sp,
            color = Theme.yellow,
        )
        if (status != null) {
            Spacer(Modifier.height(8.dp))
            Txt(status, size = 10.sp, color = Theme.fg3, maxLines = 2)
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.padding(bottom = 30.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            KeyHint("OK", t("Auswählen", "Select"))
            KeyHint(t("ZURÜCK", "BACK"), t("System", "System"))
        }
    }
}
