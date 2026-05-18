package dev.hwrecon.collector

import dev.hwrecon.model.*
import dev.hwrecon.shell.RootShell
import dev.hwrecon.util.ArmPartMap

/**
 * CpuCollector
 *
 * Aggregates CPU topology, clock tree, and regulator data from:
 *   /proc/cpuinfo                          → implementer, part, features
 *   /sys/devices/system/cpu/cpu*/          → per-core freq, governor, topology
 *   /sys/kernel/debug/clk/clk_summary      → full clock tree with rates
 *   /sys/kernel/debug/regulator/           → PMIC rail states and consumers
 *   /sys/kernel/debug/pinctrl/             → pin mux assignments
 */
class CpuCollector {

    // ── Public API ────────────────────────────────────────────────

    suspend fun collect(): CpuSummary {
        val cpuInfoLines  = RootShell.lines("cat /proc/cpuinfo")
        val cores         = collectCoreTopology()
        val features      = parseFeatures(cpuInfoLines)
        val clocks        = collectClocks()
        val regulators    = collectRegulators()
        val (soc, socName) = detectSoc()

        return CpuSummary(
            socModel      = soc,
            socName       = socName,
            architecture  = parseField(cpuInfoLines, "CPU architecture") ?: "AArch64",
            clusterLayout = describeClusterLayout(cores),
            l3SizeMb      = readL3Size(),
            processNode   = "N/A", // only available via DT or OEM sysfs
            cores         = cores,
            features      = features,
            clockEntries  = clocks,
            regulators    = regulators,
        )
    }

    // ── Per-core topology ─────────────────────────────────────────

    suspend fun collectCoreTopology(): List<CpuCore> {
        val coreIds = RootShell.lines("ls /sys/devices/system/cpu | grep -E '^cpu[0-9]+$'")
            .mapNotNull { it.removePrefix("cpu").toIntOrNull() }
            .sorted()

        val cpuinfoLines = RootShell.lines("cat /proc/cpuinfo")

        return coreIds.map { id -> buildCoreEntry(id, cpuinfoLines) }
    }

    private suspend fun buildCoreEntry(id: Int, cpuinfoLines: List<String>): CpuCore {
        val base      = "/sys/devices/system/cpu/cpu$id"
        val freqBase  = "$base/cpufreq"
        val topoBase  = "$base/topology"

        val maxFreq   = readLongFile("$freqBase/cpuinfo_max_freq")
        val curFreq   = readLongFile("$freqBase/scaling_cur_freq")
        val governor  = RootShell.readFile("$freqBase/scaling_governor").output.trim()
        val clusterId = readLongFile("$topoBase/physical_package_id").toInt()
        val online    = RootShell.readFile("$base/online").output.trim() != "0"

        // Derive cluster name from frequency range heuristics
        val cluster = when {
            maxFreq > 3_000_000L -> "prime"
            maxFreq > 2_500_000L -> "big"
            else                 -> "LITTLE"
        }

        // Identify core type from ARM part number in /proc/cpuinfo
        val partLine = cpuinfoLines
            .dropWhile { !it.contains("processor\t: $id") }
            .firstOrNull { it.startsWith("CPU part") }
        val partHex  = partLine?.substringAfterLast("0x")?.trim()?.uppercase()
        val coreType = ArmPartMap.lookup(partHex ?: "") ?: "unknown"

        val l2 = readL2Size(id)

        return CpuCore(
            id           = id,
            cluster      = cluster,
            compatible   = coreType,
            maxFreqKhz   = maxFreq,
            curFreqKhz   = curFreq,
            governor     = governor.ifBlank { "unknown" },
            l2SizeKb     = l2,
            online       = online,
        )
    }

    // ── Clock tree ────────────────────────────────────────────────

