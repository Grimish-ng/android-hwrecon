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
        checkRootAndLoadData()
    }

    private fun checkRootAndLoadData() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            
            val rooted = RootShell.isRootAvailable()
            val device = try { 
                RootShell.readFile("/system/build.prop").output.lines()
                    .firstOrNull { it.startsWith("ro.product.model") }?.substringAfter("=") ?: "Unknown" 
            } catch (e: Exception) { "Unknown" }
            
            val androidVer = try { 
                RootShell.readFile("/system/build.prop").output.lines()
                    .firstOrNull { it.startsWith("ro.build.version.release") }?.substringAfter("=") ?: "Unknown" 
            } catch (e: Exception) { "Unknown" }

            // Load all data
            val dt = try { dtCollector.collect() } catch (e: Exception) { null }
            val cpu = try { cpuCollector.collect() } catch (e: Exception) { null }
            val modules = try { moduleCollector.collect() } catch (e: Exception) { null }
            val hal = try { halCollector.collect() } catch (e: Exception) { null }
            val dmesg = try { dmesgCollector.collect() } catch (e: Exception) { null }
            val iomap = try { ioMapCollector.collect() } catch (e: Exception) { null }

            _state.value = _state.value.copy(
                isRooted = rooted,
                deviceModel = device,
                androidVersion = androidVer,
                isLoading = false,
                dtSummary = dt,
                cpuSummary = cpu,
                moduleSummary = modules,
                halSummary = hal,
                dmesgSummary = dmesg,
                ioMapSummary = iomap
            )
        }
    }

    fun switchTab(tab: String) {
        _state.value = _state.value.copy(currentTab = tab)
    }

    fun refreshCurrentTab() {
        checkRootAndLoadData()
    }
}
