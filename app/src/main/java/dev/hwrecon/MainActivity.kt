package dev.hwrecon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.hwrecon.model.*
import dev.hwrecon.viewmodel.HwReconViewModel
import dev.hwrecon.viewmodel.AppState

// Color scheme matching the HTML mockup
private val Bg = Color(0xFF0A0C0F)
private val Bg1 = Color(0xFF0F1218)
private val Bg2 = Color(0xFF141920)
private val Bg3 = Color(0xFF1A2130)
private val Border = Color(0xFF1E2D3D)
private val Accent = Color(0xFF00C8FF)
private val Accent2 = Color(0xFFFF6B2B)
private val Accent3 = Color(0xFFA8FF3E)
private val Text = Color(0xFFC8D8E8)
private val Text2 = Color(0xFF6A8099)
private val Text3 = Color(0xFF3D5268)
private val Danger = Color(0xFFFF3E6C)
private val Warn = Color(0xFFFFB830)
private val Ok = Color(0xFFA8FF3E)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Bg,
                    surface = Bg1,
                    primary = Accent,
                    onPrimary = Color.Black,
                    onBackground = Text,
                    onSurface = Text
                )
            ) {
                HwReconApp()
            }
        }
    }
}

@Composable
fun HwReconApp() {
    val viewModel: HwReconViewModel = viewModel()
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        // Top Bar
        TopBar(state)

        // Nav Tabs
        NavTabs(
            currentTab = state.currentTab,
            onTabSelected = { viewModel.switchTab(it) }
        )

        // Main Content
        Row(modifier = Modifier.fillMaxSize()) {
            // Sidebar
            Sidebar(
                currentTab = state.currentTab,
                onItemClick = { tab -> viewModel.switchTab(tab) },
                modifier = Modifier.width(220.dp)
            )

            // Content Pane
            ContentPane(
                state = state,
                onRefresh = { viewModel.refreshCurrentTab() },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun TopBar(state: AppState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Bg1)
            .padding(10.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(Accent, RoundedCornerShape(50))
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "HW·RECON",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Accent,
                letterSpacing = 1.2.sp
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "v0.1 · ROOT ACTIVE",
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            color = Text3,
            letterSpacing = 0.8.sp
        )
        Spacer(Modifier.weight(1f))
        Text(
            "${state.deviceModel} · Android ${state.androidVersion}",
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            color = Text2
        )
        Spacer(Modifier.width(12.dp))
        Surface(
            color = if (state.isRooted) Ok else Danger,
            shape = RoundedCornerShape(2.dp),
            modifier = Modifier.padding(2.dp, 1.dp)
        ) {
            Text(
                if (state.isRooted) "CONNECTED" else "NO ROOT",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = Color.Black,
                modifier = Modifier.padding(6.dp, 2.dp),
                letterSpacing = 0.8.sp
            )
        }
    }
}

@Composable
fun NavTabs(currentTab: String, onTabSelected: (String) -> Unit) {
    val tabs = listOf(
        "dt" to "🌲 Device Tree",
        "cpu" to "⚙ CPU / SoC",
        "mod" to "📦 Modules",
        "hal" to "🔌 HAL / Blobs",
        "dmesg" to "📟 dmesg",
        "iomem" to "🗺 I/O Map",
        "arch" to "🏗 Architecture"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Bg1)
            .horizontalScroll(rememberScrollState())
    ) {
        tabs.forEach { (id, label) ->
            val isActive = currentTab == id
            Text(
                text = label,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = if (isActive) Accent else Text2,
                modifier = Modifier
                    .clickable { onTabSelected(id) }
                    .background(if (isActive) Bg2 else Color.Transparent)
                    .padding(12.dp, 8.dp)
                    .border(
                        width = if (isActive) 2.dp else 0.dp,
                        color = if (isActive) Accent else Color.Transparent
                    ),
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun Sidebar(currentTab: String, onItemClick: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(Bg1)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        Text(
            "Device Tree",
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Text3,
            modifier = Modifier.padding(12.dp, 4.dp),
            letterSpacing = 1.5.sp
        )

        SidebarItem(" / (root)", "47", "dt", currentTab, onItemClick)
        SidebarItem(" /cpus", "8", "dt", currentTab, onItemClick)
        SidebarItem(" /soc", "124", "dt", currentTab, onItemClick)
        SidebarItem(" /memory", "", "dt", currentTab, onItemClick)
        SidebarItem(" /chosen", "", "dt", currentTab, onItemClick)

        Spacer(Modifier.height(8.dp))
        Text(
            "Collectors",
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Text3,
            modifier = Modifier.padding(12.dp, 4.dp),
            letterSpacing = 1.5.sp
        )

        SidebarItem("/proc/cpuinfo", "OK", "cpu", currentTab, onItemClick, badgeColor = Ok)
        SidebarItem("/sys/class/cpu", "OK", "cpu", currentTab, onItemClick, badgeColor = Ok)
        SidebarItem("/proc/modules", "237", "mod", currentTab, onItemClick, badgeColor = Warn)
        SidebarItem("/vendor/lib/modules", "112", "mod", currentTab, onItemClick, badgeColor = Warn)
        SidebarItem("/vendor/lib64/hw", "38", "hal", currentTab, onItemClick, badgeColor = Accent)
        SidebarItem("vintf manifest", "29", "hal", currentTab, onItemClick, badgeColor = Accent)
        SidebarItem("dmesg boot", "12 ERR", "dmesg", currentTab, onItemClick, badgeColor = Danger)
        SidebarItem("/proc/iomem", "OK", "iomem", currentTab, onItemClick, badgeColor = Accent)
        SidebarItem("/proc/interrupts", "OK", "iomem", currentTab, onItemClick, badgeColor = Ok)

        Spacer(Modifier.height(8.dp))
        Text(
            "Debug FS",
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Text3,
            modifier = Modifier.padding(12.dp, 4.dp),
            letterSpacing = 1.5.sp
        )

        SidebarItem("/debug/clk_summary", "OK", "cpu", currentTab, onItemClick, badgeColor = Ok)
        SidebarItem("/debug/pinctrl", "PART", "cpu", currentTab, onItemClick, badgeColor = Warn)
        SidebarItem("/debug/regulator", "OK", "cpu", currentTab, onItemClick, badgeColor = Ok)
    }
}

@Composable
fun SidebarItem(
    label: String,
    badge: String,
    tabId: String,
    currentTab: String,
    onClick: (String) -> Unit,
    badgeColor: Color = Accent
) {
    val isActive = currentTab == tabId
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(tabId) }
            .background(if (isActive) Bg2 else Color.Transparent)
            .padding(12.dp, 6.dp)
            .border(
                width = if (isActive) 2.dp else 0.dp,
                color = if (isActive) Accent else Color.Transparent
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = if (isActive) Accent else Text2,
            modifier = Modifier.weight(1f)
        )
        if (badge.isNotEmpty()) {
            Surface(
                color = badgeColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(2.dp)
            ) {
                Text(
                    badge,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = badgeColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(4.dp, 1.dp)
                )
            }
        }
    }
}

@Composable
fun ContentPane(state: AppState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(Bg).padding(16.dp)) {
        when (state.currentTab) {
            "dt" -> DeviceTreePanel(state.dtSummary, onRefresh)
            "cpu" -> CpuPanel(state.cpuSummary, onRefresh)
            "mod" -> ModulesPanel(state.moduleSummary, onRefresh)
            "hal" -> HalPanel(state.halSummary, onRefresh)
            "dmesg" -> DmesgPanel(state.dmesgSummary, onRefresh)
            "iomem" -> IoMapPanel(state.ioMapSummary, onRefresh)
            "arch" -> ArchitecturePanel(onRefresh)
            else -> Text("Select a tab", color = Text2)
        }

        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Accent
            )
        }

        state.errorMessage?.let { msg ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter),
                action = { TextButton(onClick = { /* dismiss */ }) { Text("DISMISS") } }
            ) {
                Text(msg, color = Danger)
            }
        }
    }
}

