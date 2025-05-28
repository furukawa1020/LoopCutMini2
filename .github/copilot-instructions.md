# LoopCut Mini - Copilot Instructions

<!-- Use this file to provide workspace-specific custom instructions to Copilot. For more details, visit https://code.visualstudio.com/docs/copilot/copilot-customization#_use-a-githubcopilotinstructionsmd-file -->

## Project Overview
LoopCut Mini is a React Native app with Kotlin native modules for mental health support through real-time negative speech detection using local Whisper AI.

## Key Components
- **Audio Processing**: Local Whisper.cpp integration for Japanese speech recognition
- **Negative Word Detection**: Real-time analysis of speech content using pattern matching
- **Privacy-First**: Zero data transmission, zero data storage - all processing local
- **Google Play Compliance**: Full compliance with audio recording policies

## Technical Stack
- React Native + TypeScript
- Kotlin native modules for Android
- Whisper.cpp for local speech-to-text
- No internet permissions - completely offline

## Code Guidelines
- Use TypeScript for all React Native code
- Use Kotlin for all Android native modules
- No data persistence beyond app session
- No external API calls or data transmission
- Follow Google Play audio recording policies strictly
- Implement proper foreground service for audio monitoring

## Key Features to Implement
1. Real-time Japanese speech monitoring using Whisper
2. Negative word pattern detection (3+ occurrences in 30s)
3. Vibration alerts with 3 intensity levels
4. Optional speech transcription display
5. Heads-up notifications with encouragement phrases
