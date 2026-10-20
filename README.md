# Folark — Modern Dual-Screen Foldable Launcher

> *A modern, lightweight, highly customizable Android launcher based on ARK Launcher, specifically engineered for foldable phones and designed with flagship clamshell and book-style foldables in mind.*

[![Latest release](https://img.shields.io/github/v/release/wilhelm3d/Folark?sort=semver)](https://github.com/wilhelm3d/Folark/releases/latest)
[![License: Apache-2.0](https://img.shields.io/github/license/wilhelm3d/Folark)](LICENSE)
[![Built with Jetpack Compose](https://img.shields.io/badge/Built%20with-Jetpack%20Compose-4285F4)](https://developer.android.com/jetpack/compose)
[![Android](https://img.shields.io/badge/Android-11%2B%20(API%2030%2B)-3DDC84?logo=android)](https://developer.android.com)

---

## 🚀 Overview

**Folark** is an open-source, ultra-responsive Android home-screen launcher built 100% with **Jetpack Compose** and **Material 3 Expressive Design**. Derived from the high-performance foundations of **ARK Launcher**, Folark has been completely re-architected to deliver a native, fluid, and tailor-made experience for foldable devices, supporting independent cover/inner profiles, desktop taskbar overlays, interactive widgets, root-powered recents management, and Android 17 performance efficiency.

---

## ✨ Complete Feature Matrix

### 📐 Dual-Screen & Foldable Hardware Features
- **Independent Screen Profiles**: Separate workspace layouts, grid configurations (up to 8x8), icon sizes, dock items, and background themes for the outer cover screen vs. inner main display.
- **Hardware-Accurate Posture Detection**: Real-time posture tracking using Android `WindowInfoTracker` supporting Closed, Flat (Unfolded), and Half-Opened / Flex Tabletop postures.
- **Live Hinge Unfold Animations**: Spatial 3D book-sweep, portal, drop, water-ripple, and doors unfold transitions tied directly to physical hinge angle sensors.
- **Hinge-Aware Parallax Wallpaper**: Gyroscope/accelerometer motion-sensor driven wallpaper depth shift across the fold crease.
- **Display Refresh Rate Throttling**: Dynamic LTPO refresh rate ramping up to 120Hz/144Hz on touch, automatically stepping down to 60Hz when idle to maximize battery life.

### 👑 Recents with Root Capabilities (KernelSU / Magisk / APatch)
- **Built-In Recents Screen**: Custom Compose-based task manager (`Settings ▸ Recents`) displaying running apps and active split-screen pairs.
- **Live Task Thumbnails & Screenshots**: High-resolution task screenshots decoded on-demand via a high-speed daemonized root shell (`RootTasks.snapshot`).
- **Individual & Batch App Termination**: Force-stop specific background apps or perform "Close All" while respecting pinned apps.
- **Process Memory Protection (Pin Keep-Alive)**: Pin critical apps to the top of Recents and use root process protection (`RootTasks.protect`) to prevent Android's OOM killer from terminating them.
- **Daemonized `su` Shell Architecture**: High-performance persistent root shell session (`RootShell`) with automatic 30s idle timeout and fast IPC execution (~1-5ms response).

### ⚡ Android 17 & Performance Optimizations
- **AppFunctions Search Integration**: On-device natural language and quick-action search indexing powered by Android 16/17 AppFunctions APIs.
- **Zero-Wakeup Minute Ticker**: Syncs UI clock elements precisely at minute boundaries without continuous CPU wakeups.
- **Lifecycle-Aware State Collection**: 100% of state observation uses `collectAsStateWithLifecycle()`, automatically pausing flow collection when screens are covered or off.
- **Eager App Caching**: Background package events and Binder IPC callback scans are shared eagerly across all ViewModels to eliminate duplicate system scans.

### 🪟 Multitasking & Taskbar
- **Accessibility Taskbar Overlay**: A pull-up taskbar for unfolded displays that works above all apps without requiring system overlay permissions.
- **App Pair & Triple-App Creator**: Group up to 3 apps into single-tap split-screen / floating window shortcuts with deep intent support.
- **Quick App Switching & Recents Button**: Integrated recents button and quick-switch gestures to jump back to the previously active app.
- **Morphing App Transitions**: Smooth `makeClipRevealAnimation` transitions connecting app icons to launching activities.

### 📱 Built-In Interactive Widgets
- **See-Through Clipboard History Widget**: Frosted-glass, cornerless clipboard history list with instant recopy buttons.
- **Per-App Media Volume Widget**: Active media session volume slider widget with media controls and cover art.
- **Pro Interactive Notification Widget**: Grouped notification list with inline quick reply, dismiss, and pin actions.
- **Smartspace Weather & Calendar Widget**: Animated weather conditions (via keyless Open-Meteo API) and upcoming system calendar events.
- **Nothing-Style Retro Dot Matrix Clock**: Customizable digital clock widget with dot-matrix typography, glass pill backdrops, and minute opacity styling.
- **Device Stats & Battery Gauge Widget**: Real-time monitoring of Storage, Memory, Battery, and connected Bluetooth device levels.

### 🎨 Personalization & Drawer Styles
- **4 Distinct App Drawer Layouts**: Standard Grid, Category Card Interface, Dot Matrix Retro Style, and Bottom Search Flow.
- **Liquid Glass Frosted Blur**: Real-time Glassmorphism translucency with customizable opacity and blur radius.
- **Icon Customizer & Icon Pack Engine**: Support for third-party icon packs, Material You monochrome themed icons, custom icon shapes (Squircle, Pebble, Circle, Teardrop), and per-app icon renaming.
- **Custom Gestures**: Customizable swipe-up, swipe-down, double-tap, and edge-swipe actions for lock screen, notification shade, search, or specific apps.

---

## 🛠️ Installation & ADB Setup Guide

To unlock full system integration (such as status bar auto-hiding, secure settings, and diagnostic dumps) without root access, follow these setup steps:

### 1. Install the APK
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 2. Grant Required Advanced Permissions via ADB
Connect your device via USB or Wireless Debugging and run the following commands in your terminal:

```bash
# Grant permission to modify system secure settings (for status bar & taskbar auto-hide)
adb shell pm grant org.arkikeskus.launcher android.permission.WRITE_SECURE_SETTINGS

# Grant permission for system diagnostics and state dumps (for Recents and split-screen calculation)
adb shell pm grant org.arkikeskus.launcher android.permission.DUMP
```

#### Why are these permissions needed?
- **`WRITE_SECURE_SETTINGS`**: Enables the launcher to programmatically manage immersive status bar behavior and system preference toggles cleanly.
- **`DUMP`**: Permits the launcher to inspect system window focus, widget host states, and memory allocations for robust multi-window foldable handling.

---

## 🛠️ Architecture & Tech Stack

- **UI Framework**: 100% Jetpack Compose with Material 3 Expressive Design
- **Architecture**: Multi-module Clean Architecture (`:app`, `:core:*`, `:feature:*`)
- **Dependency Injection**: Hilt
- **Persistence**: Room Database + DataStore Preferences
- **Language / Toolchain**: Kotlin 2.4.0 (K2 compiler), JDK 17/21, AGP 9.4+, Android SDK API 30+ (Android 11 to 17)

---

## 📄 License

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at:

http://www.apache.org/licenses/LICENSE-2.0

See [LICENSE](LICENSE) for full details.
