# Folark — Energy Efficiency Report

**Scope:** `app`, `core/*`, `feature/*`, `hook` (Kotlin/Compose Android launcher, v2.1.0)
**Method:** static source audit of every power-relevant mechanism (services, alarms, sensors, receivers, frame loops, polling, network)
**Date:** 2026-11-08
**Device class assumed:** foldable / dual-screen phone (Razr-class), Android 15–17

> **What this report is not.** No on-device traces were captured for it: no `batterystats`, no
> `dumpsys`, no FrameTimeline, no macrobenchmark. Every number below is either read out of the source
> (constants, intervals, sensor rates, frame delays) or an order-of-magnitude estimate derived from
> them. Anything estimated is marked **[est]**. Section 6 gives the exact commands to turn each
> estimate into a measurement.

---

## 1. Executive summary

Folark is **already a carefully power-managed launcher**. It gates animations on battery saver, follows
the hinge only while folding, reads location without waking a provider, drops the panel to 60 Hz three
seconds after the last touch, leaves adaptive-refresh displays alone entirely, and stops most loops when
the screen goes off. That is well above the norm for a Compose launcher and this report should not be
read as "the app is wasteful".

The remaining cost is **concentrated in six places**, all of them continuous-while-visible work:

| # | Hotspot | Where | Nature of cost | Severity |
|---|---------|-------|----------------|----------|
| 1 | Looping video wallpaper | `VideoBackground.kt` | hardware video decode + composition, unbounded duration | **High** (user-enabled) |
| 2 | Taskbar usage-event polling every 1–3 s | `TaskbarHost.kt`, `TaskbarWatch.kt` | binder call into `system_server` + a live `HandlerThread` | **High** |
| 3 | Weather sky redraw at 30/15 fps | `WeatherDayWidget.kt:193–244` | GPU/CPU frames for 45 s per home visit, plus 1 Hz idle re-check forever | **Medium-High** |
| 4 | Notification snapshot rebuilds | `NotificationDotListenerService.kt` | copies *all* notifications over binder; 25/s ceiling under a burst | **Medium** |
| 5 | Five-way system listener fan-in for the home status bar | `StatusBar.kt` + 4 monitors | telephony/network/broadcast callbacks kept registered | **Medium** |
| 6 | `RTC_WAKEUP` exact alarms per scheduled pair | `PairScheduler.kt` | wakes the AP out of Doze, N alarms for N pairs | **Medium** |

Ordered by *expected milliwatt savings per engineering hour*, the best four changes are:

1. Make the taskbar **stop polling when the accessibility service is already reporting foreground apps**
   (it reports `TYPE_WINDOW_STATE_CHANGED` — the information is free, the poll is redundant).
2. Cap the **video wallpaper** to a bounded awake window (same trick the weather sky already uses) and
   pause it on dim.
3. Raise the weather sky's **resting re-check** from 1 s to a push signal, and drop rain to 24 fps.
4. **Debounce `AppUsageRepository`'s DataStore write** (currently one flash write per app launch).

---

## 2. Power model inventory

Everything in the app that can hold the CPU, the display pipeline, a radio or a sensor awake, with the
condition under which it runs.

### 2.1 Persistent listeners (always alive while the process is)

| Mechanism | File | Bound to | Cost class |
|---|---|---|---|
| `NotificationListenerService` | `NotificationDotListenerService.kt` | system binding, user-granted access | process kept warm; binder callbacks per notification |
| Shared settings `DataStore` flow (`SharingStarted.Eagerly`) | `LauncherApplication.kt:44–46` | app lifetime | one file watch + 4 in-memory collectors |
| `AppUsageRepository` DataStore writes | `AppUsageRepository.kt:44` | every app launch / pair launch | flash write + full map re-serialization |
| `PairScheduleReceiver` | `AndroidManifest.xml` | `RECEIVE_BOOT_COMPLETED` + alarms | rare |
| Accessibility service | `LockAccessibilityService.kt` | only while user enabled it; subscribes to window-state events **only** when taskbar rules / Recents replacement need it | near-zero when idle ✔ |

No `WAKE_LOCK` anywhere in the project, no `ForegroundService`, no `WorkManager`, no `JobScheduler`.
That is a good baseline — the app never holds a partial wake lock.

### 2.2 Sensors

