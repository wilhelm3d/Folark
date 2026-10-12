# Folark Launcher: System Image Audit & AI Feature Roadmap

Based on an analysis of the extracted Motorola system image (`system_ext_a_out`, `product_a_out`) and your current codebase, here is a comprehensive audit focusing on API discoveries, battery efficiency, and AI-driven feature enhancements.

## 1. Motorola & OS API Integrations (Discovered in `system img`)

The system image reveals several privileged Motorola services and standard OS components that Folark can hook into to feel native on these devices.

*   **Smart Feed (`com.motorola.smartfeed`) & Google Discover**
    *   **Finding:** The image contains Motorola's minus-one screen (Smart Feed). Currently, Folark doesn't have a "swipe right from home screen" feed.
    *   **Actionable API:** Implement the standard `ILauncherOverlay` AIDL. This allows the user to swipe to the leftmost screen to reveal either Google Discover or the Moto Smart Feed, making Folark feel like a first-party launcher.
*   **Moto Desktop / Ready For (`com.motorola.desktop`)**
    *   **Finding:** Motorola includes extensive desktop-mode frameworks for external displays.
    *   **Actionable API:** Add `<category android:name="android.intent.category.SECONDARY_HOME" />` to `LauncherActivity` in `AndroidManifest.xml`. When the phone is plugged into a monitor, Folark will be launched in desktop mode, allowing you to build a Windows-like desktop experience using Compose.
*   **Edge Assistant (`com.motorola.edgeassistant`)**
    *   **Finding:** The floating edge panel requires specific permissions (`com.motorola.edgeassistant.provider.permission.READ_MODE`).
    *   **Actionable API:** You can add an Intent broadcast in Folark's gesture settings to toggle this edge panel directly via a double-tap or swipe gesture.
*   **Moto App Forecast (`com.motorola.appforecast`)**
    *   **Finding:** Motorola runs a background AI service to predict app usage.

## 2. Battery & Efficiency Micro-Optimizations

While you have already applied macro-optimizations (like `@Immutable` data models and `AlarmManager` for notifications), there are a few micro-optimizations left on the table:

*   **Hardware Sensor Batching (`ParallaxSensorManager.kt`)**
    *   **Current State:** `sensorManager?.registerListener(listener, s, SensorManager.SENSOR_DELAY_UI)`
    *   **Fix:** The hardware sensor wakes up the CPU continuously. Add the 4th parameter for `maxReportLatencyUs` (e.g., `33000` micro-seconds). This allows the hardware sensor hub to batch events and wake up the application processor in chunks, drastically saving battery during parallax.
*   **App Icon Rasterization Memory (`AppIconFetcher.kt`)**
    *   **Current State:** Renders all icons onto an `ARGB_8888` temp bitmap before hardware conversion.
    *   **Fix:** Evaluate if the drawable has an alpha channel. If it's a fully opaque icon, render it to `Bitmap.Config.RGB_565`. This cuts the VRAM consumption of the temporary bitmap allocation by 50% during scroll bursts.
*   **Coroutine Yielding in DB Migrations (`HomeLayoutRepository.kt`)**
    *   **Current State:** Bulk grid operations run in a tight `while` loop.
    *   **Fix:** Insert `yield()` inside heavy loops (like massive grid shifting). This ensures the `Dispatchers.Default` thread isn't locked, preventing dropped frames in the Compose UI thread.

## 3. AI Feature Ideas for the Launcher

To make Folark truly stand out as a modern, "smart" launcher, you can leverage on-device ML and modern Android AI APIs without compromising privacy.

### A. OS-Level App Predictions (`AppPredictionManager`)
Instead of calculating recent apps manually in `AppTaskbar.kt` using `UsageStatsManager` (which just shows what was recently opened), you can query Android's native `AppPredictionManager` API (Android 10+).
*   **How it works:** By registering Folark as the default Home app, you can request an `AppPredictor`. The Android OS (Device Personalization Services) uses a local ML model to return highly accurate app suggestions based on the user's habits, time of day, location, and connected Bluetooth devices (e.g., suggesting Spotify when headphones connect).

### B. Smart Folder Naming (ML Kit Text Classification)
When a user drags one app onto another to create a folder, use Google's local ML Kit API to automatically suggest a folder name.
*   **How it works:** If the user drops *Instagram* and *TikTok* together, grab their Play Store categories or app labels, pass them to the on-device ML text classifier, and have the UI pre-fill the folder name as "Social" or "Entertainment".

### C. Semantic Local Search (Vector Embeddings)
The current App Drawer search likely relies on text prefix matching (e.g., typing "spo" finds "Spotify").
*   **How it works:** Ship a tiny quantized embedding model (like MobileBERT) or use local tags. If the user types "music" or "driving", the search bar understands the *intent* and surfaces Spotify or Google Maps, even if those words aren't in the app names.

### D. Smart Replies in Notification Widget (`InteractiveNotificationWidget.kt`)
*   **How it works:** Android offers an on-device `SmartReplyGenerator`. When a WhatsApp or SMS notification arrives, you can generate 3 contextual reply buttons directly inside your custom notification widget, allowing users to reply with a single tap without opening the app or pulling down the system shade.

### E. Generative AI Wallpapers (Gemini Nano via AICore)
Newer Motorola and Pixel devices feature Gemini Nano.
*   **How it works:** Integrate Android 14's `AICore` to allow users to type a prompt in Folark's Theme Settings (e.g., "A futuristic neon city"). The launcher generates a unique wallpaper entirely on-device, applying it instantly with your parallax effects.
