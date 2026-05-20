package dev.hwrecon.collector

import dev.hwrecon.model.*
import dev.hwrecon.shell.RootShell

/**
 * HalCollector
 *
 * Enumerates HIDL and AIDL HAL interfaces on the device:
 *   /vendor/etc/vintf/manifest.xml              -> declared HAL interfaces
 *   /vendor/etc/vintf/compatibility_matrix.xml  -> required HAL versions
 *   /vendor/lib64/hw/                           -> HAL implementation blobs
 *   /vendor/lib/hw/                             -> 32-bit HAL blobs
 *   lshal                                       -> live hwservicemanager state
 *
 * Useful for device tree work:
 *   - Maps HAL interface names to their .so blobs
 *   - Identifies which blobs are closed-source (no corresponding .ko)
 *   - DEAD entries in lshal point to missing firmware or driver failures
 */
class HalCollector {

    private val manifestPaths = listOf(
        "/vendor/etc/vintf/manifest.xml",
        "/odm/etc/vintf/manifest.xml",
        "/system/etc/vintf/manifest.xml",
    )

    private val blobPaths = listOf(
        "/vendor/lib64/hw",
        "/vendor/lib/hw",
        "/system/lib64/hw",
    )

    // -- Public API ----

    suspend fun collect(): HalSummary {
        val declared = collectVintfInterfaces()
        val blobs    = collectRawBlobs()
        val lshal    = runLshal()

        // Annotate declared interfaces with live state from lshal
        val annotated = declared.map { hal ->
            val state = parseLshalState(lshal, hal.name)
            hal.copy(liveState = state)
        }

        return HalSummary(
            declaredInterfaces = annotated,
            rawBlobs           = blobs,
            lshalOutput        = lshal,
        )
    }

    // -- VINTF manifest ----

    /**
     * Parse VINTF manifest XML to extract HAL interface declarations.
     *
     * VINTF XML structure (simplified):
     *   <manifest>
     *     <hal format="hidl">
     *       <name>android.hardware.camera.provider</name>
     *       <version>2.7</version>
     *       <transport>hwbinder</transport>
     *     </hal>
     *   </manifest>
     */
    suspend fun collectVintfInterfaces(): List<HalInterface> {
        val interfaces = mutableListOf<HalInterface>()

        for (path in manifestPaths) {
            if (!RootShell.fileExists(path)) continue
            val xml = RootShell.readFile(path).output
            interfaces += parseVintfXml(xml)
        }

        return interfaces.distinctBy { it.name }.sortedBy { it.name }
    }

    private fun parseVintfXml(xml: String): List<HalInterface> {
        val results = mutableListOf<HalInterface>()

        // Extract <hal> blocks with a simple regex-free block scan
        val halBlocks = extractXmlBlocks(xml, "hal")

        for (block in halBlocks) {
            val name      = extractXmlTag(block, "name") ?: continue
            val version   = extractXmlTag(block, "version") ?: "?"
            val transport = extractXmlTag(block, "transport") ?: "binder"
            val blob      = inferBlobName(name)

            results.add(
                HalInterface(
                    name      = name,
                    version   = version,
                    transport = transport,
                    blob      = blob,
                    liveState = "UNKNOWN", // filled in by lshal annotation
                )
            )
        }

        return results
    }

    // -- Raw blob enumeration ----

    /** List all .so blobs in known HAL blob directories. */
    suspend fun collectRawBlobs(): List<String> {
        val blobs = mutableListOf<String>()

        for (path in blobPaths) {
            if (!RootShell.fileExists(path)) continue
            val files = RootShell.lines("ls $path 2>/dev/null | grep '\\.so$'")
            blobs += files
        }

        return blobs.distinct().sorted()
    }

    // -- lshal ----

    /**
     * Run lshal to query the hwservicemanager for live HAL process state.
     * Requires root; lshal is available in /system/bin on Android 8+.
     */
    suspend fun runLshal(): String {
        val r = RootShell.run("lshal 2>&1 | head -80")
        if (r.success && r.output.isNotBlank()) return r.output

        // Fallback: query hwservicemanager via service list
        val fallback = RootShell.run("service list 2>/dev/null | grep -i 'hardware' | head -30")
        return fallback.output.ifBlank { "lshal not available on this build" }
    }

    // -- Helpers ----

    /**
     * Determine ALIVE/DEAD/UNKNOWN for an interface from lshal output.
     * lshal marks lines with "X" (dead) or no marker (alive).
     */
    private fun parseLshalState(lshalOutput: String, ifaceName: String): String {
        val line = lshalOutput.lines().firstOrNull { it.contains(ifaceName) }
            ?: return "UNKNOWN"
        return when {
            line.trimStart().startsWith("X") -> "DEAD"
            line.trimStart().startsWith("?") -> "UNKNOWN"
            line.contains("DEAD")            -> "DEAD"
            else                             -> "ALIVE"
        }
    }

    /**
     * Infer a likely blob filename from an interface name.
     * android.hardware.graphics.composer3 -> libhwcomposer.so
     */
    private fun inferBlobName(ifaceName: String): String {
        val blobHints = mapOf(
            "camera.provider"           to "libcameras2ndk.so",
            "audio.core"                to "libaudiohal.so",
            "graphics.composer"         to "libhwcomposer.so",
            "sensors"                   to "sensors.qcom.so",
            "biometrics.fingerprint"    to "fingerprint.default.so",
            "neuralnetworks"            to "libQnnHtp.so",
            "radio"                     to "libril-qc-hal-qmi.so",
            "nfc"                       to "nfc_nci.sec.so",
            "gps"                       to "gps.default.so",
            "thermal"                   to "thermal.qcom.so",
            "vibrator"                  to "vibrator.default.so",
            "bluetooth"                 to "libbluetooth.so",
            "wifi"                      to "libwifi-hal-qcom.so",
            "power"                     to "power.qcom.so",
            "drm"                       to "libdrmclearkeyplugin.so",
            "health"                    to "libhealthd.default.so",
        )
        val key = blobHints.keys.firstOrNull { ifaceName.contains(it, ignoreCase = true) }
        return blobHints[key] ?: "${ifaceName.substringAfterLast('.').lowercase()}.default.so"
    }

    /** Extract all blocks enclosed in <tagName>...</tagName>. */
    private fun extractXmlBlocks(xml: String, tagName: String): List<String> {
        val results = mutableListOf<String>()
        var start = 0
        while (true) {
            val open  = xml.indexOf("<$tagName", start)
            if (open == -1) break
            val close = xml.indexOf("</$tagName>", open)
            if (close == -1) break
            results.add(xml.substring(open, close + "</$tagName>".length))
            start = close + 1
        }
        return results
    }

    /** Extract text content of the first occurrence of <tag>...</tag>. */
    private fun extractXmlTag(xml: String, tag: String): String? {
        val open  = xml.indexOf("<$tag>").takeIf { it != -1 } ?: return null
        val close = xml.indexOf("</$tag>", open).takeIf { it != -1 } ?: return null
        return xml.substring(open + tag.length + 2, close).trim()
    }
}
