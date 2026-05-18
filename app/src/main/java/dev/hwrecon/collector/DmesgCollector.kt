package dev.hwrecon.collector

import dev.hwrecon.model.*
import dev.hwrecon.shell.RootShell

/**
 * DmesgCollector
 *
 * Reads the kernel ring buffer (dmesg) and filters it for driver-binding
 * events critical to device tree reverse-engineering:
 *   - probe success / failure / deferral
 *   - platform device ↔ driver binding
 *   - compatible string matches
 *   - HAL crash / respawn markers
 *   - firmware / blob not-found errors
 *
 * This is the single most useful collector for DT work:
 * every `samsung,sec-nfc probe failed: -ENOENT` tells you exactly what
 * firmware binary is missing and which DT node it maps to.
 */
class DmesgCollector {

    // Probe / bind keywords to filter for
    private val bindKeywords = listOf(
        "probe", "bind", "compatible", "platform", "of_device",
        "attached", "registered", "initialized",
    )

    private val errorKeywords = listOf(
        "error", "fail", "err", "timeout", "abort", "crash",
        "ENOENT", "EPROBE_DEFER", "ENODEV", "panic",
    )

    private val warnKeywords = listOf(
        "warn", "defer", "retry", "calibration", "missing",
    )

    // ── Public API ────────────────────────────────────────────────

    suspend fun collect(): DmesgSummary {
        val raw = readDmesg()
        val entries = parse(raw)

        val probeOk       = entries.count { it.isBindEvent && it.level == DmesgLevel.OK }
        val probeDeferred = entries.count { it.raw.contains("EPROBE_DEFER", ignoreCase = true) }
        val probeFailed   = entries.count { it.level == DmesgLevel.ERROR && it.isProbeEvent }

        return DmesgSummary(
            totalLines    = raw.lines().size,
            probeOk       = probeOk,
            probeDeferred = probeDeferred,
            probeFailed   = probeFailed,
            entries       = entries,
            failures      = extractFailures(entries),
        )
    }

    // ── Raw dmesg ─────────────────────────────────────────────────

    /**
     * Read the kernel ring buffer via `dmesg`.
     * We filter inline using grep to avoid shipping 25k+ lines through IPC.
     *
     * Two passes:
     *   1. Capture all probe/bind events (binding map)
     *   2. Capture all errors (failure analysis)
     * Merge and deduplicate by timestamp.
     */
    suspend fun readDmesg(): String {
        val keywordPattern = (bindKeywords + errorKeywords + warnKeywords)
            .joinToString("|") { it }

        // Try dmesg with human-readable timestamps first
        var r = RootShell.run(
            "dmesg -T 2>/dev/null | grep -iE '($keywordPattern)' | head -300"
        )
        if (r.success && r.output.isNotBlank()) return r.output

        // Fallback: plain dmesg with raw timestamps
        r = RootShell.run(
            "dmesg 2>/dev/null | grep -iE '($keywordPattern)' | head -300"
        )
        return r.output
    }

    /** Read full dmesg (no filter) — useful for export. */
    suspend fun readFullDmesg(): String =
        RootShell.run("dmesg 2>/dev/null").output

    // ── Parsing ───────────────────────────────────────────────────

    fun parse(raw: String): List<DmesgEntry> =
        raw.lines()
            .filter { it.isNotBlank() }
            .map { parseLine(it) }
            .sortedBy { it.timestamp }

    private fun parseLine(line: String): DmesgEntry {
        // Standard format: [    1.234567] subsystem: message
        //                  or [ 1234.567] ...
        val tsMatch = Regex("\\[\\s*([\\d.]+)\\]").find(line)
        val ts      = tsMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
        val body    = tsMatch?.let { line.substring(it.range.last + 1).trim() } ?: line

        // Extract device/driver from "platform DEVICE: DRIVER bound" style messages
        val colonIdx = body.indexOf(":")
        val subsystem = if (colonIdx > 0) body.substring(0, colonIdx).trim() else null
        val message   = if (colonIdx > 0) body.substring(colonIdx + 1).trim() else body

        // Classify level
        val level = when {
            errorKeywords.any { kw -> body.contains(kw, ignoreCase = true) } &&
                body.contains(Regex("fail|error|ENOENT|ENODEV|panic|abort", RegexOption.IGNORE_CASE)) ->
                DmesgLevel.ERROR
            warnKeywords.any { kw -> body.contains(kw, ignoreCase = true) } ->
                DmesgLevel.WARN
            bindKeywords.any { kw -> body.contains(kw, ignoreCase = true) } &&
                !body.contains("fail", ignoreCase = true) ->
                DmesgLevel.OK
            else ->
                DmesgLevel.INFO
        }

        val isProbe = body.contains("probe", ignoreCase = true)
        val isBind  = body.contains("bound", ignoreCase = true) ||
                      body.contains("registered", ignoreCase = true) ||
                      body.contains("initialized", ignoreCase = true)

        return DmesgEntry(
            timestamp    = ts,
            raw          = line,
            level        = level,
            isProbeEvent = isProbe,
            isBindEvent  = isBind,
            driver       = extractDriver(body),
            device       = subsystem,
        )
    }

