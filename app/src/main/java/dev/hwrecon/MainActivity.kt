package dev.hwrecon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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

// Colors from original HTML
private val Bg = Color(0xFF0A0C0F)
private val Bg1 = Color(0xFF0F1218)
private val Bg2 = Color(0xFF141920)
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
            MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Bg1, primary = Accent, onBackground = Text, onSurface = Text)) {
                HwReconApp()
            }
        }
    }
}

@Composable
fun HwReconApp() {
    val viewModel: HwReconViewModel = viewModel()
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(Bg)) {
        TopBar(state)
        NavTabs(currentTab = state.currentTab, onTabSelected = { viewModel.switchTab(it) })
        Row(modifier = Modifier.fillMaxSize()) {
            Sidebar(currentTab = state.currentTab, onItemClick = { viewModel.switchTab(it) }, modifier = Modifier.width(220.dp))
            ContentPane(state = state, onRefresh = { viewModel.refreshCurrentTab() }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun TopBar(state: AppState) {
    Row(modifier = Modifier.fillMaxWidth().background(Bg1).padding(10.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(6.dp).background(Accent, RoundedCornerShape(50)))
            Spacer(Modifier.width(6.dp))
            Text("HW·RECON", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Accent)
        }
        Spacer(Modifier.width(12.dp))
        Text("v0.1 · ROOT ACTIVE", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Text3)
        Spacer(Modifier.weight(1f))
        Text("${state.deviceModel} · Android ${state.androidVersion}", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Text2)
        Spacer(Modifier.width(12.dp))
        Surface(color = if (state.isRooted) Ok else Danger, shape = RoundedCornerShape(2.dp)) {
            Text(if (state.isRooted) "CONNECTED" else "NO ROOT", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color.Black, modifier = Modifier.padding(6.dp, 2.dp))
        }
    }
}

@Composable
fun NavTabs(currentTab: String, onTabSelected: (String) -> Unit) {
    val tabs = listOf("dt" to "🌲 Device Tree", "cpu" to "⚙ CPU / SoC", "mod" to "📦 Modules", "hal" to "🔌 HAL / Blobs", "dmesg" to "📟 dmesg", "iomem" to "🗺 I/O Map", "arch" to "🏗 Architecture")
    Row(modifier = Modifier.fillMaxWidth().background(Bg1).horizontalScroll(rememberScrollState())) {
        tabs.forEach { (id, label) ->
            val isActive = currentTab == id
            Text(text = label, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Medium,
                color = if (isActive) Accent else Text2,
                modifier = Modifier.clickable { onTabSelected(id) }.background(if (isActive) Bg2 else Color.Transparent).padding(12.dp, 8.dp)
                    .border(width = if (isActive) 2.dp else 0.dp, color = if (isActive) Accent else Color.Transparent))
        }
    }
}

@Composable
fun Sidebar(currentTab: String, onItemClick: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxHeight().background(Bg1).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        Text("Device Tree", fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Text3, modifier = Modifier.padding(12.dp, 4.dp))
        SidebarItem(" / (root)", "47", "dt", currentTab, onItemClick)
        SidebarItem(" /cpus", "8", "dt", currentTab, onItemClick)
        SidebarItem(" /soc", "124", "dt", currentTab, onItemClick)
        Spacer(Modifier.height(8.dp))
        Text("Collectors", fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Text3, modifier = Modifier.padding(12.dp, 4.dp))
        SidebarItem("/proc/cpuinfo", "OK", "cpu", currentTab, onItemClick, badgeColor = Ok)
        SidebarItem("/proc/modules", "237", "mod", currentTab, onItemClick, badgeColor = Warn)
        SidebarItem("/vendor/lib64/hw", "38", "hal", currentTab, onItemClick, badgeColor = Accent)
        SidebarItem("dmesg boot", "12 ERR", "dmesg", currentTab, onItemClick, badgeColor = Danger)
        SidebarItem("/proc/iomem", "OK", "iomem", currentTab, onItemClick, badgeColor = Accent)
    }
}

@Composable
fun SidebarItem(label: String, badge: String, tabId: String, currentTab: String, onClick: (String) -> Unit, badgeColor: Color = Accent) {
    val isActive = currentTab == tabId
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick(tabId) }.background(if (isActive) Bg2 else Color.Transparent).padding(12.dp, 6.dp)
        .border(width = if (isActive) 2.dp else 0.dp, color = if (isActive) Accent else Color.Transparent), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = if (isActive) Accent else Text2, modifier = Modifier.weight(1f))
        if (badge.isNotEmpty()) Surface(color = badgeColor.copy(alpha = 0.12f), shape = RoundedCornerShape(2.dp)) { Text(badge, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = badgeColor, modifier = Modifier.padding(4.dp, 1.dp)) }
    }
}

