# v1.7.1 — Hotfix: Blank terminal

v1.7 shipped the embedded Alpine terminal with an xterm.js WebView, but the WebView rendered a blank dark canvas on device. The page loaded and xterm.js initialized, but the canvas never painted output.

## Fix
Replace the WebView/xterm.js implementation with a pure-Compose terminal (same pattern as SimonSchubert/Kai):

- `core/runtime/TerminalLine.kt` — `Command` / `Output` / `Error` sealed interface
- `core/runtime/AnsiParser.kt` — CSI SGR → `AnnotatedString` with 8/16/256-color support
- `ShellSession.kt` rewritten — `BufferedReader.readLine()` per stream, atomic cancel, EOF-safe
- `EmbeddedRuntimeScreen.kt` rewritten — `LazyColumn` of `Text` lines + `TextField` input

Drops ~3.5 MB of assets (xterm.min.js, xterm.min.css, JetBrainsMono Nerd Font).

## Commits
- `3d17f6c` fix(terminal): replace xterm.js webview with compose-native terminal (Kai pattern)
- `47518e1` chore: bump to v1.7.1 (versionCode 10)

## Install
```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

## Artifact
- `app-release.apk` — ~24 MB, minSdk 26, targetSdk 36, arm64-v8a only.
