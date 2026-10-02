# Folark — Modern Dual-Screen Foldable Launcher

> *A modern, lightweight, highly customizable Android launcher based on ARK Launcher, specifically engineered for foldable phones and designed in mind for flagship clamshell foldables.*

[![Latest release](https://img.shields.io/github/v/release/wilhelm3d/Folark?sort=semver)](https://github.com/wilhelm3d/Folark/releases/latest)
[![License: Apache-2.0](https://img.shields.io/github/license/wilhelm3d/Folark)](LICENSE)
[![Built with Jetpack Compose](https://img.shields.io/badge/Built%20with-Jetpack%20Compose-4285F4)](https://developer.android.com/jetpack/compose)
[![Android](https://img.shields.io/badge/Android-11%2B%20(API%2030%2B)-3DDC84?logo=android)](https://developer.android.com)

---

## 🚀 Overview

**Folark** is an open-source, ultra-responsive Android home-screen launcher built from the ground up with **Jetpack Compose** and **Material 3 Expressive Design**. Derived from the high-performance foundations of **ARK Launcher**, Folark has been completely re-architected and transformed to deliver a native, fluid, and tailor-made experience for foldable devices, with special optimizations for flagship clamshell foldables.

---

## ✨ Key Features

### 📐 Designed for Modern Foldables
- **Dual-Screen Independent Profile Architecture**: Maintain separate workspace layouts, grid configurations, widgets, and dock items for the outer cover screen and inner main display.
- **Hardware-Accurate Posture Detection**: Seamless transition between closed, flex/half-folded, and unfolded postures (`1080×2520` outer cover screen vs `2232×2484` inner main screen).

### ⚡ Chip & Display Hardware Optimizations
- **Snapdragon 8s Gen 3 / Adreno 735 GPU Hardware Layer Caching**: Hardware-accelerated GPU shader caching for buttery-smooth Compose rendering without dropped frames.
- **Kryo Multi-Core Processing**: Distributed background task handling for instant app indexing, widget updates, and layout persistence.
- **165Hz LTPO AMOLED Frame Pacing**: Native support for high-refresh-rate displays up to 165Hz with zero jitter.

### 🎨 Fluid Dynamic Motion
- **Bouncy Touch Feedback**: Tactile `0.92x` spring scale press effect on icons, cards, and buttons.
- **3D Book Unfold Sweep**: Spatial perspective rotation transition as the device unfolds from cover display to main display.
- **App Launch Zoom**: Expressive `1.25x` launch zoom curve connecting app icons seamlessly to launching activities.
- **Page Edge Bounce**: Fluid overscroll physical momentum when scrolling workspace pages and app drawers.

### 🍷 Drawer Styles & Liquid Glass
- **4 Distinct Drawer Styles**:
  - **Standard Grid**: Clean, classic vertical scrolling grid.
  - **Category Card Interface**: Horizontal paged drawer with smooth pagination.
  - **Dot Matrix Retro Style**: Minimalist Dot-Matrix styled typography and icon alignment.
  - **Bottom Search Flow Style**: Clean, streamlined launcher drawer layout.
- **Custom Accent Color Picker**: Full custom accent color selection alongside dynamic Material You system colors.
- **Liquid Glass Frosted Blur**: Real-time Glassmorphism Aesthetics translucent frosted glass blur backgrounds with customizable opacity and blur radius.

### 🔔 Pro Interactive Notification List Widget
- **Interactive Gestures**: Swipe right to dismiss notifications; swipe left to pin critical notifications.
- **Inline Quick Actions**: Reply, mark read, or open directly from the widget surface.
- **Smart App Grouping**: Intelligently aggregates notifications by application.
- **See-Through Glass Aesthetics**: Frosted glass container matching system wallpaper colors.

### 📱 Dense 8×8 Grid & 1×1 Widget Resizing
- **Dense 8×8 Workspace Grid**: High-density screen utilization suitable for foldable inner screens and cover displays.
- **1×1 Widget Resizing**: Shrink any system or built-in widget down to 1×1 tiles with Material 3 glowing handles and live dimension badges.

### ⚙️ Material 3 Expressive Adaptive Settings
- **Adaptive Settings Layout**: Automatically renders a **Tablet Two-Pane** settings view when unfolded and a single-column **Phone Layout** when folded.
- **Independent UI DPI Scaling**: Granular scale controls (70% to 140%) for Workspace, App Drawer, and Settings UI independently.

### 🌌 Parallax Wallpaper & App Renaming
- **3D Parallax Motion Effect**: Motion-sensor (gyroscope/accelerometer) driven wallpaper depth movement.
- **Long-Press App Renaming**: Custom title labels for any app icon on the home screen or app drawer.

---

## 🛠️ Architecture & Tech Stack

- **UI Framework**: 100% Jetpack Compose with Material 3 Expressive Design
- **Architecture**: Multi-module Clean Architecture (`:app`, `:core:*`, `:feature:*`)
- **Dependency Injection**: Hilt
- **Persistence**: Room Database (Workspace & App database) + DataStore (Preferences)
- **Image Loading**: Coil
- **Widget Hosting**: Android `AppWidgetHost` with custom Compose wrapper and 1×1 resizing constraints
- **Language / Toolchain**: Kotlin 2.1+, JDK 21, AGP 8.9+, Android SDK API 30+ (Android 11+)

---

## 📦 Building & Installation

### Prerequisites
- Android Studio Ladybug / ME2026 or newer
- JDK 21 (Java Development Kit)
- Android SDK API level 35

### Build APK
```bash
# Clone repository
git clone https://github.com/wilhelm3d/Folark.git
cd Folark

# Assemble debug APK
./gradlew :app:assembleDebug
```

### Install via ADB
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 Origin & License

Folark is derived from [ARK Launcher](https://github.com/jrs8205/ARK-launcher) and adapted/enhanced under the **Apache License, Version 2.0**.

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at:

http://www.apache.org/licenses/LICENSE-2.0

See [LICENSE](LICENSE) for full details.