// ── PANELS ─────────────────────────────────────────────────────

@Composable
fun DeviceTreePanel(summary: DtSummary?, onRefresh: () -> Unit) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SectionHeader("Device Tree Walker", "/proc/device-tree · decompiled via dtc")

        StatusRow("dtc v1.6.1 · DT base: /proc/device-tree · ${summary?.nodeCount ?: 0} nodes · ${summary?.propertyCount ?: 0} properties", Ok)

        if (summary == null) {
            Text("Loading device tree data...", color = Text2, modifier = Modifier.padding(16.dp))
            return@Column
        }

        SubSection("Root Compatible Strings")
        ChipRow(summary.rootCompatibles)

        SubSection("Node Tree — /cpus (sample)")
        TreeView()

        SubSection("SoC Peripherals — Platform Devices")
        PlatformDevicesTable(summary.platformDevices)

        SubSection("Raw DTS (first 40 lines)")
        CodeBlock(summary.rawDts.lines().take(40).joinToString("\n"))
    }
}

@Composable
fun CpuPanel(summary: CpuSummary?, onRefresh: () -> Unit) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SectionHeader("CPU / SoC Collector", "/proc/cpuinfo + sysfs + debugfs")

        if (summary == null) {
            Text("Loading CPU data...", color = Text2, modifier = Modifier.padding(16.dp))
            return@Column
        }

        StatusRow("${summary.socName} · ${summary.architecture} · ${summary.clusterLayout} cores · L3: ${summary.l3SizeMb}KB", Ok)

        SubSection("Core Topology")
        CoresTable(summary.cores)

        SubSection("Clock Tree (top 15)")
        ClocksTable(summary.clockEntries.take(15))

        SubSection("Regulator Status (sample)")
        RegulatorsTable(summary.regulators.take(8))

        SubSection("CPU Features")
        ChipRow(summary.features)
    }
}

