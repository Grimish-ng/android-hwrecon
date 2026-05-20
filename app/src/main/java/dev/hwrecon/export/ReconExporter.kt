package dev.hwrecon.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dev.hwrecon.model.*
import dev.hwrecon.shell.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * ReconExporter
 *
 * Serialises an HwReconReport to two export formats:
 *   1. JSON  -- structured, machine-readable, suitable for parsing in scripts
 *   2. ZIP   -- human-readable text dump + raw sysfs files, for sharing / offline analysis
 *
 * Output goes to the app's external files dir so it can be shared via
 * a FileProvider content URI without MANAGE_EXTERNAL_STORAGE permission.
 *
 * Usage:
 *   val exporter = ReconExporter(context)
 *   val jsonUri  = exporter.exportJson(report)
 *   val zipUri   = exporter.exportZip(report)
 *   exporter.share(context, zipUri, "application/zip")
 */
class ReconExporter(private val context: Context) {

    private val exportDir: File
        get() = File(context.getExternalFilesDir(null), "hwrecon").also { it.mkdirs() }

    private val timestamp: String
        get() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    // -- JSON export ----

    /**
     * Export the full report as a pretty-printed JSON file.
     * Returns a content:// URI for sharing.
     */
    suspend fun exportJson(report: HwReconReport): Uri = withContext(Dispatchers.IO) {
        val file = File(exportDir, "hwrecon_${timestamp}.json")
        file.writeText(reportToJson(report))
        fileUri(file)
    }

    // -- ZIP export ----

    /**
     * Export a ZIP archive containing:
     *   manifest.txt          -- device info header
     *   device_tree.dts       -- decompiled DTS
     *   cpu_topology.txt      -- per-core dump
     *   modules.txt           -- /proc/modules snapshot
     *   modules_vendor.txt    -- vendor .ko list
     *   hal_manifest.txt      -- VINTF interfaces
     *   dmesg_filtered.txt    -- probe/bind log
     *   iomem.txt             -- /proc/iomem snapshot
     *   interrupts.txt        -- /proc/interrupts snapshot
     *   pinctrl.txt           -- pin mux state
     *   report.json           -- full JSON
     */
    suspend fun exportZip(report: HwReconReport): Uri = withContext(Dispatchers.IO) {
        val file = File(exportDir, "hwrecon_${timestamp}.zip")

        ZipOutputStream(BufferedOutputStream(FileOutputStream(file))).use { zip ->
            // Manifest header
            zip.addEntry("manifest.txt", buildManifest(report))

            // Device Tree
            report.dtSummary?.let { dt ->
                zip.addEntry("device_tree.dts", dt.rawDts)
                zip.addEntry("dt_platform_devices.txt", formatPlatformDevices(dt))
                zip.addEntry("dt_compatibles.txt", dt.rootCompatibles.joinToString("\n"))
            }

            // CPU / SoC
            report.cpuSummary?.let { cpu ->
                zip.addEntry("cpu_topology.txt", formatCpuTopology(cpu))
                zip.addEntry("cpu_features.txt", cpu.features.joinToString("\n"))
                zip.addEntry("clk_summary.txt",  cpu.clockEntries.joinToString("\n") {
                    "${it.name.padEnd(60)} ${formatHz(it.rateHz).padStart(12)} ${if (it.enabled) "ENABLED" else "disabled"}"
                })
                zip.addEntry("regulators.txt", formatRegulators(cpu))
            }

            // Modules
            report.moduleSummary?.let { mods ->
                zip.addEntry("modules_loaded.txt",  formatLoadedModules(mods))
                zip.addEntry("modules_vendor.txt",  formatVendorBlobs(mods))
            }

            // HAL
            report.halSummary?.let { hal ->
                zip.addEntry("hal_interfaces.txt", formatHalInterfaces(hal))
                zip.addEntry("hal_blobs.txt",       hal.rawBlobs.joinToString("\n"))
                zip.addEntry("lshal.txt",           hal.lshalOutput)
            }

            // dmesg
            report.dmesgSummary?.let { dmesg ->
                zip.addEntry("dmesg_filtered.txt",   dmesg.entries.joinToString("\n") { it.raw })
                zip.addEntry("probe_failures.txt",   formatProbeFailures(dmesg))
            }

            // I/O map
            report.ioMapSummary?.let { io ->
                zip.addEntry("iomem.txt",       formatIoMem(io))
                zip.addEntry("interrupts.txt",  formatIrqs(io))
                zip.addEntry("pinctrl.txt",     formatPins(io))
            }

            // Full JSON last
            zip.addEntry("report.json", reportToJson(report))
        }

        fileUri(file)
    }

    // -- Raw sysfs dump ----

