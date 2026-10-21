# Kodrix AI Agent - Behavior & Context Guidelines

## Agent Persona & Role
You are the AI Agent operating within the **Kodrix Android App** codebase. Your goal is to help build and refine this on-device IDE. You must follow these behavioral guidelines at all times.

## Strict Rules & User Preferences
1. **NO EMOJIS**: The user has explicitly stated they do not want *any* emojis in the app. Do not use emojis in your responses, in the code you write, or in the UI text. Use standard text or Material 3 Icons (`Icons.Default.*`).
2. **Terminal Emulator Clarification**: Do not suggest or implement JetBrains `JediTerm` for the Android app, as it is tightly coupled to Swing/AWT. The preferred robust terminal emulator for Android is Termux's `terminal-view` or a native Compose ANSI parser.
3. **No External App Dependencies**: The Kodrix app must remain 100% self-contained. The Embedded Linux subsystem must never look for or execute binaries from the external Play Store Termux app (`/data/data/com.termux/`). It must rely solely on its internal `context.filesDir/usr` environment.

## The AI Subsystem Initialization Flow
- **Goal**: When a request in the workspace chat needs npm, node or other packages and the embedded Linux environment is not installed, the AI asks the user to install it.
- **Mechanism**: The workspace chat system prompt (`buildRefineSystemPrompt` in `ProjectWorkspaceViewModel.kt`) tells the AI whether the environment is installed and, if not, to include the exact string `<REQUEST_LINUX_SUBSYSTEM>`. The one-shot code generation prompt in `BaseAiProvider.kt` does not use the tag: it must always return code.
- **UI Handling**: `AiResponseParser` detects and strips the tag; `ProjectWorkspaceViewModel.kt` sets `showLinuxSubsystemPrompt = true`, and `ProjectWorkspaceScreen.kt` shows a dialog whose Install button runs `setup-termux` in the Terminal tab.

## AI Response Protocol
- Files: `FILE: path` + fenced block + `ENDFILE`. Commands: a line starting with `COMMAND:`. Both are parsed only by `AiResponseParser.kt` (with unit tests in `AiResponseParserTest.kt`); keep prompts and parser in sync.
- Never send the model a truncated file and ask for a full rewrite: `ProjectContextBuilder.kt` includes whole files or lists them as omitted.
- Paths from AI output, ZIP entries and HTTP requests must go through `SafePaths.resolveInside` before touching disk.

## Key Files to Remember
- `BaseAiProvider.kt` / `ConcreteProviders.kt`: LLM API calls and system prompt definitions.
- `AiResponseParser.kt` / `ProjectContextBuilder.kt`: parsing AI output and building project context for prompts.
- `EmbeddedTermuxManager.kt`: The embedded Linux environment manager.
- `NodeService.kt`: Handles executing node/npm commands inside the embedded Linux environment.
- `SettingsScreen.kt` & `SettingsViewModel.kt`: Global settings and API key management.
- `ProjectWorkspaceScreen.kt` & `ProjectWorkspaceViewModel.kt`: The main IDE interface.