| Sensor | Rate | When | Verdict |
|---|---|---|---|
| Hinge (`TYPE_HINGE_ANGLE`) | 100 Hz (`SAMPLING_FAST_US = 10_000`) | only during a fold/unfold transition, ≤ 2.5 s, only if "live hinge" is selected and battery saver is off | proportionate |
| Hinge (tilt) | 15 Hz (`SAMPLING_UI_US = 66_667`) | while home is resumed, half-folded, `hingeTilt` on | fine |
| Rotation vector / gyro | `SENSOR_DELAY_UI`, batched at 66 ms, rate-limited to ~30 fps, stationary auto-pause after 1.5 s, 0.5 px dead band | while parallax is on, home resumed, no battery saver, drawer not covering | **well engineered** ✔ |
| Location | **never requested actively** — `getLastKnownLocation` on fused/network/passive/GPS | on a weather refresh | **best-practice** ✔ |

### 2.3 Frame producers (display pipeline)

| Producer | Rate | Duration | Gate |
|---|---|---|---|
| Weather sky `Canvas` | 33 ms (rain/snow/storm) / 66 ms (clear/cloud/fog) | `weatherAwakeSeconds` (default **45 s**) per resume | battery saver, drawer covering, widget bounds in window |
| Weather sky idle re-check | 1 Hz, forever | widget composed | none |
| Looping video wallpaper | decoder-paced (typically 24–30 fps) | **unbounded** while home is in front | battery saver, drawer covering, lifecycle pause |
| `LivePill` effect | 16 ms via `withFrameNanos` + `delay` | 60 s per appearance/touch | `animate && LivePillPainter.animates(effect)` |
| `IconRing` indeterminate spin | every frame | 30 s (`SPIN_AWAKE_NS`) per busy icon | ring is busy (download/timer without progress) |
| `SecondTicker` | 1 Hz | while any pill/card subscribed | `WhileSubscribed(1_000)` |
| `MinuteTicker` | `ACTION_TIME_TICK` (1/min) | while clock composables exist | one receiver **per composable instance** |
| `WidgetStack` auto-rotate | `autoRotateSeconds` | while stack visible | battery saver, home covered, touch, menu, media |

### 2.4 Alarms

`PairScheduler` uses `setExactAndAllowWhileIdle(RTC_WAKEUP, …)` per scheduled pair, falling back to
`setWindow(RTC_WAKEUP, …, ±10 min)` when exact alarms are not granted. Each fire re-lays **all** alarms
via `rescheduleAll`.

---

## 3. Findings

Severity reflects *expected* battery impact on the target device, not code quality.

---

### F1 — Looping video wallpaper is unbounded in time **[High, user-enabled]**

`app/src/main/kotlin/org/arkikeskus/launcher/ui/VideoBackground.kt:71–87`

```kotlin
isLooping = true
setVolume(0f, 0f)
prepareAsync()
```

Gating in `LauncherShell.kt:359–362` is correct but *unbounded*:

```kotlin
playing = !homeCovered && !LocalBatterySaver.current
```

A hardware video decoder + SurfaceFlinger composition running continuously is the single most
expensive thing this app can do — an order of magnitude above every animation in §2.3 combined.
A user who picks a video backdrop and leaves the phone on the home screen gets decode power for the
whole screen-on period (minus `weatherAwakeSeconds`-style rest, which video does not have).

**Recommendation (highest value per line of code):**
1. Reuse the pattern `WeatherDayWidget` already implements: play for a bounded window after resume
   (e.g. 20–30 s), then `pause()` and hold the last frame. The video is ambience; nobody audits it.
2. Pause on `ACTION_SCREEN_OFF` explicitly (lifecycle resume already covers this, but a
   `PowerManager.isInteractive` check in `setPlaying` closes the race during the screen-off transition).
3. Offer the window as a setting next to "Sky moves for", so the trade-off is the user's.
4. Optional: warn in Settings that a video backdrop costs battery, and disable it automatically when
   `batterySaverWhenHot` / low-battery saver engages (already true via `!LocalBatterySaver`).

---

### F2 — Taskbar polls `UsageStatsManager` every 1–3 s while the screen is on **[Medium — partly mitigated already]**

`feature/home/src/main/kotlin/org/arkikeskus/launcher/feature/home/taskbar/TaskbarHost.kt:184–233`
`feature/home/.../taskbar/TaskbarWatch.kt:12–28`

