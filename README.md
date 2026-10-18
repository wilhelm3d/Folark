# Folark — Modern Dual-Screen Foldable Launcher

> *A modern, lightweight, highly customizable Android launcher based on ARK Launcher, specifically engineered for foldable phones and designed in mind for flagship clamshell foldables.*

[![Latest release](https://img.shields.io/github/v/release/wilhelm3d/Folark?sort=semver)](https://github.com/wilhelm3d/Folark/releases/latest)
[![License: Apache-2.0](https://img.shields.io/github/license/wilhelm3d/Folark)](LICENSE)
[![Built with Jetpack Compose](https://img.shields.io/badge/Built%20with-Jetpack%20Compose-4285F4)](https://developer.android.com/jetpack/compose)
[![Android](https://img.shields.io/badge/Android-11%2B%20(API%2030%2B)-3DDC84?logo=android)](https://developer.android.com)

---

## 🚀 Overview

**Folark** is an open-source, ultra-responsive Android home-screen launcher built from the ground up with **Jetpack Compose** and **Material 3 Expressive Design**. Derived from the high-performance foundations of **ARK Launcher**, Folark has been completely re-architected and transformed to deliver a native, fluid, and tailor-made experience for foldable devices, with special optimizations for flagship foldables and Android 17 efficiency.

---

## ✨ Key Features & Advantages

### 📐 Designed for Modern Foldables
- **Independent Dual-Screen Profile Architecture**: Maintain separate workspace layouts, grid configurations, widgets, and dock items for the outer cover screen and inner main display.
- **Hardware-Accurate Posture Detection**: Seamless transition between closed, flex/half-folded (tabletop mode), and unfolded postures.
- **Hinge-Aware Parallax Wallpapers**: Live wallpaper layers shift dynamically across the fold crease for immersive depth.

### ⚡ Android 17 Efficiency & Performance
- **Background WorkManager Polling**: Optimized `WeatherWorker` for battery-efficient background weather and smartspace updates.
- **AppFunctions Search Integration**: On-device natural language and quick-action search indexing powered by Android 16/17 AppFunctions APIs.
- **Zero-Wakeup Minute Ticker**: Syncs UI clock elements precisely at minute boundaries without continuous CPU wakeups.

### 🪟 Advanced Multitasking & Utilities
- **Triple-App & App Pair Creator**: Visual dialog to group up to 3 apps into single-tap split-screen shortcuts with deep intent support.
- **See-Through Clipboard History Widget**: Cornerless, frosted-glass clipboard widget with instant recopy actions.
- **Per-App Volume Control Widget**: Direct media session volume slider for active audio apps.
- **Morphing App Transitions**: Smooth `ClipRevealAnimation` transitions when launching shortcuts.

### 🎨 Fluid Dynamic Motion & UI Styles
- **Bouncy Touch Feedback**: Tactile spring scale press effect on icons, cards, and buttons.
- **Liquid Glass Frosted Blur**: Real-time Glassmorphism Aesthetics translucent frosted glass blur backgrounds with customizable opacity and blur radius.
- **4 Distinct Drawer Styles**: Standard Grid, Category Card Interface, Dot Matrix Retro Style, and Bottom Search Flow Style.

---

## 🛠️ Installation & ADB Permissions Guide

To unlock full system integration (such as immersive status bar hiding, secure settings modification, and system dumps) without root access, follow these setup steps:

### 1. Install the APK
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 2. Grant Required Advanced Permissions via ADB
Connect your device and run the following commands in your terminal:

```bash
# Grant permission to modify system secure settings (for status bar & taskbar auto-hide)
adb shell pm grant org.arkikeskus.launcher android.permission.WRITE_SECURE_SETTINGS

# Grant permission for system diagnostics and state dumps
adb shell pm grant org.arkikeskus.launcher android.permission.DUMP
```

#### Why are these permissions needed?
- **`WRITE_SECURE_SETTINGS`**: Enables the launcher to programmatically manage immersive cover/inner status bar behavior and system preference toggles cleanly.
- **`DUMP`**: Permits the launcher to inspect system window focus, widget host states, and memory allocations for robust multi-window foldable handling.

---

## 🛠️ Architecture & Tech Stack

- **UI Framework**: 100% Jetpack Compose with Material 3 Expressive Design
- **Architecture**: Multi-module Clean Architecture (`:app`, `:core:*`, `:feature:*`)
- **Dependency Injection**: Hilt
- **Persistence**: Room Database + DataStore Preferences
- **Language / Toolchain**: Kotlin 2.4.0 (K2 compiler), JDK 17/21, AGP 9.0+, Android SDK API 30+ (Android 11 to 17)

---

## 📄 License

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at:

http://www.apache.org/licenses/LICENSE-2.0

See [LICENSE](LICENSE) for full details.