@Composable
fun ModulesPanel(summary: ModuleSummary?, onRefresh: () -> Unit) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SectionHeader("Kernel Modules", "/proc/modules + /vendor/lib/modules")

        if (summary == null) {
            Text("Loading module data...", color = Text2, modifier = Modifier.padding(16.dp))
            return@Column
        }

        StatusRow("Kernel ${summary.kernelVersion} · ${summary.loadedModules.size} loaded · ${summary.vendorBlobs.size} vendor blobs · ${summary.errorCount} errors", if (summary.errorCount > 0) Warn else Ok)

        SubSection("Loaded Modules (top 12 by size)")
        LoadedModulesTable(summary.loadedModules.take(12))

        SubSection("Vendor Blobs (sample)")
        VendorBlobsTable(summary.vendorBlobs.take(10))
    }
}

@Composable
fun HalPanel(summary: HalSummary?, onRefresh: () -> Unit) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SectionHeader("HAL / VINTF Interfaces", "/vendor/etc/vintf + lshal + /vendor/lib64/hw")

        if (summary == null) {
            Text("Loading HAL data...", color = Text2, modifier = Modifier.padding(16.dp))
            return@Column
        }

        StatusRow("${summary.declaredInterfaces.size} declared interfaces · ${summary.rawBlobs.size} .so blobs", Accent)

        SubSection("Declared HAL Interfaces")
        HalInterfacesTable(summary.declaredInterfaces.take(15))

        SubSection("lshal Output (live state)")
        CodeBlock(summary.lshalOutput.take(2000))
    }
}

@Composable
fun DmesgPanel(summary: DmesgSummary?, onRefresh: () -> Unit) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SectionHeader("dmesg — Probe & Bind Events", "Filtered kernel ring buffer (last boot)")

        if (summary == null) {
            Text("Loading dmesg...", color = Text2, modifier = Modifier.padding(16.dp))
            return@Column
        }

        StatusRow("${summary.totalLines} lines · ${summary.probeOk} OK · ${summary.probeDeferred} DEFERRED · ${summary.probeFailed} FAILED", if (summary.probeFailed > 0) Danger else Ok)

        SubSection("Probe Failures (root cause analysis)")
        if (summary.failures.isNotEmpty()) {
            summary.failures.forEach { failure ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Bg2)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${failure.driver} → ${failure.errorCode}", color = Danger, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        Text(failure.rootCause, color = Text2, fontSize = 9.sp)
                        Text("DT hint: ${failure.dtCompatible}", color = Accent3, fontSize = 9.sp)
                    }
                }
            }
        } else {
            Text("No critical probe failures detected.", color = Ok)
        }

        SubSection("Recent Bind Events")
        summary.entries.filter { it.isBindEvent }.take(8).forEach { entry ->
            Text(
                "[${entry.timestamp}] ${entry.driver ?: entry.device} : ${entry.raw.take(80)}",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = if (entry.level == DmesgLevel.ERROR) Danger else if (entry.level == DmesgLevel.WARN) Warn else Text2,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}

@Composable
fun IoMapPanel(summary: IoMapSummary?, onRefresh: () -> Unit) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SectionHeader("I/O Memory Map & Interrupts", "/proc/iomem + /proc/interrupts + pinctrl")

        if (summary == null) {
            Text("Loading I/O map...", color = Text2, modifier = Modifier.padding(16.dp))
            return@Column
        }

        StatusRow("${summary.regions.size} memory regions · ${summary.interrupts.size} IRQs tracked · ${summary.pins.size} pin groups", Accent)

        SubSection("Physical Memory Map (selected)")
        IoMemTable(summary.regions.take(12))

        SubSection("Top Interrupt Consumers")
        InterruptsTable(summary.interrupts.take(10))

        SubSection("Pin Mux State (first 10)")
        PinsTable(summary.pins.take(10))
    }
}

