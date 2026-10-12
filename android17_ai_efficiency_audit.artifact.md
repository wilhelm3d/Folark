# Folark Launcher: Android 17 Readiness, Efficiency, & AI Roadmap

This audit focuses on future-proofing the Folark Launcher for Android 16/17 (API 36/37), identifying deep architectural battery optimizations, and integrating next-generation on-device AI features.

---

## 1. Android 16 & 17 Future-Proofing

> [!IMPORTANT]
> Android 15/16 introduces strict OS-level changes to background processes, edge-to-edge rendering, and app lifecycle (Archiving & Private Space). Preparing for Android 17 means adopting these native APIs now.

### A. OS-Level App Archiving Support
*   **The Change**: Android 15+ allows the OS to archive unused apps (removing the APK but keeping user data).
*   **Action for Folark**: Launchers must visually indicate archived apps. When querying `LauncherApps.getActivityList()`, you must check if the app is archived (often surfaced via `ApplicationInfo.isArchived` or specialized `LauncherApps` flags).
*   **Implementation**: Overlay a small "Cloud Download" badge over archived app icons. Tapping an archived app automatically triggers the OS unarchival process.

### B. Predictive Back Animations (Enforced)
*   **The Change**: Android 15 and beyond enforce Predictive Back animations.
*   **Action for Folark**: Migrate any custom back-button handling (like closing the App Drawer or Folder Dialogs) to Jetpack Compose's `PredictiveBackHandler` (available in Compose 1.7+). This allows the user to slowly swipe back and see the Home Screen scaling into view behind the App Drawer before committing to the gesture.

### C. Native Private Space API
*   **The Change**: You recently added `PrivateSpace.kt`. In Android 15+, Private Space is an official OS profile type (`UserManager.USER_TYPE_PROFILE_PRIVATE`).
*   **Action for Folark**: Ensure your app drawer strictly obeys the OS broadcast intents for locking/unlocking the Private Space. When locked, these apps MUST NOT appear in the launcher's search results or recent apps (`AppTaskbar`), as failing to hide them violates Android's privacy guidelines.

---

## 2. Deep Battery & CPU Architecture

> [!TIP]
> While Folark is already highly optimized, the way Compose handles lists and the way background receivers are registered can be refined to hit absolute zero idle drain.

### A. `LazyGrid` / `LazyColumn` Recomposition Trashing
*   **Audit Finding**: In your App Drawer (`AppDrawerScreen.kt`), if you are using `items(apps) { app -> ... }`, any insertion or removal in the list can cause all subsequent items to recompose.
*   **Optimization**: Always use the `key` and `contentType` parameters in your Lazy grids:
    ```kotlin
    items(
        items = apps,
        key = { app -> app.key },
        contentType = { "AppIcon" }
    ) { app -> AppIcon(...) }
    ```
    *Benefit*: Compose will reuse the exact UI nodes during scrolling and only draw the new apps entering the screen, saving GPU/CPU cycles and preventing micro-stutters.

### B. Time/Minute Ticking Synchronization
*   **Audit Finding**: If your `MinuteTicker.kt` or `NothingClockWidget.kt` relies on `delay(60_000)` inside a coroutine `while(true)` loop to update the clock, it will slowly drift out of sync with the system clock, causing unnecessary wakeups.
*   **Optimization**: Use a `BroadcastReceiver` listening strictly for `Intent.ACTION_TIME_TICK`. The Android OS guarantees this broadcast fires exactly at the top of the minute, allowing all apps to wake up simultaneously, rather than fragmenting CPU wakeups.

### C. BroadcastReceiver Lifecycle Scoping
*   **Audit Finding**: `SystemFlagsMonitor.kt` registers receivers for Bluetooth, NFC, etc.
*   **Optimization**: Ensure these are exclusively tied to a `SharingStarted.WhileSubscribed(5000)` flow. If any of these flows are mistakenly collected by a background service rather than just the UI, the launcher will constantly wake up in the background whenever the user toggles Bluetooth or Wi-Fi.

---

## 3. Next-Gen AI Integrations (Privacy-First)

> [!NOTE]
> Launchers are perfectly positioned to be the "AI Hub" of the phone. By utilizing Android's on-device ML APIs, Folark can become incredibly smart without sending any user data to the cloud.

### A. Notification Summarization (Gemini Nano / AICore)
Instead of just showing the full text of notifications in `InteractiveNotificationWidget`, leverage Android's `AICore` (Gemini Nano) to summarize them.
*   **Feature**: If a user gets 5 messages from a WhatsApp chat, pass the strings to the local Gemini Nano model with the prompt: *"Summarize these messages in 5 words."*
*   **Result**: The widget displays: *"Planning dinner at 8 PM"* instead of a wall of text.

### B. AppFunctions (Android 16/17 App Actions)
Android is moving heavily toward `AppFunctions`—where apps declare what they can do natively to the OS.
*   **Feature**: Integrate the `AppFunctionManager` into Folark's universal search.
*   **Result**: When the user searches, they don't just see the "Instagram" app. They see direct, executable chips like "Post a Story on Instagram" or "Send an Email to Mom" directly in the launcher search results.

### C. On-Device Semantic Search (MobileBERT)
Currently, searching for "take a picture" in an app drawer yields nothing unless the app is named "Camera".
*   **Feature**: Embed a tiny (~15MB) quantized text-embedding model (like MobileBERT via TensorFlow Lite or ML Kit).
*   **Result**: When a user types a query, map it to vector space and compare it against the descriptions of installed apps. "Notes", "Write", and "Jot down" will all instantly surface Google Keep and Evernote.

### D. Context-Aware Smart Widgets (Awareness API)
*   **Feature**: Use Google's Awareness API to completely change the layout of `FolarkWidgets.kt` based on context.
*   **Result**:
    *   *Plugging in headphones* automatically brings the Media Widget to the top.
    *   *Connecting to a Car's Bluetooth* swaps the dock out for Maps and Spotify.
    *   *Entering the Gym (Location)* surfaces fitness tracking apps.