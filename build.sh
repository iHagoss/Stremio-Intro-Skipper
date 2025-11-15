#!/bin/bash

echo "======================================"
echo "TV Player - Android TV App Build Script"
echo "======================================"
echo ""

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "ERROR: Java is not installed or not in PATH"
    echo "Please install Java JDK 11 or later"
    exit 1
fi

echo "Java version:"
java -version
echo ""

# Check if ANDROID_HOME is set
if [ -z "$ANDROID_HOME" ]; then
    echo "WARNING: ANDROID_HOME is not set"
    echo ""
    echo "To build Android apps, you need to install Android SDK:"
    echo ""
    echo "1. Download command line tools:"
    echo "   wget https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip"
    echo ""
    echo "2. Setup SDK:"
    echo "   mkdir -p ~/android-sdk/cmdline-tools"
    echo "   unzip commandlinetools-linux-9477386_latest.zip -d ~/android-sdk/cmdline-tools"
    echo "   cd ~/android-sdk/cmdline-tools && mkdir latest"
    echo "   mv bin lib NOTICE.txt source.properties latest/"
    echo ""
    echo "3. Set environment variables:"
    echo "   export ANDROID_HOME=~/android-sdk"
    echo "   export PATH=\$ANDROID_HOME/cmdline-tools/latest/bin:\$PATH"
    echo "   export PATH=\$ANDROID_HOME/platform-tools:\$PATH"
    echo ""
    echo "4. Install SDK packages:"
    echo "   yes | sdkmanager --licenses"
    echo "   sdkmanager \"platform-tools\" \"platforms;android-34\" \"build-tools;34.0.0\""
    echo ""
    echo "After setup, run this script again to build the APK."
    exit 1
fi

echo "ANDROID_HOME: $ANDROID_HOME"
echo ""

# Make gradlew executable
chmod +x gradlew

# Build the app
echo "Building debug APK..."
./gradlew assembleDebug

if [ $? -eq 0 ]; then
    echo ""
    echo "======================================"
    echo "Build successful!"
    echo "======================================"
    echo ""
    echo "APK location: app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    echo "To install on Android TV:"
    echo "  adb connect YOUR_TV_IP:5555"
    echo "  adb install app/build/outputs/apk/debug/app-debug.apk"
    echo "  adb shell am start -n com.tvplayer.app/.MainActivity"
    echo ""
else
    echo ""
    echo "Build failed. Check the error messages above."
    exit 1
fi
