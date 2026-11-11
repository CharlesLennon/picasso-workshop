# PicassoWorkshop 🚗🔧
> **Citroën C4 Grand Picasso (2006–2022) Interactive Workshop Companion**
> Dedicated offline-first automotive repair guide with interactive 3D spatial models, 2D technical blown-out schematics, real-time explode slider, torque specifications, and dual self-updating architecture.

[![Build PicassoWorkshop APK](https://github.com/CharlesLennon/picasso-workshop/actions/workflows/build-apk.yml/badge.svg)](https://github.com/CharlesLennon/picasso-workshop/actions/workflows/build-apk.yml)
[![Latest Release](https://img.shields.io/github/v/release/CharlesLennon/picasso-workshop?label=App%20Release&color=E11D48)](https://github.com/CharlesLennon/picasso-workshop/releases)
[![Data Package](https://img.shields.io/github/v/release/CharlesLennon/picasso-data?label=Content%20Package&color=38BDF8)](https://github.com/CharlesLennon/picasso-data/releases)

---

## 🌟 Highlights

1. **First-Load Vehicle Onboarding & Selector:**
   - Pre-configured target focus: **Mk1 (B58, 2006–2013)** with the ubiquitous **1.6 HDi 16V Diesel (DV6TED4 / 9HZ, 110 hp)** and **6-Speed EGS automated transmission**.
   - Dynamic vehicle switching sheet supporting 2.0 HDi (DW10BTED4), 1.6 e-HDi 8V (DV6C), 1.6 THP Turbo (Prince EP6DT), and manual/torque converter gearboxes.
   - Persistent selection saved in device `SharedPreferences`.

2. **Full Alternator Disassembly Guide (DV6 1.6 HDi Focus):**
   - **8-Step Comprehensive Procedure:**
     - Step 1: BSI 3-minute sleep protocol & battery negative clamp isolation.
     - Step 2: Offside wheel & arch splash liner removal.
     - Step 3: Auxiliary drive belt detensioning (15mm clockwise lever + 4mm locking pin).
     - Step 4: Power steering pump reservoir unbolting (moved aside without fluid drainage).
     - Step 5: B+ electrical terminal and 2-pin excitation harness disconnect.
     - Step 6: 13mm lower pivot bolt extraction (40 Nm).
     - Step 7: Upper mounting bolts & bracket detachment (40 Nm).
     - Step 8: Alternator rotation & wheel-arch extraction passage.
   - Critical warnings for PSA-specific traps (BSI corruption risk, freewheel alternator pulley inspection).

3. **Dual Visualization Engine with View Switcher `[📐 BLOWN-OUT]` ⟷ `[🧊 3D MODEL]`:**
   - **2D Technical Vector Schematic (Blown-Out / Exploded View):**
     - Scalable vector CAD canvas with coordinate-anchored callout pins (1 through 10).
     - Dotted leader lines connecting assembled positions to blown-out positions.
     - Multi-touch pinch-to-zoom (0.6x to 3.5x) and drag-to-pan.
     - Fastener badge indicators (`13mm Hex`, `15mm Tensioner`, `M8 Nut`).
   - **3D Spatial Projection Engine:**
     - 360° touch orbit with elevation pitch & azimuth yaw rotation.
     - **Interactive Explode Slider (0% to 100%):** Dynamically pushes components along their real-world 3D disassembly trajectories with depth sorting and surface normal lighting.
     - Clickable part selector chips and technical callout card with OEM part numbers and torque ratings.

4. **Decoupled Dual Self-Updating System:**
   - **App Updater (`AppUpdateManager.kt`):** Queries `CharlesLennon/picasso-workshop` GitHub Releases, downloads `app-debug.apk` directly on-device with byte-level progress reporting, and prompts `PackageInstaller` via `FileProvider`.
   - **Data Updater (`WorkshopDataManager.kt`):** Queries `CharlesLennon/picasso-data` decoupled GitHub Releases, hot-reloads `picasso-workshop-data.json` into internal storage without requiring APK upgrades.
   - **100% Offline Garage Fallback:** Bundles complete default offline dataset in `assets/workshop_data.json`.

---

## 📐 Architecture & Technology Stack

- **Platform:** Android 8.0+ (API 26 to API 34)
- **UI Framework:** 100% Jetpack Compose with Material 3 Dark Workshop theme (Citroën Red `#E11D48`, Blueprint Cyan `#38BDF8`, Golden Bolt `#FACC15`)
- **Navigation:** Jetpack Navigation Compose
- **Networking:** Ktor Client with CIO engine & Kotlinx Serialization
- **3D Engine:** Pure Kotlin vector math & Compose Canvas projection (lightweight, zero bulky native C++ NDK overhead)
- **CI/CD:** GitHub Actions building and uploading signed APK artifacts on every push to `main`

---

## 🛠️ Building & Testing

```bash
# Clone the repository
git clone https://github.com/CharlesLennon/picasso-workshop.git
cd picasso-workshop

# Run Unit Tests
./gradlew testDebugUnitTest

# Assemble Debug APK
./gradlew assembleDebug
```

---

## 📄 License
MIT License. Created for Citroën C4 Grand Picasso enthusiasts and DIY mechanics.
