# PS5 Tool

All-in-one Android companion app for jailbroken PlayStation 5 consoles.
Scan, connect and manage your PS5 over Wi-Fi: payloads, FTP file manager, games, screen casting, Linux boot and more.

![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-blue)
![License](https://img.shields.io/badge/license-GPL--3.0-green)

## Features

### Console connection
- Automatic network scan for jailbroken PS5s (manual IP override available)
- **ftpsrv is sent automatically** on connection — FTP is always ready
- Payload / ELF injector with **autoloader** (payload sequences with custom delays)

### File Manager
- Full remote file manager over FTP: browse, upload, download, rename, delete, create files and folders
- **Copy / Move** via long-press, paste into any folder

### Games Manager
- Scans `/user/app` first, then `/system_ex/app`, avoiding duplicates
- Launch games, view game info (param.sfo), mount games with **ShadowMountPlus**

### Screen Cast
- Injects **PSPlay 1.5** and auto-detects the DLNA receiver on the console
- **Storage Media**: cast videos/music from your phone, with optional external **subtitles** (.srt / .ass / .vtt)
- **Cast Browser**: built-in browser that sniffs video streams (HLS, DASH, MP4, MKV, WebM...) from any site and casts them
- Modern **on-screen remote**: D-pad seeking (±10s / ±30s), volume control, seek bar, play / pause / stop, rewind / forward
- Keeps casting in the background thanks to a foreground service with a **media notification showing live progress**

### Linux
- Boot Linux via **ps5-linux-loader 2.4** (firmware 3.00 – 7.40) with safety confirmation dialog

### Extras
- Web Interfaces hub: open any console web UI in-app (file pickers supported)
- Remote Play: direct link to Chiaki on F-Droid
- Dark / light themes with 7 accent colors
- Customizable **touch vibration** (on/off + duration 5–150 ms)
- Battery optimization exemption to keep background casting alive

## Requirements

- Android 8.0 (API 26) or newer
- A jailbroken PS5 with payload loader listening on port 9021
- Phone and PS5 on the same Wi-Fi network

## Installation

Download the latest signed APK from the [Releases](../../releases) page and install it on your phone.

## Building from source

The project is a plain Android project (no Gradle wrapper yet). It builds with the Android SDK build tools:

```bash
aapt2 compile --dir res -o build/flat
aapt2 link -o build/base.apk --manifest AndroidManifest.xml \
    -I $ANDROID_HOME/platforms/android-34/android.jar \
    -A assets build/flat/*.flat --java gen --auto-add-overlay -0 elf
# compile gen + src with any Java 8 compatible compiler against android.jar
# dex with d8 --min-api 26, then zipalign + apksigner
```

> Note: ELF files in `assets/` must be stored **uncompressed** (`-0 elf`) because the app streams them via `openFd()`.

A Gradle/Android Studio conversion is planned — contributions welcome.

## Credits

- **PSPlay 1.5** (InsideMatrixDev/MounirHero) — DLNA media player, based on **ProsperoPlayer** by KINGDKAK
- **ftpsrv** (ps5-payload-dev) — FTP server for PS5
- **ps5-linux-loader** (ps5-linux) — Linux boot loader
- **ShadowMountPlus** (drakmor, based on ShadowMount by VoidWhisper) — game mounting from USB
- **Chiaki** — Remote Play client (linked, not bundled)

All bundled payloads are GPL-3.0 open source — see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
for authors and source links. If you are an author and want your binary removed or relinked,
please open an issue.

App developed by **InsideMatrixDev**.

## Disclaimer

This app is provided as-is, with no warranty. It is intended for use on consoles you own.
It is not affiliated with Sony Interactive Entertainment.

## License

GNU General Public License v3.0 — see [LICENSE](LICENSE).
