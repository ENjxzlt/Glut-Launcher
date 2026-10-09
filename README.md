# Glut Launcher

Ein ruhiger Launcher für den Fire TV Stick (getestet für Fire OS 7/8) im
Gruvbox-Look, ohne Werbung, Empfehlungen oder Banner.

- **Startseite:** Uhr, Suchleiste, Musik-Widget, „Zuletzt genutzt“, Favoriten
  (auch Ordner), Smart-Home-Leiste, Kurzbefehle
- **Alle Apps:** sortierbar, Ordner, Systemapps ausgeblendet (Filter „System“)
- **Suche:** filtert installierte Apps beim Tippen
- **Einstellungen** (Schieberegler-Symbol oben rechts), alles einzeln schaltbar:
  - Farbschema: Gruvbox Dunkel/Hell, Nord, Catppuccin Mocha, Dracula,
    Tokyo Night, Rosé Pine, Everforest, Solarized
  - Schrift: Geist, Inter, IBM Plex Sans, JetBrains Mono, Atkinson
    Hyperlegible, Lexend, Nunito
  - Hintergrund (auch „Tageszeit“), Kontrast, Akzentfarbe, Kachelstil
  - Ordner, Systemapps ausblenden, Musik-Widget
  - Einschalt-App getrennt für tagsüber und abends
  - Home Assistant: Smart-Home-Leiste und Kino-Modus (Szene beim Start einer
    Video-App, andere Szene zurück auf der Startseite)
  - Updates direkt im Launcher
- **Menü-Taste auf einer Kachel:** Favorit, Reihenfolge, Ordner, Ausblenden,
  App-Info, Deinstallieren

„Zuletzt genutzt“ zählt nur Apps, die über diesen Launcher gestartet wurden.
Fire OS gibt fremden Apps keine Nutzungsdaten.

## Grenzen – bitte lesen

Fire OS erlaubt keinen anderen Standard-Launcher. Die Home-Taste öffnet immer
zuerst die Amazon-Startseite. Glut Launcher legt sich über einen
Bedienungshilfen-Dienst sofort darüber. Das heißt:

- Beim Drücken von Home kann die Amazon-Startseite kurz aufblitzen.
- Solange irgendein Bedienungshilfen-Dienst aktiv ist, blendet Prime Video
  die Wiedergabe-Steuerung nicht automatisch aus.
- Ein Fire-OS-Update kann den Trick jederzeit abschalten.
- Ein langes Drücken von Home öffnet weiterhin Amazons Schnelleinstellungen.

## APK holen

Jeder Push baut die APK über GitHub Actions („APK bauen“). Unter *Actions →
letzter Lauf → Artifacts* liegt `glut-launcher-apk`.

### Eigener Signaturschlüssel (optional, am zuverlässigsten)

Ohne Secret nutzt der Workflow einen zwischengespeicherten Schlüssel (siehe
„Updates“). Wer ganz sicher gehen will, legt einmalig auf einem Rechner einen
eigenen an:

```sh
keytool -genkeypair -keystore sideload.jks -alias sideload -keyalg RSA \
  -keysize 2048 -validity 10000
base64 -w0 sideload.jks   # macOS: base64 -i sideload.jks
```

Dann im Repo unter *Settings → Secrets and variables → Actions* anlegen:
`SIGNING_KEYSTORE_BASE64` (Ausgabe von base64), `SIGNING_STORE_PASSWORD`,
`SIGNING_KEY_ALIAS` (`sideload`), `SIGNING_KEY_PASSWORD`. Die `.jks`-Datei
nicht ins Repo legen.

## Installieren nur mit Fire TV und Handy

1. Am Stick: *Einstellungen → Mein Fire TV → Entwickleroptionen*:
   *ADB-Debugging* an, *Apps unbekannter Herkunft* an (bzw. für Downloader
   erlauben). Fehlen die Entwickleroptionen: *Mein Fire TV → Info*, dort
   siebenmal auf den Gerätenamen klicken.
2. Im Amazon Appstore die App **Downloader** installieren und darin diese
   Adresse eingeben:
   `github.com/ENjxzlt/Sanity/releases/latest/download/glut-launcher.apk`
