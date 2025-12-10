@echo off
echo Cleaning Flutter project...
flutter clean

echo Updating Gradle properties...
echo android.abiFilters=armeabi-v7a,arm64-v8a >> android\gradle.properties

echo Building APK for ARM architectures only...
flutter build apk --debug --no-tree-shake-icons --target-platform android-arm,android-arm64

echo Build complete!
