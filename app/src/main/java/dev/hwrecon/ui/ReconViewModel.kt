package dev.hwrecon.ui

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hwrecon.collector.CpuCollector
import dev.hwrecon.collector.DmesgCollector
import dev.hwrecon.collector.DtCollector
import dev.hwrecon.collector.HalCollector
import dev.hwrecon.collector.IoMapCollector
import dev.hwrecon.collector.ModuleCollector
import dev.hwrecon.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// UI state

enum class CollectorStatus { IDLE, LOADING, DONE, ERROR }

data class CollectorState(
    val dtStatus:     CollectorStatus = CollectorStatus.IDLE,
    val cpuStatus:    CollectorStatus = CollectorStatus.IDLE,
    val modStatus:    CollectorStatus = CollectorStatus.IDLE,
    val halStatus:    CollectorStatus = CollectorStatus.IDLE,
    val dmesgStatus:  CollectorStatus = CollectorStatus.IDLE,
    val ioMapStatus:  CollectorStatus = CollectorStatus.IDLE,
    val report:       HwReconReport?  = null,
    val activeTab:    ReconTab        = ReconTab.DEVICE_TREE,
    val errorMessage: String?         = null,
    val isRooted:     Boolean         = false,
    val rootChecked:  Boolean         = false,
)

enum class ReconTab(val label: String, val icon: String) {
    DEVICE_TREE ("Device Tree",  "DT"),
    CPU_SOC     ("CPU / SoC",    "CPU"),
    MODULES     ("Modules",      "MOD"),
    HAL_BLOBS   ("HAL / Blobs",  "HAL"),
    DMESG       ("dmesg",        "LOG"),
    IO_MAP      ("I/O Map",      "IO"),
}

// ViewModel

class ReconViewModel : ViewModel() {

    private val _state = MutableStateFlow(CollectorState())
    val state: StateFlow<CollectorState> = _state.asStateFlow()

    private val dtCollector     = DtCollector()
    private val cpuCollector    = CpuCollector()
    private val moduleCollector = ModuleCollector()
    private val halCollector    = HalCollector()
    private val dmesgCollector  = DmesgCollector()
    private val ioMapCollector  = IoMapCollector()

    private var workingReport = HwReconReport(
        deviceModel    = "${Build.MANUFACTURER} ${Build.MODEL}",
        androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        timestamp      = System.currentTimeMillis(),
        dtSummary      = null,
        cpuSummary     = null,
        moduleSummary  = null,
        halSummary     = null,
        dmesgSummary   = null,
        ioMapSummary   = null,
    )

    // Public actions

    fun selectTab(tab: ReconTab) {
        _state.update { it.copy(activeTab = tab) }
    }

    fun checkRoot() {
        viewModelScope.launch {
            val result = dev.hwrecon.shell.RootShell.run("id")
            val rooted = result.output.contains("uid=0")
            _state.update { it.copy(isRooted = rooted, rootChecked = true) }
        }
    }

    fun runAll() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    dtStatus     = CollectorStatus.LOADING,
                    cpuStatus    = CollectorStatus.LOADING,
                    modStatus    = CollectorStatus.LOADING,
                    halStatus    = CollectorStatus.LOADING,
                    dmesgStatus  = CollectorStatus.LOADING,
                    ioMapStatus  = CollectorStatus.LOADING,
                    errorMessage = null,
                )
            }

            val dtJob    = launch { runDtCollector() }
            val cpuJob   = launch { runCpuCollector() }
            val modJob   = launch { runModuleCollector() }
            val halJob   = launch { runHalCollector() }
            val dmesgJob = launch { runDmesgCollector() }
            val ioJob    = launch { runIoMapCollector() }

            joinAll(dtJob, cpuJob, modJob, halJob, dmesgJob, ioJob)
        }
    }

    fun runDtOnly()    = viewModelScope.launch { runDtCollector() }
    fun runCpuOnly()   = viewModelScope.launch { runCpuCollector() }
    fun runModOnly()   = viewModelScope.launch { runModuleCollector() }
    fun runHalOnly()   = viewModelScope.launch { runHalCollector() }
    fun runDmesgOnly() = viewModelScope.launch { runDmesgCollector() }
    fun runIoOnly()    = viewModelScope.launch { runIoMapCollector() }

    // Collector runners

    private suspend fun runDtCollector() {
        runCollector(
            statusSetter = { s -> _state.update { it.copy(dtStatus = s) } },
            block        = { dtCollector.collect() },
            onSuccess    = { result ->
                workingReport = workingReport.copy(dtSummary = result)
                _state.update { it.copy(report = workingReport) }
            }
        )
    }

    private suspend fun runCpuCollector() {
        runCollector(
            statusSetter = { s -> _state.update { it.copy(cpuStatus = s) } },
            block        = { cpuCollector.collect() },
            onSuccess    = { result ->
                workingReport = workingReport.copy(cpuSummary = result)
                _state.update { it.copy(report = workingReport) }
            }
        )
    }

    private suspend fun runModuleCollector() {
        runCollector(
            statusSetter = { s -> _state.update { it.copy(modStatus = s) } },
            block        = { moduleCollector.collect() },
            onSuccess    = { result ->
                workingReport = workingReport.copy(moduleSummary = result)
                _state.update { it.copy(report = workingReport) }
            }
        )
    }

    private suspend fun runHalCollector() {
        runCollector(
            statusSetter = { s -> _state.update { it.copy(halStatus = s) } },
            block        = { halCollector.collect() },
            onSuccess    = { result ->
                workingReport = workingReport.copy(halSummary = result)
                _state.update { it.copy(report = workingReport) }
            }
        )
    }

    private suspend fun runDmesgCollector() {
        runCollector(
            statusSetter = { s -> _state.update { it.copy(dmesgStatus = s) } },
            block        = { dmesgCollector.collect() },
            onSuccess    = { result ->
                workingReport = workingReport.copy(dmesgSummary = result)
                _state.update { it.copy(report = workingReport) }
            }
        )
    }

    private suspend fun runIoMapCollector() {
        runCollector(
            statusSetter = { s -> _state.update { it.copy(ioMapStatus = s) } },
            block        = { ioMapCollector.collect() },
            onSuccess    = { result ->
                workingReport = workingReport.copy(ioMapSummary = result)
                _state.update { it.copy(report = workingReport) }
            }
        )
    }

    // Generic collector helper

    private suspend fun <T> runCollector(
        statusSetter : (CollectorStatus) -> Unit,
        block        : suspend () -> T,
        onSuccess    : suspend (T) -> Unit,
    ) {
        statusSetter(CollectorStatus.LOADING)
        try {
            val result = withContext(Dispatchers.IO) { block() }
            onSuccess(result)
            statusSetter(CollectorStatus.DONE)
        } catch (e: Exception) {
            statusSetter(CollectorStatus.ERROR)
            _state.update { it.copy(errorMessage = e.message) }
        }
    }
}
