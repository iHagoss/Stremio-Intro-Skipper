# TV Player - Android ExoPlayer App for Firestick

A complete Android TV application with ExoPlayer for Amazon Firestick and Android TV devices. Features custom player controls, configurable skip functionality (intro, recap, credits), audio/subtitle delay controls, and API key management.

## Features

### Custom Player Controls
- **Comprehensive Time Display**:
  - Current time of day (CTOD)
  - Elapsed playtime
  - Total runtime
  - Remaining runtime
  - Projected finish time
- **Media Controls**: Play/Pause, Rewind 10s, Fast Forward 30s, Previous, Next
- **Auto-hide controls** after 5 seconds of inactivity
- **D-pad navigation** optimized for TV remote control

### Skip Functionality
- **Skip Intro**: Automatically skip show intros
- **Skip Recap**: Skip "previously on" recaps
- **Skip Credits**: Skip end credits
- **Next Episode**: Prompt to play next episode
- **Manual timing configuration** - set start/end seconds for each skip type
- **Auto-skip toggles** - enable automatic skipping
- **Skip buttons appear** during configured time ranges

### Audio/Subtitle Sync
- **Audio delay adjustment**: -500ms to +500ms (UI and storage complete)
- **Subtitle delay adjustment**: -500ms to +500ms (UI and storage complete)
- Quick adjustment dialogs with preset values
- Values persist across restarts
- **Note**: Delays are stored but **cannot be applied to playback** due to Media3 limitations (TextRenderer is final)
- **Recommended**: Use external players like VLC or MX Player for delay functionality

### Settings & Configuration
- **API Keys Management**:
  - Trakt API Key
  - TMDB API Key
  - TVDB API Key
- **Debrid Servers**:
  - Real-Debrid API Key
  - TorBox API Key
  - AllDebrid API Key
- **Skip Timings**: Manual entry for intro, recap, credits, next episode (in seconds)
- **All settings persist** across app restarts via SharedPreferences
- **No rebuild required** to change any configuration

### External Player Support
- Compatible with **Stremio** and **Syncler+** integration
- Handles VIEW intents with video URLs
- Supports http:// and https:// video streams
- Seamless external player handoff

## Build Requirements

### Prerequisites
1. **Android SDK** (API Level 33)
2. **Java JDK 11** or higher
3. **Gradle 7.5** (included via wrapper)

### Android SDK Setup (if not installed)

#### On Linux/Replit:
```bash
# Download Android command line tools
wget https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip

# Setup SDK directory
mkdir -p ~/android-sdk/cmdline-tools
unzip commandlinetools-linux-9477386_latest.zip -d ~/android-sdk/cmdline-tools
cd ~/android-sdk/cmdline-tools
mkdir latest
mv cmdline-tools/* latest/ 2>/dev/null || mv bin lib NOTICE.txt source.properties latest/

# Set environment variables (add to ~/.bashrc for persistence)
export ANDROID_HOME=~/android-sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$PATH
export PATH=$ANDROID_HOME/platform-tools:$PATH

# Accept licenses
yes | sdkmanager --licenses

# Install required SDK components
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

#### On macOS:
```bash
brew install android-sdk
export ANDROID_HOME=/usr/local/share/android-sdk
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

#### On Windows:
Download Android Studio or Android SDK command line tools from:
https://developer.android.com/studio

## Building the APK

### Quick Build (using build script):
```bash
chmod +x build.sh
./build.sh
```

### Manual Build:
```bash
chmod +x gradlew
./gradlew assembleDebug
```

### Build Output
APK location: `app/build/outputs/apk/debug/app-debug.apk`

## Installation

### Install on Firestick/Android TV:

#### Method 1: ADB (Android Debug Bridge)
```bash
# Enable ADB on Firestick: Settings > My Fire TV > Developer Options > ADB Debugging

# Connect via IP address
adb connect <firestick-ip-address>:5555

# Install APK
adb install app/build/outputs/apk/debug/app-debug.apk
```

#### Method 2: USB Install
1. Copy APK to USB drive
2. Install a file manager on Firestick (e.g., "X-plore File Manager")
3. Enable "Install from Unknown Sources"
4. Navigate to APK and install

