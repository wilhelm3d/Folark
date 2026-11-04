# Changelog

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

### Fixes and clean-up
- Android 16/17 API updates (background activity starts, widget sizes, VPN detection).
- Zero compiler warnings; deliberate uses of deprecated platform APIs are marked and explained.
- Removed a setting that did nothing (Recents "show actions") and 13 unused texts; one usage-access check instead of two.
