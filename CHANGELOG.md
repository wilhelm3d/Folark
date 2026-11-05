# Changelog

## Unreleased

### Settings
- Fixed: after System UI crashed once, Folark's own split stayed paused until the phone restarted, which hid the size buttons in the split drawer and turned the three-app split into a floating window. The pause now ends the next time System UI starts normally, and dropping a third app while it's paused says why.
- Settings sorted anew, one topic per page: Look & feel (Theme & colours, Icons & badges, Motion & haptics), Home screen (Home screen, Dock & folders, Gestures), Apps & multitasking (App drawer, Taskbar, Recents, Split screen & peek) and Phone & system (Displays & fold, System, Permissions, Root, Backup). Everything about split screen and peek is now on one page, wherever the split starts from.
- Each page has one live preview pinned on top that stays while the settings scroll under it. A change brings the view it touches to the front (the menu, the glass, the backdrop, the dots…) and the frame lights up; slider values show while dragging. Tap the chips to switch views, or the title to fold the preview away.

### Split screen
- Efficiency: the animated previews in Settings (taskbar, split drawer, icon nudge) pause while scrolled out of sight; Folark's code is compiled ahead of time at install (baseline profile), for a faster first start of home, the drawer and Recents; Compose may skip redrawing parts whose settings and apps didn't change (stability config).
- Split screen (Settings ▸ Recents & split screen): faint marks at ⅓, ½ and ⅔ while dragging the divider, and a let-go near one lands on it; a haptic tick at each mark; sizes kept when the phone turns or folds; each set of apps reopens at the sizes it had last time; the divider can be locked (2 apps, 3 apps or always; also from the taskbar's split menu); while text or a picture is dragged out of an app in split screen the panes light up; peek view's divider flung towards the strip closes that app (off by default).
- Three apps: 50 / 25 / 25, 25 / 50 / 25 and 25 / 25 / 50 in the taskbar's split menu.
- Scenes keep their sizes: a split added to home or the taskbar from Recents opens at the sizes it had (free sizes, three apps, peek), and peek scenes show a peek mark on home.
- Recents: with three apps picked for peek view, tap the one to show big.
- Taskbar: long-press a pinned scene to open it another way this once: peek view, half and half, first app ⅔ or ⅓; a three-app scene as a third each or as peek view (middle app big).
- Taskbar: the mark between pinned and recent apps can be a line, a bold line, a dot, a gap or nothing (Settings ▸ Taskbar).
- Efficiency (taskbar): icons are kept per dark / light theme (switching back finds them drawn), and pinned icons load together in the background.
- Efficiency (home): a widget being placed reads its name and icon off the main thread; live pills and cards share one clock; the weather animation looks less often whether it is scrolled back in.
- Recents opens faster: the task list and the split are read at once, all saved screenshots come in one root call and show together, apps are looked up by index, the memory badge is read again only after 30 s, and the root helper isn't started again for apps it had no picture for a moment ago. Cards recompose only when what they show changes.
- Recents: split cards show the split as it really is (a third, peek, three apps side by side as placed), when Android reports where the apps sit.
- Recents: a split card dropped on the taskbar is pinned there as one scene (single apps as before).
- Recents: optional haptic tick for each card scrolled past (Settings ▸ Recents ▸ Motion ▸ Tick while scrolling).
- Efficiency (data): deleting a scene removes its home shortcuts with one query; each app's own colour (themed icons) is kept across restarts and read again only after an update; text patterns in search and root parsing are built once.
- Efficiency (System UI module): reflection is looked up once and kept (it ran on every frame of a divider drag); split events go to Folark in batches; System UI's questions to Folark wait 0.4 s at most.
- Efficiency (split screen): a third app started from its intent is found from System UI's split events instead of re-reading the window list; window-list reads without events are fewer; the window-list parser only reads the lines that matter.
- Efficiency (settings): the settings are built once per change, not once per reader.
- Three-app split: a third pane System UI says yes to but doesn't show no longer counts; the third app then floats over the split.
- Divider sizes: the module checks that the divider stayed at 30 / 70 % after a snap; where System UI moves it straight back (50/50 only), the split drawer and the taskbar's split menu stop offering the sizes until System UI restarts.
- The crash guard no longer pauses Folark's module after a reboot (System UI starting right after the phone booted is a restart, not a crash), and a pause ends when the phone restarts. A false pause hid the split drawer's size row and blocked three-app split.
- Three-app split: the module finds System UI's startTask / startIntent for the third pane by their leading parameters (the split index in the first number after them) and reports why it failed; the divider stops leave three-app layouts alone.
- The split drawer's Size row shows whenever Folark's module runs, labelled 30/70, 50/50, 70/30; picking a size turns peek off.
- Switching apps in peek view (tapping the app peeking in) no longer crashes System UI: Motorola's StageCoordinator.setSideStagePosition picks a stage by each split listener's place in its list, so one listener more than apps (Folark's) threw IndexOutOfBounds. Folark's listener steps out while it runs.
- Recents: split cards take a dropped app as the third whenever Folark's Vector module runs (not only with the three-app split setting on).
- Split drawer: Peek is a symbol like the size buttons.
- Peek view no longer crashes System UI: while Motorola's own divider animation runs (to peek, half and half, closing, its three-app layouts) Folark's sizing keeps out of it; switching layouts under it mid-way wiped data Motorola reads every frame.
- Three apps: moving a divider resizes the apps too (they kept their old widths and were cut off): the all-on-screen layout is laid out at the dividers.
- Recents: an app dropped on a split card joins it as the third app (Recents went home and the app floated): the split is made again and the third added once it has settled, as the split drawer does.
- Split check: with root, "10. Last System UI crash" shows what System UI died of last.
- The split divider stays where you let go, for two apps and three (Settings ▸ Recents & split screen ▸ Divider stays where you let go, on by default): Motorola only knew half and half, peek and closing, so 70/30 jumped to peek view. Between 15 % and 85 % of the screen the divider stays put and the apps take exactly that size; nearer the edge peek view and closing still work. Three apps stay as let go while each has at least 15 % of the screen.
- Recents: "Add split to home screen" / "Add 3-app split to home screen" in a split card's long-press menu (saved as a scene, the same scene reused when those apps were saved before, with a shortcut on the home screen of the display Recents is on).
- Recents: peek view is a symbol on the picking bar, and takes three apps where Folark's Vector module runs (Motorola's three-app peek: the first app picked big in the middle, the other two peeking in at the edges); a three-app split card's "Peek view" switches to it too. Without the module it stays two apps.
- The third app of a three-app split waits in System UI until the new two-app split has settled: it went into the split shown before (System UI was still forming the new one) and no three-app split came of it. The module now logs the split state before and after the third app.
- Three apps a third each: a third app joining a split now makes three equal thirds (33 / 33 / 33) instead of Motorola's carousel, where one app shrank to a 10 % strip and the split looked like two apps. Folark's Vector module uses the even three-app layout Motorola already has but never picks on the Razr Fold. Also 33/33/33 in the split drawer's corner next to 67/33 · 50/50 · 33/67 (pick two more apps, made a real three-app split whatever the setting), "A third each" in the taskbar's split menu with three apps, and Settings ▸ Recents & split screen ▸ Three apps a third each (on by default; off keeps Motorola's carousel).
- A third / two thirds for two apps is usable now: Motorola's own sizing only knows 50/50 and its 5/90 layouts and kept the smaller app as wide as the bigger one, cut off under it. For ⅓ and ⅔ Folark's Vector module lays the two apps out side by side at the divider, each really that wide.
- The divider stops at ⅓, ½ and ⅔ (33 / 50 / 67 %, the sizes Motorola's split screen lays apps out at; Settings ▸ Recents & split screen ▸ Divider stops). Folark's Vector module adds the stops in System UI after every rebuild of the divider's stops (DividerSnapAlgorithm.updateTargets), so they no longer fall back to 50/50 after a move; three-app layouts keep Motorola's own.
- Works on the Razr's Android 17 build again: the module finds System UI's split calls by their leading parameters and passes a real InstanceId; the split drawer docks beside the app with `startTasks` (System UI's `startIntentAndTask` throws there) after waiting for its task to reach WM Shell; the Native engine falls back to the commands when System UI turns a request down. Check split screen shows the reason for a failure.

### Taskbar
- Steps aside while the app drawer is open (or being pulled up) and comes back when it closes.
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