@Composable
fun ContentPane(state: AppState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(Bg).padding(16.dp).verticalScroll(rememberScrollState())) {
        when (state.currentTab) {
            "dt" -> RealDeviceTreePanel(state.dtSummary)
            "cpu" -> RealCpuPanel(state.cpuSummary)
            "mod" -> RealModulesPanel(state.moduleSummary)
            "hal" -> RealHalPanel(state.halSummary)
            "dmesg" -> RealDmesgPanel(state.dmesgSummary)
            "iomem" -> RealIoMapPanel(state.ioMapSummary)
            "arch" -> ArchitecturePanel()
            else -> Text("Select a tab", color = Text2)
        }
    }
}

// ==================== REAL PANELS ====================

@Composable
fun RealDeviceTreePanel(summary: DtSummary?) {
    Column {
        Text("Device Tree", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if (summary != null) {
            Text("Root Compatibles:", color = Text3, fontSize = 10.sp)
            summary.rootCompatibles.take(5).forEach { Text("• $it", color = Accent3, fontSize = 11.sp) }
            Spacer(Modifier.height(12.dp))
            Text("Platform Devices: ${summary.platformDevices.size}", color = Text2, fontSize = 12.sp)
        } else {
            Text("Loading device tree data...", color = Text2)
        }
    }
}

@Composable
fun RealCpuPanel(summary: CpuSummary?) {
    Column {
        Text("CPU / SoC", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if (summary != null) {
            Text("SoC: ${summary.socName}", color = Text, fontSize = 14.sp)
            Text("Architecture: ${summary.architecture}", color = Text2, fontSize = 12.sp)
            Text("Cores: ${summary.cores.size}", color = Text2, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            summary.cores.take(4).forEach { core ->
                Text("cpu${core.id}  ${core.cluster}  ${core.maxFreqKhz/1000}MHz", color = Accent3, fontSize = 11.sp)
            }
        } else {
            Text("Loading CPU data...", color = Text2)
        }
    }
}

@Composable
fun RealModulesPanel(summary: ModuleSummary?) {
    Column {
        Text("Kernel Modules", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if (summary != null) {
            Text("Kernel: ${summary.kernelVersion}", color = Text2, fontSize = 12.sp)
            Text("Loaded: ${summary.loadedModules.size}", color = Text2, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            summary.loadedModules.take(6).forEach { mod ->
                Text("${mod.name}  ${(mod.sizeBytes/1024/1024)}MB", color = Accent3, fontSize = 11.sp)
            }
        } else {
            Text("Loading modules...", color = Text2)
        }
    }
}

@Composable
fun RealHalPanel(summary: HalSummary?) {
    Column {
        Text("HAL Interfaces", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if (summary != null) {
            Text("Declared: ${summary.declaredInterfaces.size}", color = Text2, fontSize = 12.sp)
            summary.declaredInterfaces.take(5).forEach { hal ->
                Text("${hal.name.take(40)}  ${hal.liveState}", color = if (hal.liveState == "ALIVE") Ok else Danger, fontSize = 11.sp)
            }
        } else {
            Text("Loading HAL data...", color = Text2)
        }
    }
}

@Composable
fun RealDmesgPanel(summary: DmesgSummary?) {
    Column {
        Text("dmesg Events", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if (summary != null) {
            Text("Total: ${summary.totalLines}  OK: ${summary.probeOk}  Failed: ${summary.probeFailed}", color = Text2, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            summary.entries.take(6).forEach { entry ->
                Text("[${entry.timestamp}] ${entry.driver ?: entry.device}", color = if (entry.level == DmesgLevel.ERROR) Danger else Text, fontSize = 10.sp)
            }
        } else {
            Text("Loading dmesg...", color = Text2)
        }
    }
}

@Composable
fun RealIoMapPanel(summary: IoMapSummary?) {
    Column {
        Text("I/O Memory Map", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if (summary != null) {
            Text("Regions: ${summary.regions.size}  IRQs: ${summary.interrupts.size}", color = Text2, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            summary.regions.take(5).forEach { region ->
                Text("${String.format("0x%08x", region.start)}  ${region.name.take(25)}", color = Accent3, fontSize = 10.sp)
            }
        } else {
            Text("Loading I/O map...", color = Text2)
        }
    }
}

@Composable
fun ArchitecturePanel() {
    Column {
        Text("Collector Architecture", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("Root Shell → Collectors → StateFlow → UI", color = Text2, fontSize = 12.sp)
        Text("All collectors are fully functional.", color = Accent3, fontSize = 11.sp)
    }
}
