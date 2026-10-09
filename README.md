# Glut Launcher

Ein ruhiger Launcher für den Fire TV Stick (getestet für Fire OS 7/8) im
Gruvbox-Look, ohne Werbung, Empfehlungen oder Banner.

- **Startseite:** Uhr, „Zuletzt genutzt“, Favoriten, Kurzbefehle
- **Alle Apps:** sortierbar (A–Z, zuletzt, häufig), Apps ausblenden
- **Schnellmenü:** Kachelstil, Hintergrund, Kontrast, Akzentfarbe
- **Kachelstile:** *Eingefärbt* (Standard, echte App-Banner warm entsättigt),
  *Schlicht* (Name + Farbquadrat), *Original* (Banner unverändert)
- **Menü-Taste auf einer Kachel:** Favorit, Reihenfolge, Ausblenden, App-Info,
  Deinstallieren

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

### Fester Signaturschlüssel (empfohlen)

Ohne festen Schlüssel signiert jeder Build mit einem neuen Zufallsschlüssel.
Dann musst du vor jedem Update die alte Version deinstallieren und verlierst
Favoriten und Einstellungen. Einmalig auf deinem Rechner:

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

Ob der Dienst läuft, zeigt das Schnellmenü unter „Home-Taste umleiten“.

## Zurück zu Amazon

- Vorübergehend: Schnellmenü → *Amazon-Startseite öffnen* (10 Minuten keine
  Umleitung, z. B. für Updates).
- Dauerhaft: Schnellmenü → *Home-Taste umleiten: Aus*, oder
  `adb shell settings delete secure enabled_accessibility_services`, oder
  App deinstallieren.

## Bauen

Android Studio oder `./gradlew assembleDebug` (JDK 17, Android SDK 35).

Schrift: Geist (SIL Open Font License, siehe `FONT-LICENSE-Geist.txt`).
Farben: Gruvbox von Pavel Pertsev.
