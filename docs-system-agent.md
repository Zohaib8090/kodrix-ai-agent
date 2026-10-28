# Kodrix system agent

Power-button assistant (VoiceInteractionService) -> voice (SpeechRecognizer) -> cheap-model tool loop
(escalates to a stronger model on repeated failures) -> tools: device actions, memory, MCP servers,
and a rootless Alpine Linux (proot) shell.

## Verified (Android 15 x86_64 emulator, targetSdk 36)
- Linux: install, exec, exit codes, persistence, output cap, timeout kills the whole process tree, no crash.
- Assistant: registers as the active voice-interaction service; ACTION_ASSIST opens the overlay; mic
  permission prompt; typed fallback; request reaches the agent (reports "no AI provider" without a key).
- JVM unit tests: agent loop, LLM/MCP client, memory, tar extractor, Linux env, process tree, STT errors.

## Not verified
- Real arm64 phone (arm64 proot builds untested), real speech recognition/TTS, a live model call,
  `apk add` / network inside Linux, a real MCP server, the physical power-button long-press mapping
  (device settings: Digital assistant app = Kodrix, power button = assistant).
- The older EmbeddedTermuxManager (IDE features) does not work on Android 10+ and is unchanged.
- proot is GPL; ship its license if distributing.
