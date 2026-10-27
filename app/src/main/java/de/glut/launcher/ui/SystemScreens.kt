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
import de.glut.launcher.data.BtDevice
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
                KeyHint(t("ZURÜCK", "BACK"), t("Netzwerk", "Network"))
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

/** Bluetooth: ein-/ausschalten, suchen, koppeln und entkoppeln. */
@Composable
fun BluetoothScreen(
    focusKey: Int,
    available: Boolean,
    enabled: Boolean,
    scanning: Boolean,
    devices: List<BtDevice>,
    status: String?,
    onToggle: () -> Unit,
    onScan: () -> Unit,
    onDevice: (BtDevice) -> Unit,
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
            Txt("Bluetooth", size = 28.sp, color = Theme.fg0, weight = FontWeight.SemiBold)
            Txt(t("Einstellungen › System › Netzwerk › Bluetooth", "Settings › System › Network › Bluetooth"), size = 11.sp, color = Theme.fg4)
            Spacer(Modifier.height(14.dp))
            if (!available) {
                Txt(t("Dieses Gerät hat kein Bluetooth.", "This device has no Bluetooth."), size = 12.sp, color = Theme.fg3)
            } else {
                MenuRow("Bluetooth", onToggle, Modifier.focusRequester(first), value = if (enabled) t("An", "On") else t("Aus", "Off"))
                if (enabled) {
                    MenuRow(
                        if (scanning) t("Suche läuft …", "Scanning …") else t("Geräte suchen", "Search for devices"),
                        onScan,
                        subtitle = t("Gerät in den Kopplungsmodus versetzen", "Put the device in pairing mode"),
                    )
                    val paired = devices.filter { it.bonded }
                    val nearby = devices.filter { !it.bonded }
                    if (paired.isNotEmpty()) SectionHeader(t("Gekoppelt", "Paired"))
                    for (device in paired) {
                        MenuRow(device.name, { onDevice(device) }, subtitle = t("OK entkoppelt das Gerät", "OK unpairs the device"), checked = true)
                    }
                    if (nearby.isNotEmpty()) SectionHeader(t("In der Nähe", "Nearby"))
                    for (device in nearby) {
                        MenuRow(device.name, { onDevice(device) }, subtitle = t("OK koppelt das Gerät", "OK pairs the device"))
                    }
                }
            }
            if (status != null) Txt(status, size = 10.sp, color = Theme.fg3, maxLines = 3, modifier = Modifier.padding(start = 12.dp, top = 8.dp))
            Spacer(Modifier.height(16.dp))
            KeyHint(t("ZURÜCK", "BACK"), t("Netzwerk", "Network"))
        }
    }
}
