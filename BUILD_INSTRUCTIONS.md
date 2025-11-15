# Building Your Android TV Media Player APK

## Quick Start - Use GitHub Actions (Easiest Method)

Your project is already configured with GitHub Actions for automatic builds. Here's how to get your APK:

### Step 1: Push to GitHub
```bash
git add .
git commit -m "Ready for build"
git push origin main
```

### Step 2: Download Your APK
1. Go to your GitHub repository
2. Click on the "Actions" tab
3. Find the latest workflow run (it will be named "Android CI")
4. Wait for the build to complete (usually 3-5 minutes)
5. Scroll down to "Artifacts" section
6. Download `app-debug.apk` or `app-release.apk`

### Step 3: Install on Your Device
1. Transfer the APK to your Android TV/Firestick
2. Enable "Unknown Sources" in Settings → Security
3. Use a file manager app to install the APK
4. Launch "TV Player" from your apps

---

## Alternative Method - Build Locally

If you have Android Studio installed on your computer:

### Prerequisites
- Android Studio (latest version)
- Android SDK with API Level 34
- Java Development Kit (JDK) 17 or higher

### Build Steps

1. **Clone the repository** (if you haven't already):
   ```bash
   git clone <your-repo-url>
   cd <project-folder>
   ```

2. **Open in Android Studio**:
   - File → Open → Select the project folder
   - Wait for Gradle sync to complete

3. **Build the APK**:
   - Build → Build Bundle(s) / APK(s) → Build APK(s)
   - Wait for build to complete (2-5 minutes)
   - Click "locate" when the notification appears
   - APK will be in: `app/build/outputs/apk/debug/app-debug.apk`

4. **Install on device**:
   - Connect your Android TV/Firestick via ADB
   - Or transfer the APK manually and install

---

## Alternative Method - Command Line Build

If you prefer command line:

### Prerequisites
- Android SDK installed and configured
- `ANDROID_HOME` environment variable set
- Java JDK 17 or higher

### Build Commands

```bash
# Navigate to project directory
cd <project-folder>

# Make Gradle wrapper executable (Linux/Mac)
chmod +x gradlew

# Build debug APK
./gradlew assembleDebug

# Build release APK (requires signing configuration)
./gradlew assembleRelease

# Find your APK at:
# Debug: app/build/outputs/apk/debug/app-debug.apk
# Release: app/build/outputs/apk/release/app-release.apk
```

---

## Installing APK on Android TV/Firestick

### Method 1: Using ADB (Recommended for Developers)
```bash
# Enable ADB debugging on your device first
# Then connect and install:
adb connect <device-ip-address>
adb install app-debug.apk
```

### Method 2: File Manager (Easiest for End Users)
1. Transfer APK to device via:
   - USB drive
   - Network file sharing
   - Cloud storage (Dropbox, Google Drive)
2. Install a file manager app (e.g., "File Commander")
3. Enable "Unknown Sources" in device settings
4. Open file manager and navigate to APK
5. Click to install

### Method 3: Downloader App (Easiest for Firestick)
1. Install "Downloader" app from Amazon App Store
2. Enter a URL where your APK is hosted
3. Download and install directly

---

## Troubleshooting

### Build Fails with "SDK not found"
- Install Android SDK via Android Studio
- Set `ANDROID_HOME` environment variable
- Create `local.properties` with SDK path:
  ```
  sdk.dir=/path/to/your/android/sdk
  ```

### Build Fails with "Java version incompatible"
- Install JDK 17 or higher
- Set `JAVA_HOME` to correct JDK path

### "Gradle daemon timeout"
- Increase timeout in `gradle.properties`:
  ```
  org.gradle.daemon.idletimeout=10800000
  ```

### APK won't install on device
- Enable "Unknown Sources" in Settings → Security
- Check if device has enough storage space
- Uninstall previous version if upgrading

### GitHub Actions build fails
- Check the Actions tab for error logs
- Ensure `build.yml` is in `.github/workflows/`
- Verify branch name matches (main or master)

---

## What's Included in This Build

✅ **Core Features**:
- Media3 ExoPlayer for high-quality video playback
- Intelligent skip detection (intros, recaps, credits)
- D-Pad volume control (UP/DOWN)
- Metadata parsing from filenames
- Custom controller mappings for TV remotes
- Episode/title display in media controller

✅ **Skip Detection Strategies**:
- Chapter marker detection (highest priority)
- Metadata heuristics
- Community APIs (IntroHater, IntroSkipper)
- Manual user preferences (fallback)

✅ **Controller Features**:
- D-Pad navigation
- Volume control (UP/DOWN)
- Scrubbing (LEFT/RIGHT)
- Play/Pause controls
- Auto-hide overlay

⏳ **Future Features** (not yet implemented):
- Trakt.tv integration
- Debrid API integration
- Audio/subtitle track selection UI
- Stremio autoplay
- Playback speed indicator

---

## Testing Your Build

### Test Checklist
1. ✅ App launches successfully
2. ✅ Video loads and plays
3. ✅ D-Pad controls work (navigation, volume, scrubbing)
4. ✅ Skip buttons appear when detection succeeds
5. ✅ Episode info displays for TV shows
6. ✅ Controls auto-hide after timeout
7. ✅ Volume control doesn't interfere with playback

### Test Video Preparation
- Use filenames like: `Show.Name.S01E01.Episode.Title.mp4`
- Or provide videos with chapter markers
- Test with both TV shows and movies

---

## Need Help?

- Check the main `replit.md` for detailed project documentation
- Review code comments in `MainActivity.java` and `SmartSkipManager.java`
- Check GitHub Actions logs for build errors
- Ensure all dependencies are properly configured in `build.gradle`

---

**Good luck with your build!** 🚀
