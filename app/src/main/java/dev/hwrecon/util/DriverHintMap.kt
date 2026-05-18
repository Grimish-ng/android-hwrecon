package dev.hwrecon.util

/**
 * Heuristic mapping from DT compatible strings or module names to likely
 * Linux kernel driver names or human-readable hints.
 * Used to annotate platform devices and vendor blobs with "driver hint".
 */
object DriverHintMap {

    private val compatToDriver = mapOf(
        "qcom,geni-uart" to "msm_serial_hs",
        "qcom,i2c-geni" to "i2c-qcom-geni",
        "qcom,spi-geni" to "spi-geni-qcom",
        "qcom,dwc3" to "dwc3-qcom",
        "qcom,pcie-sm8650" to "qcom_pcie",
        "qcom,adreno" to "msm_drm / adreno",
        "arm,cortex-a520" to "psci / cpu",
        "arm,cortex-a720" to "psci / cpu",
        "arm,cortex-x4" to "psci / cpu",
        "samsung,sec_ts" to "sec_ts",
        "samsung,sec-nfc" to "nfc_sec",
        "qcom,mdss" to "msm_drm",
        "qcom,wcd9xxx" to "wcd",
        "cirrus,cs35l41" to "cs35l41",
    )

    private val moduleToCompat = mapOf(
        "msm_drm" to "qcom,mdss",
        "adreno" to "qcom,adreno",
        "sec_ts" to "samsung,sec_ts",
        "nfc_sec" to "samsung,sec-nfc",
        "qcom_pcie" to "qcom,pcie-sm8650",
        "wcd" to "qcom,wcd9xxx",
        "cs35l41" to "cirrus,cs35l41",
        "ath11k" to "pci18ee:006c",
        "cnss" to "qcom,cnss",
        "ipa" to "qcom,ipa",
    )

    fun lookup(compatible: String): String =
        compatToDriver.entries.firstOrNull { compatible.contains(it.key, ignoreCase = true) }?.value
            ?: compatible.substringAfterLast(',').ifBlank { "unknown" }

    fun moduleToCompatible(moduleName: String): String? =
        moduleToCompat.entries.firstOrNull { moduleName.contains(it.key, ignoreCase = true) }?.value
}
