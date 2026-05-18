package dev.hwrecon.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hwrecon.collector.*
import dev.hwrecon.model.*
import dev.hwrecon.shell.RootShell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppState(
    val isRooted: Boolean = false,
    val deviceModel: String = "Unknown",
    val androidVersion: String = "Unknown",
    val isLoading: Boolean = false,
    val currentTab: String = "dt",
    val dtSummary: DtSummary? = null,
    val cpuSummary: CpuSummary? = null,
    val moduleSummary: ModuleSummary? = null,
    val halSummary: HalSummary? = null,
    val dmesgSummary: DmesgSummary? = null,
    val ioMapSummary: IoMapSummary? = null,
    val errorMessage: String? = null,
)

class HwReconViewModel : ViewModel() {

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val dtCollector = DtCollector()
    private val cpuCollector = CpuCollector()
    private val moduleCollector = ModuleCollector()
    private val halCollector = HalCollector()
    private val dmesgCollector = DmesgCollector()
    private val ioMapCollector = IoMapCollector()

    init {
        checkRootAndLoad()
    }

    private fun checkRootAndLoad() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val rooted = RootShell.isRootAvailable()
            val device = try {
                RootShell.readFile("/system/build.prop").output
                    .lines()
                    .firstOrNull { it.startsWith("ro.product.model") }
                    ?.substringAfter("=") ?: "Unknown Device"
            } catch (e: Exception) { "Unknown Device" }

            val androidVer = try {
                RootShell.readFile("/system/build.prop").output
                    .lines()
                    .firstOrNull { it.startsWith("ro.build.version.release") }
                    ?.substringAfter("=") ?: "Unknown"
            } catch (e: Exception) { "Unknown" }

            _state.value = _state.value.copy(
                isRooted = rooted,
                deviceModel = device,
                androidVersion = androidVer,
                isLoading = false
            )

            if (rooted) {
                loadAllData()
            } else {
                _state.value = _state.value.copy(
                    errorMessage = "Root access not available. Limited functionality only."
                )
            }
        }
    }

    fun switchTab(tab: String) {
        _state.value = _state.value.copy(currentTab = tab)
        // Lazy load data for the tab if not already loaded
        viewModelScope.launch {
            when (tab) {
                "dt" -> if (_state.value.dtSummary == null) loadDt()
                "cpu" -> if (_state.value.cpuSummary == null) loadCpu()
                "mod" -> if (_state.value.moduleSummary == null) loadModules()
                "hal" -> if (_state.value.halSummary == null) loadHal()
                "dmesg" -> if (_state.value.dmesgSummary == null) loadDmesg()
                "iomem" -> if (_state.value.ioMapSummary == null) loadIoMap()
            }
        }
    }

    private suspend fun loadAllData() {
        loadDt()
        loadCpu()
        loadModules()
        loadHal()
        loadDmesg()
        loadIoMap()
    }

    private suspend fun loadDt() {
        try {
            val summary = dtCollector.collect()
            _state.value = _state.value.copy(dtSummary = summary)
        } catch (e: Exception) {
            _state.value = _state.value.copy(errorMessage = "DT load failed: ${e.message}")
        }
    }

    private suspend fun loadCpu() {
        try {
            val summary = cpuCollector.collect()
            _state.value = _state.value.copy(cpuSummary = summary)
        } catch (e: Exception) {
            _state.value = _state.value.copy(errorMessage = "CPU load failed: ${e.message}")
        }
    }

    private suspend fun loadModules() {
        try {
            val summary = moduleCollector.collect()
            _state.value = _state.value.copy(moduleSummary = summary)
        } catch (e: Exception) {
            _state.value = _state.value.copy(errorMessage = "Modules load failed: ${e.message}")
        }
    }

    private suspend fun loadHal() {
        try {
            val summary = halCollector.collect()
            _state.value = _state.value.copy(halSummary = summary)
        } catch (e: Exception) {
            _state.value = _state.value.copy(errorMessage = "HAL load failed: ${e.message}")
        }
    }

    private suspend fun loadDmesg() {
        try {
            val summary = dmesgCollector.collect()
            _state.value = _state.value.copy(dmesgSummary = summary)
        } catch (e: Exception) {
            _state.value = _state.value.copy(errorMessage = "dmesg load failed: ${e.message}")
        }
    }

    private suspend fun loadIoMap() {
        try {
            val summary = ioMapCollector.collect()
            _state.value = _state.value.copy(ioMapSummary = summary)
        } catch (e: Exception) {
            _state.value = _state.value.copy(errorMessage = "I/O Map load failed: ${e.message}")
        }
    }

    fun refreshCurrentTab() {
        val tab = _state.value.currentTab
        viewModelScope.launch {
            when (tab) {
                "dt" -> loadDt()
                "cpu" -> loadCpu()
                "mod" -> loadModules()
                "hal" -> loadHal()
                "dmesg" -> loadDmesg()
                "iomem" -> loadIoMap()
            }
        }
    }
}
