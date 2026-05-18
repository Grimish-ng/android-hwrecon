package dev.hwrecon.util

/**
 * Maps ARM CPU part numbers (from /proc/cpuinfo) to human-readable core types.
 * Based on ARM architecture reference manuals and common SoC implementations.
 */
object ArmPartMap {

    private val partMap = mapOf(
        "0xD02" to "cortex-a34",
        "0xD03" to "cortex-a53",
        "0xD04" to "cortex-a35",
        "0xD05" to "cortex-a55",
        "0xD06" to "cortex-a65",
        "0xD07" to "cortex-a57",
        "0xD08" to "cortex-a72",
        "0xD09" to "cortex-a73",
        "0xD0A" to "cortex-a75",
        "0xD0B" to "cortex-a76",
        "0xD0C" to "cortex-a77",
        "0xD0D" to "cortex-a78",
        "0xD0E" to "cortex-a78c",
        "0xD13" to "cortex-a78ae",
        "0xD40" to "cortex-a76ae",
        "0xD41" to "cortex-a77",
        "0xD42" to "cortex-a78",
        "0xD43" to "cortex-a78c",
        "0xD44" to "cortex-x1",
        "0xD45" to "cortex-x2",
        "0xD46" to "cortex-x3",
        "0xD47" to "cortex-x4",
        "0xD48" to "cortex-x925",
        "0xD80" to "cortex-a520",
        "0xD81" to "cortex-a720",
        "0xD82" to "cortex-a725",
        "0xD83" to "cortex-x925",
        "0xD84" to "cortex-a520ae",
        "0xD85" to "cortex-a720ae",
    )

    fun lookup(partHex: String): String? =
        partMap[partHex.uppercase()] ?: if (partHex.startsWith("0x")) partHex else null
}
