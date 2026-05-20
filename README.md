# HW·RECON

> Hardware reconnaissance tool for Android — built for kernel developers, BSP engineers, and device tree reverse-engineers targeting **rooted devices**.

![Platform](https://img.shields.io/badge/platform-Android%2010%2B-brightgreen)
![Root](https://img.shields.io/badge/root-required-red)
![Language](https://img.shields.io/badge/language-Kotlin-purple)
![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-blue)

---

## What it does

HW·RECON reads directly from `sysfs`, `procfs`, `debugfs`, and the live device tree to give you a complete hardware profile of a rooted Android device. The goal is to accelerate:

- Writing or porting a **Device Tree Source** (`.dts` / `.dtsi`)
- Identifying which **driver blobs** and **kernel modules** handle each peripheral
- Understanding the **SoC peripheral topology** (I2C buses, SPI buses, clocks, regulators, pin mux)
- Mapping **HAL interfaces** to their `.so` blobs and kernel drivers
- Diagnosing **probe failures** and missing firmware at boot

---

## Collectors

| Tab | Sources | Key output |
|---|---|---|
| **Device Tree** | `/proc/device-tree`, `dtc` binary | Root compatible strings, node tree, DTS fragment, platform device ↔ driver cross-reference |
| **CPU / SoC** | `/proc/cpuinfo`, `/sys/devices/system/cpu`, `/sys/kernel/debug/clk` | Cluster topology, ARM part decoding, feature flags, clock tree, PMIC rails |
| **Modules** | `/proc/modules`, `/vendor/lib/modules` | Loaded module list with DT compatible hints, vendor `.ko` blob enumeration |
| **HAL / Blobs** | `/vendor/etc/vintf/manifest.xml`, `/vendor/lib64/hw`, `lshal` | VINTF interface table, blob inventory, live HAL process state |
| **dmesg** | `su -c dmesg` | Filtered probe/bind log, error code → root cause cross-reference, DT node annotation |
| **I/O Map** | `/proc/iomem`, `/proc/interrupts`, `/sys/kernel/debug/pinctrl` | Physical memory regions, IRQ → device map, GPIO pin mux state |

---

## Architecture

```
Kernel sources (/proc, /sys, /firmware/devicetree)
        │
        ▼
RootShell  (su -c via ProcessBuilder · coroutine-dispatched)
        │
        ├── DtCollector       ← device tree walker + dtc decompiler
        ├── CpuCollector      ← cpuinfo + clock + regulator tree
        ├── ModuleCollector   ← /proc/modules + vendor blob enum
        ├── HalCollector      ← VINTF XML parser + lshal runner
        ├── DmesgCollector    ← boot log filter + probe failure analyser
        └── IoMapCollector    ← iomem + interrupts + pinctrl
                │
                ▼
        Kotlin Coroutines / StateFlow / ViewModel
                │
                ▼
        Jetpack Compose UI  ←→  ReconExporter (JSON + ZIP)
```

---

## Project structure

```
hwrecon/
├── app/src/main/java/dev/hwrecon/
│   ├── MainActivity.kt
│   ├── collector/
│   │   ├── DtCollector.kt
│   │   ├── CpuCollector.kt
│   │   ├── ModuleCollector.kt
│   │   ├── HalCollector.kt
│   │   ├── DmesgCollector.kt
│   │   └── IoMapCollector.kt
│   ├── model/
│   │   └── Models.kt           ← all data classes
│   ├── shell/
│   │   └── RootShell.kt        ← su command executor
│   ├── ui/
│   │   ├── ReconViewModel.kt   ← orchestration + StateFlow
│   │   ├── ReconScreen.kt      ← all Compose panels
│   │   └── ReconComponents.kt  ← design system components
│   ├── export/
│   │   └── ReconExporter.kt    ← JSON + ZIP report generation
│   └── util/
│       ├── ArmPartMap.kt       ← ARM CPU part number → core name
│       └── DriverHintMap.kt    ← DT compatible ↔ module name
└── gradle/
    └── libs.versions.toml
```

---

## Requirements

| Requirement | Detail |
|---|---|
| Android | 10+ (API 29+) |
| Root | Required — Magisk or equivalent |
| `dtc` binary | Optional — push a static ARM64 build to `/data/local/tmp/dtc` for DTS decompilation |
| Architecture | ARM64 (tested) · ARM32 (untested) |

### Push `dtc` binary

```bash
# Build or grab a prebuilt static ARM64 dtc
adb push dtc /data/local/tmp/dtc
adb shell "su -c chmod 755 /data/local/tmp/dtc"
```

Prebuilt static `dtc` binaries for Android are available from the
[dtc-static-aarch64](https://github.com/sbwml/dtc-static) project.

---

## Building

```bash
# Clone and build debug APK
git clone https://github.com/youruser/hwrecon.git
cd hwrecon
./gradlew assembleDebug

# Install to a connected rooted device
adb install app/build/outputs/apk/debug/app-debug.apk
```

Minimum SDK 29, target SDK 35, compiled with Kotlin 2.0 and AGP 8.5.

---

## Export

Tap **RUN ALL** to collect all subsystems simultaneously. Reports can be exported from the overflow menu as:

- **JSON** — structured, machine-readable; suitable for parsing in scripts or feeding to a DT generator
- **ZIP** — full text dump with one file per subsystem, plus raw DTS and lshal output; ideal for sharing with the team or attaching to a bug report

Exports land in `/sdcard/Android/data/dev.hwrecon/files/hwrecon/` and are shareable via any installed app.

---

## Adding a new collector

1. Create `YourCollector.kt` in `collector/` with a `suspend fun collect(): YourSummary`
2. Add `YourSummary` data class to `Models.kt`
3. Add a `runYourCollector()` function to `ReconViewModel` following the existing pattern
4. Add a new entry to the `ReconTab` enum and a Compose panel function in `ReconScreen.kt`

---

## Disclaimer

This tool is intended for legitimate kernel development, device bring-up, and hardware analysis on devices you own. Reading `/proc` and `/sys` on a rooted device is legal and non-destructive — this app only reads, never writes.

---

## License

MIT — see [LICENSE](LICENSE)
