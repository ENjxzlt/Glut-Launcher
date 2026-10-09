# Glut Launcher

A calm, ad-free home screen for Amazon Fire TV sticks. No sponsored rows, no
banners, no "recommended for you". Just your apps, in a theme you pick.

> [!WARNING]
> **This is a vibe-coded hobby project.**
> Almost all of the code was written by an AI assistant (Claude) in a single
> long chat session, steered and tested by one person on one device. There is
> no test suite, no code review by an Android developer and no QA beyond
> "it works on my Fire TV Stick Lite".
>
> - Expect bugs, rough edges and things that only work on some devices.
> - The app relies on an accessibility-service workaround that Amazon can
>   break with any Fire OS update.
> - It asks for powerful permissions (accessibility service, notification
>   access, package installs). Read the [Permissions](#permissions) and
>   [Privacy](#privacy) sections, and read the code if that matters to you.
>
> Use it at your own risk. No warranty of any kind.

> [!NOTE]
> The app's interface is currently **German only**.

---

## Contents

- [Features](#features)
- [How it works (and why it is a bit slow)](#how-it-works-and-why-it-is-a-bit-slow)
- [Compatibility](#compatibility)
- [Installation](#installation)
- [Permissions](#permissions)
- [Updating](#updating)
- [Going back to the Amazon home screen](#going-back-to-the-amazon-home-screen)
- [Privacy](#privacy)
- [Building from source](#building-from-source)
- [Credits and licenses](#credits-and-licenses)

## Features

**Home screen**
- Clock and date, app search, quick buttons for Wi-Fi, profile and Fire TV settings
- *Recently used* row (apps launched through Glut)
- *Favourites* row, which can also hold folders
- *Now playing* widget for music running in the background, with
  play/pause and skip buttons
- Optional *Smart home* row for Home Assistant lights, switches, scenes and scripts
- Shortcuts to all apps, Bluetooth and the Amazon home screen

**All apps**
- Sort by name, recently used or most used
- Folders
- Hide apps; pre-installed system apps are hidden by default and still reachable
  through search and the *System* filter

**Remote control**
- `OK` opens an app; the `Menu` (☰) button on a tile opens its options:
  favourite, reorder, move to folder, hide, app info, uninstall

**Customisation** (everything can be switched on or off in the settings, via
the sliders icon in the top right)
- Colour themes: Gruvbox Dark/Light, Nord, Catppuccin Mocha, Dracula,
  Tokyo Night, Rosé Pine, Everforest, Solarized Dark
- Fonts: Geist, Inter, IBM Plex Sans, JetBrains Mono, Atkinson Hyperlegible,
  Lexend, Nunito
- Backgrounds: *Glut*, *Time of day* (cool in the morning, warm in the evening,
  dark at night), *Contours*, *Plain*; plus contrast level and accent colour
- Tile style: app banners tinted to match the theme (default), plain name
  tiles, or original banners

**Extras**
- *Startup app*: open a chosen app automatically when the TV wakes up, with
  separate choices for daytime and evening
- *Home Assistant* integration: log in with username and password (2FA
  supported) or a long-lived token
- *Cinema mode*: trigger a Home Assistant scene when you start selected
  video apps, and another one when you come back to the home screen
- *In-app updates* from this repository's GitHub releases

## How it works (and why it is a bit slow)

Fire OS does not let you choose a different default launcher. The Home button
always opens Amazon's home screen. Glut works around that:

1. An **accessibility service** watches for exactly one thing: Amazon's
   home screen (`com.amazon.tv.launcher`) coming to the foreground.
2. When that happens, it immediately puts a **snapshot of the Glut home
   screen** on top as an overlay, so you do not have to look at Amazon's UI.
3. It then starts the real Glut activity, which replaces the snapshot once it
   is drawn.

The catch: after the Home button is pressed, Android blocks app launches from
anything except the system home app for about **5 seconds**. Glut's launch is
queued until that window ends. On a Fire TV Stick Lite this means:

- about 1 s until Amazon's home screen appears and is immediately covered by
  the snapshot,
- about 5 s in total until Glut accepts input.

The snapshot only hides Amazon. It does not make Glut usable sooner, and it
cannot be fixed from inside an app without root. Remote key presses during that
window are swallowed so they do not trigger anything on Amazon's screen.

**Fast Home button:** on devices where Fire OS allows accessibility services
to filter key events, Glut can catch the Home button directly and skip
Amazon's home screen entirely. On the Fire TV Stick Lite this permission is
stripped (`dumpsys accessibility` shows `capabilities=0`), and the option is
hidden. With the fast Home button active, a long press opens Glut's own quick
menu (sleep, settings, Amazon home) instead of Amazon's.

## Compatibility

| Device | Status |
|---|---|
| Fire TV Stick Lite, Fire OS 7 | Tested: works, with the ~5 s delay described above |
| Other Fire TV devices on Fire OS 7 or 8 | Untested; should behave similarly |
| Fire TV devices on **Vega OS** (e.g. Fire TV Stick 4K Select) | **Not supported.** Vega OS does not run Android apps |
| Android TV / Google TV | Untested. There you can simply set Glut as the default launcher; the workaround is not needed |

Minimum Android version: 9 (API 28).

## Installation

You need: the Fire TV device and a way to run `adb` once, either an Android
phone (e.g. with [Termux](https://termux.dev)) or a computer. Without `adb`, Glut works
as a normal app, but the Home button keeps going to Amazon.

### 1. Install the APK on the Fire TV

1. On the Fire TV, open *Settings → My Fire TV → Developer options*. Turn on
   **ADB debugging** and **Apps from unknown sources** (or allow it for the
   *Downloader* app). If you do not see *Developer options*, go to *Settings →
   My Fire TV → About* and click the device name seven times.
2. Install **Downloader** from the Amazon Appstore and open this URL:
   ```
   github.com/ENjxzlt/Glut-Launcher/releases/latest/download/glut-launcher.apk
   ```
3. Install the APK. Glut now shows up under *Apps*.

### 2. Connect with adb

Find the Fire TV's IP address under *Settings → My Fire TV → About → Network*.
Your phone or computer must be on the same network.

**Android phone with Termux:**
```sh
pkg update
pkg install android-tools
adb connect 192.168.x.x:5555
```
Use the Termux build from F-Droid or GitHub; the Play Store version is outdated.

**Computer:** install the Android platform tools, then run
`adb connect 192.168.x.x:5555`.

A prompt appears on the TV. Tick *Always allow from this computer* and confirm.
`adb devices` should now list the device as `device`, not `unauthorized`.

### 3. Grant the permissions

First check whether another accessibility service is already active:
```sh
adb shell settings get secure enabled_accessibility_services
```
If this prints anything other than `null` or an empty line (for example
Projectivy or Home on Fire), turn that tool's home-button redirect off first.
The next command **replaces** the list, and two redirects fighting each other
cause a black screen.

```sh
# Home button redirect (required)
adb shell settings put secure enabled_accessibility_services de.glut.launcher/de.glut.launcher.service.HomeRedirectService
adb shell settings put secure accessibility_enabled 1

# Now-playing widget (optional)
adb shell cmd notification allow_listener de.glut.launcher/de.glut.launcher.service.MediaListenerService

# In-app updates (optional)
adb shell appops set de.glut.launcher REQUEST_INSTALL_PACKAGES allow
```

To re-enable the service after changing its configuration, set the value to
`""` first and then set it again. Setting the same value twice does not
restart the service.

### 4. Finish up

```sh
adb disconnect
```
Then **turn ADB debugging off again** on the Fire TV. Otherwise any device on
your network can connect to it. The settings above persist across reboots and
in-app updates. You only need to repeat them after uninstalling and
reinstalling Glut.

Check *Settings → System* in Glut: *Home-Taste umleiten* should say
*Dienst aktiv*.

## Permissions

| Permission | Why |
|---|---|
| Accessibility service | Detects when Amazon's home screen appears and opens Glut instead. It is configured to receive events from `com.amazon.tv.launcher` only and cannot read screen content. On devices that allow it, it also handles the Home key. |
| Notification listener | Android only shares active media sessions (title, artist, cover, play/pause) with apps that have this permission. Glut does not read or store notifications. |
| Query installed apps | To list your apps. |
| Install / delete packages | In-app updates; uninstall from a tile's menu (both always ask for confirmation). |
| Internet | Update check against GitHub; Home Assistant on your local network. |
| Cleartext traffic | Many Home Assistant installs use plain `http://` on the local network. |

### Known side effect

While **any** accessibility service is active, Prime Video does not
auto-hide its playback controls. This is Prime Video's behaviour, not specific
to Glut.

## Updating

In Glut: *Settings → System → Nach Updates suchen*. Glut checks the
`neuester-build` GitHub release, downloads the APK and hands it to the system
installer, which asks you to confirm. When opened, Glut also checks on its own,
at most every six hours (can be turned off).

Updates only install if the new APK is signed with the same key as the
installed one (see [Signing](#signing)). If you get a "different signature"
error, uninstall once and install the latest APK again with Downloader. You
will then need to repeat the `adb` steps.

## Going back to the Amazon home screen

- **For a moment:** *Amazon-Startseite* on the home screen, or in the settings.
  The redirect pauses for 10 minutes, e.g. for system updates or Amazon
  profile settings.
- **Turn the redirect off:** *Settings → System → Home-Taste umleiten*.
- **Completely:**
  ```sh
  adb shell settings put secure enabled_accessibility_services ""
  ```
  or uninstall the app.

## Privacy

- No analytics, no tracking, no ads, no account.
- Network requests go to:
  - `api.github.com` / `github.com` for update checks and downloads (can be
    switched off),
  - your own Home Assistant server, only if you set it up.
- Home Assistant credentials are exchanged for tokens; the tokens are stored
  in the app's private storage. Your password is not stored.
- *Recently used* and *most used* are counted locally and only for apps
  launched through Glut.

## Building from source

Requirements: JDK 17 and the Android SDK (compile SDK 35).

```sh
./gradlew assembleRelease   # optimized build (R8), as shipped
./gradlew assembleDebug     # debug build
```

Without signing configuration the release APK is unsigned. See below.

### Continuous integration

`.github/workflows/build.yml` builds the release APK on every push, uploads it
as a workflow artifact and, for the main development branch, replaces the
`neuester-build` release. The release notes contain `versionCode=` and
`versionName=` lines, which the in-app updater reads. The version code is the
workflow run number.

### Signing

In-app updates need every build to be signed with the same key.

- **Default:** on the first run the workflow creates a keystore with the
  standard public debug credentials and keeps it in the GitHub Actions cache,
  never in the repository. A scheduled run touches the cache twice a week so it
  does not expire. If the cache is lost anyway, the next build has a new key and
  users have to reinstall once.
- **Recommended for anything beyond personal use:** provide your own key as
  repository secrets. It takes precedence over the cached one:

  ```sh
  keytool -genkeypair -keystore sideload.jks -alias sideload -keyalg RSA -keysize 2048 -validity 10000
  base64 -w0 sideload.jks   # macOS: base64 -i sideload.jks
  ```

  Add these secrets under *Settings → Secrets and variables → Actions*:
  `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`,
  `SIGNING_KEY_PASSWORD`. Never commit the `.jks` file.

  Switching keys means existing installs have to be reinstalled once.

If you fork this project, change the release URL in
`app/src/main/java/de/glut/launcher/data/Updater.kt` to your own repository.
Otherwise your build updates itself from this one.

## Credits and licenses

- **Fonts** (bundled, all under the SIL Open Font License 1.1): Geist (Vercel),
  Inter, IBM Plex Sans, JetBrains Mono, Atkinson Hyperlegible, Lexend, Nunito.
  See `FONT-LICENSE-Geist.txt` for the license text.
- **Colour themes** are adaptations of these open-source schemes: Gruvbox,
  Nord, Catppuccin, Dracula, Tokyo Night, Rosé Pine, Everforest and Solarized.
  All credit for the palettes goes to their authors.
- **Code license:** _not yet chosen_. Until a `LICENSE` file is added, all
  rights are reserved by default.

Amazon, Fire TV and Fire OS are trademarks of Amazon.com, Inc. or its
affiliates. This project is not affiliated with, endorsed by or sponsored by
Amazon. App names shown on tiles belong to their respective owners.
