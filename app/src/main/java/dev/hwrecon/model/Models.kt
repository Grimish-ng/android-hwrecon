package dev.hwrecon.model

// ─────────────────────────────────────────────
//  Device Tree
// ─────────────────────────────────────────────

data class DtNode(
    val name: String,
    val path: String,
    val compatible: List<String> = emptyList(),
    val reg: String? = null,
    val properties: Map<String, String> = emptyMap(),
    val children: List<DtNode> = emptyList(),
)

data class DtSummary(
    val rootCompatibles: List<String>,
    val nodeCount: Int,
    val propertyCount: Int,
    val platformDevices: List<PlatformDevice>,
    val rawDts: String,
)

data class PlatformDevice(
    val node: String,
    val compatible: String,
    val reg: String,
    val driverHint: String,
)

// ─────────────────────────────────────────────
//  CPU / SoC
// ─────────────────────────────────────────────

data class CpuCore(
    val id: Int,
    val cluster: String,          // LITTLE / big / prime
    val compatible: String,       // cortex-a520, cortex-x4, etc.
    val maxFreqKhz: Long,
    val curFreqKhz: Long,
    val governor: String,
    val l2SizeKb: Int,
    val online: Boolean,
)

data class CpuSummary(
    val socModel: String,
    val socName: String,
    val architecture: String,
    val clusterLayout: String,
    val l3SizeMb: Int,
    val processNode: String,
    val cores: List<CpuCore>,
    val features: List<String>,
    val clockEntries: List<ClockEntry>,
    val regulators: List<RegulatorEntry>,
)

data class ClockEntry(
    val name: String,
    val rateHz: Long,
    val enabled: Boolean,
    val parent: String?,
)

data class RegulatorEntry(
    val name: String,
    val voltageUv: Int,
    val state: String,          // ON / OFF / BYPASS / ECO
    val consumers: List<String>,
)

// ─────────────────────────────────────────────
//  Kernel Modules
// ─────────────────────────────────────────────

data class KernelModule(
    val name: String,
    val sizeBytes: Long,
    val useCount: Int,
    val dependencies: List<String>,
    val state: String,          // Live / Loading / Unloading
    val loadAddress: String,
    val dtCompatible: String?,  // inferred from name heuristics
)

data class VendorBlob(
    val filename: String,
    val sizeBytes: Long,
    val path: String,
    val dtCompatible: String?,
    val note: String,
)

data class ModuleSummary(
    val kernelVersion: String,
    val compiler: String,
    val loadedModules: List<KernelModule>,
    val vendorBlobs: List<VendorBlob>,
    val errorCount: Int,
)

// ─────────────────────────────────────────────
//  HAL / Blobs
// ─────────────────────────────────────────────

data class HalInterface(
    val name: String,
    val version: String,
    val transport: String,       // binder / hwbinder / passthrough
    val blob: String,
    val liveState: String,       // ALIVE / DEAD / UNKNOWN
)

data class HalSummary(
    val declaredInterfaces: List<HalInterface>,
    val rawBlobs: List<String>,
    val lshalOutput: String,
)

// ─────────────────────────────────────────────
//  dmesg
// ─────────────────────────────────────────────

enum class DmesgLevel { OK, WARN, ERROR, INFO }

data class DmesgEntry(
    val timestamp: Float,
    val raw: String,
    val level: DmesgLevel,
    val isProbeEvent: Boolean,
    val isBindEvent: Boolean,
    val driver: String?,
    val device: String?,
)

data class ProbeFailure(
    val driver: String,
    val dtCompatible: String,
    val errorCode: String,
    val rootCause: String,
)

data class DmesgSummary(
    val totalLines: Int,
    val probeOk: Int,
    val probeDeferred: Int,
    val probeFailed: Int,
    val entries: List<DmesgEntry>,
    val failures: List<ProbeFailure>,
)

// ─────────────────────────────────────────────
//  I/O Memory Map
// ─────────────────────────────────────────────

data class IoMemRegion(
    val start: Long,
    val end: Long,
    val name: String,
    val dtNote: String,
) {
    val sizeBytes get() = end - start + 1
}

data class IrqEntry(
    val irqNumber: Int,
    val count: Long,
    val type: String,
    val device: String,
)

data class PinEntry(
    val group: Int,
    val gpio: String,
    val function: String,
    val pull: String,
    val drive: String,
    val note: String?,
)

data class IoMapSummary(
    val regions: List<IoMemRegion>,
    val interrupts: List<IrqEntry>,
    val pins: List<PinEntry>,
)

// ─────────────────────────────────────────────
//  Top-level aggregated result
// ─────────────────────────────────────────────

data class HwReconReport(
    val deviceModel: String,
    val androidVersion: String,
    val timestamp: Long,
    val dtSummary: DtSummary?,
    val cpuSummary: CpuSummary?,
    val moduleSummary: ModuleSummary?,
    val halSummary: HalSummary?,
    val dmesgSummary: DmesgSummary?,
    val ioMapSummary: IoMapSummary?,
)