> **Correction after re-reading the gating.** The original rating of High was too strong: polling
> already stops whenever the accessibility service is connected (`!LockAccessibilityService.isRunning`),
> so this cost is limited to users who run the taskbar through the overlay window and never enable the
> service. The reverse case was the real defect — service connected but *not* subscribed to
> window-state events meant polling off **and** no events, so the running dots could go stale.

```kotlin
const val FAST_MS = 1_000L      // after a switch
const val SLOW_MS = 3_000L      // while the bar is shown
const val HIDDEN_MS = 10_000L   // bar hidden: handles only
```

Every tick calls `usage.queryEvents(lastEventAt, now)` — a **binder round trip into `system_server`**
that also holds a dedicated `HandlerThread("folark-taskbar-apps")` alive. The backoff (`×1.5` toward the
cap) and the screen-off / cover-screen bail-outs are good, but the steady state while a user is browsing
is still one query per 3 s, and one per 10 s while sitting in another app **[est: 20–60 queries per
10 min of use]**.

Crucially, the app **already has a free, event-driven source of the same information**:
`LockAccessibilityService.onAccessibilityEvent` receives `TYPE_WINDOW_STATE_CHANGED` and calls
`TaskbarHost.onForegroundApp(pkg)` with an exact package/class pair — documented in the source as
*"more exact than usage events"*.

**Recommendation:**
1. When the accessibility service is connected and watching, **use `HIDDEN_MS` (or stop the tick
   entirely)** and rely on the service callback. Polling then only exists as a fallback for devices
   without the service — which is exactly the case `TaskbarWatch` was written for.
2. Keep `checkNow()` as the wake-up path (screen on, unfold, handle touch) so staleness is bounded.
3. If polling must stay, raise `SLOW_MS` to 5 s: the running-dot indicator does not need 3 s resolution.

**Status: applied.** `TaskbarWatch.cap()` now takes `heard`; `LockAccessibilityService.reportsForeground`
reports whether the service is subscribed to window-state events; the watch thread stays up whenever
the bar needs the app in front (closing the stale-dots hole) and rests at a 20 s safety-net pace while
the service is hearing switches. `HIDDEN_MS` went from 10 s to 30 s.

---

### F3 — Weather sky: 45 s of 30 fps + a 1 Hz forever-loop **[Medium-High]**

`feature/home/.../widgets/WeatherDayWidget.kt:189–244`

```kotlin
val frameMs = if (sky == Sky.RAIN || sky == Sky.SNOW || sky == Sky.STORM) 33L else 66L
val awakeNs = if (widgets.weatherAwakeSeconds <= 0) Long.MAX_VALUE else widgets.weatherAwakeSeconds * 1_000_000_000L
...
if (onScreen && now - awakeSince < awakeNs) { time.floatValue = ...; delay(frameMs) }
else { delay(1_000) }   // "Scrolled away, or resting: look again now and then"
```

Three separate costs:

1. **Active window.** 45 s of rain at 30 fps = ~1350 full `Canvas` redraws of 38–56 particles plus
   clouds, on top of the wallpaper. Draining but bounded, and correctly gated (battery saver, drawer,
   `boundsInWindow`).
2. **Idle poll forever.** Even after the sky rests, the coroutine wakes **1×/second for as long as the
   widget is composed** — and home stays composed behind every app. With one widget per display
   (inner + outer) that is 2 wake-ups/s indefinitely.
3. **"Always" setting.** `weatherAwakeSeconds = 0` ⇒ `Long.MAX_VALUE` ⇒ **unbounded 30 fps**. The
   CHANGELOG fixed exactly this bug once (`"a phone left on home drew rain at 30 frames a second until
   the screen went off"`); the setting re-opens it deliberately. That is legitimate as a choice, but it
   should be labelled as the high-power option.

**Recommendation:**
1. Replace the 1 s poll with a pushed signal: `snapshotFlow { placed[0]?.boundsInWindow() }` +
   `LifecycleResumeEffect` + a `LocalHomeCovered` read. Resting then costs **zero**.
2. Drop precip to 24 fps (42 ms) — visually indistinguishable for rain/snow, ~20 % fewer frames.
3. Rename/annotate the "Always" option (`Sky moves for ▸ Always`) with an explicit battery note, and
   force-rest it when `batterySaverWhenHot`/low-battery engages even if the user chose Always.