@Composable
fun ArchitecturePanel(onRefresh: () -> Unit) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SectionHeader("Collector Architecture", "Android App → Root Shell → sysfs/proc → Parsers → Export")

        Text(
            "Data Flow Diagram (see HTML mockup for full SVG)",
            color = Text2,
            fontSize = 10.sp,
            modifier = Modifier.padding(8.dp)
        )

        CodeBlock(
            """
// RootShell.kt — coroutine-safe su command runner
object RootShell {
  suspend fun run(cmd: String): ShellResult = withContext(Dispatchers.IO) { ... }
}

// DtCollector.kt
class DtCollector(private val shell: RootShell) {
  suspend fun collectCompatibles(): List<String> { ... }
  suspend fun decompileDt(): String { ... }
}

// Similar pattern for CpuCollector, ModuleCollector, HalCollector, DmesgCollector, IoMapCollector
            """.trimIndent()
        )

        Text(
            "Full architecture matches the provided SVG in the HTML mockup. All collectors use structured coroutines + StateFlow for reactive UI updates.",
            color = Text2,
            fontSize = 9.sp,
            modifier = Modifier.padding(8.dp)
        )
    }
}

// ── REUSABLE UI COMPONENTS ─────────────────────────────────────

@Composable
fun SectionHeader(title: String, path: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .border(bottom = BorderStroke(1.dp, Border)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Accent,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.weight(1f))
        Text(
            path,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            color = Text3
        )
    }
}

@Composable
fun StatusRow(text: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Bg2)
            .border(1.dp, Border, RoundedCornerShape(4.dp))
            .padding(10.dp, 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, RoundedCornerShape(50))
        )
        Spacer(Modifier.width(8.dp))
        Text(text, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Text2)
    }
}

@Composable
fun SubSection(title: String) {
    Text(
        title,
        fontFamily = FontFamily.Monospace,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        color = Text3,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
        letterSpacing = 1.2.sp
    )
}

@Composable
fun ChipRow(items: List<String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.take(8).forEach { item ->
            Surface(
                color = if (item.contains("qcom") || item.contains("samsung")) Accent.copy(alpha = 0.08f) else Bg3,
                shape = RoundedCornerShape(2.dp),
                border = BorderStroke(1.dp, if (item.contains("qcom") || item.contains("samsung")) Accent.copy(alpha = 0.25f) else Border)
            ) {
                Text(
                    item,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = if (item.contains("qcom") || item.contains("samsung")) Accent else Text2,
                    modifier = Modifier.padding(6.dp, 3.dp)
                )
            }
        }
    }
}