3. Installieren. Glut Launcher steht jetzt unter *Apps* und läuft schon,
   nur die Home-Taste führt noch zu Amazon.
4. Für die Home-Taste braucht es einmal ADB:
   - **Android-Handy:** z. B. Termux (`pkg install android-tools`) oder eine
     ADB-App aus dem Play Store. Handy und Stick im selben WLAN,
     `adb connect <IP-des-Sticks>`, am Fernseher „Zulassen“ bestätigen, dann
     die zwei `settings put`-Befehle unten.
   - **iPhone:** kein brauchbares ADB. Dann bleibt der Launcher eine normale
     App ohne Home-Umleitung, oder du leihst dir kurz einen Rechner.

## Installieren mit Rechner

1. Am Stick: *Einstellungen → Mein Fire TV → Entwickleroptionen →
   ADB-Debugging* einschalten. Die IP steht unter *Mein Fire TV → Info →
   Netzwerk*.
2. Am Rechner:

```sh
adb connect <IP-des-Sticks>
adb install -r glut-launcher.apk

# Home-Taste umleiten (es gibt dafür keinen Schalter mehr in den Einstellungen)
adb shell settings put secure enabled_accessibility_services de.glut.launcher/de.glut.launcher.service.HomeRedirectService
adb shell settings put secure accessibility_enabled 1
```

Achtung: Der erste `settings put` **ersetzt** die Liste aller aktiven
Bedienungshilfen-Dienste. Läuft schon einer (z. B. Projectivy oder
Home on Fire), erst nachsehen und beide mit `:` getrennt eintragen – oder
besser den anderen abschalten, denn zwei Umleitungen kämpfen gegeneinander
(schwarzer Bildschirm):

```sh
adb shell settings get secure enabled_accessibility_services
```

Ob der Dienst läuft, zeigt *Einstellungen → System → Home-Taste umleiten*.

Für das **Musik-Widget** braucht der Launcher Zugriff auf die Mediensitzungen
anderer Apps. Android gibt den nur an zugelassene Benachrichtigungs-Dienste:

```sh
adb shell cmd notification allow_listener de.glut.launcher/de.glut.launcher.service.MediaListenerService
```

Die **Einschalt-App** nutzt die Home-Umleitung: Sie greift, wenn nach dem
Aufwachen die Amazon-Startseite erscheint. Kehrt der Stick direkt in eine App
zurück, passiert nichts.

## Updates

*Einstellungen → System → Nach Updates suchen.* Der Launcher prüft das
GitHub-Release, lädt die neue APK und startet die Installation; Fire OS fragt
einmal nach Bestätigung. Dafür muss „Apps unbekannter Herkunft“ für Glut
Launcher erlaubt sein (Entwickleroptionen).

Updates funktionieren nur, wenn alte und neue Version mit demselben Schlüssel
signiert sind. Der Build-Workflow hält dafür einen Schlüssel im GitHub-Cache und
frischt ihn zweimal pro Woche auf. Geht der Cache trotzdem verloren, meldet das
Update „anderer Signaturschlüssel“ – dann einmal neu installieren. Dauerhaft
sicher ist nur ein eigener Schlüssel als Secret (siehe unten).

## Zurück zu Amazon

- Vorübergehend: Schnellmenü → *Amazon-Startseite öffnen* (10 Minuten keine
  Umleitung, z. B. für Updates).
- Dauerhaft: Schnellmenü → *Home-Taste umleiten: Aus*, oder
  `adb shell settings delete secure enabled_accessibility_services`, oder
  App deinstallieren.

## Bauen

Android Studio oder `./gradlew assembleDebug` (JDK 17, Android SDK 35).

Schriften: Geist, Inter, IBM Plex Sans, JetBrains Mono, Atkinson Hyperlegible,
Lexend, Nunito – alle unter der SIL Open Font License (`FONT-LICENSE-Geist.txt`
als Beispieltext der Lizenz).
Farbschemata: Gruvbox, Nord, Catppuccin, Dracula, Tokyo Night, Rosé Pine,
Everforest, Solarized – jeweils von ihren Autor:innen unter freien Lizenzen.