---

### F4 — Notification snapshot rebuilds are cheap per event, expensive at burst rate **[Medium]**

`app/src/main/kotlin/org/arkikeskus/launcher/notifications/NotificationDotListenerService.kt`

```kotlin
const val COALESCE_MS = 40L        // posts/removals gathered for one rebuild
const val QUIET_UPDATE_MS = 1_000L // ongoing (progress/timer) updates
```

The coalescing, the quiet-update path (`refreshLive` only, no dots/people/widgets), the retry cap, and
the cancel-on-disconnect teardown are all correct and above average. Two residual issues:

1. **`COALESCE_MS = 40` is a rate ceiling of 25 full rebuilds/second.** A steady stream of *posts*
   (a mail sync, a group chat, a CI bot) is not an ongoing-notification update, so it never hits the
   1 s quiet gate. Each rebuild calls `getActiveNotifications()` — a binder transaction that parcel
   copies **every active notification, including its extras** — then re-derives counts, icons, people
   tiles and widget entries.
2. **`refreshMailPackages()` on every package add/remove** does a `queryIntentActivities` sweep on IO.

**Recommendation:**
1. Add a hard floor for full rebuilds (e.g. `MIN_FULL_REBUILD_MS = 250`), with the existing
   `refreshWithRetries()` left as the escape hatch for removals that must be reflected immediately.
2. For a removal, the snapshot can be patched from the `sbn.key` in the callback instead of a full
   re-read — a map delete plus a recount.
3. Coalesce `ACTION_PACKAGE_ADDED/REMOVED` bursts (an install session emits several) behind the same
   250 ms floor.

**Status: applied.** `MIN_FULL_REBUILD_MS = 250` now floors full rebuilds; `scheduleRefresh()` *defers*
to the floor rather than dropping the rebuild, so no event is lost and worst-case latency goes from
40 ms to 250 ms. `refreshWithRetries()` (batch deadlines, time changes) stays outside the floor.

---

### F5 — Home status bar keeps five system listener families registered **[Medium]**

`feature/home/.../StatusBar.kt:104–108` combines:

| Source | Mechanism | Frequency |
|---|---|---|
| `BatteryMonitor` | sticky `ACTION_BATTERY_CHANGED` | ~1/min, several/min while charging |
| `ConnectivityMonitor` | Wi-Fi network callback | on change |
| `SignalMonitor` | two `TelephonyCallback`s | **frequent while the radio moves** |
| `SystemFlagsMonitor` | 2 `ContentObserver`s + 5 protected broadcasts, ~10 system reads per change | on change |
| `NotificationBadgeRepository.icons` | notification listener | per notification |

`SystemFlagsMonitor` already does the right thing (`conflate()` the trigger, read ~10 flags on `IO`,
`distinctUntilChanged`), and `BatteryMonitor` shares one receiver across all widgets
(`shareIn(WhileSubscribed(5_000))`). The residual cost is that all five stay subscribed for 5 s after
the status bar stops being collected — and the status bar is collected **whenever home is composed**,
including behind another app, if the bar is enabled.

**Recommendation:**
1. Feed the status-bar view model a `visible` gate (same `LocalHomeCovered` / lifecycle flag used by
   parallax and the weather sky) and stop collection when home is covered. Signal strength and network
   callbacks are the two that matter here; they are exactly the sort of thing that should not be
   registered while the user is in another app.
2. `StatusViewModel` could reduce on `distinctUntilChanged()` of the whole `StatusBarState` — cheap
   insurance against a radio flap redrawing the bar for an identical value.

---

### F6 — Per-pair `RTC_WAKEUP` exact alarms **[Medium]**

`app/.../pairs/PairScheduler.kt:130–152`

```kotlin
manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)   // one per scheduled pair
```

`RTC_WAKEUP` + `allowWhileIdle` means each scheduled pair takes the device **out of Doze** for its
delivery, and each delivery then re-lays every alarm (reads `scheduledPairs`, writes a
`SharedPreferences` string set). With N schedules that is N Doze exits per period.

Also note: `rescheduleAll` is triggered from a collector on `settingsRepository.scheduledPairs`
(`LauncherApplication.kt:115–119`). If that flow is not `distinctUntilChanged`, **any** settings write
re-lays all alarms.

