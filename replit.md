# Android TV Media Player - Project Documentation

## Project Overview
An Android TV/Firestick media player application built with Media3 (ExoPlayer) featuring intelligent skip detection for intros, recaps, and credits. The app includes custom D-Pad controller mappings, metadata parsing from filenames, and integration support for external services.

**Target Platform:** Android TV, Amazon Firestick  
**Build System:** Gradle 8.10.2  
**Primary Language:** Java  
**Minimum SDK:** 21 (Android 5.0)  
**Target SDK:** 34 (Android 14)

## Recent Changes (November 15, 2025)

### ✅ Core Features Implemented

#### 1. GitHub Actions Build Workflow
- **File:** `.github/workflows/build.yml`
- Fixed branch configuration to trigger on `main` and `master` branches
- Added Gradle dependency caching for faster builds
- Configured both debug and release APK builds
- APK artifacts uploaded automatically on successful build

#### 2. Intelligent Skip Detection System
- **File:** `app/src/main/java/com/tvplayer/app/skipdetection/SmartSkipManager.java`
- Implemented tiered priority system with concurrent strategy execution
- Priority Tiers (highest to lowest):
  - **Tier 500:** Chapter Markers & Metadata Heuristics (most reliable)
  - **Tier 400:** Community APIs (IntroHater, IntroSkipper)
  - **Tier 300:** Audio Fingerprinting (placeholder for future implementation)
  - **Tier 100:** Manual User Preferences (final failsafe)
- Higher tier results always supersede lower tier results regardless of confidence
- All strategies run concurrently for optimal performance
- Winner selection happens in synchronized block after all results collected

#### 3. Media Metadata Extraction
- **File:** `app/src/main/java/com/tvplayer/app/MediaMetadataParser.java`
- Parses season/episode information from video filenames
- Supports multiple formats: `S01E01`, `1x01`, `s1e1`, etc.
- Extracts title from filename (removes file extension and metadata)
- Used to populate `MediaIdentifier` for proper skip detection

#### 4. Enhanced Media Controller UI
- **File:** `app/src/main/res/layout/activity_main.xml`
- Added title and episode info display between time clocks
- TextViews default to hidden and only show when valid data available
- Prevents incorrect display for movies or missing metadata
- Marquee scrolling enabled for long titles

#### 5. D-Pad Volume Control
- **File:** `app/src/main/java/com/tvplayer/app/MainActivity.java`
- D-Pad UP: Increase volume
- D-Pad DOWN: Decrease volume
- Uses `AudioManager` to adjust MUSIC stream independently from system volume
- Non-intrusive Toast indicator shows current volume percentage
- Does not interfere with playback or pause video
- Smart navigation: allows normal D-Pad navigation when controls/skip buttons visible

### 📋 Architecture Details

#### Skip Detection Strategy Flow
```
1. User plays video → MainActivity.startSkipDetection()
2. Extract metadata from filename → MediaMetadataParser.parse()
3. Build MediaIdentifier with all available data (title, season, episode, duration, chapters)
4. Launch concurrent strategy execution
5. All strategies execute simultaneously in separate threads
6. Results collected and sorted by tier priority
7. Highest tier result with valid segments wins
8. Skip buttons displayed based on winning result
```

#### Volume Control Flow
```
1. User presses D-Pad UP/DOWN
2. Check if controls/skip buttons are hidden
3. If hidden → Adjust volume using AudioManager.adjustStreamVolume()
4. Show Toast indicator with volume percentage
5. If visible → Let system handle navigation (button focus)
```

## Project Structure

```
.
├── .github/
│   └── workflows/
│       └── build.yml              # GitHub Actions build workflow
├── app/
│   ├── build.gradle               # App-level Gradle configuration
│   └── src/main/
│       ├── java/com/tvplayer/app/
│       │   ├── MainActivity.java              # Main activity with player
│       │   ├── ApiService.java                # HTTP client for external APIs
│       │   ├── MediaMetadataParser.java       # Filename parsing utility
│       │   ├── PreferencesHelper.java         # User preferences manager
│       │   ├── SkipMarkers.java               # Skip markers data structure
│       │   └── skipdetection/
│       │       ├── SmartSkipManager.java      # Main skip detection coordinator
│       │       ├── MediaIdentifier.java       # Media metadata container
│       │       ├── SkipDetectionCallback.java # Callback interface
│       │       ├── SkipDetectionResult.java   # Detection results container
│       │       ├── SkipDetectionStrategy.java # Strategy interface
│       │       └── strategies/
│       │           ├── AudioFingerprintStrategy.java  # Audio fingerprint detection
│       │           ├── CacheStrategy.java             # Cache lookup
│       │           ├── ChapterStrategy.java           # Chapter marker detection
│       │           ├── IntroHaterStrategy.java        # IntroHater API client
│       │           ├── IntroSkipperStrategy.java      # IntroSkipper API client
│       │           ├── ManualPreferenceStrategy.java  # Manual user preferences
│       │           └── MetadataHeuristicStrategy.java # Metadata-based detection
│       └── res/
│           └── layout/
│               └── activity_main.xml          # Main UI layout
├── gradle/wrapper/                            # Gradle wrapper files
├── build.gradle                               # Root Gradle configuration
├── settings.gradle                            # Gradle settings
└── replit.md                                  # This file
```

