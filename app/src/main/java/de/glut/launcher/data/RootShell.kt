package de.glut.launcher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Führt Befehle über `su` aus. Ohne Root schlägt jeder Aufruf kontrolliert fehl. */
object RootShell {
    suspend fun run(command: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            val code = process.waitFor()
            check(code == 0) { output.trim().ifBlank { "su: Exit $code" } }
            output
        }
    }

    suspend fun available(): Boolean = run("id -u").getOrNull()?.trim() == "0"
}
