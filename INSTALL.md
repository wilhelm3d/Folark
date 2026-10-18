# Installing a release build of Folark

This guide builds the **release** version of Folark, signed with your own key, and installs it on the
phone. The release build is what you use every day; the debug build is only for development.

Everything below runs on the Windows PC in **PowerShell**, from the project folder:

```
cd C:\Users\wilhe\Documents\Folark-redesign
```

---

## Before you start: save your home screen

Folark is installed today with the **debug key**. Android will not update an app signed with one
key using an app signed with another, so the first release install needs an **uninstall**, and an
uninstall wipes Folark's layout and settings.

1. On the phone open **Folark Settings → Backup & restore → Back up now** and save the file
   somewhere you can find it (Downloads, or a cloud folder).
2. You restore it after the install (step 5).

You only lose data this one time. Later release builds update over each other with no uninstall.

---

## 1. Make your signing key (once)

The key proves future updates come from you. Keep the file **and** the passwords safe: without them
you can never update the installed app, only uninstall and reinstall it.

Android Studio ships the `keytool` program. Run:

```
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v `
  -keystore folark-release.jks -alias folark `
  -keyalg RSA -keysize 4096 -validity 10000
```

It asks for a password (twice) and for a name and city; the name fields can be anything. When it
asks for a key password, press Enter to use the same one.

You now have `folark-release.jks` in the project folder. It is already in `.gitignore`, so it is
never committed. Copy it to a second place too (a USB stick or a password manager).

---

## 2. Tell the build where the key is (once)

Create a file named **`keystore.properties`** in the project folder (next to `settings.gradle.kts`)
with this content, using your own password:

```
storeFile=folark-release.jks
storePassword=YOUR_PASSWORD
keyAlias=folark
keyPassword=YOUR_PASSWORD
```

This file is in `.gitignore` too. If it is missing, the build still works but quietly signs the
release with the debug key, which is not what you want.

---

## 3. Build the release

```
.\gradlew clean assembleRelease
```

The finished app is:

```
app\build\outputs\apk\release\app-release.apk
```

Check it is signed with your key (it should print `Signer #1` with your name):

```
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\37.0.0\apksigner.bat" verify --print-certs app\build\outputs\apk\release\app-release.apk
```

(If that folder name differs, look in `%LOCALAPPDATA%\Android\Sdk\build-tools\` for the version you
have.)

**In Android Studio instead:** Build → Generate Signed App Bundle or APK → **APK** → choose
`folark-release.jks`, alias `folark` and your passwords → **release** → Create. The APK lands in
`app\release\`.

---

## 4. Install on the phone

Plug in the phone with USB debugging on.

**First time only** (the switch from the debug key; you made the backup above):

```
adb -s ZY22MPZ92C uninstall org.arkikeskus.launcher
adb -s ZY22MPZ92C install app\build\outputs\apk\release\app-release.apk
```

**Every later update** (keeps your layout and settings):

```
adb -s ZY22MPZ92C install -r app\build\outputs\apk\release\app-release.apk
```

You can also copy the APK to the phone and open it in the Files app ("install unknown apps" must be
allowed for Files).

---

## 5. Set Folark up again (first time only)

1. Press Home and choose **Folark**, **Always**.
2. In the first-run intro choose **Restore from a backup**, and pick the backup file from the start of this guide.
3. Give the permissions back. Android forgets them on an uninstall:
   - **Notification access**: Settings → Permissions in Folark.
   - **Accessibility service** (double tap to lock, taskbar): Settings → Permissions in Folark.
   - **Usage access** (taskbar recent apps): Settings → Permissions in Folark.
4. Run the one-time adb grants again (split screen controls, refresh rate, animation speed, keeping
   the accessibility service on):

```
adb -s ZY22MPZ92C shell pm grant org.arkikeskus.launcher android.permission.WRITE_SECURE_SETTINGS
adb -s ZY22MPZ92C shell pm grant org.arkikeskus.launcher android.permission.DUMP
```

Updates with `install -r` keep all of these; you only do step 5 after an uninstall.

---

## Updating later

Before each new release build, raise the version in `app\build.gradle.kts`:

```
versionCode = 51        // always higher than the installed one
versionName = "1.6.1"
```

Then repeat **step 3** and the **update** line of step 4.

---

## If something goes wrong

| Problem | Fix |
|---|---|
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | The phone has a build signed with another key. Back up, uninstall, install (step 4, first time). |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | The `versionCode` is lower than the installed one. Raise it and build again. |
| The release crashes but debug did not | The release build shrinks the code (R8). Send the crash log: `adb -s ZY22MPZ92C logcat -d -b crash > crash.txt` |
| `keytool` not found | Use the path inside your Android Studio folder, `...\Android Studio\jbr\bin\keytool.exe`. |
| Lost the key or its password | The installed app can no longer be updated. Make a new key, back up, uninstall, install. |
