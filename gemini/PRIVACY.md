# ARK-launcher Privacy Policy

*Last updated: 2026-11-02 · applies to ARK-launcher v0.8.0 and later
([v0.7.11 policy](https://github.com/jrs8205/ARK-launcher/blob/v0.7.11/PRIVACY.md))*

*Tämä seloste suomeksi: [PRIVACY.fi.md](PRIVACY.fi.md)*

ARK-launcher is a free, open-source Android home-screen launcher (Apache 2.0).
This policy describes exactly what data the app handles. The source code is
public, so every claim here can be verified:
<https://github.com/jrs8205/ARK-launcher>.

## The short version

- Your home-screen layout, settings, app list and notification data **stay on
  your device**. There is no telemetry, no analytics, no ads and no tracking of
  any kind.
- The network is used for exactly one purpose: showing the weather in the
  clock widget (including resolving the municipality name). Nothing else in
  the app talks to the internet.
- The app has no user accounts and never sends usage data, analytics or
  advertising identifiers to the developer or to any third party.
- Since v0.7.12 the app contains no self-updater and no Google Drive backup:
  updates come from the app store of your choice (or GitHub), and backups are
  local files you export yourself.

## Data stored on your device

- **Home-screen layout** — icon, folder and widget placements; stored in a
  local database.
- **Settings and preferences** — grid size, gestures, icon pack choice, hidden
  apps, custom app labels, most-used ordering and similar; stored locally.
- **Split screen pairs** — the last few pairs of apps you put side by side
  (package names only), for the split picker's "Paired before" row, and the
  apps you mark as favorites there. For each of those pairs Folark also keeps
  the times it was last made (up to six) and how often its "Open with …"
  suggestion went by untapped, so that suggestion (Settings ▸ Recents ▸ Split
  screen, off by default) offers only pairs you made often lately. Stored
  locally, not in backups (the favorites are part of the settings); Settings ▸
  Recents ▸ Split picker can switch the row off and forget the pairs.
- **Scenes** — the scenes (app pairs) you save, with their layout, where a
  third app goes and what they do on the cover screen; part of the settings.
- **Notification data** — if you grant notification access, notifications are
  read to show dots/badges, status-bar icons and the optional notification and
  people widgets, including their open, reply and dismiss actions. The same
  data shows an app's latest notifications in its long-press menu, the
  sender's photo on a chat app's dot, a short nudge of an app's icon when a
  notification arrives, and ongoing activities (timers,
  navigation, calls, downloads) as a live pill on home and on the taskbar. Opened up
  with a long press, the pill also shows the notification's longer text and its own
  buttons (pause, stop...); pressing one sends that button's action to its app, as
  the notification shade would. Each can be switched off in Settings ▸ Notifications,
  and none of it is stored. Message text
  is processed in memory and never stored persistently or transmitted by the
  launcher; replies are sent through the originating app. People you pin and
  identity links you create are saved in settings, including any matched contact
  name, number, photo reference and contact URI, and included in local backups.
  Optional batch delivery delays only launcher surfaces; Android notifications,
  sounds and pop-ups remain active. Notification keys and delivery times are
  saved locally to retain waiting state after a restart and excluded from backups.
- **Clipboard history** — only while the clipboard history widget is on your
  home screen or Settings ▸ App drawer ▸ Clipboard row is on, Folark keeps the
  last 30 things you copied (texts, and pictures as a small copy), to show them
  and copy, open, call, search or share them again; Folark only works out on the
  phone whether a clip is a link, number or email. Android lets only the app in front read the clipboard, so Folark
  looks when the home screen comes into view (Android may show its "pasted from
  your clipboard" notice then). Texts Android marks as sensitive, such as
  passwords and one-time codes, are never kept. The list is stored in a private
  file (pictures in a private folder) on the device, is not part of backups and
  is never transmitted unless you tap Share or Open; "Clear
  all" in the widget, removing a clip, or uninstalling deletes it.
- **Backups** — Settings ▸ Backup & restore writes a backup file to the
  location you choose, and, if you turn on automatic backup, keeps one
  up-to-date copy in a folder you pick, refreshed at most once a day. A backup
  contains the home-screen layout of both displays, all settings (including
  pinned people and the most-used ordering) and your home background pictures —
  nothing else. It never contains notification text or the waiting state of
  held notifications. The app itself never uploads a backup anywhere; if you
  pick a cloud folder in the system file picker, that app stores the file. A
  backup is restored only when you choose it, in settings or in the first-run
  intro after a reinstall. The app opts out of Android's system backup
  (`allowBackup="false"`), so its data is never copied into your Google
  account's device backup or carried over by a device-to-device transfer; the
  files you save are the only backups.

## Network use

1. **Weather** (only while the weather row is enabled or a weather widget is
   on the home screen, and either location permission is granted or you have
   typed in a weather location): the app requests the current conditions and
   the forecast from the keyless [Open-Meteo](https://open-meteo.com) API. Your
   coordinates are rounded to about one-kilometre precision before the request;
   the exact position never leaves the device. If you type a town into
   Settings ▸ Weather location, that text and the device's interface language
   are sent once to Open-Meteo's place search to find its coordinates, and the
   device's own location is then not used for weather at all.
2. **Municipality name** shown next to the weather: the name is resolved with
   the device's own geocoder first. If the device has no working geocoder
   backend (some models don't), the app falls back to the keyless
   [BigDataCloud](https://www.bigdatacloud.com) reverse-geocoding API, sending
   the same ~1 km rounded coordinates plus the device's interface language —
   at most once per 30-minute weather refresh, and only when the local
   geocoder failed.

That is the complete list. The "Check for updates" row in Settings only opens
the GitHub releases page in your browser — the app itself performs no update
checks or downloads.

Neither request carries account information, cookies or advertising
identifiers. Like all internet traffic they necessarily reveal your IP address
to the server; the requests identify themselves with a generic
`ARK-launcher/<version>` User-Agent that contains no device details.

## Permissions

| Permission | Used for |
|---|---|
| `QUERY_ALL_PACKAGES` | Listing and launching your installed apps — the launcher's core purpose |
| `ACCESS_HIDDEN_PROFILES` | Android 15+: showing your Private Space in the app drawer as a separate, locked section. While it is locked Folark lists none of its apps; unlocked, its apps appear only in that section (never on home, in search, in widgets or in the usage counts). Locking and unlocking go through Android, which asks for your screen lock. Nothing about it is stored or transmitted |
| Notification access (`BIND_NOTIFICATION_LISTENER_SERVICE`) | Notification dots/badges (optionally with the sender's photo), status-bar icons, notification and people widgets, notifications in the long-press menu, the live activity pill, and local batch delivery |
| Accessibility service (`BIND_ACCESSIBILITY_SERVICE`, opt-in) | The double-tap-to-lock gesture and, if you turn it on in settings, Folark's taskbar on the unfolded screen (the service draws the taskbar's corner touch zones and app icons in overlay windows, starts the app you pick, and asks Android to split the screen when you drag an icon for split screen). When you drop one app on another in Folark Recents for attached view, the service makes one touch on the split screen's divider to move it, as your finger would. Only if you set per-app rules in Settings ▸ Taskbar (hide or always show it in chosen apps), or turn on Settings ▸ Recents ▸ Replace Android's Recents, the service is told which app or screen comes to the front (its package and screen name, nothing else), used at once and not stored. With Replace Android's Recents, when Android's Recents screen opens, the service goes home and opens Folark Recents instead. If you install Vector (LSPosed) and enable Folark's module for the system launcher there, the launcher opens Folark Recents itself instead; it only asks Folark whether that switch is on (a yes/no). If you also enable the module for System UI, it tells Folark the moment the lock screen starts to leave and which app it is uncovering (that app's package name), so Folark's unlock-to-home animation starts in step (Settings ▸ each display ▸ Also when unlocking ▸ Sync with the lock screen). This is used at once and not stored; Folark keeps only the time of the last report, to show in Settings that the module is active. In System UI the module never changes what the lock screen does. Besides that report, it only changes a split screen when you ask Folark to: putting two open apps side by side (dragging an app to a screen edge and picking the second one, with Settings ▸ Recents ▸ Split screen ▸ Split engine on Automatic or Native), for which Folark sends System UI the two apps' task numbers and which side each goes on; adding a third app (Settings ▸ Recents ▸ Real three-app split, saved app pairs with a third app), for which Folark sends System UI that app's task number or a link that opens the app; switching the split to Android's peek view, back to half and half, or to one app taking two thirds or a third (Recents, the taskbar's split menu, scenes), for which Folark may send the task number of the app the size is meant for; opening a notification beside the app you used last (a long press in Folark's notifications widget, Split on the taskbar's live card; Settings ▸ Recents ▸ Split screen), for which Folark sends System UI that notification's own tap action and the other app's task number; or telling Folark where the divider is when you save a split as a scene in Recents. System UI answers yes or no (and, for the divider, which stop it is at). With Settings ▸ Appearance ▸ Status bar margins on (off by default), the module also asks Folark how much room to keep at the ends of Android's status bar (the switch and two numbers) and widens the status bar's side margins to that; nothing else is read or changed there. System UI accepts such a request only when Android confirms it came from Folark. While the module runs in System UI it also tells Folark when the split screen changes: which open apps (by task number) entered or left a split, and whether the split shows. Folark uses this only to know when a split it asked for has formed, keeps it in memory only, and never stores or sends it anywhere. Folark keeps the time System UI last said it is ready, and the time and kind of its last split request with System UI's last yes or no: if System UI restarts right after such a request, Folark pauses the module's split requests and uses Android's split commands instead (Settings ▸ Recents ▸ Split screen shows this and can turn it back on; Check split screen shows what each part answered). Folark Recents can be opened only by apps allowed to start apps from Recents (the system launcher). Otherwise the service receives no accessibility events and cannot read screen content, and only runs if you enable it yourself |
| `DUMP` (opt-in, only via a one-time `adb shell pm grant` you run yourself) | The taskbar's split screen: Folark reads Android's list of recent tasks to find the task number of the app it just opened, and sends Android's split-screen commands; the split screen button also reads which apps are in the split screen, to offer swapping sides or one app full screen. Read on the device for that moment only, never stored or transmitted. With it Folark's own Recents (Settings ▸ Recents) also reads which apps are open, and the taskbar can show them; Close all uses Android's own command |
| Root (opt-in, only if your phone is rooted and you allow Folark in your root manager) | Folark Recents: Android's saved screenshots of the open apps (kept in memory while Recents is open, never written or sent anywhere), and closing a single app. The split picker's recent apps row can show the same screenshots for the few apps it lists (Settings ▸ Recents ▸ Split picker ▸ Recent apps row ▸ Previews, off by default; kept in memory while the picker is open). Without the DUMP grant, root can also send Android's split-screen commands and read which apps are open for them, exactly as the DUMP grant does (Settings ▸ Recents ▸ Split screen ▸ Split engine; Root uses only that way). Pinned apps (Settings ▸ Recents) can be kept in memory with root: Folark exempts them from battery optimisation, puts them in Android's "active" group and raises their priority, and takes the exemption back when you unpin. Each switch is in Settings ▸ Recents; without root those two parts are simply missing. Settings ▸ Root can also, each only if you turn it on: lock the screen, open Android's Recents and pull down the notification shade with root; change Android's hidden status bar icons with root when the WRITE_SECURE_SETTINGS grant isn't there; and give Folark, with root instead of ADB or Android's settings pages, the permissions listed here (DUMP, usage access, WRITE_SECURE_SETTINGS, notification access, its accessibility service, adding widgets) or make it the default home app. On first start, the Permissions page offers "Use root": only when you tap it (and your root manager allows it), Folark gives itself all of these at once, plus the permissions that page asks for (calendar, contacts, phone state, location, nearby devices), and becomes the default home app; each can be taken back in Android's settings. Settings ▸ Recents ▸ Split screen can also, when you tap them, restart System UI with root (after installing or updating Folark's Vector module) and show the module's own log lines, read with root from Android's log (or Vector's log files) and filtered to lines starting with "Folark:"; they are shown on the phone only and leave it only if you tap Copy and paste them somewhere. Root only ever runs fixed commands with Folark's own names; "Use root" off and Folark never asks for root |
| Usage access (`PACKAGE_USAGE_STATS`, opt-in) | The taskbar's recent apps: which apps were brought to the front in the last hours. Read on the device when the taskbar is shown, never stored or transmitted. Only if you allow it in Android's settings |
| `WRITE_SECURE_SETTINGS` (opt-in, only via a one-time `adb shell pm grant` you run yourself) | Settings ▸ Permissions ▸ System controls: turning Folark's own accessibility service back on if Android switched it off, Android's top refresh rate, Android's animation speed, and the list of status bar icons Android hides (Settings ▸ Appearance ▸ Android's status bar icons; only the icons offered there are ever changed). Folark changes only these settings, and only when you choose to; nothing is read from them beyond their current values, and nothing is transmitted |
| `READ_CALENDAR` | Showing your next event in the clock widget |
| `READ_CONTACTS` | Contact results in app search and matching people-widget contacts (opt-in) |
| `READ_PHONE_STATE` | Signal-strength indicator in the status bar |
| `SET_ALARM` | Search actions: "timer 10 min" or "alarm 7:30" hands your clock app the timer or alarm, which it shows you to confirm. Folark reads no alarms. Search actions (timers, alarms, events, directions, email, notes, phone numbers) only open the matching app with the typed text filled in; nothing typed is stored or transmitted by Folark |
| `ACCESS_COARSE_LOCATION` | Weather in the clock widget |
| `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `BLUETOOTH` (≤ Android 11) | Connectivity indicators in the status bar |
| `INTERNET` | The weather requests described above |
| `REQUEST_DELETE_PACKAGES` | The "uninstall" action in an icon's long-press menu |
| `EXPAND_STATUS_BAR` | The swipe-down-for-notifications gesture |

Calendar, contacts and phone-state data are read on demand for the features
above, used on the device only, and never stored beyond in-memory caches or
transmitted anywhere. Location is handled the same way on the device; the only
location data that ever leaves it is the ~1 km rounded coordinates sent to the
weather and reverse-geocoding services described under *Network use*.

## Children

ARK-launcher is a general-purpose utility with no content of its own, no ads
and no purchases. It is not directed at children, and it does not knowingly
collect personal data from children — or from anyone else: the app has no
accounts and collects no personal data at all.

## Changes and contact

Changes to this policy are made in the public repository, where the full
history is visible. Questions and reports:
<https://github.com/jrs8205/ARK-launcher/issues>.