## Building the Application

### Option 1: GitHub Actions (Recommended)
1. Push changes to `main` or `master` branch
2. GitHub Actions will automatically build the APK
3. Download the APK artifact from the Actions tab
4. Install on your Android TV/Firestick device

### Option 2: Local Build
```bash
# Make Gradle wrapper executable
chmod +x gradlew

# Build debug APK
./gradlew assembleDebug

# Build release APK (requires signing configuration)
./gradlew assembleRelease

# Output location
# Debug: app/build/outputs/apk/debug/app-debug.apk
# Release: app/build/outputs/apk/release/app-release.apk
```

### Option 3: Android Studio
1. Open project in Android Studio
2. Build → Build Bundle(s) / APK(s) → Build APK(s)
3. Locate APK in `app/build/outputs/apk/`

## User Preferences

The app supports the following user preferences (stored in SharedPreferences):

### Manual Skip Timing Preferences
- `pref_intro_start` / `pref_intro_end` - Manual intro skip times (seconds)
- `pref_recap_start` / `pref_recap_end` - Manual recap skip times (seconds)
- `pref_credits_start` / `pref_credits_end` - Manual credits skip times (seconds)

### API Configuration
- `pref_introhater_enabled` - Enable/disable IntroHater API
- `pref_introskipper_enabled` - Enable/disable IntroSkipper API
- `pref_trakt_access_token` - Trakt.tv authentication token
- `pref_debrid_api_key` - Debrid service API key

### Playback Settings
- Standard ExoPlayer preferences managed through PreferenceManager

## Controller Mappings

### D-Pad Controls
- **UP/DOWN:** Volume control (when controls hidden)
- **LEFT/RIGHT:** Rewind/Fast-forward (5-second scrub)
- **CENTER/ENTER:** Toggle controls overlay

### Media Keys
- **Play/Pause:** Toggle playback
- **Fast Forward:** Scrub forward
- **Rewind:** Scrub backward

### Skip Buttons (Auto-displayed)
- **Skip Intro:** Jump past opening credits
- **Skip Recap:** Skip "previously on" segments
- **Skip Credits:** Jump to next episode
- **Next Episode:** Load next episode (requires Stremio integration)
- **Cancel:** Dismiss skip button overlay

## Known Limitations & Future Work

### Not Yet Implemented
1. **Trakt.tv Integration** - OAuth device flow and playback syncing
2. **Debrid API Integration** - Full server communication implementation
3. **Track Selection UI** - Audio/subtitle language and video quality menus
4. **Stremio Autoplay** - Automatic next episode loading
5. **Playback Speed UI** - Dynamic speed indicator on button
6. **Audio Fingerprinting** - Strategy currently returns empty results

### Technical Constraints
- Replit environment lacks Android SDK - build via GitHub Actions or locally
- Chapter marker detection requires proper chapter metadata in video files
- Community API strategies require internet connection
- Manual preferences used as fallback when no metadata available

## Dependencies

### Core Libraries
- `androidx.media3:media3-exoplayer:1.5.0` - Media playback engine
- `androidx.media3:media3-ui:1.5.0` - Player UI components
- `com.squareup.okhttp3:okhttp:4.12.0` - HTTP client
- `com.google.code.gson:gson:2.10.1` - JSON parsing

### Android Support
- `androidx.appcompat:appcompat:1.7.0`
- `com.google.android.material:material:1.12.0`
- `androidx.constraintlayout:constraintlayout:2.2.0`

## Testing

To test the application:
1. Prepare test videos with standard episode naming (e.g., `Show.Name.S01E01.mp4`)
2. Install APK on Android TV/Firestick device
3. Grant necessary permissions (storage access)
4. Load video via file picker or external intent
5. Test skip detection, volume control, and metadata display

## Support & Troubleshooting

### Common Issues
- **Build timeouts in Replit:** Use GitHub Actions or local build environment
- **Skip detection not working:** Ensure video has proper filename format or chapter markers
- **Volume control not responding:** Check that controls overlay is hidden (press BACK to hide)
- **Episode info not showing:** Verify filename matches supported format patterns

## Changelog

### 2025-11-15
- ✅ Fixed GitHub Actions build workflow configuration
- ✅ Implemented tiered smart skip detection system
- ✅ Added MediaMetadataParser for filename parsing
- ✅ Enhanced media controller with title/episode display
- ✅ Implemented D-Pad volume control with non-intrusive indicator
- ✅ Fixed media info TextView defaults to prevent incorrect display
- ✅ Clarified concurrent strategy execution in SmartSkipManager

---

*Last updated: November 15, 2025*
