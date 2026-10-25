# Folark v2.0.0 — Ultimate Dual-Screen Foldable Launcher

> *A modern, lightweight, ultra-customizable Android launcher based on ARK Launcher, specifically engineered for dual-screen foldables, clamshells, and book-style foldables with Android 17 performance efficiency.*

[![Latest Release](https://img.shields.io/github/v/release/wilhelm3d/Folark?sort=semver)](https://github.com/wilhelm3d/Folark/releases/latest)
[![License: Apache-2.0](https://img.shields.io/github/license/wilhelm3d/Folark)](LICENSE)
[![Built with Jetpack Compose](https://img.shields.io/badge/Built%20with-Jetpack%20Compose-4285F4)](https://developer.android.com/jetpack/compose)
[![Android](https://img.shields.io/badge/Android-11%2B%20(API%2030%2B)-3DDC84?logo=android)](https://developer.android.com)

---

## 🚀 Overview

**Folark v2.0.0** is an open-source, ultra-responsive Android home-screen launcher built 100% with **Jetpack Compose** and **Material 3 Expressive Design**. Re-architected from the high-performance foundations of **ARK Launcher**, Folark delivers a native, fluid experience tailored for foldable devices, featuring independent dual-screen profiles, desktop taskbar overlays, LSPosed system hooks, root-powered Recents, and interactive widgets.

---

## ✨ Complete Feature Matrix (No Matter How Small!)

### 📐 1. Dual-Screen & Foldable Hardware Engineering
- **Independent Screen Profiles**: Maintain separate workspace grid configurations (up to 8x8), icon sizes, dock items, wallpapers, and background themes for the outer cover display vs. inner main display.
- **Hardware-Accurate Posture Detection**: Real-time posture tracking using Android `WindowInfoTracker` supporting Closed, Flat (Unfolded), and Half-Opened / Flex Tabletop postures.
- **Live Hinge Unfold Animations**: Spatial 3D book-sweep, portal, drop, water-ripple, and doors unfold transitions tied directly to physical hinge angle sensors.
- **Hinge-Aware Parallax Wallpapers**: Gyroscope/accelerometer motion-sensor driven wallpaper depth shift across the fold crease.
- **LTPO Dynamic Refresh Rate Throttling**: Ramps refresh rate up to 120Hz/144Hz during touch gestures, automatically stepping down to 60Hz after 3 seconds of inactivity to preserve battery life.
- **Fold-Aware Orientation Lock**: Respects separate orientation preferences for closed vs opened fold states.

---

### 🔌 2. LSPosed (Vector Module) & SystemUI Hooks
- **Native 3-App Triple Split Screen (`SplitThreeHook`)**: Hooks into Android 17's `WM Shell` inside SystemUI to tile **three apps simultaneously** on screen (2 split + 1 third pane).
- **Instant SystemUI Unlock Sync (`UnlockHook`)**: Hooks `KeyguardUnlockAnimationController` to synchronize Folark's unlock-to-home reveal animation directly with SystemUI's lock screen exit.
- **Nav Handle Long-Press Search Override**: Replaces Google Assistant long-press on the gesture bar with Folark's instant on-device search.
- **Recents Gesture Handover (`RecentsHook`)**: Intercepts Android's system Recents command (Alt+Tab, Recents key, swipe-and-hold) and hands it directly over to Folark Recents without visual jank.
- **Secure State Provider (`RecentsHookState`)**: ContentProvider sharing system settings between SystemUI and Folark with Android 14+ caller package verification.

---

### 👑 3. Recents & Root Task Manager
- **Custom Recents Screen**: Custom Compose-based task manager (`Settings ▸ Recents`) displaying running apps and active split-screen pairs.
- **Live Task Screenshots (`LruCache`)**: High-resolution task screenshots decoded on-demand via a daemonized root shell (`RootTasks.snapshot`) with 720px `RGB_565` downsampling to keep RAM under 48MB.
- **Individual & Batch App Termination**: Force-stop specific background apps or execute "Close All" while protecting pinned apps.
- **Process Memory Protection (Pin Keep-Alive)**: Pin critical apps to the top of Recents and use root process protection (`RootTasks.protect`) to prevent Android's OOM killer from terminating them.
- **Daemonized `su` Shell Architecture**: High-performance persistent root shell session (`RootShell`) with automatic 30s idle timeout and fast IPC execution (~1-5ms response).

---

### 📱 4. Built-In Interactive Widgets
- **See-Through Clipboard History Widget**: Frosted-glass, cornerless clipboard history list with instant recopy buttons.
- **Per-App Media Volume Widget**: Active media session volume slider widget with media controls and cover art.
- **Pro Interactive Notification Widget**: Grouped notification list with inline quick reply, dismiss, and pin actions.
- **Smartspace Weather & Calendar Widget**: Animated weather conditions (via keyless Open-Meteo API) and upcoming system calendar events.
- **Nothing-Style Retro Dot Matrix Clock**: Customizable digital clock widget with dot-matrix typography, glass pill backdrops, and minute opacity styling.
- **Device Stats & Battery Gauge Widget**: Real-time monitoring of Storage, Memory, Battery, and connected Bluetooth device levels.

---

### 🎨 5. Personalization, Drawer & Customizer
- **4 Distinct App Drawer Layouts**: Standard Grid, Category Card Interface, Dot Matrix Retro Style, and Bottom Search Flow.
- **Smart Rules (Moto Smart Mode) Support**: Native support for hidden system activities (like Moto Smart Mode) directly in the App Drawer and Search engine.
- **Liquid Glass Frosted Blur**: Real-time Glassmorphism translucency with customizable opacity and blur radius.
- **Icon Customizer & Icon Pack Engine**: Support for third-party icon packs, Material You monochrome themed icons, custom icon shapes (Squircle, Pebble, Circle, Teardrop), and per-app icon renaming.
- **App Hiding & Private Space**: Hide sensitive apps from the drawer or lock them behind Android 15/17 Private Space.

---

### 🪟 6. Multitasking, Gestures & Taskbar
- **Accessibility Taskbar Overlay**: A pull-up taskbar for unfolded displays that works above all apps without requiring system overlay permissions.
- **App Pair & Triple-App Creator**: Group up to 3 apps into single-tap split-screen / floating window shortcuts with deep intent support.
- **Double-Tap To Lock (`LockAccessibilityService`)**: Instant screen locking via Accessibility Service or root.
- **Morphing App Transitions**: Smooth `makeClipRevealAnimation` transitions connecting app icons to launching activities.
- **Tactile Bouncy Scale Feedback**: Spring-based touch scale feedback on icon presses and card taps.

---

### ⚡ 7. Android 17 & Architecture Efficiency
- **AppFunctions Search Integration**: On-device natural language and quick-action search indexing powered by Android 16/17 AppFunctions APIs.
- **Zero-Wakeup Minute Ticker**: Syncs UI clock elements precisely at minute boundaries without continuous CPU wakeups.
- **100% Lifecycle-Aware Flow Collection**: Every state subscription uses `collectAsStateWithLifecycle()`, automatically pausing flow collection when screens are covered or off.
- **Eager App Caching**: Background package events and Binder IPC callback scans are shared eagerly across all ViewModels to eliminate duplicate system scans.

---

## 🛠️ ADB Rights & Permissions Setup Guide

To unlock full system integration (such as status bar auto-hiding, secure settings, and diagnostic dumps) without root access, grant the following permissions via ADB:

### 1. Install the Release APK
```bash
adb install -r app-release.apk
```

### 2. Grant Required Permissions via ADB Terminal
Connect your phone via USB or Wireless Debugging and run:

```bash
# Grant permission to modify system secure settings (for status bar & taskbar auto-hide)
adb shell pm grant org.arkikeskus.launcher android.permission.WRITE_SECURE_SETTINGS

# Grant permission for system diagnostics and state dumps (for Recents and split-screen calculation)
adb shell pm grant org.arkikeskus.launcher android.permission.DUMP

# Grant permission for usage stats tracking (for Taskbar recent apps)
adb shell pm grant org.arkikeskus.launcher android.permission.PACKAGE_USAGE_STATS
```

#### Why are these permissions needed?
- **`WRITE_SECURE_SETTINGS`**: Enables Folark to programmatically manage immersive status bar behavior, display refresh rates, and system animation speed.
- **`DUMP`**: Permits Folark to inspect system window focus, widget host states, and memory allocations for multi-window foldable handling.
- **`PACKAGE_USAGE_STATS`**: Enables the Taskbar to display your recently used apps.

---

## 🔌 LSPosed / Vector Module Setup Guide

If your phone is rooted using **KernelSU**, **Magisk**, or **APatch** with LSPosed/Vector installed, follow these steps to enable Folark's deep SystemUI hooks (3-App Split, Unlock Sync, Recents Handover):

### Step 1: Open LSPosed / Vector Manager
1. Open the **LSPosed** or **Vector** Manager app on your device.
2. Navigate to the **Modules** tab.

### Step 2: Enable the Folark Module
1. Locate **Folark** in the modules list.
2. Toggle the switch to **ON** (Enable Module).

### Step 3: Configure Module Scope
In the module settings, ensure the following target scopes are checked:
- `System UI` (`com.android.systemui`)
- `System Launcher` (`com.motorola.launcher3` / `com.google.android.apps.nexuslauncher` / `com.android.launcher3`)

### Step 4: Restart SystemUI or Reboot
Perform a quick soft-reboot or restart SystemUI (`adb shell su -c pkill systemui`).

### Step 5: Enable SystemUI Recents in Folark Settings
1. Open **Folark Settings** $\rightarrow$ **Recents**.
2. Toggle **Replace Android Recents** to **ON**.
3. You will now see **"Module Active"** highlighted in green!

---

## 🛠️ Tech Stack & Architecture

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
