# ClipSaver

A small Android app (Android 10+) that saves TikTok videos to your phone's gallery.

- Paste a link and tap **Download**, or in TikTok tap **Share → ClipSaver** and it starts on its own.
- Videos land in **Movies/ClipSaver** (shows up in Gallery / Google Photos).
- Uses [yt-dlp](https://github.com/yt-dlp/yt-dlp) via [youtubedl-android](https://github.com/yausername/youtubedl-android) under the hood. When TikTok changes something and downloads start failing, open the **⋮ menu → Update engine**; no app update needed.
- No storage permission required; only Internet.

## Build it

### Option A: GitHub (no computer setup)
1. Create a new GitHub repo and upload this folder's contents.
2. The included workflow (`.github/workflows/build.yml`) runs automatically.
3. Open **Actions → latest run → Artifacts → ClipSaver-apks** and download the zip.
4. On your phone, install `app-arm64-v8a-release.apk` (works on nearly all modern phones; use `app-universal-release.apk` if unsure). Allow "install unknown apps" when prompted.

### Option B: Android Studio
1. Open this folder in Android Studio (Ladybug or newer). Let it sync; it downloads Gradle and the Android SDK as needed.
2. Plug in your phone with USB debugging on and press **Run**, or use **Build → Build APK(s)**.

The first launch takes a few seconds while the bundled downloader unpacks.

## Notes
- Private videos and ones that require login can't be downloaded.
- The release APK is signed with the debug key so it installs without extra setup. Use your own keystore before sharing it with anyone.
- Downloading others' videos for anything beyond personal use can break TikTok's terms and the creator's copyright. Ask before reposting.
- youtubedl-android is GPL-3.0, so if you distribute this app, its source has to be available under GPL-3.0 too.
