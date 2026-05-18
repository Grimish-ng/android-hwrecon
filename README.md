# HW·RECON — Android Hardware Reconnaissance Tool

**HW·RECON** is a powerful, dark-themed Android app for deep hardware and kernel-level reconnaissance on **rooted** Android devices (with graceful fallback for non-rooted devices).

It is designed for security researchers, reverse engineers, and developers who need detailed insight into:
- Device Tree (DT)
- CPU/SoC topology & clocks
- Loaded kernel modules + vendor blobs
- HIDL/AIDL HAL interfaces
- dmesg probe/bind events with root-cause analysis
- Physical memory map, interrupts, and pinmux state

---

## Features

- **Full root shell integration** via `su -c` (coroutine-safe with timeout protection)
- **6 specialized collectors**:
  - Device Tree decompiler + platform device enumeration
  - CPU core topology, frequency governors, clock tree, regulators
  - Kernel modules + vendor `.ko` blobs with DT binding hints
  - VINTF manifest + live `lshal` HAL state
  - Smart dmesg filtering with error code explanations (`-ENOENT`, `EPROBE_DEFER`, etc.)
  - I/O memory map, IRQ consumers, pinctrl pinmux
- **Beautiful terminal-style Jetpack Compose UI** matching professional recon tools
- **Reactive StateFlow architecture** — data loads on-demand per tab
- **Export-ready** (easy to extend with JSON/tar.gz export)
- Works great on Snapdragon 8 Gen series, Samsung Exynos, and other modern SoCs

---

## Requirements

- **Rooted Android device** (recommended for full functionality)
- Android 8.0+ (API 26+)
- `su` binary available (Magisk, KernelSU, etc.)

Non-rooted devices will show limited read-only data and a clear warning.

---

## Building the APK

### Option 1: Android Studio (Recommended)

1. Clone or download this repository.
2. Open the project in **Android Studio** (Hedgehog or newer recommended).
3. Let Android Studio sync Gradle and generate the wrapper if prompted.
4. Click **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
5. The signed debug APK will appear in `app/build/outputs/apk/debug/`.

### Option 2: Command Line

```bash
# Generate Gradle wrapper (first time only)
gradle wrapper

# Build debug APK
./gradlew assembleDebug
```

The APK will be at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## Pushing to GitHub

```bash
git init
git add .
git commit -m "Initial commit: HW·RECON v0.1 - Full Android hardware recon tool"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/hw-recon.git
git push -u origin main
```

**Recommended repository settings:**
- Add topics: `android`, `root`, `reverse-engineering`, `device-tree`, `kernel`, `hal`, `recon`
- Enable "Releases" for future APK distribution
- Add a `.github/workflows` CI if you want automated builds

---

## Project Structure

```
HWReconApp/
├── app/
│   ├── src/main/java/dev/hwrecon/
│   │   ├── MainActivity.kt          # Full Compose UI (7 tabs)
│   │   ├── collector/               # All 6 data collectors
│   │   ├── model/                   # Data classes
│   │   ├── shell/                   # RootShell.kt
│   │   ├── util/                    # DriverHintMap, ArmPartMap
│   │   └── viewmodel/               # HwReconViewModel
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
├── .gitignore
└── README.md
```

---

## Future Enhancements (Roadmap)

- One-tap full report export (JSON + tar.gz)
- Expandable Device Tree viewer
- Firmware blob scanner
- Thermal zones & power rails
- SELinux policy dumper
- Web dashboard export option

---

## License

MIT License — feel free to use, modify, and contribute.

**Built with ❤️ for the Android reverse engineering community.**

---

*HW·RECON v0.1 — May 2026*
