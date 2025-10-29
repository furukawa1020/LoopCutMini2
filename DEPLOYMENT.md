# LoopCutMini2 Deployment Guide

This document provides instructions for preparing and deploying the LoopCutMini2 application to the Google Play Store.

## Prerequisites

- Android Studio installed
- Google Play Console account
- Keystore for signing the app
- Node.js and npm installed

## Keystore Generation

Before creating a release build, you need to generate a keystore file:

1. Open a terminal and navigate to your project directory
2. Run the following command to generate a keystore:
   ```bash
   keytool -genkey -v -keystore loopcutmini.keystore -alias loopcutmini -keyalg RSA -keysize 2048 -validity 10000
   ```
3. Follow the prompts to enter your information
4. Move the generated keystore file to the `android/app` directory

**IMPORTANT**: Keep your keystore file and passwords secure. If you lose them, you won't be able to update your app on the Play Store.

## Building a Release APK

### Method 1: Using Environment Variables

1. Set the environment variables for your keystore:
   ```bash
   export KEYSTORE_FILE=path/to/loopcutmini.keystore
   export KEYSTORE_PASSWORD=your-keystore-password
   export KEY_ALIAS=loopcutmini
   export KEY_PASSWORD=your-key-password
   ```

2. Build the release APK:
   ```bash
   cd android
   ./gradlew assembleRelease
   ```

### Method 2: Modifying build.gradle

1. Open `android/app/build.gradle`
2. Modify the signingConfigs section with your keystore information:
   ```gradle
   signingConfigs {
       release {
           storeFile file("loopcutmini.keystore")
           storePassword "your-keystore-password"
           keyAlias "loopcutmini"
           keyPassword "your-key-password"
       }
   }
   ```

3. Build the release APK:
   ```bash
   cd android
   ./gradlew assembleRelease
   ```

The release APK will be generated at `android/app/build/outputs/apk/release/app-release.apk`

## Testing the Release Build

Before uploading to the Play Store:

1. Install the release APK on your device:
   ```bash
   adb install android/app/build/outputs/apk/release/app-release.apk
   ```

2. Test all functionality to ensure it works correctly in the release build
3. Verify that native modules (WhisperBridge, LoopCutModule) function properly
4. Check for any performance issues or crashes

## Google Play Store Preparation

### App Metadata

Prepare the following information for your Play Store listing:

1. App title: "LoopCutMini2"
2. Short description (80 characters max)
3. Full description (4000 characters max)
4. App category (e.g., Tools, Productivity)
5. Content rating information
6. Privacy policy URL

### Graphics Assets

Create and prepare the following graphics:

1. App icon (512x512 PNG)
2. Feature graphic (1024x500 PNG)
3. Phone screenshots (minimum 2)
4. 7-inch tablet screenshots (if supporting tablets)
5. 10-inch tablet screenshots (if supporting tablets)

### App Bundle or APK

For the Play Store, you can upload either:

1. Android App Bundle (recommended):
   ```bash
   cd android
   ./gradlew bundleRelease
   ```
   The bundle will be at `android/app/build/outputs/bundle/release/app-release.aab`

2. APK file (as built in the previous section)

## Uploading to Google Play Console

1. Log in to the [Google Play Console](https://play.google.com/console)
2. Create a new app or select your existing app
3. Navigate to "Production" > "Create new release"
4. Upload your AAB or APK file
5. Fill in the release notes
6. Complete the store listing with your prepared metadata and graphics
7. Set up pricing and distribution
8. Submit for review

## Post-Release Monitoring

After your app is published:

1. Monitor crash reports in the Play Console
2. Check user reviews and feedback
3. Prepare updates to address any issues

## Updating the App

For future updates:

1. Increment the `versionCode` and update `versionName` in `android/app/build.gradle`
2. Make your code changes
3. Build a new release APK or bundle
4. Create a new release in the Play Console
5. Upload the new APK or bundle
6. Submit for review