@Composable
fun TreeView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Bg2)
            .border(1.dp, Border, RoundedCornerShape(4.dp))
            .padding(12.dp)
    ) {
        Text("├─ cpu@0", color = Accent2, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        Text("   compatible = \"arm,cortex-a520\"", color = Text2, fontSize = 9.sp)
        Text("   enable-method = \"psci\"", color = Text2, fontSize = 9.sp)
        Text("├─ cpu@100", color = Accent2, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        Text("   compatible = \"arm,cortex-a720\"", color = Text2, fontSize = 9.sp)
        Text("└─ cpu@400 ×1 prime", color = Accent2, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        Text("   compatible = \"arm,cortex-x4\"", color = Text2, fontSize = 9.sp)
    }
}

@Composable
fun PlatformDevicesTable(devices: List<PlatformDevice>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Bg2)
            .border(1.dp, Border)
    ) {
        Row(Modifier.background(Bg3).padding(8.dp)) {
            Text("Node", modifier = Modifier.weight(0.35f), color = Text3, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Text("Compatible", modifier = Modifier.weight(0.35f), color = Text3, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Text("Driver Hint", modifier = Modifier.weight(0.3f), color = Text3, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }
        devices.take(8).forEach { dev ->
            Row(Modifier.padding(8.dp).border(bottom = BorderStroke(0.5.dp, Border))) {
                Text(dev.node, modifier = Modifier.weight(0.35f), color = Accent2, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                Text(dev.compatible.take(28), modifier = Modifier.weight(0.35f), color = Text, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                Text(dev.driverHint, modifier = Modifier.weight(0.3f), color = Accent3, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
fun CoresTable(cores: List<CpuCore>) {
    // Similar table implementation...
    Column(modifier = Modifier.fillMaxWidth().background(Bg2).border(1.dp, Border)) {
        // Header + rows (simplified for brevity; full implementation would mirror HTML dtable)
        cores.take(8).forEach { core ->
            Text(
                "cpu${core.id}  ${core.cluster}  ${core.compatible}  ${core.maxFreqKhz / 1000}MHz  ${core.governor}",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = Text,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
fun ClocksTable(clocks: List<ClockEntry>) {
    Column(modifier = Modifier.fillMaxWidth().background(Bg2).border(1.dp, Border)) {
        clocks.forEach { clk ->
            Text(
                "${clk.name.take(32)}  ${clk.rateHz / 1_000_000}MHz  ${if (clk.enabled) "EN" else "OFF"}",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = if (clk.enabled) Accent3 else Text2,
                modifier = Modifier.padding(6.dp, 3.dp)
            )
        }
    }
}

@Composable
fun RegulatorsTable(regs: List<RegulatorEntry>) {
    Column(modifier = Modifier.fillMaxWidth().background(Bg2).border(1.dp, Border)) {
        regs.forEach { reg ->
            Text(
                "${reg.name.take(24)}  ${reg.voltageUv / 1000} mV  ${reg.state}",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = if (reg.state == "ON") Ok else Warn,
                modifier = Modifier.padding(6.dp, 3.dp)
            )
        }
    }
}

@Composable
fun LoadedModulesTable(mods: List<KernelModule>) {
    Column(modifier = Modifier.fillMaxWidth().background(Bg2).border(1.dp, Border)) {
        mods.forEach { mod ->
            Text(
                "${mod.name.take(20)}  ${(mod.sizeBytes / 1024 / 1024)}MB  use=${mod.useCount}  ${mod.state}",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = Text,
                modifier = Modifier.padding(6.dp, 3.dp)
            )
        }
    }
}

@Composable
fun VendorBlobsTable(blobs: List<VendorBlob>) {
    Column(modifier = Modifier.fillMaxWidth().background(Bg2).border(1.dp, Border)) {
        blobs.forEach { blob ->
            Text(
                "${blob.filename.take(28)}  ${(blob.sizeBytes / 1024)}KB  ${blob.note.take(30)}",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = Text2,
                modifier = Modifier.padding(6.dp, 3.dp)
            )
        }
    }
}

@Composable
fun HalInterfacesTable(ifaces: List<HalInterface>) {
    Column(modifier = Modifier.fillMaxWidth().background(Bg2).border(1.dp, Border)) {
        ifaces.forEach { iface ->
            Text(
                "${iface.name.take(36)}  v${iface.version}  ${iface.liveState}",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = if (iface.liveState == "ALIVE") Ok else if (iface.liveState == "DEAD") Danger else Text2,
                modifier = Modifier.padding(6.dp, 3.dp)
            )
        }
    }
}

@Composable
fun IoMemTable(regions: List<IoMemRegion>) {
    Column(modifier = Modifier.fillMaxWidth().background(Bg2).border(1.dp, Border)) {
        regions.forEach { r ->
            Text(
                "${String.format("0x%08x", r.start)}–${String.format("0x%08x", r.end)}  ${r.name.take(24)}  ${r.dtNote}",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = Text,
                modifier = Modifier.padding(6.dp, 3.dp)
            )
        }
    }
}

@Composable
fun InterruptsTable(irks: List<IrqEntry>) {
    Column(modifier = Modifier.fillMaxWidth().background(Bg2).border(1.dp, Border)) {
        irks.forEach { irq ->
            Text(
                "IRQ ${irq.irqNumber}  ${irq.count} hits  ${irq.type}  ${irq.device.take(20)}",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = Text,
                modifier = Modifier.padding(6.dp, 3.dp)
            )
        }
    }
}

@Composable
fun PinsTable(pins: List<PinEntry>) {
    Column(modifier = Modifier.fillMaxWidth().background(Bg2).border(1.dp, Border)) {
        pins.forEach { pin ->
            Text(
                "gpio${pin.gpio} → ${pin.function}  ${pin.note ?: ""}",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = Accent3,
                modifier = Modifier.padding(6.dp, 3.dp)
            )
        }
    }
}

@Composable
fun CodeBlock(code: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Bg1)
            .border(1.dp, Border, RoundedCornerShape(4.dp))
            .padding(12.dp)
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            code,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            color = Text2,
            lineHeight = 14.sp
        )
    }
}
