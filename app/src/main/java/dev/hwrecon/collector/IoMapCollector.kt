package dev.hwrecon.collector

import dev.hwrecon.model.*
import dev.hwrecon.shell.RootShell

/**
 * IoMapCollector
 *
 * Collects physical memory layout, interrupt assignments, and pin mux state:
 *   /proc/iomem          -> physical address map (I/O regions, DRAM, reserved)
 *   /proc/interrupts     -> IRQ number -> device mapping with hit counts
 *   /sys/kernel/debug/pinctrl/  -> pin mux function assignments
 *
 * The iomem map is critical for writing memory-mapped peripheral nodes in DT:
 * every `reg = <addr size>` pair in a DT node comes from this map.
 * The interrupt table maps GIC SPI numbers directly to DT `interrupts` cells.
 */
class IoMapCollector {

    // -- Public API ----

    suspend fun collect(): IoMapSummary {
        val regions    = collectIoMem()
        val interrupts = collectInterrupts()
        val pins       = collectPinCtrl()

        return IoMapSummary(
            regions    = regions,
            interrupts = interrupts,
            pins       = pins,
        )
    }

    // -- /proc/iomem ----

    /**
     * Parse /proc/iomem.
     *
     * Format:
     *   00000000-000fffff : Boot ROM
     *   0a600000-0a7fffff : 0a600000.usb
     *   80000000-ffffffff : System RAM
     *     80000000-813fffff :   Kernel code
     *
     * We read top-level entries only (no leading spaces = top-level).
     */
    suspend fun collectIoMem(): List<IoMemRegion> {
        val lines = RootShell.lines("cat /proc/iomem")

        return lines
            .filter { !it.startsWith("  ") } // skip sub-regions
            .mapNotNull { line ->
                // "start-end : name"
                val dashIdx = line.indexOf('-')
                val colonIdx = line.indexOf(':')
                if (dashIdx == -1 || colonIdx == -1) return@mapNotNull null

                val startHex = line.substring(0, dashIdx).trim()
                val endHex   = line.substring(dashIdx + 1, colonIdx).trim()
                val name     = line.substring(colonIdx + 1).trim()

                val start = startHex.toLongOrNull(16) ?: return@mapNotNull null
                val end   = endHex.toLongOrNull(16)   ?: return@mapNotNull null

                IoMemRegion(
                    start   = start,
                    end     = end,
                    name    = name,
                    dtNote  = inferDtNote(name, start),
                )
            }
            .sortedBy { it.start }
    }

    // -- /proc/interrupts ----

    /**
     * Parse /proc/interrupts.
     *
     * Format (first few columns):
     *   IRQ  cpu0  cpu1 ... type  device
     *   300: 1284933  0  0  GIC-0 300 Level  adreno
     */
    suspend fun collectInterrupts(): List<IrqEntry> {
        val lines = RootShell.lines("cat /proc/interrupts")
        if (lines.isEmpty()) return emptyList()

        // First line is the CPU header -- determine column count
        val cpuCount = lines[0].trim().split(Regex("\\s+")).size

        return lines.drop(1).mapNotNull { line ->
            val trimmed = line.trim()
            val colonIdx = trimmed.indexOf(':')
            if (colonIdx == -1) return@mapNotNull null

            val irqNum = trimmed.substring(0, colonIdx).trim().toIntOrNull() ?: return@mapNotNull null
            val rest   = trimmed.substring(colonIdx + 1).trim()
            val parts  = rest.split(Regex("\\s+"))

            if (parts.size < cpuCount + 2) return@mapNotNull null

            // Sum counts across all CPUs
            val totalCount = parts.take(cpuCount).mapNotNull { it.toLongOrNull() }.sum()
            // Type field comes after the CPU count columns
            val typeField  = listOfNotNull(
                parts.getOrNull(cpuCount),
                parts.getOrNull(cpuCount + 1)
            ).joinToString(" ")
            val device     = parts.drop(cpuCount + 2).joinToString(" ").trim()

            IrqEntry(
                irqNumber = irqNum,
                count     = totalCount,
                type      = typeField.trim().ifBlank { "?" },
                device    = device.ifBlank { "unknown" },
            )
        }.sortedByDescending { it.count }
    }

    // -- /sys/kernel/debug/pinctrl ----

    /**
     * Dump pin mux state from the first available pinctrl controller.
     * Useful for identifying which GPIO is assigned to which peripheral
     * (camera I2C, NFC IRQ, display TE, etc.).
     */
    suspend fun collectPinCtrl(): List<PinEntry> {
        val pinctrlBase = "/sys/kernel/debug/pinctrl"
        val controllers = RootShell.listDir(pinctrlBase)
        val controller  = controllers.firstOrNull { it.contains("sm8") || it.contains("tlmm") }
            ?: controllers.firstOrNull()
            ?: return emptyList()

        val pinmuxPath = "$pinctrlBase/$controller/pinmux-pins"
        val r = RootShell.run("cat $pinmuxPath 2>/dev/null | head -120")
        if (!r.success || r.output.isBlank()) {
            // Try pingroups as fallback
            return collectFromPinGroups("$pinctrlBase/$controller")
        }

        return parsePinmuxPins(r.output)
    }