    suspend fun collectClocks(): List<ClockEntry> {
        val r = RootShell.run("cat /sys/kernel/debug/clk/clk_summary 2>/dev/null | head -120")
        if (!r.success || r.output.isBlank()) return emptyList()

        /*
         * clk_summary format (space-delimited):
         * clock name          enable_cnt  prepare_cnt  protect_cnt  rate
         * gcc_sys_noc_axi_clk      1           1            0       933333333
         */
        return r.output.lines()
            .filter { it.isNotBlank() && !it.startsWith("clock") && !it.startsWith("---") }
            .mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size < 5) return@mapNotNull null
                val name    = parts[0]
                val enabled = parts[1].toIntOrNull() ?: 0
                val rateHz  = parts.last().toLongOrNull() ?: return@mapNotNull null
                ClockEntry(
                    name    = name,
                    rateHz  = rateHz,
                    enabled = enabled > 0,
                    parent  = null, // populate via clk_tree if needed
                )
            }
            .sortedByDescending { it.rateHz }
            .take(30)
    }

    // ── Regulator tree ────────────────────────────────────────────

    suspend fun collectRegulators(): List<RegulatorEntry> {
        val regulatorDir = "/sys/kernel/debug/regulator"
        val entries = RootShell.listDir(regulatorDir)
        if (entries.isEmpty()) return emptyList()

        return entries.mapNotNull { name ->
            val base        = "$regulatorDir/$name"
            val statusRaw   = RootShell.readFile("$base/status").output.trim()
            val voltageRaw  = RootShell.readFile("$base/voltage").output.trim()
            val consumersRaw = RootShell.lines("cat $base/consumers 2>/dev/null | head -8")

            val state = when {
                statusRaw.contains("enabled", ignoreCase = true)  -> "ON"
                statusRaw.contains("bypass",  ignoreCase = true)  -> "BYPASS"
                statusRaw.contains("eco",     ignoreCase = true)  -> "ECO"
                else                                               -> "OFF"
            }

            RegulatorEntry(
                name       = name,
                voltageUv  = voltageRaw.toIntOrNull() ?: 0,
                state      = state,
                consumers  = consumersRaw.filter { it.isNotBlank() },
            )
        }.sortedBy { it.name }
    }

    // ── Feature flags ─────────────────────────────────────────────

    private fun parseFeatures(cpuinfoLines: List<String>): List<String> =
        cpuinfoLines
            .firstOrNull { it.startsWith("Features") }
            ?.substringAfter(":")
            ?.trim()
            ?.split(" ")
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    // ── Helpers ───────────────────────────────────────────────────

    private suspend fun detectSoc(): Pair<String, String> {
        // Qualcomm SoC ID from /sys or DT
        val socId = RootShell.readFile("/sys/devices/soc0/soc_id").output.trim()
        val socHw = RootShell.readFile("/sys/devices/soc0/hw_platform").output.trim()
        val dtCompat = RootShell.run(
            "cat /proc/device-tree/compatible 2>/dev/null | tr '\\0' '\\n' | head -1"
        ).output.trim()

        val model = socId.ifBlank { dtCompat.substringAfter(",").ifBlank { "Unknown" } }
        val name  = socHw.ifBlank { dtCompat }
        return Pair(model, name)
    }

    private suspend fun readL3Size(): Int {
        val r = RootShell.readFile("/sys/devices/system/cpu/cpu0/cache/index3/size")
        val raw = r.output.trim().uppercase()
        return when {
            raw.endsWith("M") -> (raw.removeSuffix("M").toFloatOrNull()?.times(1024))?.toInt() ?: 0
            raw.endsWith("K") -> raw.removeSuffix("K").toIntOrNull() ?: 0
            else              -> raw.toIntOrNull() ?: 0
        }
    }

    private suspend fun readL2Size(coreId: Int): Int {
        val r = RootShell.readFile("/sys/devices/system/cpu/cpu$coreId/cache/index2/size")
        val raw = r.output.trim().uppercase()
        return when {
            raw.endsWith("M") -> (raw.removeSuffix("M").toFloatOrNull()?.times(1024))?.toInt() ?: 0
            raw.endsWith("K") -> raw.removeSuffix("K").toIntOrNull() ?: 0
            else              -> raw.toIntOrNull() ?: 0
        }
    }

    private suspend fun readLongFile(path: String): Long =
        RootShell.readFile(path).output.trim().toLongOrNull() ?: 0L

    private fun parseField(lines: List<String>, key: String): String? =
        lines.firstOrNull { it.startsWith(key) }
            ?.substringAfter(":")
            ?.trim()

    private fun describeClusterLayout(cores: List<CpuCore>): String {
        val groups = cores.groupBy { it.cluster }
        return groups.entries.joinToString("+") { "${it.value.size}" } +
               " (${groups.keys.joinToString(".")})"
    }
}
