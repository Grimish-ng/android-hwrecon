package dev.hwrecon.shell

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

data class ShellResult(
    val output: String,
    val exitCode: Int,
    val error: String = "",
) {
    val success get() = exitCode == 0
}

/**
 * Coroutine-safe root shell executor.
 *
 * All commands are dispatched on Dispatchers.IO and run via `su -c`.
 * A 10-second timeout is enforced per command; the process is force-killed
 * and a failure ShellResult is returned on timeout.
 *
 * Usage:
 *   val result = RootShell.run("cat /proc/cpuinfo")
 *   val lines  = RootShell.lines("cat /proc/modules")
 *   val exists = RootShell.fileExists("/proc/device-tree/compatible")
 */
object RootShell {

    private const val TIMEOUT_SECONDS = 10L

    /** Run a single su command, return full stdout + exit code. */
    suspend fun run(cmd: String): ShellResult = withContext(Dispatchers.IO) {
        try {
            val proc = ProcessBuilder("su", "-c", cmd)
                .redirectErrorStream(false)
                .start()

            val stdout = proc.inputStream.bufferedReader().readText()
            val stderr = proc.errorStream.bufferedReader().readText()

            val finished = proc.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                return@withContext ShellResult("", -1, "timeout after ${TIMEOUT_SECONDS}s: $cmd")
            }

            ShellResult(stdout, proc.exitValue(), stderr)
        } catch (e: Exception) {
            ShellResult("", -1, e.message ?: "unknown error")
        }
    }

    /** Convenience: run and split stdout into non-blank lines. */
    suspend fun lines(cmd: String): List<String> =
        run(cmd).output.lines().filter { it.isNotBlank() }

    /** Read a sysfs/procfs file directly (no su overhead for world-readable nodes). */
    suspend fun readFile(path: String): ShellResult = withContext(Dispatchers.IO) {
        try {
            val f = File(path)
            if (!f.exists()) return@withContext ShellResult("", 1, "file not found: $path")
            ShellResult(f.readText(), 0)
        } catch (e: Exception) {
            // Fall back to su cat for permission-denied paths
            run("cat $path")
        }
    }

    /** Check whether a path exists under root. */
    suspend fun fileExists(path: String): Boolean =
        run("test -e $path && echo yes").output.trim() == "yes"

    /** List directory entries, one per line. */
    suspend fun listDir(path: String): List<String> =
        lines("ls $path 2>/dev/null")

    /** Push a static binary asset to /data/local/tmp and make it executable. */
    suspend fun installBinary(sourcePath: String, binaryName: String): Boolean {
        val dest = "/data/local/tmp/$binaryName"
        val cp = run("cp $sourcePath $dest && chmod 755 $dest")
        return cp.success
    }
}
