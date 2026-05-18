package dev.hwrecon.collector

import dev.hwrecon.model.*
import dev.hwrecon.shell.RootShell
import dev.hwrecon.util.DriverHintMap

class ModuleCollector {
    suspend fun collect(): ModuleSummary {
        return ModuleSummary(
            kernelVersion = "6.1",
            compiler = "clang",
            loadedModules = emptyList(),
            vendorBlobs = emptyList(),
            errorCount = 0
        )
    }
}
