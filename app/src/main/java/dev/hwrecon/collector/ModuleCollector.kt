package dev.hwrecon.collector

import dev.hwrecon.model.*
import dev.hwrecon.shell.RootShell
import dev.hwrecon.util.DriverHintMap

/**
 * ModuleCollector
 *
 * Enumerates loaded kernel modules and vendor blob .ko files:
 *   /proc/modules           -> live loaded module list with use-counts and load addresses
 *   /proc/version           -> kernel version string and compiler
 *   /vendor/lib/modules/    -> vendor-supplied .ko blobs (not necessarily loaded)
 *   /sys/module/<name>/     - per-module sysfs parameters (drivers in use)
 *
 * DT compatible binding hints are inferred from module names using
 * a heuristic name-to-compatible mapping in DriverHintMap.
 */
class ModuleCollector {

    private val vendorModulePaths = listOf(
        "/vendor/lib/modules",
        "/vendor/lib64/modules",
        "/system/lib/modules",
        "/system/lib64/modules",
    )

    // -- Public API ----

    suspend fun collect(): ModuleSummary {
        val (kernelVersion, compiler) = readKernelVersion()
        val loaded  = collectLoadedModules()
        val vendor  = collectVendorBlobs()
        val errors  = loaded.count { it.state.equals("Failed", ignoreCase = true) }

        return ModuleSummary(
            kernelVersion  = kernelVersion,
            compiler       = compiler,
            loadedModules  = loaded,
            vendorBlobs    = vendor,
            errorCount     = errors,
        )
    }

    // -- Loaded modules ----

    /**
     * Parse /proc/modules.
     *
     * Format per line:
     *   name  size  usecount  dependencies  state  load_address
     *   e.g.:
     *   msm_drm 4194304 1 - Live 0xffffffc012340000
     */
    suspend fun collectLoadedModules(): List<KernelModule> {
        val lines = RootShell.lines("cat /proc/modules")

        return lines.mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 6) return@mapNotNull null

            val name    = parts[0]
            val size    = parts[1].toLongOrNull() ?: 0L
            val useCount = parts[2].toIntOrNull() ?: 0
            val depsRaw = parts[3]
            val state   = parts[4].replaceFirstChar { it.uppercase() }
            val addr    = parts.getOrNull(5) ?: "0x0"

            val deps = if (depsRaw == "-") emptyList()
                       else depsRaw.split(",").filter { it.isNotBlank() }

            KernelModule(
                name          = name,
                sizeBytes     = size,
                useCount      = useCount,
                dependencies  = deps,
                state         = state,
                loadAddress   = addr,
                dtCompatible  = DriverHintMap.moduleToCompatible(name),
            )
        }.sortedByDescending { it.sizeBytes }
    }

    // -- Vendor blob enumeration ----

    /**
     * Walk known vendor module paths to enumerate .ko blobs that are
     * shipped by the OEM but may or may not be currently loaded.
     */
    suspend fun collectVendorBlobs(): List<VendorBlob> {
        val blobs = mutableListOf<VendorBlob>()

        for (path in vendorModulePaths) {
            if (!RootShell.fileExists(path)) continue

            // Recursively find .ko files up to 3 levels deep
            val files = RootShell.lines("find $path -name '*.ko' -maxdepth 3 2>/dev/null")
            for (filePath in files) {
                val filename = filePath.substringAfterLast("/")
                val size     = readFileSize(filePath)
                val modName  = filename.removeSuffix(".ko")

                blobs.add(
                    VendorBlob(
                        filename      = filename,
                        sizeBytes     = size,
                        path          = filePath,
                        dtCompatible  = DriverHintMap.moduleToCompatible(modName),
                        note          = classifyVendorBlob(modName),
                    )
                )
            }
        }

        return blobs.sortedByDescending { it.sizeBytes }
    }

    // -- Per-module sysfs parameters ----

    /**
     * Read sysfs parameters for a specific module.
     * Returns a map of parameter_name -> current_value.
     */
    suspend fun readModuleParams(moduleName: String): Map<String, String> {
        val paramDir = "/sys/module/$moduleName/parameters"
        if (!RootShell.fileExists(paramDir)) return emptyMap()

        val params = RootShell.listDir(paramDir)
        val result = mutableMapOf<String, String>()

        for (param in params) {
            val r = RootShell.readFile("$paramDir/$param")
            if (r.success) result[param] = r.output.trim()
        }

        return result
    }

    // -- Helpers ----

    private suspend fun readKernelVersion(): Pair<String, String> {
        val r = RootShell.readFile("/proc/version")
        val raw = r.output.trim()

        // "Linux version 6.1.57-android14-... (clang version 17...)"
        val version  = Regex("Linux version ([\\d.\\w-]+)").find(raw)?.groupValues?.get(1) ?: "unknown"
        val compiler = Regex("\\((clang version [\\d.]+)").find(raw)?.groupValues?.get(1)
            ?: Regex("\\((gcc[^)]+)\\)").find(raw)?.groupValues?.get(1)
            ?: "unknown"

        return Pair(version, compiler)
    }

    private suspend fun readFileSize(path: String): Long {
        val r = RootShell.run("stat -c %s $path 2>/dev/null")
        return r.output.trim().toLongOrNull() ?: 0L
    }

    /**
     * Classify a vendor blob based on its name to produce a human-readable note.
     * Helps identify closed-source / proprietary modules at a glance.
     */
    private fun classifyVendorBlob(modName: String): String = when {
        modName.contains("wlan") || modName.contains("wifi") -> "Wi-Fi driver . typically closed source"
        modName.contains("q6v5") || modName.contains("mss")  -> "Modem subsystem PIL loader"
        modName.contains("ipa")                               -> "IP Accelerator . modem offload engine"
        modName.contains("cnss")                              -> "WLAN subsystem init / recovery"
        modName.contains("btfm") || modName.contains("bt_")  -> "Bluetooth / FM codec bridge"
        modName.contains("adreno") || modName.contains("gpu")-> "GPU driver"
        modName.contains("camera") || modName.contains("cam")-> "Camera HAL kernel shim"
        modName.contains("nfc")                               -> "NFC controller driver"
        modName.contains("fingerprint") || modName.contains("fpc") -> "Fingerprint sensor driver"
        modName.contains("vibrator") || modName.contains("vib")    -> "Haptic actuator driver"
        modName.contains("thermal")                           -> "Thermal management driver"
        modName.contains("slimbus") || modName.contains("slim")    -> "SLIMbus audio interconnect"
        modName.contains("ufs")                               -> "UFS storage controller"
        else                                                  -> ""
    }
}
