package de.glut.launcher.data

data class SystemUser(val id: Int, val name: String, val running: Boolean, val current: Boolean)

/** Benutzerkonten des Geräts. Alles geht über Root, normale Apps sehen sie nicht. */
object SystemUsers {
    private val line = Regex("""UserInfo\{(\d+):([^:}]*):""")

    suspend fun list(): List<SystemUser>? {
        val users = RootShell.run("pm list users").getOrNull() ?: return null
        val current = RootShell.run("am get-current-user").getOrNull()?.trim()?.toIntOrNull() ?: 0
        return users.lines().mapNotNull { text ->
            val match = line.find(text) ?: return@mapNotNull null
            val id = match.groupValues[1].toInt()
            SystemUser(id, match.groupValues[2].ifBlank { "Nutzer $id" }, text.contains("running"), id == current)
        }
    }

    suspend fun switchTo(id: Int) = RootShell.run("am switch-user $id").isSuccess

    suspend fun adbOverNetwork(): Boolean =
        (RootShell.run("getprop service.adb.tcp.port").getOrNull()?.trim()?.toIntOrNull() ?: -1) > 0

    suspend fun setAdbOverNetwork(on: Boolean) =
        RootShell.run("setprop service.adb.tcp.port ${if (on) 5555 else -1}; stop adbd; start adbd").isSuccess
}
