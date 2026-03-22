# Native Android App (Kotlin)

This is a native Android replacement for the Python Buildozer app.

## What it includes
- Login flow (`admin` / `admin`)
- URL input
- Quality selection (`best`, `720`, `worst`, `audio`)
- Native download through Android `DownloadManager`

## Important difference from yt-dlp version
Native Android app does not directly run `yt-dlp`.

- Direct media URLs (`.mp4`, `.mp3`, `.m4a`, `.webm`, `.m3u8`) can be downloaded immediately.
- For YouTube/TikTok/Instagram extraction, use the Flask backend API (see section below).

## Backend Integration

This app is designed to work with the Flask backend located in `backend_flask_extractor/app.py`.

1. Deploy the Flask backend to Render, Railway, or your own server.
2. Copy the public URL of your backend.
3. In `MainActivity.kt`, update this line:
   ```kotlin
   private const val EXTRACTOR_API_BASE = "https://your-backend-url-here.onrender.com"
   ```
   Example:
   ```kotlin
   private const val EXTRACTOR_API_BASE = "https://video-extractor.onrender.com"
   ```
4. Rebuild the app and install.

The app will now:
1. Take user's video URL
2. Send it to Flask API: `GET /extract?url=...&quality=...`
3. Receive JSON: `{"status": "success", "url": "direct-link", "title": "...", "thumbnail": "..."}`
4. Use Android DownloadManager to save the direct link to device

## Build in Android Studio
1. Open Android Studio.
2. Choose "Open" and select this folder: `native_android_app`.
3. Let Gradle sync.
4. Run app on emulator or phone.

## Build signed release APK/AAB
1. In Android Studio, go to Build > Generate Signed Bundle / APK.
2. Create or choose your keystore.
3. Select AAB (Play Store) or APK.
4. Finish wizard and build.

## File map
- `app/src/main/java/org/example/androiddownloader/MainActivity.kt`
- `app/src/main/AndroidManifest.xml`
- `app/build.gradle.kts`