    private suspend fun collectFromPinGroups(controllerPath: String): List<PinEntry> {
        val r = RootShell.run("cat $controllerPath/pingroups 2>/dev/null | head -200")
        if (!r.success || r.output.isBlank()) return emptyList()

        return parsePinGroups(r.output)
    }

    // -- Parsers ----

    /**
     * Parse "pinmux-pins" output:
     *   pin 0 (gpio0): qup0_se0 (GPIO UNCLAIMED)
     *   pin 1 (gpio1): UNCLAIMED
     */
    private fun parsePinmuxPins(raw: String): List<PinEntry> {
        var group = 0
        return raw.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                // "pin N (gpioN): FUNCTION (device)"
                val pinMatch  = Regex("pin\\s+(\\d+)\\s+\\(([^)]+)\\):\\s*(.+)").find(line)
                    ?: return@mapNotNull null
                val pinNum   = pinMatch.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val gpioName = pinMatch.groupValues[2]
                val rest     = pinMatch.groupValues[3]

                val funcParts = rest.split(" ")
                val function  = funcParts.firstOrNull()?.takeIf { it != "UNCLAIMED" } ?: "gpio"

                PinEntry(
                    group    = group++,
                    gpio     = gpioName,
                    function = function,
                    pull     = "N/A",
                    drive    = "N/A",
                    note     = inferPinNote(function, gpioName),
                )
            }
    }

    /**
     * Parse "pingroups" output -- alternative format on some kernels:
     *   group: qup0_se0_pins [0 1]
     *   function: qup0_se0
     */
    private fun parsePinGroups(raw: String): List<PinEntry> {
        val entries = mutableListOf<PinEntry>()
        var curGroup = ""
        var curFunc  = "gpio"

        raw.lines().forEach { line ->
            val t = line.trim()
            when {
                t.startsWith("group:") -> curGroup = t.removePrefix("group:").trim()
                t.startsWith("function:") -> {
                    curFunc = t.removePrefix("function:").trim()
                    entries.add(
                        PinEntry(
                            group    = entries.size,
                            gpio     = curGroup,
                            function = curFunc,
                            pull     = "N/A",
                            drive    = "N/A",
                            note     = inferPinNote(curFunc, curGroup),
                        )
                    )
                }
            }
        }
        return entries
    }

    // -- Helpers ----

    /** Map a physical address range name to a DT node hint. */
    private fun inferDtNote(name: String, start: Long): String = when {
        name.contains("usb", ignoreCase = true)     -> "usb@${start.toString(16)}"
        name.contains("gpu", ignoreCase = true) ||
        name.contains("adreno", ignoreCase = true)  -> "gpu@${start.toString(16)}"
        name.contains("pcie", ignoreCase = true)    -> "pcie@${start.toString(16)}"
        name.contains("Serial RAM", ignoreCase = true) ||
        name.contains("System RAM", ignoreCase = true) -> "memory@${start.toString(16)}"
        name.contains("watchdog", ignoreCase = true)-> "watchdog@${start.toString(16)}"
        name.contains("APSS", ignoreCase = true)    -> "timer@${start.toString(16)}"
        name.contains("display", ignoreCase = true) ||
        name.contains("mdss", ignoreCase = true)    -> "mdss@${start.toString(16)}"
        name.contains("qup", ignoreCase = true) ||
        name.contains("uart", ignoreCase = true) ||
        name.contains("i2c", ignoreCase = true)     -> "serial@${start.toString(16)}"
        name.endsWith(".gpu") || name.endsWith(".usb") ||
        name.endsWith(".pcie")                      -> "${name.substringAfterLast('/').substringBefore('.')}"
        else -> ""
    }

    /** Annotate a pin's function with a human-readable note for common peripherals. */
    private fun inferPinNote(function: String, gpio: String): String? = when {
        function.contains("cam",      ignoreCase = true) -> "Camera I2C / MCLK"
        function.contains("nfc",      ignoreCase = true) -> "NFC IRQ / enable"
        function.contains("dp_hot",   ignoreCase = true) -> "DisplayPort hotplug detect"
        function.contains("mi2s",     ignoreCase = true) -> "Audio MI2S data / clk"
        function.contains("sdc",      ignoreCase = true) ||
        function.contains("ufs",      ignoreCase = true) -> "UFS / eMMC storage"
        function.contains("qup",      ignoreCase = true) -> "QUP serial (I2C/SPI/UART)"
        function.contains("fingerprint", ignoreCase = true) -> "Fingerprint SPI / IRQ"
        function.contains("ts",       ignoreCase = true) ||
        function.contains("touch",    ignoreCase = true) -> "Touchscreen IRQ / reset"
        else -> null
    }
}
