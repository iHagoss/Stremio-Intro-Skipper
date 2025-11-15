# Setup Instructions for TV Player Android App

## Important Note

This is an **Android TV application** that must be built and installed on an Android TV device or emulator. It cannot run directly in the browser like a web application.

## What You Have

A complete Android TV app with:
- ✅ ExoPlayer video player integration
- ✅ Remote API integration for skip markers
- ✅ Auto-skip intro functionality
- ✅ Skip Intro and Skip Credits buttons
- ✅ Android TV remote (D-pad) navigation support
- ✅ LEANBACK launcher configuration
- ✅ Fullscreen optimized UI

## Next Steps

### 1. Configure Your API and Video Source

Edit `app/src/main/java/com/tvplayer/app/MainActivity.java` (lines 28-29):

```java
private static final String VIDEO_URL = "https://your-video-url.com/video.mp4";
private static final String MARKERS_API_URL = "https://your-api.com/skip-markers.json";
```

### 2. Build the APK

You have two options:

#### Option A: Build on Your Local Machine

1. Install [Android Studio](https://developer.android.com/studio)
2. Download this project
3. Open in Android Studio
4. Click **Build → Build Bundle(s) / APK(s) → Build APK(s)**
5. APK will be in `app/build/outputs/apk/debug/`

#### Option B: Build via Command Line

If you have Android SDK installed:

```bash
./build.sh
```

Or manually:

```bash
chmod +x gradlew
./gradlew assembleDebug
```

### 3. Install on Android TV

#### Via USB (ADB):

```bash
# Enable USB debugging on your Android TV
# Connect via USB or network

adb install app/build/outputs/apk/debug/app-debug.apk
```

#### Via Network (ADB over WiFi):

```bash
# Enable ADB debugging in Android TV Settings
# Note your TV's IP address

adb connect 192.168.1.XXX:5555
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.tvplayer.app/.MainActivity
```

#### Via File Transfer:

1. Copy APK to USB drive
2. Use a file manager app on Android TV
3. Navigate to APK and install

## API Endpoint Format

Your API should return JSON in this format:

```json
{
  "intro": {
    "start": 0,
    "end": 90
  },
  "credits": {
    "start": 2500,
    "end": 2700
  }
}
```

**Important:** All timestamps are in **seconds** (not milliseconds).

## Testing Without a Real API

The app includes fallback default markers if the API fails:
- Intro: 0-90 seconds
- Credits: 2500-2700 seconds

This allows you to test the skip functionality even without a working API.

## Project Files Overview

```
app/src/main/
├── java/com/tvplayer/app/
│   ├── MainActivity.java      # Main video player activity
│   ├── ApiService.java        # HTTP client using OkHttp
│   └── SkipMarkers.java       # Data model for JSON response
├── res/
│   ├── layout/
│   │   └── activity_main.xml  # UI layout with player and buttons
│   ├── values/
│   │   ├── styles.xml         # Fullscreen theme
│   │   └── colors.xml         # Color resources
│   └── drawable/              # App icons
└── AndroidManifest.xml        # Permissions and launcher config
```

## Customization

### Change Button Position

Edit `app/src/main/res/layout/activity_main.xml`:
- Modify `android:layout_gravity` for positioning
- Adjust margins and padding

### Modify Auto-Skip Behavior

Edit `MainActivity.java`:
- `autoSkipIntro()` method: Change auto-skip logic
- `introAutoSkipped` flag: Control if intro auto-skips only once

### Update Polling Interval

In `startPositionUpdates()` method, change `500` (ms) to your preferred interval.

## Troubleshooting

**Build fails:**
- Ensure Java JDK 11+ is installed
- Verify Android SDK is properly configured
- Check internet connection for dependency downloads

**API not working:**
- Check INTERNET permission in AndroidManifest.xml (already included)
- Verify API URL is accessible from your network
- Check Logcat logs: `adb logcat | grep TVPlayer`

**Buttons not appearing:**
- Verify API returns valid JSON
- Check timestamps are reasonable for your video length
- Ensure timestamps are in seconds, not milliseconds

## Support for Different Video Formats

ExoPlayer supports:
- HLS (`.m3u8`)
- DASH (`.mpd`)
- MP4
- WebM
- And many more

Just change the `VIDEO_URL` to your preferred format.

## Questions?

Check the main `README.md` for detailed documentation and building instructions.
