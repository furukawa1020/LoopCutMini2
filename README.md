# LoopCutMini2

LoopCutMini2 is an Expo React Native application with native modules for audio transcription and loop detection.

## Important: Development Build Required

**This app contains native modules (WhisperBridge, LoopCutModule) that are NOT compatible with Expo Go.**

You must use a development build to run this application.

## Features

- Audio transcription using Whisper.cpp
- Loop detection for repetitive audio patterns
- Haptic feedback for detected loops
- Background audio processing service

## Getting Started

1. Install dependencies with legacy peer deps:

   ```bash
   npm install --legacy-peer-deps
   ```

2. Run the development build on Android:

   ```bash
   npx expo run:android
   ```

   Note: You cannot use `expo start` with Expo Go for this project.

## Native Module Requirements

This project includes native modules that require:

- Android SDK with NDK support
- CMake for native code compilation
- Physical device for testing audio and haptic feedback

## Documentation

For detailed information, see:

- [Testing Guide](./TESTING.md): Instructions for testing the app and native modules
- [Deployment Guide](./DEPLOYMENT.md): Steps for preparing and deploying to Google Play Store

## Development

You can start developing by editing the files inside the **app** directory. This project uses [file-based routing](https://docs.expo.dev/router/introduction).

## Troubleshooting

If you encounter issues with the native modules:

1. Ensure you're using a development build, not Expo Go
2. Check that all required permissions are granted
3. Verify that the Whisper model file is correctly included
4. See the [Testing Guide](./TESTING.md) for more troubleshooting tips

## Learn More

- [Expo documentation](https://docs.expo.dev/)
- [React Native documentation](https://reactnative.dev/)
- [Whisper.cpp](https://github.com/ggerganov/whisper.cpp)
