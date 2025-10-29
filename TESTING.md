# LoopCutMini2 Testing Guide

This document provides instructions for testing the LoopCutMini2 application, focusing on development builds and native module functionality verification.

## Prerequisites

- Android Studio installed
- Android device with USB debugging enabled
- Node.js and npm installed
- Git installed

## Local Development Build Setup

1. Clone the repository and checkout the feature branch:
   ```bash
   git clone https://github.com/furukawa1020/LoopCutMini2.git
   cd LoopCutMini2
   git checkout devin/1748406494-fix-build-errors
   ```

2. Install dependencies with legacy peer deps:
   ```bash
   npm install --legacy-peer-deps
   ```

3. Connect your Android device via USB and ensure it's recognized:
   ```bash
   adb devices
   ```

4. Run the development build:
   ```bash
   npx expo run:android
   ```

## Testing Native Module Functionality

### WhisperBridge (Audio Transcription)

The WhisperBridge module provides audio transcription using Whisper.cpp. To test:

1. Launch the app on your device
2. Grant microphone permissions when prompted
3. Navigate to the transcription screen
4. Speak clearly into the microphone
5. Verify that your speech is transcribed correctly
6. Check the app logs for any errors related to WhisperBridge initialization

Expected behavior:
- The app should initialize the Whisper model successfully
- Your speech should be transcribed with reasonable accuracy
- The transcription should appear on the screen

### LoopCutModule (Haptic Feedback)

The LoopCutModule provides haptic feedback and loop detection. To test:

1. Navigate to the loop detection screen
2. Start the loop detection service
3. Create repetitive sounds or speech patterns
4. Verify that the device vibrates when loops are detected
5. Check the app logs for any errors related to LoopCutModule

Expected behavior:
- The device should vibrate when repetitive patterns are detected
- The app should display visual feedback for detected loops
- The service should run in the background without crashing

## Troubleshooting Common Issues

### Model Loading Failures

If the Whisper model fails to load:
1. Check that the model file exists in the correct location
2. Verify that the app has storage permissions
3. Check logcat for specific error messages

### Audio Recording Issues

If audio recording doesn't work:
1. Ensure microphone permissions are granted
2. Check that no other app is using the microphone
3. Verify the audio recording service is running

### Loop Detection Issues

If loop detection doesn't work:
1. Ensure the service is started correctly
2. Check that vibration permissions are granted
3. Verify the loop detection algorithm parameters

## Reporting Issues

When reporting issues, please include:
1. Device model and Android version
2. Steps to reproduce the issue
3. Relevant logs from logcat
4. Screenshots or recordings if applicable
