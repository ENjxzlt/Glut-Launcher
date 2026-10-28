# Changelog

All notable changes to Glut Launcher. Versions follow
[Semantic Versioning](https://semver.org/).

## [1.6.2] – 2026-10-28

### Changed
- Dock: the apps are centred in the glass bar and no longer show a title when focused.

## [1.6.1] – 2026-10-28

### Fixed
- Dock icons: logos on a transparent background (Play Store) and icons whose
  artwork touches the edge (YouTube) now sit on a plate with a margin, wide
  logos are fitted instead of stretched. Normal rounded icons still fill the
  square.

## [1.6.0] – 2026-10-28

### Added
- Jellyfin suggestions on the home screen (Settings → Jellyfin): *Continue
  watching*, *Recently added* and *Suggested*. Sign in with Quick Connect or
  username and password. The focused card shows its details in place of the clock.
- Icon pack support (ADW/Nova format, Settings → Appearance → Icon pack).

### Changed
- Home screen: *Recently used* is gone; a very large clock (Lexend) with a
  light gradient; glass-style tab bar, smart home card and now-playing card.
- Favourites are a dock of square (1:1) app icons.

> Tested on a Fire TV Stick 4K Max against a mock Jellyfin server and a
> synthetic icon pack; not against a real Jellyfin 12 server or a Play Store pack.

## [1.5.0] – 2026-10-28

### Changed
- New look inspired by Apple TV: a tab bar (Home / Apps / Search), a large
  top shelf that shows the focused app (with the now-playing widget), bigger
  rounded tiles, a lift-and-shadow focus effect instead of the coloured frame,
  light pill rows in menus, a two-pane settings layout and a soft fade at the
  bottom of the home screen.
- Colour themes, tile styles and all features stay as they were.

## [1.4.1] – 2026-10-28

### Changed
- Screensaver stock photos are now nature only: featured pictures from
  Wikimedia Commons (forests, mountains, beaches, waterfalls, lakes, sunsets,
  flowers), landscape format only. The licence credit (title, author, licence)
  is shown in the corner. Replaces picsum.photos.

## [1.4.0] – 2026-10-27

### Added
- Own screensaver that replaces Amazon's (Settings → Screensaver, needs root).
  Source: your own images and videos (folder `screensaver` in the app's storage)
  or random stock photos from picsum.photos (Unsplash licence). Soft crossfade
  with slow zoom, silent videos, optional clock, adjustable change interval and
  start delay, preview.

> Image, video and clock playback were tested on a Fire TV Stick 4K Max; the
> settings page, switching in the app and stock photos were not.

## [1.3.1] – 2026-10-27

### Fixed
- The update check no longer fails when GitHub's API rate limit is reached
  (60 requests per hour per IP address). It falls back to the release page, and
  the error message now names the real reason.

## [1.3.0] – 2026-10-27

### Added
- Settings are now a main menu with sub-menus (Appearance, Home & apps,
  On wake-up, Smart home, Updates & info, System).
- When Glut is the default launcher, the *System* menu replaces Amazon's
  settings: Network (Wi-Fi, Bluetooth, ADB over network), Users, Sound,
  Manage apps (stop, clear cache/data, uninstall), Power & root, Device info and
  Developer. Most of it needs root (`su`).
- Bluetooth: switch on/off, search, pair and unpair.

### Changed
- System pages and root mode only appear when Glut is the default launcher.
- *Root mode* is on by default, so restart and sleep run directly.
- The top buttons on the home screen open the new pages in that mode.

> Not tested on a device before release.

## [1.2.1] – 2026-10-27

### Fixed
- The Wi-Fi, profile and settings buttons on the home screen now open the new
  Wi-Fi, user and system pages. Back returns to the home screen.

## [1.2.0] – 2026-10-27

### Added
- *System* pages (Settings → System & network): Wi-Fi list with connect,
  password entry and forget; user picker; ADB over network; a *root mode* that
  restarts or sleeps the device directly. Needs a rooted device (`su`).
- *Custom image/video* background (Settings → Background). Put files in
  `/sdcard/Android/data/de.glut.launcher/files/backgrounds`; videos loop
  silently and pause while the launcher is in the background. Adjustable dimming.

### Changed
- New permissions: Wi-Fi state/changes, network state and location (granted via
  root, Android only shows Wi-Fi scan results to apps with location access).

> Not tested on a device before release.

## [1.1.0] – 2026-10-27

### Added
- *Only launcher* mode: when Glut is the system's default home app (for example
  with Amazon's launcher disabled on a rooted device), the Menu button (☰) on
  the home screen opens the quick menu (sleep, restart, settings).
- In this mode the settings and the quick menu hide the Amazon home and Home
  button redirect options, which do not apply.

## [1.0.0] – 2026-10-10

First stable release. Tested on a Fire TV Stick Lite (Fire OS 7).

> **Vibe-coded hobby project.** Almost all of the code was written by an AI
> assistant and tested on a single device. Expect rough edges. See the README
> for details, permissions and privacy.

### Home screen
- Clock and date, app search, quick buttons for Wi-Fi, profile, Fire TV
  settings and launcher settings
- *Recently used* and *Favourites* rows; favourites can also hold folders
- *Now playing* widget with play/pause and skip for music playing in the
  background (needs notification access via ADB)
- Optional *Smart home* row for Home Assistant lights, switches, fans,
  scenes and scripts

### Apps
- *All apps* sorted by name, recently used or most used
- Folders, hidden apps, pre-installed system apps hidden by default
- App banners tinted to match the theme; apps without a banner get a
  generated tile, and Amazon's own apps (Games, Kids, Photos, Silk) get
  dedicated tiles in the launcher's style

### Customisation
- Colour themes: Gruvbox Dark/Light, Nord, Catppuccin Mocha, Dracula,
  Tokyo Night, Rosé Pine, Everforest, Solarized Dark
- Fonts: Geist, Inter, IBM Plex Sans, JetBrains Mono, Atkinson Hyperlegible,
  Lexend, Nunito
- Backgrounds including *Time of day*, contrast levels, accent colours,
  three tile styles
- English and German interface

### Fire TV integration
- Home button redirect via an accessibility service, with an instant
  snapshot of the home screen while Android blocks app launches after Home
- Starts automatically after a reboot and after an update
- *Startup app* for daytime and evening after the TV wakes up
- Fire TV settings, Wi-Fi and Bluetooth open Amazon's own settings pages
- *Restart* via the system power menu
- *Cinema mode*: run Home Assistant scenes when starting and leaving video apps

### Updates
- In-app updates from GitHub releases; stable channel by default, optional
  beta channel for every change

### Known limitations
- About 5 seconds after pressing Home until Glut accepts input (Android's
  app-switch protection; cannot be bypassed without root)
- Prime Video does not auto-hide its controls while any accessibility service
  is active
- Fire TV devices on Vega OS are not supported