    // ── Failure extraction ────────────────────────────────────────

    /**
     * Cross-reference ERROR-level probe entries against known error codes
     * to produce human-readable root-cause notes.
     */
    private fun extractFailures(entries: List<DmesgEntry>): List<ProbeFailure> =
        entries
            .filter { it.level == DmesgLevel.ERROR && it.isProbeEvent }
            .mapNotNull { entry ->
                val errCode = Regex("-E[A-Z_]+").find(entry.raw)?.value ?: return@mapNotNull null
                val driver  = entry.driver ?: entry.device ?: return@mapNotNull null
                ProbeFailure(
                    driver       = driver,
                    dtCompatible = inferCompatibleFromDriver(driver),
                    errorCode    = errCode,
                    rootCause    = explainErrorCode(errCode, entry.raw),
                )
            }
            .distinctBy { it.driver }

    // ── Helpers ───────────────────────────────────────────────────

    /**
     * Extract a likely driver name from a dmesg line.
     * Handles patterns like:
     *   "msm_drm: probe failed"
     *   "platform 3d00000.gpu: driver adreno bound"
     *   "nfc_sec: -ENOENT"
     */
    private fun extractDriver(body: String): String? {
        // "platform ADDR.NAME: driver DRIVER bound"
        val boundMatch = Regex("driver ([\\w_-]+) bound").find(body)
        if (boundMatch != null) return boundMatch.groupValues[1]

        // "NAME: probe" — name before first colon
        val colonIdx = body.indexOf(":")
        if (colonIdx > 0) {
            val candidate = body.substring(0, colonIdx).trim()
            if (candidate.length < 40 && !candidate.contains(" ")) return candidate
        }
        return null
    }

    /** Heuristic: map a driver/module name back to a likely DT compatible string. */
    private fun inferCompatibleFromDriver(driver: String): String = when {
        driver.contains("adreno")  -> "qcom,adreno"
        driver.contains("msm_drm") -> "qcom,mdss"
        driver.contains("wcd")     -> "qcom,wcd9xxx"
        driver.contains("cs35l41") -> "cirrus,cs35l41"
        driver.contains("sec_ts")  -> "samsung,sec_ts"
        driver.contains("nfc")     -> "samsung,sec-nfc"
        driver.contains("ath11k")  -> "pci18ee:006c"
        driver.contains("qcom_pcie") -> "qcom,pcie-sm8650"
        driver.contains("thermal") -> "qcom,tsens"
        else                       -> "unknown"
    }

    /**
     * Map a POSIX error code string to a short root-cause explanation.
     * Targeted at the errors most commonly seen in Android boot logs.
     */
    private fun explainErrorCode(code: String, context: String): String = when (code) {
        "-ENOENT"       -> "Missing firmware binary in /vendor/firmware or /firmware/image"
        "-EPROBE_DEFER" -> "Dependency not yet ready — will retry; check ordering in DT"
        "-ENODEV"       -> "Device not detected — check I2C/SPI address or power rail"
        "-ENOMEM"       -> "Memory allocation failure — check CMA/DMA reserved regions in DT"
        "-EINVAL"       -> "Invalid parameter — mismatched DT property or firmware version"
        "-EACCES"       -> "Permission denied — SELinux policy or secureboot blocking access"
        "-ETIMEDOUT"    -> "Communication timeout — check bus speed, pull-up resistors in DT"
        "-EBUSY"        -> "Resource in use — duplicate DT node or conflicting driver"
        "-EIO"          -> "I/O error — hardware not responding; check power and clock enables"
        "-EOPNOTSUPP"   -> "Operation not supported — driver version mismatch with firmware"
        else -> when {
            context.contains("firmware") -> "Firmware file missing from ramdisk or vendor partition"
            context.contains("calibration") -> "Sensor/thermal calibration data not found in NVRAM"
            context.contains("clk")     -> "Clock not enabled — check gcc/cam_cc clock DT bindings"
            else                        -> "Unknown — inspect full dmesg context for surrounding lines"
        }
    }
}