**Recommendation:**
1. Collapse to **one** `setExactAndAllowWhileIdle` for the *soonest* pair; on fire, re-lay the next.
   The receiver already calls `rescheduleAll()` after a fire, so this is mostly deleting the loop.
2. Use the ±10 min `setWindow` path by default (already the fallback) and reserve exact alarms for a
   user-facing "on time" toggle. A scheduled *scene* is not an alarm clock; late delivery is acceptable.
3. Verify `scheduledPairs` is `.distinctUntilChanged()` before it reaches `rescheduleAll`.

**Status: applied.** `rescheduleAll()` now arms one alarm for the soonest pair; `fire()` lays the next
occurrence first (`after = firedAt`, strictly future, so it cannot re-arm itself), then launches or
notifies every pair due at that moment — the named one plus any sharing the minute, so consolidating
did not introduce the same-instant skip. Per-pair pending intents from earlier versions are cancelled
on the first reschedule, so an upgrade leaves none behind.

---

### F7 — One DataStore write per app launch **[Medium, cumulative]**

`core/data/.../AppUsageRepository.kt:44–63` — `recordLaunches` calls `dataStore.edit { … }`, which
re-serializes the whole `key\tscore\tlastUsed` map and commits it. The same `DataStore` also carries
every preference, so each launch invalidates the store all four eager collectors in
`LauncherApplication` are reading.

That is a flash write plus a full deserialize fan-out **per icon tap** — small individually, but a
launcher is the most-launched-from app on the device, and `BackupRepository` explicitly excludes
`app_usage` from backups because it "changed the Drive dedup hash on every launch", which confirms the
write volume.

**Recommendation:** accumulate launches in memory and flush on a debounce (e.g. 30 s, or on
`LauncherActivity.onStop()`), the way `NotificationDotListenerService` already persists `heldUntil` in
one place rather than per event. Frecency ranking tolerates a 30 s lag perfectly.

---

### F8 — `MinuteTicker` registers one receiver per clock [Low-Medium]

`feature/home/.../MinuteTicker.kt:24–39` — every call site (`StatusBar.rememberClock`,
`SmartspaceWidget`, `NothingClockWidget`, …) creates its own `BroadcastReceiver` for
`ACTION_TIME_TICK`. With the clock widget on both displays plus the status bar, that is 3–4 receivers
firing 1×/minute, each waking the main looper separately.

**Recommendation:** promote it to a shared singleton clock (the pattern `SecondTicker` already uses) so
there is exactly one `ACTION_TIME_TICK` receiver for the whole app.

---

### F9 — `LivePill` effects: 60 s of frame work per appearance [Low]

`core/ui/.../LivePill.kt:147–158` — `withFrameNanos` + `delay(16)` for `STILL_AFTER_MS = 60_000`, per
pill. Correctly rests afterwards and correctly shares `SecondTicker` for the text. With a timer and a
navigation pill visible, that is two 60 fps producers for a minute after every home visit. Acceptable;
worth noting that 24 fps (`delay(24)`) is still smooth for a glow and saves ~40 % of frames.

### F10 — `IconRing` busy spin: every frame for 30 s per busy icon [Low]

`core/ui/.../IconRing.kt:80–89, 128` — `SPIN_AWAKE_NS = 30 s`, `SPIN_PERIOD_MS = 1400`. The angle is
written to a `mutableFloatStateOf` read inside `drawWithContent`, so only the ring redraws (no
recomposition) — good. But N concurrent downloads = N spinning rings at display refresh rate. Cap the
number of simultaneously spinning rings, or drive them from a single shared 30 fps ticker.

### F11 — `LauncherActivity` refresh-rate policy is correct; verify the idle floor [Positive / verify]

`LauncherActivity.kt:391–470` implements exactly the right behaviour: no preference at all on
adaptive-refresh (LTPO) displays, otherwise top rate while touched, dropping to the slowest mode ≥ 60 Hz
3 s after `ACTION_UP`, gated by battery saver and by Android's "Smooth display" cap.

One thing to check on device: `IDLE_REFRESH_RATE = 60f` is a *floor*, so an untouched home screen holds
60 Hz rather than letting a non-LTPO panel drop to 30/48 Hz. If the panel has a 30 Hz mode, offering it
as the idle mode would roughly halve display power on a still home screen. Measure before changing —
Compose's ripple/scroll feel may regress on 30 Hz panels.

### F12 — No baseline profile is shipped [Low, startup energy]