    /**
     * Pull raw sysfs files directly using su and add them to an open zip.
     * Call this BEFORE closing the ZipOutputStream if you want raw binary nodes.
     */
    suspend fun addRawSysfsToZip(zip: ZipOutputStream) {
        val rawPaths = listOf(
            "/proc/cpuinfo",
            "/proc/modules",
            "/proc/iomem",
            "/proc/interrupts",
            "/proc/version",
            "/proc/meminfo",
        )
        for (path in rawPaths) {
            val r = RootShell.readFile(path)
            if (r.success && r.output.isNotBlank()) {
                zip.addEntry("raw${path}", r.output)
            }
        }
    }

    // -- Share intent ----

    fun share(ctx: Context, uri: Uri, mimeType: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, "Share HW Recon Report"))
    }

    // -- JSON serialisation ----

    /**
     * Minimal hand-written JSON serialiser -- avoids pulling in Gson/Moshi
     * as a dependency. Produces pretty-printed output.
     */
    private fun reportToJson(report: HwReconReport): String {
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"device\": \"${report.deviceModel}\",")
        sb.appendLine("  \"android\": \"${report.androidVersion}\",")
        sb.appendLine("  \"timestamp\": ${report.timestamp},")

        // DT
        report.dtSummary?.let { dt ->
            sb.appendLine("  \"deviceTree\": {")
            sb.appendLine("    \"rootCompatibles\": ${dt.rootCompatibles.toJsonArray()},")
            sb.appendLine("    \"nodeCount\": ${dt.nodeCount},")
            sb.appendLine("    \"propertyCount\": ${dt.propertyCount},")
            sb.appendLine("    \"platformDevices\": [")
            dt.platformDevices.forEachIndexed { i, d ->
                val comma = if (i < dt.platformDevices.lastIndex) "," else ""
                sb.appendLine("      {\"node\":\"${d.node}\",\"compatible\":\"${d.compatible}\",\"reg\":\"${d.reg}\",\"driverHint\":\"${d.driverHint}\"}$comma")
            }
            sb.appendLine("    ]")
            sb.appendLine("  },")
        }

        // CPU
        report.cpuSummary?.let { cpu ->
            sb.appendLine("  \"cpu\": {")
            sb.appendLine("    \"soc\": \"${cpu.socModel}\",")
            sb.appendLine("    \"socName\": \"${cpu.socName}\",")
            sb.appendLine("    \"clusterLayout\": \"${cpu.clusterLayout}\",")
            sb.appendLine("    \"features\": ${cpu.features.toJsonArray()},")
            sb.appendLine("    \"coreCount\": ${cpu.cores.size}")
            sb.appendLine("  },")
        }

        // Modules
        report.moduleSummary?.let { mods ->
            sb.appendLine("  \"modules\": {")
            sb.appendLine("    \"kernelVersion\": \"${mods.kernelVersion}\",")
            sb.appendLine("    \"loadedCount\": ${mods.loadedModules.size},")
            sb.appendLine("    \"vendorBlobCount\": ${mods.vendorBlobs.size},")
            sb.appendLine("    \"errorCount\": ${mods.errorCount}")
            sb.appendLine("  },")
        }

        // dmesg summary
        report.dmesgSummary?.let { dmesg ->
            sb.appendLine("  \"dmesg\": {")
            sb.appendLine("    \"totalLines\": ${dmesg.totalLines},")
            sb.appendLine("    \"probeOk\": ${dmesg.probeOk},")
            sb.appendLine("    \"probeDeferred\": ${dmesg.probeDeferred},")
            sb.appendLine("    \"probeFailed\": ${dmesg.probeFailed},")
            sb.appendLine("    \"failures\": [")
            dmesg.failures.forEachIndexed { i, f ->
                val comma = if (i < dmesg.failures.lastIndex) "," else ""
                sb.appendLine("      {\"driver\":\"${f.driver}\",\"compatible\":\"${f.dtCompatible}\",\"error\":\"${f.errorCode}\",\"cause\":\"${f.rootCause.replace("\"","'")}\"} $comma")
            }
            sb.appendLine("    ]")
            sb.appendLine("  }")
        }

        sb.appendLine("}")
        return sb.toString()
    }

    // -- Text formatters ----

    private fun buildManifest(report: HwReconReport) = buildString {
        appendLine("=== HW.RECON Report ===============================")
        appendLine("Device  : ${report.deviceModel}")
        appendLine("Android : ${report.androidVersion}")
        appendLine("Created : ${Date(report.timestamp)}")
        appendLine("===================================================")
    }

    private fun formatPlatformDevices(dt: DtSummary) = buildString {
        appendLine("# Platform Devices - DT Binding Cross-reference")
        appendLine("%-40s %-40s %-12s %s".format("Node", "Compatible", "Reg", "Driver"))
        appendLine("-".repeat(110))
        dt.platformDevices.forEach { d ->
            appendLine("%-40s %-40s %-12s %s".format(d.node, d.compatible, d.reg, d.driverHint))
        }
    }

    private fun formatCpuTopology(cpu: CpuSummary) = buildString {
        appendLine("# CPU Topology - ${cpu.socModel} (${cpu.socName})")
        appendLine("Architecture : ${cpu.architecture}")
        appendLine("Clusters     : ${cpu.clusterLayout}")
        appendLine("L3 Cache     : ${cpu.l3SizeMb} KB")
        appendLine()
        appendLine("%-8s %-22s %-12s %-10s %-14s %s".format("Core","Type","MaxFreq","Cluster","Governor","L2"))
        appendLine("-".repeat(90))
        cpu.cores.forEach { c ->
            appendLine("%-8s %-22s %-12s %-10s %-14s %d KB".format(
                "cpu${c.id}", c.compatible, formatHz(c.maxFreqKhz * 1000), c.cluster, c.governor, c.l2SizeKb))
        }
    }

    private fun formatRegulators(cpu: CpuSummary) = buildString {
        appendLine("# Regulator Tree")
        cpu.regulators.forEach { r ->
            appendLine("${r.name.padEnd(30)} ${(r.voltageUv/1000.0).toString().padStart(8)} mV  ${r.state.padEnd(8)}  ${r.consumers.joinToString(", ")}")
        }
    }

    private fun formatLoadedModules(mods: ModuleSummary) = buildString {
        appendLine("# Loaded Modules - kernel ${mods.kernelVersion}")
        appendLine("%-40s %10s %6s %-12s %s".format("Module", "Size", "UseCount", "State", "DT Compatible"))
        appendLine("-".repeat(100))
        mods.loadedModules.forEach { m ->
            appendLine("%-40s %10s %6d %-12s %s".format(
                m.name, formatBytes(m.sizeBytes), m.useCount, m.state, m.dtCompatible ?: ""))
        }
    }

    private fun formatVendorBlobs(mods: ModuleSummary) = buildString {
        appendLine("# Vendor Module Blobs")
        mods.vendorBlobs.forEach { b ->
            appendLine("${b.filename.padEnd(40)} ${formatBytes(b.sizeBytes).padStart(10)}  ${b.note}")
        }
    }

    private fun formatHalInterfaces(hal: HalSummary) = buildString {
        appendLine("# VINTF HAL Interfaces")
        appendLine("%-50s %-8s %-12s %-30s %s".format("Interface","Version","Transport","Blob","LiveState"))
        appendLine("-".repeat(120))
        hal.declaredInterfaces.forEach { i ->
            appendLine("%-50s %-8s %-12s %-30s %s".format(
                i.name, i.version, i.transport, i.blob, i.liveState))
        }
    }

    private fun formatProbeFailures(dmesg: DmesgSummary) = buildString {
        appendLine("# Probe Failures - DT Cross-reference")
        dmesg.failures.forEach { f ->
            appendLine("Driver     : ${f.driver}")
            appendLine("Compatible : ${f.dtCompatible}")
            appendLine("Error      : ${f.errorCode}")
            appendLine("Cause      : ${f.rootCause}")
            appendLine()
        }
    }

    private fun formatIoMem(io: IoMapSummary) = buildString {
        appendLine("# /proc/iomem")
        io.regions.forEach { r ->
            val range = "0x${r.start.toString(16).padStart(8,'0')}-0x${r.end.toString(16).padStart(8,'0')}"
            appendLine("${range.padEnd(28)} ${formatBytes(r.sizeBytes).padStart(10)}  ${r.name}  ${r.dtNote}")
        }
    }

    private fun formatIrqs(io: IoMapSummary) = buildString {
        appendLine("# /proc/interrupts - top consumers")
        io.interrupts.forEach { i ->
            appendLine("IRQ %4d : %12d  %-20s %s".format(i.irqNumber, i.count, i.type, i.device))
        }
    }

    private fun formatPins(io: IoMapSummary) = buildString {
        appendLine("# Pin Mux State")
        io.pins.forEach { p ->
            val note = if (p.note != null) "  <- ${p.note}" else ""
            appendLine("${p.gpio.padEnd(10)} func=${p.function.padEnd(20)} pull=${p.pull.padEnd(5)} drive=${p.drive}$note")
        }
    }

    // -- Helpers ----

    private fun ZipOutputStream.addEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun fileUri(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    private fun List<String>.toJsonArray(): String =
        joinToString(",", "[", "]") { "\"${it.replace("\"", "\\\"")}\"" }

    private fun formatHz(hz: Long): String = when {
        hz >= 1_000_000_000L -> "${"%.2f".format(hz / 1_000_000_000.0)} GHz"
        hz >= 1_000_000L     -> "${"%.0f".format(hz / 1_000_000.0)} MHz"
        hz >= 1_000L         -> "${"%.0f".format(hz / 1_000.0)} kHz"
        else                 -> "$hz Hz"
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_048_576L -> "${"%.1f".format(bytes / 1_048_576.0)} MB"
        bytes >= 1_024L     -> "${"%.0f".format(bytes / 1_024.0)} KB"
        else                -> "$bytes B"
    }
}