#### Method 3: Downloader App
1. Install "Downloader" app on Firestick
2. Upload APK to a web server or cloud storage
3. Enter URL in Downloader and install

## Configuration

### Setting Up Skip Timings
1. Open the app on your TV
2. Press the **Settings** button (gear icon) in the player controls
3. Navigate to "Skip Timings" section
4. Enter start and end times (in seconds) for:
   - Intro Start/End
   - Recap Start/End
   - Credits Start/End
   - Next Episode Start
5. Enable auto-skip toggles if desired

### Adding API Keys
1. Open Settings
2. Navigate to "API Keys" section
3. Enter your API keys for:
   - Trakt (get from https://trakt.tv/oauth/applications)
   - TMDB (get from https://www.themoviedb.org/settings/api)
   - TVDB (get from https://thetvdb.com/api-information)
4. Add debrid server keys as needed

### Adjusting Audio/Subtitle Delay
During playback:
1. Show controls (tap screen or press OK/Select on remote)
2. Press the **Audio Delay** or **Subtitle Delay** button
3. Select preset delay value (-500ms to +500ms)

## Usage

### Standalone Mode
Launch from Android TV home screen (Leanback launcher)

### External Player Mode (Stremio/Syncler)
1. Install app on Firestick/Android TV
2. In Stremio or Syncler, select a video
3. Choose "Play with" or "External Player"
4. Select "TV Player" from the list
5. Video plays with skip functionality

### Player Controls
- **Click/Select**: Show/hide controls
- **Play/Pause**: Toggle playback
- **Rewind**: Seek backward 10 seconds
- **Fast Forward**: Seek forward 30 seconds
- **Previous**: Restart video
- **Next**: Close player
- **Skip Buttons**: Appear automatically during configured time ranges

## Project Structure

```

TVPlayer/
├── .github/
│   └── workflows/
│       ├── build.yml
│       └── unzip.yml
├── app/
│   ├── build.gradle                 # App-level Gradle configuration
│   ├── proguard-rules.pro
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml  # App manifest with permissions
│           ├── java/
│           │   └── com/
│           │       └── tvplayer/
│           │           └── app/
│           │               ├── MainActivity.java          # Main player activity
│           │               ├── PreferencesHelper.java     # SharedPreferences wrapper
│           │               ├── SettingsActivity.java      # Settings screen
│           │               ├── SkipMarkers.java           # Skip timing data model
│           │               └── skipdetection/
│           │                   ├── MediaIdentifier.java
│           │                   ├── SkipDetectionCallback.java
│           │                   ├── SkipDetectionResult.java
│           │                   ├── SkipDetectionStrategy.java
│           │                   ├── SmartSkipManager.java
│           │                   └── strategies/
│           │                       ├── CacheStrategy.java
│           │                       ├── ChapterStrategy.java
│           │                       ├── IntroHaterStrategy.java
│           │                       ├── IntroSkipperStrategy.java
│           │                       ├── ManualPreferenceStrategy.java
│           │                       └── MetadataHeuristicStrategy.java
│           └── res/
│               ├── drawable/
│               │   ├── control_button_background.xml
│               │   ├── ic_launcher_foreground.xml
│               │   └── skip_button_background.xml
│               ├── layout/
│               │   ├── activity_main.xml
│               │   └── activity_settings.xml
│               ├── mipmap-anydpi-v26/
│               │   └── ic_launcher.xml
│               ├── values/
│               │   ├── colors.xml
│               │   ├── strings.xml
│               │   └── styles.xml
│               └── xml/
│                   └── preferences.xml
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── .gitignore
├── build.gradle                     # Project-level Gradle configuration
├── build.sh                         # Build script
├── gradle.properties
├── gradlew                          # Gradle wrapper executable (Linux/macOS)
├── gradlew.bat                      # Gradle wrapper executable (Windows)
├── README.md                        # Project documentation
└── settings.gradle                  # Defines project modules

```

## Technical Details

### Specifications
- **Target SDK**: API 35 (Android 15)
- **Minimum SDK**: API 22 (Android 5.1 - Firestick compatible)
- **Build System**: Gradle 8.10.2
- **Language**: Java 11
- **Media Framework**: AndroidX Media3 (ExoPlayer) 1.1.1

### Dependencies
- ExoPlayer (androidx.media3) - Video playback
- AndroidX AppCompat - Backward compatibility
- Material Design Components - UI elements
- AndroidX Leanback - TV optimization
- AndroidX Preference - Settings UI
- OkHttp - Network requests
- Gson - JSON parsing

### Permissions
- `INTERNET` - Stream video content
- `ACCESS_NETWORK_STATE` - Check connectivity

## Troubleshooting

### Build fails with "SDK not found"
Set ANDROID_HOME environment variable:
```bash
export ANDROID_HOME=~/android-sdk
```

### APK won't install on Firestick
Enable "Install from Unknown Sources" in Firestick settings:
Settings > My Fire TV > Developer Options > Apps from Unknown Sources

### Controls don't appear
- Click/tap the screen or press OK/Select on your remote
- Controls auto-hide after 5 seconds

### Skip buttons don't show
- Verify skip timings are configured in Settings
- Ensure current playback position is within configured time range
- Skip timings are in seconds, not minutes

### External player not recognized
Ensure the app is installed and appears in the system's list of video players

## Development

### Modifying the App
1. Edit source files in `app/src/main/java/com/tvplayer/app/`
2. Modify layouts in `app/src/main/res/layout/`
3. Update strings in `app/src/main/res/values/strings.xml`
4. Rebuild: `./build.sh`

### Adding Features
- Skip timings are loaded from SharedPreferences in `PreferencesHelper`
- Add new preferences in `app/src/main/res/xml/preferences.xml`
- Player logic is in `MainActivity.java`

## License

This project is provided as-is for personal use.

## Support

For issues or questions:
1. Check skip timing configuration in Settings
2. Verify API keys are entered correctly
3. Ensure video URL is valid (http/https)
4. Check Firestick has internet connectivity

## Credits

Built with:
- ExoPlayer (Google)
- AndroidX Libraries (Google)
- Material Design Components (Google)

Here is the updated README reflecting your new Gradle build, project structure, and specifications accurately:

***

# TV Player - Android ExoPlayer App for Firestick

A complete Android TV application with ExoPlayer for Amazon Firestick and Android TV devices. Features custom player controls, configurable skip functionality (intro, recap, credits), audio/subtitle delay controls, and API key management.

## Features

### Custom Player Controls
- **Comprehensive Time Display**:
  - Current time of day (CTOD)
  - Elapsed playtime
  - Total runtime
  - Remaining runtime
  - Projected finish time
- **Media Controls**: Play/Pause, Rewind 10s, Fast Forward 30s, Previous, Next
- **Auto-hide controls** after 5 seconds of inactivity
- **D-pad navigation** optimized for TV remote control

### Skip Functionality
- **Skip Intro**: Automatically skip show intros
- **Skip Recap**: Skip "previously on" recaps
- **Skip Credits**: Skip end credits
- **Next Episode**: Prompt to play next episode
- **Manual timing configuration** - set start/end seconds for each skip type
- **Auto-skip toggles** - enable automatic skipping
- **Skip buttons appear** during configured time ranges

### Audio/Subtitle Sync
- **Audio delay adjustment**: -500ms to +500ms (UI and storage complete)
- **Subtitle delay adjustment**: -500ms to +500ms (UI and storage complete)
- Quick adjustment dialogs with preset values
- Values persist across restarts
- **Note**: Delays are stored but **cannot be applied to playback** due to Media3 limitations (TextRenderer is final)
- **Recommended**: Use external players like VLC or MX Player for delay functionality

### Settings & Configuration
- **API Keys Management**:
  - Trakt API Key
  - TMDB API Key
  - TVDB API Key
- **Debrid Servers**:
  - Real-Debrid API Key
  - TorBox API Key
  - AllDebrid API Key
- **Skip Timings**: Manual entry for intro, recap, credits, next episode (in seconds)
- **All settings persist** across app restarts via SharedPreferences
- **No rebuild required** to change any configuration

### External Player Support
- Compatible with **Stremio** and **Syncler+** integration
- Handles VIEW intents with video URLs
- Supports http:// and https:// video streams
- Seamless external player handoff

## Build Requirements

### Prerequisites
1. **Android SDK** (API Level 35)
2. **Java JDK 11** or higher
3. **Gradle 8.10.2** (included via wrapper)

### Android SDK Setup (if not installed)

#### On Linux/Replit:
```bash
# Download Android command line tools
wget https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip

# Setup SDK directory
mkdir -p ~/android-sdk/cmdline-tools
unzip commandlinetools-linux-9477386_latest.zip -d ~/android-sdk/cmdline-tools
cd ~/android-sdk/cmdline-tools
mkdir latest
mv cmdline-tools/* latest/ 2>/dev/null || mv bin lib NOTICE.txt source.properties latest/

# Set environment variables (add to ~/.bashrc for persistence)
export ANDROID_HOME=~/android-sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$PATH
export PATH=$ANDROID_HOME/platform-tools:$PATH

# Accept licenses
yes | sdkmanager --licenses

# Install required SDK components
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

#### On macOS:
```bash
brew install android-sdk
export ANDROID_HOME=/usr/local/share/android-sdk
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

#### On Windows:
Download Android Studio or Android SDK command line tools from:
https://developer.android.com/studio

## Building the APK

### Quick Build (using build script):
```bash
chmod +x build.sh
./build.sh
```

### Manual Build:
```bash
chmod +x gradlew
./gradlew assembleDebug
```

### Build Output
APK location: `app/build/outputs/apk/debug/app-debug.apk`

## Installation

### Install on Firestick/Android TV:

#### Method 1: ADB (Android Debug Bridge)
```bash
# Enable ADB on Firestick: Settings > My Fire TV > Developer Options > ADB Debugging

# Connect via IP address
adb connect <firestick-ip-address>:5555

# Install APK
adb install app/build/outputs/apk/debug/app-debug.apk
```

#### Method 2: USB Install
1. Copy APK to USB drive
2. Install a file manager on Firestick (e.g., "X-plore File Manager")
3. Enable "Install from Unknown Sources"
4. Navigate to APK and install

#### Method 3: Downloader App
1. Install "Downloader" app on Firestick
2. Upload APK to web server or cloud storage
3. Enter URL in Downloader and install

## Configuration

### Setting Up Skip Timings
1. Open the app on your TV
2. Press the **Settings** button (gear icon) in the player controls
3. Navigate to "Skip Timings" section
4. Enter start and end times (in seconds) for:
   - Intro Start/End
   - Recap Start/End
   - Credits Start/End
   - Next Episode Start
5. Enable auto-skip toggles if desired

### Adding API Keys
1. Open Settings
2. Navigate to "API Keys" section
3. Enter your API keys for:
   - Trakt (https://trakt.tv/oauth/applications)
   - TMDB (https://www.themoviedb.org/settings/api)
   - TVDB (https://thetvdb.com/api-information)
4. Add debrid server keys as needed

### Adjusting Audio/Subtitle Delay
During playback:
1. Show controls (tap screen or press OK/Select on remote)
2. Press the **Audio Delay** or **Subtitle Delay** button
3. Select preset delay value (-500ms to +500ms)

## Usage

### Standalone Mode
Launch from Android TV home screen (Leanback launcher)

### External Player Mode (Stremio/Syncler)
1. Install app on Firestick/Android TV
2. In Stremio or Syncler, select a video
3. Choose "Play with" or "External Player"
4. Select "TV Player" from the list
5. Video plays with skip functionality

### Player Controls
- **Click/Select**: Show/hide controls
- **Play/Pause**: Toggle playback
- **Rewind**: Seek backward 10 seconds
- **Fast Forward**: Seek forward 30 seconds
- **Previous**: Restart video
- **Next**: Close player
- **Skip Buttons**: Appear automatically during configured time ranges

## Project Structure

```
TVPlayer/
├── .github/
│   └── workflows/
│       ├── build.yml
│       └── unzip.yml
├── app/
│   ├── build.gradle                 # App-level Gradle configuration
│   ├── proguard-rules.pro
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml  # App manifest with permissions
│           ├── java/
│           │   └── com/
│           │       └── tvplayer/
│           │           └── app/
│           │               ├── MainActivity.java          # Main player activity
│           │               ├── PreferencesHelper.java     # SharedPreferences wrapper
│           │               ├── SettingsActivity.java      # Settings screen
│           │               ├── SkipMarkers.java           # Skip timing data model
│           │               └── skipdetection/
│           │                   ├── MediaIdentifier.java
│           │                   ├── SkipDetectionCallback.java
│           │                   ├── SkipDetectionResult.java
│           │                   ├── SkipDetectionStrategy.java
│           │                   ├── SmartSkipManager.java
│           │                   └── strategies/
│           │                       ├── CacheStrategy.java
│           │                       ├── ChapterStrategy.java
│           │                       ├── IntroHaterStrategy.java
│           │                       ├── IntroSkipperStrategy.java
│           │                       ├── ManualPreferenceStrategy.java
│           │                       └── MetadataHeuristicStrategy.java
│           └── res/
│               ├── drawable/
│               │   ├── control_button_background.xml
│               │   ├── ic_launcher_foreground.xml
│               │   └── skip_button_background.xml
│               ├── layout/
│               │   ├── activity_main.xml
│               │   └── activity_settings.xml
│               ├── mipmap-anydpi-v26/
│               │   └── ic_launcher.xml
│               ├── values/
│               │   ├── colors.xml
│               │   ├── strings.xml
│               │   └── styles.xml
│               └── xml/
│                   └── preferences.xml
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── .gitignore
├── build.gradle                     # Project-level Gradle configuration
├── build.sh                         # Build script
├── gradle.properties
├── gradlew                          # Gradle wrapper executable (Linux/macOS)
├── gradlew.bat                      # Gradle wrapper executable (Windows)
├── README.md                        # Project documentation
└── settings.gradle                  # Defines project modules
```

## Technical Details

### Specifications
- **Target SDK**: API 35 (Android 15)
- **Minimum SDK**: API 22 (Android 5.1 - Firestick compatible)
- **Build System**: Gradle 8.10.2
- **Language**: Java 11
- **Media Framework**: AndroidX Media3 (ExoPlayer) 1.7.1

### Dependencies
- AndroidX Media3 components (ExoPlayer) 1.7.1 - Video playback
- AndroidX AppCompat 1.6.1 - Backward compatibility
- Google Material Design Components 1.9.0 - UI elements
- AndroidX Leanback 1.1.0-rc02 - TV optimization
- AndroidX Preference 1.2.1 - Settings UI
- OkHttp 4.11.0 - Network requests
- Gson 2.10.1 - JSON parsing

### Permissions
- `INTERNET` - Stream video content
- `ACCESS_NETWORK_STATE` - Check connectivity

## Troubleshooting

### Build fails with "SDK not found"
Set ANDROID_HOME environment variable:
```bash
export ANDROID_HOME=~/android-sdk
```

### APK won't install on Firestick
Enable "Install from Unknown Sources" in Firestick settings:
Settings > My Fire TV > Developer Options > Apps from Unknown Sources

### Controls don't appear
- Click/tap the screen or press OK/Select on your remote
- Controls auto-hide after 5 seconds

### Skip buttons don't show
- Verify skip timings are configured in Settings
- Ensure current playback position is within configured time range
- Skip timings are in seconds, not minutes

### External player not recognized
Ensure the app is installed and appears in the system's list of video players

## Development

### Modifying the App
1. Edit source files in `app/src/main/java/com/tvplayer/app/`
2. Modify layouts in `app/src/main/res/layout/`
3. Update strings in `app/src/main/res/values/strings.xml`
4. Rebuild: `./build.sh`

### Adding Features
- Skip timings are loaded from SharedPreferences in `PreferencesHelper`
- Add new preferences in `app/src/main/res/xml/preferences.xml`
- Player logic is in `MainActivity.java`

## License

This project is provided as-is for personal use.

## Support

For issues or questions:
1. Verify skip timing configuration in Settings
2. Ensure API keys are correct
3. Confirm video URL is valid (http/https)
4. Check Firestick internet connectivity

## Credits

Built with:
- ExoPlayer (Google)
- AndroidX Libraries (Google)
- Material Design Components (Google)
