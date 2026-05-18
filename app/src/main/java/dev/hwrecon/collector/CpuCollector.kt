package dev.hwrecon.collector

import dev.hwrecon.model.*
import dev.hwrecon.shell.RootShell
import dev.hwrecon.util.ArmPartMap

/**
 * CpuCollector
 */
class CpuCollector {

    suspend fun collect(): CpuSummary {
        val cpuInfoLines = RootShell.lines("cat /proc/cpuinfo")
        val cores = collectCoreTopology()
        val features = parseFeatures(cpuInfoLines)
        val clocks = collectClocks()
        val regulators = collectRegulators()
        val (soc, socName) = detectSoc()

        return CpuSummary(
            socModel = soc,
            socName = socName,
            architecture = parseField(cpuInfoLines, "CPU architecture") ?: "AArch64",
            clusterLayout = describeClusterLayout(cores),
            l3SizeMb = readL3Size(),
            processNode = "N/A",
            cores = cores,
            features = features,
            clockEntries = clocks,
            regulators = regulators,
        )
    }

    suspend fun collectCoreTopology(): List<CpuCore> {
        val coreIds = RootShell.lines("ls /sys/devices/system/cpu | grep -E '^cpu[0-9]+$'")
            .mapNotNull { it.removePrefix("cpu").toIntOrNull() }
            .sorted()

        val cpuinfoLines = RootShell.lines("cat /proc/cpuinfo")
        return coreIds.map { id -> buildCoreEntry(id, cpuinfoLines) }
    }

    private suspend fun buildCoreEntry(id: Int, cpuinfoLines: List<String>): CpuCore {
        val base = "/sys/devices/system/cpu/cpu$id"
        val freqBase = "$base/cpufreq"
        val topoBase = "$base/topology"

        val maxFreq = readLongFile("$freqBase/cpuinfo_max_freq")
        val curFreq = readLongFile("$freqBase/scaling_cur_freq")
        val governor = RootShell.readFile("$freqBase/scaling_governor").output.trim()
        val online = RootShell.readFile("$base/online").output.trim() != "0"

        val cluster = when {
            maxFreq > 3_000_000L -> "prime"
            maxFreq > 2_500_000L -> "big"
            else -> "LITTLE"
        }

        val partLine = cpuinfoLines
            .dropWhile { !it.contains("processor\t: $id") }
            .firstOrNull { it.startsWith("CPU part") }
        val partHex = partLine?.substringAfterLast("0x")?.trim()?.uppercase()
        val coreType = ArmPartMap.lookup(partHex ?: "") ?: "unknown"

        val l2 = readL2Size(id)

        return CpuCore(
            id = id,
            cluster = cluster,
            compatible = coreType,
            maxFreqKhz = maxFreq,
            curFreqKhz = curFreq,
            governor = governor.ifBlank { "unknown" },
            l2SizeKb = l2,
            online = online,
        )
    }

    suspend fun collectClocks(): List<ClockEntry> {
        val r = RootShell.run("cat /sys/kernel/debug/clk/clk_summary 2>/dev/null | head -120")
        if (!r.success || r.output.isBlank()) return emptyList()

        return r.output.lines()
            .filter { it.isNotBlank() && !it.startsWith("clock") && !it.startsWith("---") }
            .mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size < 5) return@mapNotNull null
                val name = parts[0]
                val enabled = parts[1].toIntOrNull() ?: 0
                val rateHz = parts.last().toLongOrNull() ?: return@mapNotNull null
                ClockEntry(name = name, rateHz = rateHz, enabled = enabled > 0, parent = null)
            }
            .sortedByDescending { it.rateHz }
            .take(30)
    }

    suspend fun collectRegulators(): List<RegulatorEntry> {
        val regulatorDir = "/sys/kernel/debug/regulator"
        val entries = RootShell.listDir(regulatorDir)
        if (entries.isEmpty()) return emptyList()

        return entries.mapNotNull { name ->
            val base = "$regulatorDir/$name"
            val statusRaw = RootShell.readFile("$base/status").output.trim()
            val voltageRaw = RootShell.readFile("$base/voltage").output.trim()

            val state = when {
                statusRaw.contains("enabled", ignoreCase = true) -> "ON"
                statusRaw.contains("bypass", ignoreCase = true) -> "BYPASS"
                else -> "OFF"
            }

            RegulatorEntry(
                name = name,
                voltageUv = voltageRaw.toIntOrNull() ?: 0,
                state = state,
                consumers = emptyList()
            )
        }.sortedBy { it.name }
    }

    private fun parseFeatures(cpuinfoLines: List<String>): List<String> =
        cpuinfoLines.firstOrNull { it.startsWith("Features") }
            ?.substringAfter(":")
            ?.trim()
            ?.split(" ")
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    private suspend fun detectSoc(): Pair<String, String> {
        val socId = RootShell.readFile("/sys/devices/soc0/soc_id").output.trim()
        val dtCompat = RootShell.run("cat /proc/device-tree/compatible 2>/dev/null | tr '\\0' '\\n' | head -1").output.trim()
        return Pair(socId.ifBlank { "Unknown" }, dtCompat)
    }

    private suspend fun readL3Size(): Int = 0
    private suspend fun readL2Size(coreId: Int): Int = 0
    private suspend fun readLongFile(path: String): Long =
        RootShell.readFile(path).output.trim().toLongOrNull() ?: 0L

    private fun parseField(lines: List<String>, key: String): String? =
        lines.firstOrNull { it.startsWith(key) }?.substringAfter(":")?.trim()

    private fun describeClusterLayout(cores: List<CpuCore>): String = "${cores.size} cores"
}
