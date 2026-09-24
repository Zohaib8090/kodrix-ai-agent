# Kodrix AI Agent Project Documentation

## Architecture Overview
Kodrix is an Android application built with **Kotlin** and **Jetpack Compose**. It serves as an AI-powered IDE/coding agent that runs directly on an Android device.

### Core Systems
1. **AI Provider Engine (`BaseAiProvider.kt` & `ConcreteProviders.kt`)**
   - Supports multiple LLM providers (Gemini, OpenAI, Anthropic, DeepSeek, Groq, Mistral, xAI, OpenRouter, etc.).
   - Communicates via OpenAI-compatible REST APIs and specialized endpoints (like Gemini's `generativelanguage.googleapis.com/v1beta/models`).
   - Supports **Vision** (via `screenshot` payloads) and dynamic model fetching.
   - Requires strict system prompts to output code in a standardized format (`FILE: ... \n ```[lang] ... ``` \n ENDFILE`).

2. **Embedded Linux Subsystem (`EmbeddedTermuxManager.kt`)**
   - Provides a full, self-contained Linux rootfs directly inside the Android app sandbox (`context.filesDir/usr`).
   - Capable of downloading and installing Termux bootstrap zip files dynamically.
   - **Crucial Rule:** The app is strictly sandboxed. It does *not* rely on the external Google Play Termux application (`/data/data/com.termux/`) for any binaries. It executes everything internally using its own `binDir` and `libDir`.

3. **Development Environment (`NodeService.kt`)**
   - Manages Node.js and npm execution inside the embedded Linux environment.
   - Streams live output (`stdout`/`stderr`) to the Compose UI.
   - Can run a local React/Vite development server (e.g. `npm run dev -- --port 5173`) so users can preview web apps inside a WebView locally on their phone.

4. **UI Architecture**
   - **`SettingsScreen.kt`**: Central hub for managing AI API keys, default models, and global preferences.
   - **`ProjectWorkspaceScreen.kt`**: Main IDE interface containing the File Explorer, Code Editor, Chat interface, Web Preview, and Terminal tabs.
   - **`ProjectWorkspaceViewModel.kt`**: Handles state management for the IDE, including chat history, file tree generation, and terminal logs.

## Important Configurations
- **Icons**: Uses Material 3 standard icons (`Icons.Default.*`). Emojis are strictly forbidden in UI text and logs by user preference.
- **Terminal Emulator**: Currently exploring an upgrade to a robust terminal emulator (e.g., Termux `terminal-view`) to replace the basic text-log implementation.
- **Gradle**: Uses Kotlin DSL (`build.gradle.kts`) and Jetpack Compose. Includes dependencies like Coil (`coil.compose`) for image loading (clipboard/vision support).

## Project Guidelines
- Keep responses and UI text professional; **DO NOT USE EMOJIS**.
- When adding new dependencies, verify they are compatible with Android Jetpack Compose (e.g., JetBrains `JediTerm` is Swing-only and unsupported).
- Ensure the app remains 100% self-contained regarding its Linux environment.
