#!/bin/bash

echo "Building TV Player APK..."

if [ ! -f "gradlew" ]; then
    echo "Error: gradlew not found"
    exit 1
fi

chmod +x gradlew

echo "Running Gradle build..."
./gradlew assembleDebug

if [ $? -eq 0 ]; then
    echo ""
    echo "Build successful!"
    echo "APK location: app/build/outputs/apk/debug/app-debug.apk"
    ls -lh app/build/outputs/apk/debug/app-debug.apk 2>/dev/null || echo "APK file not found at expected location"
else
    echo ""
    echo "Build failed. Check the errors above."
    exit 1
fi
