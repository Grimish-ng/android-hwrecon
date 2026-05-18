package dev.hwrecon.collector

import dev.hwrecon.model.*
import dev.hwrecon.shell.RootShell
import dev.hwrecon.util.DriverHintMap

/**
 * DtCollector
 *
 * Reads the live device tree from /proc/device-tree (or /sys/firmware/devicetree/base),
 * decompiles it to DTS via a bundled static `dtc` binary, and parses
 * platform device nodes to produce DT binding hints for driver identification.
 *
 * Key sources:
 *   /proc/device-tree/compatible       → root compatible strings
 *   /proc/device-tree/                 → full live DT (binary)
 *   /sys/firmware/devicetree/base/     → same, browsable node-by-node
 *   /data/local/tmp/dtc                → bundled static dtc binary (push via installBinary)
 */
class DtCollector {

    private val dtBase = "/proc/device-tree"
    private val dtcBin = "/data/local/tmp/dtc"

    // ── Public API ────────────────────────────────────────────────

    suspend fun collect(): DtSummary {
        val compatibles = readRootCompatibles()
        val platformDevices = collectPlatformDevices()
        val rawDts = decompileDt()
        val nodeCount = countNodes()
        val propCount = estimatePropertyCount(rawDts)

        return DtSummary(
            rootCompatibles = compatibles,
            nodeCount = nodeCount,
            propertyCount = propCount,
            platformDevices = platformDevices,
            rawDts = rawDts,
        )
    }

    /** Read root-level compatible strings (null-delimited in the binary file). */
    suspend fun readRootCompatibles(): List<String> {
        val result = RootShell.run("cat $dtBase/compatible | tr '\\0' '\\n'")
        return result.output.lines().map { it.trim() }.filter { it.isNotBlank() }
    }

    /** Decompile the live DT back to human-readable .dts via dtc. */
    suspend fun decompileDt(): String {
        val r = RootShell.run("$dtcBin -I fs $dtBase -O dts 2>/dev/null")
        if (r.success && r.output.isNotBlank()) return r.output
        // dtc not available — fall back to manual property walk
        return manualDtDump()
    }

    /** Walk /sys/firmware/devicetree/base to enumerate platform device nodes. */
    suspend fun collectPlatformDevices(): List<PlatformDevice> {
        val sysDtBase = "/sys/firmware/devicetree/base"
        val socPath = "$sysDtBase/soc"
        val socExists = RootShell.fileExists(socPath)
        val searchBase = if (socExists) socPath else sysDtBase

        val nodes = RootShell.lines("ls $searchBase 2>/dev/null")
        val devices = mutableListOf<PlatformDevice>()

        for (node in nodes) {
            val nodePath = "$searchBase/$node"
            val compatPath = "$nodePath/compatible"
            val regPath = "$nodePath/reg"

            val compatRaw = RootShell.run("cat $compatPath 2>/dev/null | tr '\\0' ' '")
            if (!compatRaw.success || compatRaw.output.isBlank()) continue

            val compatible = compatRaw.output.trim().split(" ").firstOrNull() ?: continue
            val reg = readRegProperty(regPath)

            devices.add(
                PlatformDevice(
                    node = node,
                    compatible = compatible,
                    reg = reg,
                    driverHint = DriverHintMap.lookup(compatible),
                )
            )
        }

        return devices.sortedBy { it.node }
    }

    /** Read a specific DT node's raw DTS fragment (useful for the GPU, USB, etc.). */
    suspend fun readNodeFragment(nodeName: String): String {
        val path = findNodePath(nodeName) ?: return "// node $nodeName not found"
        val props = RootShell.lines("ls $path 2>/dev/null")
        val sb = StringBuilder()
        sb.appendLine("$nodeName {")
        for (prop in props) {
            val value = readProperty("$path/$prop")
            if (value != null) sb.appendLine("  $prop = $value;")
        }
        sb.appendLine("};")
        return sb.toString()
    }

    // ── Private helpers ───────────────────────────────────────────

    /** Count nodes by listing directory entries under /proc/device-tree. */
    private suspend fun countNodes(): Int {
        val r = RootShell.run("find $dtBase -type d 2>/dev/null | wc -l")
        return r.output.trim().toIntOrNull() ?: 0
    }

    /** Estimate property count from decompiled DTS line count. */
    private fun estimatePropertyCount(dts: String): Int =
        dts.lines().count { it.contains("=") && !it.trimStart().startsWith("//") }

    /**
     * Fallback DT dump when dtc binary is not available.
     * Walks /sys/firmware/devicetree/base manually and prints key properties.
     */
    private suspend fun manualDtDump(): String {
        val base = "/sys/firmware/devicetree/base"
        val sb = StringBuilder()
        sb.appendLine("/ {")

        val topNodes = RootShell.lines("ls $base 2>/dev/null")
        for (node in topNodes.take(32)) {
            val nodePath = "$base/$node"
            val isDir = RootShell.run("test -d $nodePath && echo dir").output.trim() == "dir"
            if (!isDir) {
                val value = readProperty(nodePath)
                if (value != null) sb.appendLine("\t$node = $value;")
                continue
            }
            sb.appendLine("\t$node {")
            val children = RootShell.lines("ls $nodePath 2>/dev/null")
            for (child in children.take(16)) {
                val value = readProperty("$nodePath/$child")
                if (value != null) sb.appendLine("\t\t$child = $value;")
            }
            sb.appendLine("\t};")
        }

        sb.appendLine("};")
        return sb.toString()
    }

    /** Read a DT property, returning a formatted string value or null if binary/empty. */
    private suspend fun readProperty(path: String): String? {
        val r = RootShell.run("cat $path 2>/dev/null | strings | head -4 | tr '\\n' ' '")
        val text = r.output.trim()
        return if (text.isNotBlank()) "\"$text\"" else null
    }

    /**
     * Read a `reg` property and format as hex address.
     * DT reg is big-endian 32-bit cells; we read via xxd and extract.
     */
    private suspend fun readRegProperty(regPath: String): String {
        val r = RootShell.run("xxd $regPath 2>/dev/null | head -2")
        if (!r.success || r.output.isBlank()) return "N/A"
        // Parse the first 4 bytes as big-endian u32 address
        val hex = r.output.replace(Regex("[^0-9a-fA-F]"), "")
        return if (hex.length >= 8) "0x${hex.substring(0, 8).uppercase()}" else "N/A"
    }

    /** Fuzzy search for a node by name across the DT hierarchy. */
    private suspend fun findNodePath(nodeName: String): String? {
        val r = RootShell.run("find /sys/firmware/devicetree/base -name '$nodeName' -type d 2>/dev/null | head -1")
        val path = r.output.trim()
        return if (path.isNotBlank()) path else null
    }
}
