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

    init {
        checkRoot()
    }

    private fun checkRoot() {
        viewModelScope.launch {
            val rooted = RootShell.isRootAvailable()
            _state.value = _state.value.copy(isRooted = rooted)
        }
    }

    fun switchTab(tab: String) {
        _state.value = _state.value.copy(currentTab = tab)
    }

    fun refreshCurrentTab() {
        // Can be expanded later
    }
}