`app/build.gradle.kts:117` depends on `androidx.profileinstaller`, `isMinifyEnabled = true` in all
builds, and `compose-stability.conf` feeds the compiler — but `**/baseline-prof*` matches **nothing**.
The installer therefore has nothing to install. Cold start of a launcher happens constantly (HOME press
after every task), so a compiled baseline profile is a direct, user-visible reduction in CPU time per
home press.

**Recommendation:** add a `:baselineprofile` module with a `StartupProfile` for
`LauncherActivity`/`LauncherShell` and generate `app/src/main/baseline-prof.txt`. This is the only
finding here that speeds up an event that happens dozens of times a day.

---

## 4. What is already good — do not regress

These are deliberate, correct decisions found in the code. They are listed so future changes don't
undo them:

- **Battery saver is a real, threaded state**, not a token: `LocalBatterySaver` gates the video
  background, parallax, hinge tilt, blur radius, weather animation and the refresh-rate ceiling
  (`PowerSaving.kt`, `LauncherShell.kt:228–231`, `HomeScreen.kt:554`). It can follow the system saver,
  low battery, or thermal status ≥ `MODERATE` (`rememberDeviceHot`).
- **Adaptive refresh rate is respected** — the launcher asks for *nothing* on LTPO panels instead of
  pinning 120 Hz (`LauncherActivity.hasAdaptiveRefreshRate`).
- **Location never wakes a provider**: `getLastKnownLocation` only, rounded to 2 decimals before it
  leaves the device, geocoder results cached 6 h/24 h (`WeatherRepository.kt:203–230`).
- **Network is rare and backoff-aware**: 30 min interval (doubled in battery saver), exponential retry
  from 60 s capped at the interval, single-flight via `AtomicBoolean` (`WeatherRepository.refresh`).
- **Shared clocks**: `SecondTicker` (one 1 Hz source for every pill/card), `BatteryMonitor.shareIn`
  (one sticky receiver for every battery widget), `HingeSensor` (one sensor listener for live hinge +
  tilt + wallpaper follow, at the fastest rate any client wants).
- **Sensor work has a stationary pause and a dead band** (`ParallaxSensorManager`) and a batching rate
  (`66_000 µs`).
- **Notification work has coalescing, a quiet path for ongoing updates, retry caps, and full teardown
  on disconnect.**
- **Recents releases bitmap memory on `onTrimMemory`/`onLowMemory` and throttles the root memory read**
  (`MEMORY_KEEP_MS`) — memory pressure is a secondary energy cost.
- **No wake locks, no foreground services, no periodic `WorkManager` jobs, one manifest receiver.**

---

## 5. Ranked opportunity list

| Rank | Change | Files | Effort | Expected effect |
|---|---|---|---|---|
| 1 | Stop taskbar polling when the accessibility service reports foreground apps | `TaskbarHost.kt`, `TaskbarWatch.kt` | S | removes ~20–60 binder queries / 10 min **[est]** |
| 2 | Bound the video wallpaper (play N s, then hold last frame) | `VideoBackground.kt`, `LauncherShell.kt` | S | removes unbounded decoder power |
| 3 | Replace weather-sky 1 Hz idle poll with a pushed visibility signal | `WeatherDayWidget.kt` | S | removes 1–2 wake-ups/s **forever** |
| 4 | Debounce `AppUsageRepository` writes (flush on stop) | `AppUsageRepository.kt`, call sites | S | removes 1 flash write per launch |
| 5 | Hard floor (250 ms) on full notification rebuilds | `NotificationDotListenerService.kt` | S | caps burst cost at 4/s instead of 25/s |
| 6 | One alarm for the soonest pair; `setWindow` by default | `PairScheduler.kt` | M | N−1 fewer Doze exits per period |
| 7 | Gate the home status bar on home visibility | `StatusBar.kt`, `HomeScreen`/`LauncherShell` | M | stops telephony/network callbacks behind apps |
| 8 | Share one `ACTION_TIME_TICK` receiver | `MinuteTicker.kt` | S | 3–4 receivers → 1 |
| 9 | Ship a baseline profile | new `:baselineprofile`, `app/` | M | faster (lower-energy) cold start per HOME press |
| 10 | 24 fps for precip sky / live pills; cap spinning rings | `WeatherDayWidget.kt`, `LivePill.kt`, `IconRing.kt` | S | ~20–40 % fewer frames in those windows |

