# Changelog

All notable changes to Glut Launcher. Versions follow
[Semantic Versioning](https://semver.org/).

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
