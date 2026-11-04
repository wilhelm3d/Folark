# Changelog

## Unreleased

### Split screen
- Three-app split: the module finds System UI's startTask / startIntent for the third pane by their leading parameters (the split index in the first number after them) and reports why it failed; the divider stops leave three-app layouts alone.
- The split drawer's Size row shows whenever Folark's module runs, labelled 30/70, 50/50, 70/30; picking a size turns peek off.
- The divider stops at 30 %, 50 % and 70 % (Settings ▸ Recents & split screen ▸ Divider stops; Folark's Vector module adds the stops in System UI). Layout choices, saved pairs and the taskbar's split menu use 30 / 70 in place of a third / two thirds.
- Works on the Razr's Android 17 build again: the module finds System UI's split calls by their leading parameters and passes a real InstanceId; the split drawer docks beside the app with `startTasks` (System UI's `startIntentAndTask` throws there) after waiting for its task to reach WM Shell; the Native engine falls back to the commands when System UI turns a request down. Check split screen shows the reason for a failure.

### Taskbar
- The bar scrolls sideways when it holds more than fits (never from a pinned icon, nor on a drag up to split).
- Media controls show a progress bar, worked out from the player's last state (no polling); a drag seeks.
- Over a full-screen video the mini pill steps aside too, and the pull handles shrink to a thin strip so the player's corner buttons stay tappable.
- The launch glow reuses one window and caches its colour; the app watcher backs off while nothing changes and restarts properly after the screen was off.

### Home, drawer and look
- Opening or closing a folder in the app drawer never brings the keyboard up.
- Unfolding the phone opens the home pages out from the hinge (Settings ▸ Appearance ▸ Unfold animation).
- Per-app icon shapes (app menu ▸ Change icon ▸ Shape).
- A separate heading font, and six more fonts every phone has (Condensed, Light, Medium, Casual, Small caps, Cursive).
- Forgiving app search in the drawer and the split picker: initials ("gm"), letters in order ("gml"), one typo ("spotfy"), accents ignored; best match first.

### Setup and backup
- The first-run intro has a Split screen & taskbar page with live checks (overlay, usage access, the ADB grant with its command to copy, the Vector module).
- Backups also keep the apps the taskbar hides in, the split pairs used most and the system-control choices; **Share setup** sends the whole setup to another phone.

### Code
- Workspace.kt (1,801 → 1,118 lines) and AppTaskbar.kt (1,662 → 865 lines) are split into smaller files; the home grid's cells are separate composables that skip recomposition while unchanged.
- Split dumps are reused within a gesture (750 ms) instead of being read again.

## 2.1.0 (versionCode 210)

### Settings
- New **Start** page: whether Folark is your home app and has notification access (one tap to fix), quick switches (taskbar, Folark's Recents, Liquid Glass everywhere, notifications in long-press menus, app-open animation) and what's new, each line opening its page.
- Pages are listed in four groups: Look & feel, Home screen, Apps & multitasking, Phone & system. Shorter, consistent page descriptions; "Recents" is now "Recents & split screen".
- Taskbar, Recents and App drawer pages are split into separate cards (for example *Apps on the bar*, *Look & motion*, *Pull handles*, *Split screen from the bar*, *Placement*); rarely changed options sit under a folding **Advanced** row. A setting found with the search opens its Advanced section by itself.
- App drawer search options have their own *Search* card; the A–Z fine scroll sits with the A–Z bar; two-pane with the tabs.
- Appearance page: sub-headings for icons, weather & status bar, widgets and home pages.
- The search index is generated again (all new settings are findable; the Start page's shortcuts are left out so results point to the real pages).

### Taskbar
- Recent apps and running dots follow app switches while the bar is open over an app.
- Pull handles and the mini pill stay above the gesture strip in overlay mode; side handles are excluded from the back gesture.
- The app watcher pauses while the screen is off and checks at once on screen-on and when the bar opens.
- The hide button over an app is remembered for that app ("Keep taskbar open in this app again" undoes it).
- Pin / unpin from the long-press menu; drag a pinned icon along the bar to reorder.
- Drop an app from the drawer, the home grid or a Recents card onto the bar to pin it.
- An open split screen shows as one tile; a tap brings the split back.
- TalkBack actions on icons (split, floating window, shortcuts, pin, move left/right); haptics on every drop zone.
- Launch glow in the app's colour (or accent, white, off).
- Themes AMOLED and Neon; show animations Flip, Unfold and Pop.
- One accent colour helper for every highlight on the bar.

### Recents
- Older split pairs shown as one double card (switchable).
- The app you just left gets a fresh picture.
- Memory use per card with root (off by default).
- Drag a card onto the taskbar to pin its app.
- The tapped card grows into the app.
- Entrances Wave, Swing and Drop.

### App drawer
- Two-pane drawer when unfolded (off by default).
- Shortcut chips under the top search result.
- 27 more Android settings pages in search; pages the phone lacks are left out.
- A–Z bar: hold a letter, then slide to scroll smoothly.

### Split screen
- With Folark's Vector module the picker opens in the half beside the first app instead of full screen.
- Size row: ⅓, ½ or ⅔ for the picked app.
- Header below the status bar; last row clear of the taskbar; the picked icon glows; the picker uses Folark's accent colour.

### Animations
- App-open animations Pop and Unfold.

### Icons
- Five more icon shapes: Square, Arch, Hexagon, Diamond and Cookie.
- Themed icons in each app's own colour: **Own colour** (a bold plate of the app's colour, the glyph almost white) and **Soft colour** (a pale or deep plate tinted with it, the glyph in the colour). Apps without a themed icon get one made in their colour too ("Theme all icons").
- **Shape every icon** (on by default with a shape chosen): older apps' plain icons sit on a plate in a pale tone of their own colour, so they take the shape like the rest.
- The taskbar's launch glow reads the app's colour the same way as the themed icons.

### Haptics
- Settings ▸ Appearance ▸ Haptics: one switch, Light / Medium / Strong, and each moment on its own: opening apps (a crisp click), split screen (a rising double tap), closing apps (a soft thud, firmer for Close all), opening Recents (a gentle swell) and dragging (ticks on pick-up, drop zones and landing).
- Built from the vibration motor's own haptic primitives where supported, Android's standard effects otherwise; follows the phone's touch-feedback setting. A split plays only its own feel, not a launch click as well.

### Fixes and clean-up
- Android 16/17 API updates (background activity starts, widget sizes, VPN detection).
- Zero compiler warnings; deliberate uses of deprecated platform APIs are marked and explained.
- Removed a setting that did nothing (Recents "show actions") and 13 unused texts; one usage-access check instead of two.