---

## 6. How to verify (turn estimates into numbers)

Run these on the target device with the launcher as default home. Compare before/after by changing one
thing at a time.

```bash
# 1. Which components actually hold the CPU (partial wake lock count should be 0)
adb shell dumpsys power | grep -iE "wake ?lock|mWakefulness"
adb shell dumpsys batterystats --charged org.arkikeskus.launcher | sed -n '/Per-PID/,/^$/p'

# 2. Alarms and their wakeup type
adb shell dumpsys alarm | grep -A4 org.arkikeskus.launcher

# 3. Job/sensor/notification-listener registry
adb shell dumpsys sensorservice | grep -i hinge
adb shell cmd notification get_notification_channels org.arkikeskus.launcher

# 4. Binder pressure from the taskbar watch (look at UsageStatsManager calls / sec)
adb shell dumpsys activity service usagestats | head -40

# 5. Jank + frames while the weather sky animates (before/after frame-rate change)
adb shell dumpsys gfxinfo org.arkikeskus.launcher framestats

# 6. The honest end-to-end test: 1 h idle-on-home, screen on, same brightness
adb shell dumpsys batterystats --reset
# ... leave the phone on home for 1 h ...
adb shell dumpsys batterystats | grep -iE "estimated power|org.arkikeskus.launcher"
```

A `macrobenchmark` module measuring `StartupTimingMetric` on the launcher would make item 9 in §5
falsifiable rather than aspirational.

---

## 7. Assumptions and caveats

- All rates and windows are quoted from source constants as of this commit; behaviour on a specific ROM
  (Motorola's battery manager, aggressive OEM killers) may differ, especially for the
  `NotificationListenerService` rebinding and the accessibility service.
- The taskbar and accessibility paths are **mutually exclusive in practice on some devices** — if the
  user never grants "Display over other apps" or the accessibility service, F2's polling is the only
  source and cannot simply be deleted; it can only be demoted to a fallback.
- Severity for F1 depends entirely on whether the user chose a video wallpaper; the default is a still
  image, so the expected impact across the install base is far lower than the code's worst case.
- Nothing here was profiled. Two findings (F11's 30 Hz idle mode, F4's real rebuild cost with ~50 active
  notifications) are explicitly marked "measure first".

---

## 8. What was applied in this change

Three findings were fixed, all chosen because a user cannot detect the difference:

| Finding | Change | Files |
|---|---|---|
| F2 | Watch pace is a policy input (`heard`), not a start/stop decision; safety-net pace while the accessibility service reports switches; `HIDDEN_MS` 10 s → 30 s; stale-dots hole closed | `TaskbarWatch.kt`, `TaskbarHost.kt`, `LockAccessibilityService.kt`, `TaskbarWatchTest.kt` |
| F4 | 250 ms floor between full notification snapshot rebuilds, deferring rather than dropping | `NotificationDotListenerService.kt` |
| F6 | One alarm for the soonest pair, re-laid on each fire; legacy per-pair alarms cancelled; same-minute pairs still both come up | `PairScheduler.kt` |

No settings, strings, UI or behaviour visible to the user changed.

**Verification actually run**

- `./gradlew :feature:home:compileDebugKotlin :feature:home:testDebugUnitTest` — BUILD SUCCESSFUL,
  including `TaskbarWatchTest` (extended with the `heard` cases and the new `HIDDEN_MS` backoff).
- `./gradlew :app:compileDebugKotlin -x :feature:appdrawer:compileDebugKotlin` — BUILD SUCCESSFUL
  (only pre-existing deprecation warnings).
- The full `:app` build is **blocked by an unrelated, pre-existing compile error** in the user's own
  uncommitted app-drawer-folder work (`AppDrawerViewModel.createDrawerFolder` calls an `update { }`
  helper that does not exist, and calls a `suspend` function from a non-suspend lambda). That block is
  skipped, not fixed, for this change.

**Not done, deliberately**

- **F1 (video wallpaper)** — no feature-neutral fix exists; it needs an opt-in awake window.
- **F3 (weather sky idle poll)** — a neutral fix needs a page-visibility signal plumbed to the widget.
- F5, F7, F8, F9, F10, F12 remain open as written above.
- No `batterystats`/FrameTimeline measurement was taken, so the expected savings remain estimates.
