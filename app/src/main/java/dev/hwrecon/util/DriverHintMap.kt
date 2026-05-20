package dev.hwrecon.util

/**
 * DriverHintMap
 *
 * Two-way lookup between:
 *   - DT compatible strings  ->  kernel driver/module name
 *   - Kernel module name     ->  DT compatible string
 *
 * Used by DtCollector and ModuleCollector to cross-reference
 * platform device nodes with the modules that claim them.
 *
 * Sources: kernel Documentation/devicetree/bindings/, upstream driver headers,
 *          and Qualcomm / Samsung BSP sources.
 */
object DriverHintMap {

    // compatible string -> driver module name
    private val compatToDriver = mapOf(
        // -- Qualcomm display ----
        "qcom,mdss-dsi-ctrl"        to "msm_drm",
        "qcom,mdss-dsi"             to "msm_drm",
        "qcom,sm8650-dpu"           to "msm_drm",
        "qcom,mdp5"                 to "msm_drm",

        // -- Qualcomm GPU ----
        "qcom,adreno"               to "adreno",
        "qcom,adreno-740.1"         to "adreno",
        "qcom,adreno-730"           to "adreno",
        "qcom,kgsl-3d0"             to "msm_kgsl",

        // -- Qualcomm serial / QUP ----
        "qcom,geni-uart"            to "msm_geni_serial",
        "qcom,geni-se"              to "geni_se",
        "qcom,i2c-geni"             to "i2c_geni",
        "qcom,spi-geni"             to "spi_geni_qcom",
        "qcom,qupv3-geni-se"        to "geni_se_qup",

        // -- Qualcomm USB ----
        "qcom,dwc3"                 to "dwc3_qcom",
        "snps,dwc3"                 to "dwc3",
        "qcom,usb-ssphy-qmp"        to "msm_usb_ssphy_qmp",

        // -- Qualcomm PCIe ----
        "qcom,pcie-sm8650"          to "pcie_qcom",
        "qcom,pcie-sm8450"          to "pcie_qcom",
        "qcom,pcie-sm8350"          to "pcie_qcom",

        // -- Qualcomm clock controllers ----
        "qcom,sm8650-gcc"           to "gcc_sm8650",
        "qcom,sm8650-camcc"         to "cam_cc_sm8650",
        "qcom,sm8650-dispcc"        to "disp_cc_sm8650",
        "qcom,sm8650-videocc"       to "video_cc_sm8650",

        // -- Qualcomm SPMI / PMIC ----
        "qcom,spmi-pmic-arb"        to "spmi_pmic_arb",
        "qcom,pm8550"               to "qcom_spmi",
        "qcom,pm8550b"              to "qcom_spmi",
        "qcom,pm8998"               to "qcom_spmi",

        // -- Audio ----
        "qcom,wcd9395"              to "wcd9395",
        "qcom,wcd938x"              to "wcd938x",
        "qcom,q6core"               to "q6core",
        "qcom,slim-ngd-ctrl"        to "slimbus",
        "cirrus,cs35l41"            to "cs35l41",
        "cirrus,cs35l45"            to "cs35l45",
        "realtek,rt5665"            to "snd_soc_rt5665",
        "ti,tas2562"                to "tas2562",

        // -- Modem / subsystem ----
        "qcom,q6v5-mss"             to "q6v5_mss",
        "qcom,pil-tz-generic"       to "pil_tz_generic",
        "qcom,ipa"                  to "ipa",
        "qcom,cnss2"                to "cnss2",
        "qcom,wcn3990"              to "ath10k_snoc",

        // -- Wi-Fi ----
        "pci18ee:006c"              to "ath11k_pci",  // WCN7850 Wi-Fi 7
        "pci17cb:1103"              to "ath11k_pci",  // WCN6855 Wi-Fi 6E
        "qcom,wcnss-wlan"           to "wcnss_wlan",

        // -- Touchscreen ----
        "samsung,sec_ts"            to "sec_ts",
        "synaptics,dsx"             to "synaptics_dsx",
        "goodix,gt9110"             to "goodix",
        "elan,ekth6315"             to "elan_i2c",
        "atmel,maxtouch"            to "atmel_mxt_ts",

        // -- NFC ----
        "samsung,sec-nfc"           to "nfc_sec",
        "nxp,pn544"                 to "pn544",
        "nxp,pn553"                 to "nxp-nci",
        "st,st21nfcb"               to "st21nfcb_i2c",

        // -- Fingerprint ----
        "egistec,et713"             to "et713_fp",
        "fpc,fpc1020"               to "fpc1020",
        "goodix,goodix-fp"          to "goodix_fp",

        // -- Thermal / TSENS ----
        "qcom,tsens"                to "qcom_tsens",
        "qcom,sm8650-tsens"         to "qcom_tsens",
        "qcom,spmi-temp-alarm"      to "qcom_spmi_temp_alarm",

        // -- Storage ----
        "qcom,ufshc"                to "ufs_qcom",
        "jedec,ufs-2.0"             to "ufshcd",
        "qcom,sdhci-msm-v5"         to "sdhci_msm",

        // -- Watchdog ----
        "qcom,apss-wdt-sm8650"      to "qcom_wdt",
        "qcom,kpss-wdt"             to "qcom_wdt",

        // -- Power / CPUfreq ----
        "qcom,cpufreq-hw"           to "cpufreq_qcom_hw",
        "operating-points-v2"       to "cpufreq_dt",
        "qcom,cpu-sleep-qll"        to "qcom_llcc",

        // -- Camera ----
        "qcom,cam-req-mgr"          to "cam_req_mgr",
        "qcom,camss"                to "qcom_camss",
        "sony,imx766"               to "imx766",
        "samsung,s5khm6"            to "s5khm6",
    )

    // Reverse map: module name -> compatible (first match wins)
    private val driverToCompat: Map<String, String> by lazy {
        compatToDriver.entries
            .groupBy { it.value }
            .mapValues { it.value.first().key }
    }

    /**
     * Look up a DT compatible string and return the likely kernel module name.
     * Returns "unknown" if not found.
     */
    fun lookup(compatible: String): String =
        compatToDriver[compatible]
            ?: compatToDriver.entries
                .firstOrNull { compatible.contains(it.key, ignoreCase = true) }
                ?.value
            ?: "unknown"

    /**
     * Look up a kernel module/driver name and return its primary DT compatible string.
     * Returns null if not found.
     */
    fun moduleToCompatible(moduleName: String): String? =
        driverToCompat[moduleName]
            ?: driverToCompat.entries
                .firstOrNull { moduleName.contains(it.key, ignoreCase = true) }
                ?.value

    /** Return all known compatible strings as a sorted list. */
    fun allCompatibles(): List<String> = compatToDriver.keys.sorted()
}
