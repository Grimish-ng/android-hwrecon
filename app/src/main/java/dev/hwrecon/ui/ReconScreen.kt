package dev.hwrecon.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.hwrecon.model.*

// ====
//  Root Screen
// ====

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReconScreen(vm: ReconViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    // Trigger root check on first composition
    LaunchedEffect(Unit) { vm.checkRoot() }

    Column(
        Modifier
            .fillMaxSize()
            .background(ReconTheme.bg)
    ) {
        TopBar(state, onRunAll = { vm.runAll() })
        NavTabs(state.activeTab, onSelect = { vm.selectTab(it) })
        TabContent(state, vm)
    }
}

// -- Top bar ----

@Composable
fun TopBar(state: CollectorState, onRunAll: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(ReconTheme.bg1)
            .border(1.dp, ReconTheme.border)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Logo
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(6.dp).background(ReconTheme.accent, RoundedCornerShape(50)))
            MonoText("HW.RECON", color = ReconTheme.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }

        MonoText("v0.1", color = ReconTheme.text3, fontSize = 9.sp)

        Spacer(Modifier.weight(1f))

        // Root status
        val rootColor = if (state.isRooted) ReconTheme.ok else ReconTheme.danger
        val rootLabel = when {
            !state.rootChecked -> "CHECKING..."
            state.isRooted     -> "ROOT ACTIVE"
            else               -> "NO ROOT"
        }
        Badge(rootLabel, if (state.isRooted) BadgeType.OK else BadgeType.ERROR)

        // Run all button
        Button(
            onClick = onRunAll,
            enabled = state.isRooted,
            colors  = ButtonDefaults.buttonColors(
                containerColor = ReconTheme.accent.copy(alpha = 0.15f),
                contentColor   = ReconTheme.accent,
            ),
            shape   = RoundedCornerShape(3.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.height(28.dp),
        ) {
            MonoText("RUN ALL", color = ReconTheme.accent, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// -- Nav tabs ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NavTabs(active: ReconTab, onSelect: (ReconTab) -> Unit) {
    val scroll = rememberScrollState()
    Row(
        Modifier
            .fillMaxWidth()
            .background(ReconTheme.bg1)
            .horizontalScroll(scroll)
            .border(1.dp, ReconTheme.border)
    ) {
        ReconTab.entries.forEach { tab ->
            val isActive = tab == active
            val textColor = if (isActive) ReconTheme.accent else ReconTheme.text2

            Column(
                Modifier
                    .clickable { onSelect(tab) }
                    .background(if (isActive) ReconTheme.bg2 else Color.Transparent)
                    .drawBehind {
                        if (isActive) {
                            drawRect(
                                color = ReconTheme.accent,
                                topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 2.dp.toPx()),
                                size = androidx.compose.ui.geometry.Size(size.width, 2.dp.toPx()),
                            )
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                MonoText(
                    "${tab.icon} ${tab.label.uppercase()}",
                    color      = textColor,
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            // Right divider
            Box(Modifier.width(1.dp).height(36.dp).background(ReconTheme.border))
        }
    }
}

// -- Tab content router ----

@Composable
fun TabContent(state: CollectorState, vm: ReconViewModel) {
    val report = state.report

    when (state.activeTab) {
        ReconTab.DEVICE_TREE -> DeviceTreePanel(report?.dtSummary, state.dtStatus, vm)
        ReconTab.CPU_SOC     -> CpuPanel(report?.cpuSummary, state.cpuStatus, vm)
        ReconTab.MODULES     -> ModulesPanel(report?.moduleSummary, state.modStatus, vm)
        ReconTab.HAL_BLOBS   -> HalPanel(report?.halSummary, state.halStatus, vm)
        ReconTab.DMESG       -> DmesgPanel(report?.dmesgSummary, state.dmesgStatus, vm)
        ReconTab.IO_MAP      -> IoMapPanel(report?.ioMapSummary, state.ioMapStatus, vm)
    }
}

// ====
//  Collector placeholder / loading state
// ====

@Composable
fun CollectorPlaceholder(status: CollectorStatus, onRun: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (status) {
                CollectorStatus.IDLE -> {
                    MonoText("NOT YET COLLECTED", color = ReconTheme.text3, fontSize = 11.sp)
                    Button(
                        onClick = onRun,
                        colors  = ButtonDefaults.buttonColors(
                            containerColor = ReconTheme.accent.copy(alpha = 0.12f),
                            contentColor   = ReconTheme.accent,
                        ),
                        shape = RoundedCornerShape(3.dp),
                    ) {
                        MonoText("COLLECT NOW", color = ReconTheme.accent, fontSize = 10.sp)
                    }
                }
                CollectorStatus.LOADING -> {
                    CircularProgressIndicator(color = ReconTheme.accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    MonoText("COLLECTING...", color = ReconTheme.accent, fontSize = 10.sp)
                }
                CollectorStatus.ERROR -> {
                    MonoText("COLLECTION FAILED", color = ReconTheme.danger, fontSize = 11.sp)
                    Button(onClick = onRun, colors = ButtonDefaults.buttonColors(containerColor = ReconTheme.danger.copy(alpha = 0.12f))) {
                        MonoText("RETRY", color = ReconTheme.danger, fontSize = 10.sp)
                    }
                }
                CollectorStatus.DONE -> {} // handled by caller
            }
        }
    }
}

// ====
//  Device Tree Panel
// ====

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeviceTreePanel(dt: DtSummary?, status: CollectorStatus, vm: ReconViewModel) {
    if (dt == null || status != CollectorStatus.DONE) {
        CollectorPlaceholder(status) { vm.runDtOnly() }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().background(ReconTheme.bg),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            SectionHeader("Device Tree Walker", "/proc/device-tree . decompiled via dtc")
            StatusRow("${dt.nodeCount} nodes . ${dt.propertyCount} properties . ${dt.rootCompatibles.size} root compatibles")
            Spacer(Modifier.height(12.dp))
        }

        item {
            SubSection("Root Compatible Strings")
            Spacer(Modifier.height(7.dp))
            ChipRow(dt.rootCompatibles, highlightedChips = dt.rootCompatibles.filter { it.startsWith("qcom,") || it.startsWith("samsung,") }.toSet())
            Spacer(Modifier.height(4.dp))
        }

        item {
            SubSection("Platform Devices - DT Binding Cross-reference")
            Spacer(Modifier.height(7.dp))
        }

        items(dt.platformDevices) { dev ->
            PlatformDeviceRow(dev)
        }

        item {
            SubSection("Raw DTS Fragment")
            Spacer(Modifier.height(7.dp))
            CodeBlock(dt.rawDts.take(3000).let { if (dt.rawDts.length > 3000) "$it\n...truncated" else it })
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun PlatformDeviceRow(dev: PlatformDevice) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(ReconTheme.bg1, RoundedCornerShape(4.dp))
            .border(1.dp, ReconTheme.border, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonoText(dev.node, color = ReconTheme.accent2, fontSize = 10.sp, modifier = Modifier.width(140.dp))
        MonoText(dev.compatible, color = ReconTheme.text, fontSize = 9.sp, modifier = Modifier.weight(1f))
        MonoText(dev.reg, color = ReconTheme.text2, fontSize = 9.sp, modifier = Modifier.width(90.dp))
        MonoText(dev.driverHint, color = ReconTheme.accent3, fontSize = 9.sp, modifier = Modifier.width(100.dp))
    }
}

// ====
//  CPU / SoC Panel
// ====

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CpuPanel(cpu: CpuSummary?, status: CollectorStatus, vm: ReconViewModel) {
    if (cpu == null || status != CollectorStatus.DONE) {
        CollectorPlaceholder(status) { vm.runCpuOnly() }
        return
    }

    LazyColumn(Modifier.fillMaxSize().background(ReconTheme.bg), contentPadding = PaddingValues(16.dp)) {
        item {
            SectionHeader("CPU / SoC Topology", "/proc/cpuinfo . /sys/devices/system/cpu")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DataCard("SoC", cpu.socModel, cpu.socName, modifier = Modifier.widthIn(min = 150.dp))
                DataCard("Architecture", cpu.architecture, modifier = Modifier.widthIn(min = 150.dp))
                DataCard("Clusters", cpu.clusterLayout, modifier = Modifier.widthIn(min = 150.dp))
                DataCard("L3 Cache", "${cpu.l3SizeMb} KB", modifier = Modifier.widthIn(min = 150.dp))
            }
            Spacer(Modifier.height(4.dp))
        }

        item {
            SubSection("Per-Core Topology")
            Spacer(Modifier.height(7.dp))
        }

        items(cpu.cores) { core -> CpuCoreRow(core) }

        item {
            SubSection("CPU Feature Flags")
            Spacer(Modifier.height(7.dp))
            ChipRow(cpu.features, highlightedChips = setOf("sve","sve2","bf16","i8mm","aes","sha512","fp","asimd"))
            Spacer(Modifier.height(4.dp))
        }

        if (cpu.clockEntries.isNotEmpty()) {
            item {
                SubSection("Clock Tree - Top Entries")
                Spacer(Modifier.height(7.dp))
            }
            items(cpu.clockEntries.take(10)) { clk ->
                val max = cpu.clockEntries.maxOf { it.rateHz }.coerceAtLeast(1)
                BarRow(
                    label    = clk.name,
                    value    = formatHz(clk.rateHz),
                    fraction = clk.rateHz.toFloat() / max,
                    barColor = if (clk.enabled) ReconTheme.accent else ReconTheme.text3,
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        if (cpu.regulators.isNotEmpty()) {
            item {
                SubSection("Regulator Tree - PMIC Rails")
                Spacer(Modifier.height(7.dp))
            }
            items(cpu.regulators) { reg -> RegulatorRow(reg) }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun CpuCoreRow(core: CpuCore) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp)
            .background(ReconTheme.bg1, RoundedCornerShape(4.dp))
            .border(1.dp, ReconTheme.border, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonoText("cpu${core.id}", color = ReconTheme.accent, fontSize = 10.sp, modifier = Modifier.width(50.dp))
        MonoText(core.compatible, color = ReconTheme.text, fontSize = 9.sp, modifier = Modifier.weight(1f))
        MonoText(formatHz(core.maxFreqKhz * 1000), color = ReconTheme.accent3, fontSize = 9.sp, modifier = Modifier.width(70.dp))
        Badge(core.cluster, if (core.cluster == "prime") BadgeType.ERROR else if (core.cluster == "big") BadgeType.WARN else BadgeType.INFO)
        MonoText(core.governor, color = ReconTheme.text2, fontSize = 9.sp, modifier = Modifier.width(80.dp))
        MonoText("L2: ${core.l2SizeKb}K", color = ReconTheme.text3, fontSize = 9.sp)
    }
}

@Composable
fun RegulatorRow(reg: RegulatorEntry) {
    val stateColor = when (reg.state) {
        "ON"     -> ReconTheme.ok
        "BYPASS" -> ReconTheme.warn
        "ECO"    -> ReconTheme.accent
        else     -> ReconTheme.text3
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp)
            .background(ReconTheme.bg1, RoundedCornerShape(4.dp))
            .border(1.dp, ReconTheme.border, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonoText(reg.name, color = ReconTheme.accent2, fontSize = 10.sp, modifier = Modifier.width(130.dp))
        MonoText("${reg.voltageUv / 1000.0} V", color = ReconTheme.text, fontSize = 9.sp, modifier = Modifier.width(60.dp))
        MonoText("? ${reg.state}", color = stateColor, fontSize = 9.sp, modifier = Modifier.width(70.dp))
        MonoText(reg.consumers.take(2).joinToString(", "), color = ReconTheme.text2, fontSize = 9.sp, modifier = Modifier.weight(1f))
    }
}

// ====
//  Modules Panel
// ====

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModulesPanel(mods: ModuleSummary?, status: CollectorStatus, vm: ReconViewModel) {
    if (mods == null || status != CollectorStatus.DONE) {
        CollectorPlaceholder(status) { vm.runModOnly() }
        return
    }

    LazyColumn(Modifier.fillMaxSize().background(ReconTheme.bg), contentPadding = PaddingValues(16.dp)) {
        item {
            SectionHeader("Kernel Modules & Driver Blobs", "/proc/modules . /vendor/lib/modules")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DataCard("Loaded Modules", "${mods.loadedModules.size}", valueColor = ReconTheme.accent, modifier = Modifier.widthIn(min = 140.dp))
                DataCard("Vendor Blobs", "${mods.vendorBlobs.size}", valueColor = ReconTheme.accent2, modifier = Modifier.widthIn(min = 140.dp))
                DataCard("Kernel", mods.kernelVersion, mods.compiler, modifier = Modifier.widthIn(min = 140.dp))
                DataCard("Load Errors", "${mods.errorCount}", valueColor = if (mods.errorCount > 0) ReconTheme.danger else ReconTheme.ok, modifier = Modifier.widthIn(min = 140.dp))
            }
            Spacer(Modifier.height(4.dp))
        }

        item { SubSection("Loaded Modules - /proc/modules"); Spacer(Modifier.height(7.dp)) }

        items(mods.loadedModules.take(30)) { mod -> ModuleRow(mod) }

        item { SubSection("Vendor Blob Analysis"); Spacer(Modifier.height(7.dp)) }

        items(mods.vendorBlobs.take(20)) { blob -> VendorBlobRow(blob) }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun ModuleRow(mod: KernelModule) {
    val stateColor = when (mod.state.lowercase()) {
        "live"     -> ReconTheme.ok
        "loading"  -> ReconTheme.warn
        "failed"   -> ReconTheme.danger
        else       -> ReconTheme.text2
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp)
            .background(ReconTheme.bg1, RoundedCornerShape(4.dp))
            .border(1.dp, ReconTheme.border, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonoText(mod.name, color = ReconTheme.accent2, fontSize = 10.sp, modifier = Modifier.width(140.dp))
        MonoText(formatBytes(mod.sizeBytes), color = ReconTheme.text2, fontSize = 9.sp, modifier = Modifier.width(60.dp))
        MonoText("? ${mod.state}", color = stateColor, fontSize = 9.sp, modifier = Modifier.width(70.dp))
        MonoText(mod.dtCompatible ?: "-", color = ReconTheme.text3, fontSize = 9.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
fun VendorBlobRow(blob: VendorBlob) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp)
            .background(ReconTheme.bg1, RoundedCornerShape(4.dp))
            .border(1.dp, ReconTheme.border, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonoText(blob.filename, color = ReconTheme.accent, fontSize = 10.sp, modifier = Modifier.width(140.dp))
        MonoText(formatBytes(blob.sizeBytes), color = ReconTheme.text2, fontSize = 9.sp, modifier = Modifier.width(60.dp))
        MonoText(blob.dtCompatible ?: "-", color = ReconTheme.accent3, fontSize = 9.sp, modifier = Modifier.width(140.dp))
        MonoText(blob.note, color = ReconTheme.text3, fontSize = 9.sp, modifier = Modifier.weight(1f))
    }
}

// ====
//  HAL Panel
// ====

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HalPanel(hal: HalSummary?, status: CollectorStatus, vm: ReconViewModel) {
    if (hal == null || status != CollectorStatus.DONE) {
        CollectorPlaceholder(status) { vm.runHalOnly() }
        return
    }

    LazyColumn(Modifier.fillMaxSize().background(ReconTheme.bg), contentPadding = PaddingValues(16.dp)) {
        item {
            SectionHeader("HAL Blobs & VINTF Manifest", "/vendor/etc/vintf/manifest.xml . lshal")
            StatusRow("${hal.declaredInterfaces.size} interfaces . ${hal.rawBlobs.size} .so blobs")
            Spacer(Modifier.height(12.dp))
        }

        item { SubSection("VINTF Manifest - HAL Interfaces"); Spacer(Modifier.height(7.dp)) }

        items(hal.declaredInterfaces) { iface -> HalInterfaceRow(iface) }

        item {
            SubSection("Raw Blobs - /vendor/lib64/hw")
            Spacer(Modifier.height(7.dp))
            ChipRow(hal.rawBlobs, highlightedChips = hal.rawBlobs.filter {
                it.contains("qnn") || it.contains("adreno") || it.contains("ril") || it.contains("sec")
            }.toSet())
            Spacer(Modifier.height(8.dp))
        }

        item {
            SubSection("lshal Output")
            Spacer(Modifier.height(7.dp))
            CodeBlock(hal.lshalOutput)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun HalInterfaceRow(iface: HalInterface) {
    val stateColor = when (iface.liveState) {
        "ALIVE"   -> ReconTheme.ok
        "DEAD"    -> ReconTheme.danger
        else      -> ReconTheme.warn
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp)
            .background(ReconTheme.bg1, RoundedCornerShape(4.dp))
            .border(1.dp, ReconTheme.border, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonoText(iface.name.removePrefix("android.hardware."), color = ReconTheme.accent2, fontSize = 9.sp, modifier = Modifier.weight(1f))
        MonoText(iface.version, color = ReconTheme.text2, fontSize = 9.sp, modifier = Modifier.width(32.dp))
        MonoText(iface.transport, color = ReconTheme.text3, fontSize = 9.sp, modifier = Modifier.width(70.dp))
        MonoText(iface.blob, color = ReconTheme.text2, fontSize = 9.sp, modifier = Modifier.width(140.dp))
        MonoText("? ${iface.liveState}", color = stateColor, fontSize = 9.sp)
    }
}

// ====
//  dmesg Panel
// ====

@Composable
fun DmesgPanel(dmesg: DmesgSummary?, status: CollectorStatus, vm: ReconViewModel) {
    if (dmesg == null || status != CollectorStatus.DONE) {
        CollectorPlaceholder(status) { vm.runDmesgOnly() }
        return
    }

    LazyColumn(Modifier.fillMaxSize().background(ReconTheme.bg), contentPadding = PaddingValues(16.dp)) {
        item {
            SectionHeader("dmesg - Boot Log Analysis", "su -c dmesg | filtered")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DataCard("Total Lines",   "${dmesg.totalLines}", modifier = Modifier.widthIn(min = 130.dp))
                DataCard("Probe OK",      "${dmesg.probeOk}",       valueColor = ReconTheme.ok, modifier = Modifier.widthIn(min = 130.dp))
                DataCard("Deferred",      "${dmesg.probeDeferred}", valueColor = ReconTheme.warn, modifier = Modifier.widthIn(min = 130.dp))
                DataCard("Failed",        "${dmesg.probeFailed}",   valueColor = ReconTheme.danger, modifier = Modifier.widthIn(min = 130.dp))
            }
            Spacer(Modifier.height(4.dp))
        }

        item { SubSection("Driver Binding Log"); Spacer(Modifier.height(7.dp)) }

        items(dmesg.entries.take(80)) { entry -> DmesgEntryRow(entry) }

        if (dmesg.failures.isNotEmpty()) {
            item { SubSection("Probe Failures - DT Node Cross-reference"); Spacer(Modifier.height(7.dp)) }
            items(dmesg.failures) { fail -> FailureRow(fail) }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun DmesgEntryRow(entry: DmesgEntry) {
    val color = when (entry.level) {
        DmesgLevel.ERROR -> ReconTheme.danger
        DmesgLevel.WARN  -> ReconTheme.warn
        DmesgLevel.OK    -> ReconTheme.ok
        DmesgLevel.INFO  -> ReconTheme.text2
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MonoText("[${"%10.6f".format(entry.timestamp)}]", color = ReconTheme.text3, fontSize = 9.sp)
        MonoText(entry.raw.substringAfter("]").trim().take(120), color = color, fontSize = 9.sp)
    }
}

@Composable
fun FailureRow(fail: ProbeFailure) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .background(Color(0x0FFF3E6C), RoundedCornerShape(4.dp))
            .border(1.dp, Color(0x30FF3E6C), RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MonoText(fail.driver, color = ReconTheme.danger, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            MonoText(fail.dtCompatible, color = ReconTheme.text2, fontSize = 9.sp)
            Spacer(Modifier.weight(1f))
            MonoText(fail.errorCode, color = ReconTheme.accent4, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        }
        MonoText(fail.rootCause, color = ReconTheme.text3, fontSize = 9.sp)
    }
}

// ====
//  I/O Map Panel
// ====

@Composable
fun IoMapPanel(io: IoMapSummary?, status: CollectorStatus, vm: ReconViewModel) {
    if (io == null || status != CollectorStatus.DONE) {
        CollectorPlaceholder(status) { vm.runIoOnly() }
        return
    }

    LazyColumn(Modifier.fillMaxSize().background(ReconTheme.bg), contentPadding = PaddingValues(16.dp)) {
        item {
            SectionHeader("I/O Memory Map & Interrupt Map", "/proc/iomem . /proc/interrupts")
            Spacer(Modifier.height(4.dp))
        }

        item { SubSection("Physical Memory Map - /proc/iomem"); Spacer(Modifier.height(7.dp)) }

        items(io.regions) { region -> IoRegionRow(region) }

        item { SubSection("Interrupt Map - Top Consumers"); Spacer(Modifier.height(7.dp)) }

        items(io.interrupts.take(20)) { irq -> IrqRow(irq) }

        if (io.pins.isNotEmpty()) {
            item { SubSection("Pin Mux State - /sys/kernel/debug/pinctrl"); Spacer(Modifier.height(7.dp)) }
            items(io.pins.take(32)) { pin -> PinRow(pin) }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun IoRegionRow(region: IoMemRegion) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp)
            .background(ReconTheme.bg1, RoundedCornerShape(4.dp))
            .border(1.dp, ReconTheme.border, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonoText("0x${region.start.toString(16).uppercase().padStart(8, '0')}", color = ReconTheme.accent, fontSize = 9.sp, modifier = Modifier.width(90.dp))
        MonoText("-> 0x${region.end.toString(16).uppercase().padStart(8, '0')}", color = ReconTheme.text3, fontSize = 9.sp, modifier = Modifier.width(100.dp))
        MonoText(formatBytes(region.sizeBytes), color = ReconTheme.text2, fontSize = 9.sp, modifier = Modifier.width(60.dp))
        MonoText(region.name, color = ReconTheme.text, fontSize = 9.sp, modifier = Modifier.weight(1f))
        if (region.dtNote.isNotBlank()) MonoText(region.dtNote, color = ReconTheme.accent3, fontSize = 9.sp)
    }
}

@Composable
fun IrqRow(irq: IrqEntry) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp)
            .background(ReconTheme.bg1, RoundedCornerShape(4.dp))
            .border(1.dp, ReconTheme.border, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonoText("IRQ ${irq.irqNumber}", color = ReconTheme.accent, fontSize = 9.sp, modifier = Modifier.width(56.dp))
        MonoText("%,d".format(irq.count), color = ReconTheme.accent3, fontSize = 9.sp, modifier = Modifier.width(90.dp))
        MonoText(irq.type, color = ReconTheme.text3, fontSize = 9.sp, modifier = Modifier.width(80.dp))
        MonoText(irq.device, color = ReconTheme.text, fontSize = 9.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
fun PinRow(pin: PinEntry) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp)
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonoText(pin.gpio.padEnd(8), color = ReconTheme.accent2, fontSize = 9.sp, modifier = Modifier.width(70.dp))
        MonoText(pin.function, color = ReconTheme.text, fontSize = 9.sp, modifier = Modifier.width(100.dp))
        if (pin.note != null) MonoText("<- ${pin.note}", color = ReconTheme.text3, fontSize = 9.sp)
    }
}

// ====
//  Formatters
// ====

fun formatHz(hz: Long): String = when {
    hz >= 1_000_000_000L -> "${"%.2f".format(hz / 1_000_000_000.0)} GHz"
    hz >= 1_000_000L     -> "${"%.0f".format(hz / 1_000_000.0)} MHz"
    hz >= 1_000L         -> "${"%.0f".format(hz / 1_000.0)} kHz"
    else                 -> "$hz Hz"
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576L -> "${"%.1f".format(bytes / 1_048_576.0)} MB"
    bytes >= 1_024L     -> "${"%.0f".format(bytes / 1_024.0)} KB"
    else                -> "$bytes B"
}
