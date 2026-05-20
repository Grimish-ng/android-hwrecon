package dev.hwrecon.util

/**
 * ArmPartMap
 *
 * Maps ARM CPU part numbers (from /proc/cpuinfo "CPU part" field)
 * to human-readable core names. Part numbers are architecture-family
 * specific and defined by ARM in their TRM documents.
 *
 * Implementer 0x41 = ARM Ltd (all entries below)
 * Implementer 0x51 = Qualcomm
 * Implementer 0x53 = Samsung LSI
 */
object ArmPartMap {

    private val partMap = mapOf(
        // -- Cortex-A (Application) ----
        "D03"  to "Cortex-A53",
        "D04"  to "Cortex-A35",
        "D05"  to "Cortex-A55",
        "D06"  to "Cortex-A65",
        "D07"  to "Cortex-A57",
        "D08"  to "Cortex-A72",
        "D09"  to "Cortex-A73",
        "D0A"  to "Cortex-A75",
        "D0B"  to "Cortex-A76",
        "D0C"  to "Neoverse-N1",
        "D0D"  to "Cortex-A77",
        "D0E"  to "Cortex-A76AE",
        "D40"  to "Cortex-A78",
        "D41"  to "Cortex-X1",
        "D42"  to "Cortex-A55 (r2)",
        "D43"  to "Cortex-A75 (r3)",
        "D44"  to "Cortex-X1C",
        "D46"  to "Cortex-A510",    // Cortex-A510 (Makalu-ELP), aka "little" in SDG3
        "D47"  to "Cortex-A710",    // Cortex-A710 (Matterhorn), "big"
        "D48"  to "Cortex-X2",
        "D49"  to "Neoverse-N2",
        "D4B"  to "Cortex-A78C",
        "D4C"  to "Cortex-X1C",
        "D4D"  to "Cortex-A715",    // Cortex-A715 (Makalu)
        "D4E"  to "Cortex-X3",
        "D80"  to "Cortex-A520",    // Cortex-A520 (Chaberton), SD 8 Gen 3 LITTLE
        "D81"  to "Cortex-A720",    // Cortex-A720 (Chaberton), SD 8 Gen 3 mid
        "D82"  to "Cortex-X4",      // Cortex-X4, SD 8 Gen 3 prime
        "D83"  to "Cortex-A725",
        "D84"  to "Cortex-X925",
        "D85"  to "Cortex-X4",      // alternate part encoding seen on some SoCs
        "D87"  to "Cortex-A520",

        // -- Cortex-R (Real-time) ----
        "D13"  to "Cortex-R52",
        "D14"  to "Cortex-R52+",
        "D16"  to "Cortex-R82",

        // -- Cortex-M (Microcontroller) ----
        "C20"  to "Cortex-M23",
        "C21"  to "Cortex-M33",
        "C23"  to "Cortex-M55",
        "C24"  to "Cortex-M85",

        // -- Neoverse (Infrastructure) ----
        "D0C"  to "Neoverse-N1",
        "D49"  to "Neoverse-N2",
        "D4F"  to "Neoverse-V2",

        // -- Qualcomm custom (implementer 0x51) ----
        "800"  to "Kryo 2xx Silver",
        "801"  to "Kryo 2xx Gold",
        "802"  to "Kryo 3xx Silver (Cortex-A55)",
        "803"  to "Kryo 3xx Gold (Cortex-A75)",
        "804"  to "Kryo 4xx Gold (Cortex-A76)",
        "805"  to "Kryo 4xx Silver (Cortex-A55)",
        "C00"  to "Falkor",
        "C01"  to "Saphira",

        // -- Samsung (implementer 0x53) ----
        "001"  to "Exynos M1 (Mongoose)",
        "002"  to "Exynos M3",
        "003"  to "Exynos M4",
        "004"  to "Exynos M5",
    )

    /**
     * Look up a part number string (e.g. "D85", "800") and return the
     * human-readable core name, or null if not in the map.
     */
    fun lookup(part: String): String? =
        partMap[part.uppercase().padStart(3, '0')]

    /**
     * Return a description including implementer name.
     * implementer: "0x41" = ARM, "0x51" = Qualcomm, "0x53" = Samsung
     */
    fun describe(implementerHex: String, partHex: String): String {
        val vendor = when (implementerHex.uppercase().trimStart('0', 'X')) {
            "41" -> "ARM"
            "51" -> "Qualcomm"
            "53" -> "Samsung LSI"
            "50" -> "Applied Micro"
            "56" -> "Marvell"
            "61" -> "Apple"
            "66" -> "Faraday"
            "69" -> "Intel"
            else -> "Unknown (0x$implementerHex)"
        }
        val coreName = lookup(partHex) ?: "Unknown core (part 0x$partHex)"
        return "$vendor $coreName"
    }
}
