# Sonora

A lightweight Android media downloader and player. Paste a link, pick a quality, and the file lands in your Downloads folder — with a built-in library and player so you can watch or listen without leaving the app.

Powered by [yt-dlp](https://github.com/yt-dlp/yt-dlp) via [youtubedl-android](https://github.com/yausername/youtubedl-android).

## Features

- **Search** — paste a URL, fetch metadata (title, thumbnail, duration), choose video quality or audio format (MP3/M4A), and name the file before downloading.
- **Live progress** — download progress, speed, ETA, and cancellation.
- **Library** — everything you download is tracked in a local database with playback position remembered.
- **Player** — built-in ExoPlayer-based playback with resume support.
- **Engine updates** — the bundled yt-dlp binary can be checked and updated from Settings, so the app keeps working when sites change.
- **Storage choice** — save to public Downloads or pick a folder with the system file picker.

## Requirements

- Android 8.0 (API 26) or newer
- `arm64-v8a` device for the default build (build with `-Puniversal=true` for all ABIs)

## Install

Grab the latest `app-release.apk` from the [Releases](../../releases) page and sideload it. You may need to allow "install unknown apps" for your browser or file manager.

> Release builds are signed with a local debug key so they install over debug builds without wiping app data. It is not a Play Store signing key.

## Build from source

You need JDK 17+ and the Android SDK (compileSdk 36).

```bash
git clone https://github.com/<your-user>/LightDownloader.git
cd LightDownloader
echo "sdk.dir=$HOME/Android/Sdk" > local.properties

./gradlew assembleRelease   # -> app/build/outputs/apk/release/app-release.apk
./gradlew assembleDebug     # debug build
```

Or just open the project in Android Studio and hit Run.

## Tech stack

Kotlin · Jetpack Compose (Material 3) · Room · Media3/ExoPlayer · Coroutines · yt-dlp (youtubedl-android) + FFmpeg

## Disclaimer

This is a personal project. Only download content you have the right to download, and respect the terms of service of the sites you use it with. The author is not responsible for how it is used.
