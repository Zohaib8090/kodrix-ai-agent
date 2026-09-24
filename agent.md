# Kodrix AI Agent - Behavior & Context Guidelines

## Agent Persona & Role
You are the AI Agent operating within the **Kodrix Android App** codebase. Your goal is to help build and refine this on-device IDE. You must follow these behavioral guidelines at all times.

## Strict Rules & User Preferences
1. **NO EMOJIS**: The user has explicitly stated they do not want *any* emojis in the app. Do not use emojis in your responses, in the code you write, or in the UI text. Use standard text or Material 3 Icons (`Icons.Default.*`).
2. **Terminal Emulator Clarification**: Do not suggest or implement JetBrains `JediTerm` for the Android app, as it is tightly coupled to Swing/AWT. The preferred robust terminal emulator for Android is Termux's `terminal-view` or a native Compose ANSI parser.
3. **No External App Dependencies**: The Kodrix app must remain 100% self-contained. The Embedded Linux subsystem must never look for or execute binaries from the external Play Store Termux app (`/data/data/com.termux/`). It must rely solely on its internal `context.filesDir/usr` environment.

## Current Work in Progress
### The AI Subsystem Initialization Flow
- **Goal**: When a user asks the AI inside the Kodrix app to build a website/app, the AI must proactively ask the user to start the Linux subsystem.
- **Mechanism**: The AI is instructed via its System Prompt (in `BaseAiProvider.kt`) to output the exact string `<REQUEST_LINUX_SUBSYSTEM>`.
- **UI Handling**: `ProjectWorkspaceViewModel.kt` parses the chat messages. When it detects the `<REQUEST_LINUX_SUBSYSTEM>` tag, it strips the tag from the UI and sets `showLinuxSubsystemPrompt = true`. This triggers a prompt in `ProjectWorkspaceScreen.kt` asking the user to initialize the Embedded Linux environment.

## Key Files to Remember
- `BaseAiProvider.kt` / `ConcreteProviders.kt`: LLM API calls and system prompt definitions.
- `EmbeddedTermuxManager.kt`: The embedded Linux environment manager.
- `NodeService.kt`: Handles executing node/npm commands inside the embedded Linux environment.
- `SettingsScreen.kt` & `SettingsViewModel.kt`: Global settings and API key management.
- `ProjectWorkspaceScreen.kt` & `ProjectWorkspaceViewModel.kt`: The main IDE interface.
