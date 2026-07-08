# NativeCode — TODO List

Tracking pending tasks for the FluxLinux → NativeCode upgrade.

---

## ✅ Done

- [x] Update app icon
  - Replaced logo with new Android mascot + terminal design
  - Removed blue background, zoomed/cropped to fit frame
  - Updated `assets/logo/logo.png` and `assets/logo/logo.webp`
  - Updated Android launcher icons (`mipmap-mdpi` through `mipmap-xxxhdpi`)
  - Updated documentation references (`README.md`, `docs/README.md`, `docs/assets_reference.md`)
- [x] Rename package `com.ivarna.fluxlinux` → `com.ivarna.nativecode`
  - Updated `app/build.gradle.kts` (namespace + applicationId)
  - Updated `AndroidManifest.xml` (FileProvider authority + deep link scheme `nativecode://`)
  - Updated all Kotlin `package` declarations (moved from `com.zenithblue.fluxlinux` → `com.ivarna.nativecode`)
  - Updated `proguard-rules.pro`
  - Deep link scheme changed from `fluxlinux://` to `nativecode://`
- [x] Integrate AI Tools Management
  - Created `AiToolsScreen.kt` for UI interface
  - Added Codex scripting & UI logic (`setup_codex_debian.sh`)
  - Added AI Tools banner directly inside `DistroSettingsScreen`

- [x] Add Projects workflow
  - Updated `BottomTab` enum in `GlassBottomNavigation.kt`
  - Created SAF picker for Android to format Termux linux paths in `ProjectsScreen.kt`
  - Added persistent path storage into `StateManager.kt`

- [x] Redesign ProjectsScreen with glassmorphism UI
  - CrystalButton with cyan-to-magenta gradient
  - Glass project cards with border glow and path chips
  - Empty state glass orb illustration
  - Staggered fade+scale animations
  - Removed deprecated hazeChild to fix Android 15 crash

---

## 🔲 Pending

### Phase 1: Codex Agent Launcher
- [ ] Add agent-selection dialog when a project card is tapped
  - Options: "Open with Codex" (primary), "Open Folder" (SAF), "Remove"
  - Glass-styled bottom sheet or dialog

### Phase 2: Codex Daemon Bridge Script
- [ ] Create `codex_daemon.sh` wrapper script (assets/scripts/common/)
  - Runs inside the distro (proot-distro login) in the project directory
  - Polls `/tmp/codex_prompt.txt` for new prompts
  - Runs `codex -q "$PROMPT"` and writes stdout+stderr to `/tmp/codex_response.txt`
  - Signals completion via `/tmp/codex_done.txt`
  - Supports multi-turn by maintaining conversation context in `/tmp/codex_context.txt`
  - Graceful shutdown on `/tmp/codex_stop.txt`

### Phase 3: Codex Chat UI (CodexScreen.kt)
- [ ] Build `CodexScreen` — a full-screen chat interface replicating Codex App
  - Top bar: project name, back button, connection status indicator
  - Message list: user bubbles (right, cyan accent) + Codex bubbles (left, glass card)
  - Code blocks inside messages with syntax-highlighted styling
  - Input bar at bottom with glass styling, send button
  - Typing indicator while waiting for daemon response
  - Empty state: "Start a conversation with Codex"

### Phase 4: Android ↔ Daemon Communication Layer
- [ ] `CodexSessionManager` — Kotlin bridge class
  - `startSession(distroId, projectPath)`: launches daemon via TermuxIntentFactory
  - `sendPrompt(prompt)`: writes to `/tmp/codex_prompt.txt` inside distro
  - `pollResponse()`: reads `/tmp/codex_response.txt` and `/tmp/codex_done.txt`
  - `stopSession()`: writes `/tmp/codex_stop.txt`
  - Uses coroutines with polling loop (500ms interval)

### Phase 5: TermuxIntentFactory Extension
- [ ] `buildLaunchCodexDaemonIntent(distroId, projectPath)`
  - For PRoot: `proot-distro login $distroId -- bash codex_daemon.sh "$projectPath"`
  - For Chroot: mounts tmp, runs daemon inside chroot
  - Runs in background so Termux session stays alive

### Phase 6: Navigation Wiring
- [ ] Add `Screen.CODEX_CHAT` to navigation graph
- [ ] Pass `projectPath` and `distroId` as navigation arguments
- [ ] ProjectsScreen → agent dialog → CodexScreen
- [ ] Handle back navigation properly

### Phase 7: Polish & Testing
- [ ] Auto-scroll to bottom on new messages
- [ ] Copy-to-clipboard for code blocks
- [ ] Error handling: daemon not running, no response, API key missing
- [ ] Toast/snackbar for "Codex session started in Termux"
- [ ] Build, install, and verify on device

### Phase 8: UI Overhaul (Unified Dashboard)
- [ ] Refactor `MainActivity.kt` to remove Bottom Navigation
- [ ] Consolidate Distro management, Settings, Tools, and Projects into `HomeScreen.kt`
- [ ] Implement Glassmorphism "Debian Hero Card" with direct launch buttons
- [ ] Add AI Tools and IDE Editors banners to main dashboard
- [ ] Port Project management workflow to the bottom of the dashboard
- [ ] Maintain all existing logic while achieving a stunning, unified aesthetic

---

## 🔲 Feature: Embedded Terminal & In-App X11 Display

> **Goal:** Replace shell-script IPC (Termux external app → X11 external app) with direct
> library-level embedding. Terminal icon in top bar opens an in-app Termux terminal session.
> When a display server launches inside that session, the X11 output renders in-app via `LorieView`.

### Phase A: Library Module Wiring

- [ ] Add `:modules:termux-x11:app` to `settings.gradle.kts` as a library (change `com.android.application` to `com.android.library`, remove `applicationId`, strip signing/splits config)
  - Key classes needed: `LorieView`, `CmdEntryPoint`, `InputEventSender`
  - Has native `.so` via CMake — keep `externalNativeBuild` block, add `aar` output
- [ ] Verify `:modules:termux-app:terminal-emulator` and `:modules:termux-app:terminal-view` already included in `settings.gradle.kts` (they are — confirmed)
- [ ] Add dependencies to `app/build.gradle.kts`:
  ```kotlin
  implementation(project(":modules:termux-app:terminal-emulator"))
  implementation(project(":modules:termux-app:terminal-view"))
  implementation(project(":modules:termux-x11:app"))
  ```
- [ ] Resolve compile SDK mismatch: termux-x11 uses `compileSdkVersion 34`, app uses `36` — bump x11 module to 36 or force `compileSdk` override
- [ ] Resolve Java version mismatch: termux-x11 uses `VERSION_1_9`, app uses `VERSION_17` — align to 17
- [ ] Handle AIDL: `buildFeatures.aidl true` required in x11 module (already set)

### Phase B: Embedded Terminal UI

- [ ] Create `EmbeddedTerminalView.kt` — Compose wrapper around `TerminalView` (from `terminal-view`)
  - Hosts `TerminalView` inside `AndroidView { ... }`
  - Connects `TerminalSession` (from `terminal-emulator`) backed by `/system/bin/sh` or Termux binary
  - Implements `TerminalViewClient` for key/text input forwarding
  - Full keyboard input passthrough via `TerminalView`
- [ ] Create `TerminalSessionManager.kt`
  - `startSession(execPath, args, env, cwd)`: spawns `TerminalSession`
  - Detects when a display server starts (watch for `DISPLAY=:0` or socket `/tmp/.X11-unix/X0`)
  - Exposes `StateFlow<Boolean>` `isX11Active` for X11 trigger
- [ ] Add terminal icon (e.g. `Icons.Default.Terminal` or custom SVG) to top app bar in `MainActivity.kt` / `HomeScreen.kt`
  - On click: show `EmbeddedTerminalView` in bottom sheet or full-screen overlay
  - Persist terminal session across navigation (ViewModel scoped to Activity)

### Phase C: In-App X11 Display (`LorieView`)

- [ ] Create `X11DisplayScreen.kt` — Compose screen embedding `LorieView`
  - `AndroidView { LorieView(context) }` sized to fill screen
  - `LorieView` connects via Unix domain socket (same process as `CmdEntryPoint`)
- [ ] Wire `CmdEntryPoint` startup:
  - On `isX11Active == true`, call `CmdEntryPoint.start()` (or equivalent) to initialize the X server socket
  - Pass `LorieView`'s `Surface` to native renderer
- [ ] Auto-navigate to `X11DisplayScreen` when `isX11Active` transitions `false → true`
  - Show overlay/transition: "Display server detected — switching to X11 view"
  - Back button returns to terminal view (session stays alive)
- [ ] Input routing: forward touch/keyboard events from `LorieView` via `InputEventSender`

### Phase D: Navigation & UI Integration

- [ ] Add `Screen.TERMINAL` and `Screen.X11_DISPLAY` to navigation graph
- [ ] Terminal icon in top bar: visible on all main screens
- [ ] State machine: `IDLE → TERMINAL_OPEN → X11_ACTIVE → BACK_TO_TERMINAL → IDLE`
- [ ] Handle lifecycle: pause/resume `LorieView` surface on activity lifecycle events
- [ ] Handle rotation: `LorieView` resize via `SurfaceHolder.Callback`

### Phase E: Polish & Testing

- [ ] Extra keys bar (reuse `ExtraKeysView` from `termux-x11`) below terminal for Ctrl/Tab/Esc/Arrow
- [ ] Font size adjustment in terminal (pinch-to-zoom)
- [ ] Copy/paste from terminal selection
- [ ] Graceful fallback if X11 socket not found after 30s
- [ ] Build, install, verify on device: terminal opens, `startx` or `DISPLAY=:0 xclock` renders in-app

---

## 📝 Architecture: Embedded Terminal + X11

```
┌──────────────────────────────────────────┐
│              Top Bar                     │
│   [≡ NativeCode]          [⬛ Terminal]  │  ← terminal icon
└──────────────────────────────────────────┘
         │ click
         ▼
┌──────────────────────────────────────────┐
│         EmbeddedTerminalView             │
│   TerminalView (terminal-view lib)       │
│   TerminalSession (terminal-emulator)    │
│   → user runs: startx / Xvfb / etc.     │
└──────────────┬───────────────────────────┘
               │ detects DISPLAY=:0 / socket
               ▼
┌──────────────────────────────────────────┐
│         X11DisplayScreen                 │
│   LorieView (termux-x11 lib)             │
│   CmdEntryPoint (native X server)        │
│   InputEventSender (touch/kbd → X11)    │
└──────────────────────────────────────────┘
```

**Key classes:**
- `TerminalSession` — `modules/termux-app/terminal-emulator` — process lifecycle + PTY
- `TerminalView` — `modules/termux-app/terminal-view` — renders terminal to canvas
- `LorieView` — `modules/termux-x11/app` — SurfaceView rendering X11 frames via OpenGL ES
- `CmdEntryPoint` — `modules/termux-x11/app` — JNI bridge starting the native X server

---

## 📝 Notes

**Codex CLI invocation inside distro:**
```bash
export OPENAI_API_KEY=sk-...
cd /sdcard/Project/Taskstack
codex -q "Implement a todo app in React"
```

**File-based IPC protocol:**
- `/tmp/codex_prompt.txt` → Android writes, daemon reads
- `/tmp/codex_response.txt` → daemon writes, Android reads  
- `/tmp/codex_done.txt` → daemon writes "done" when finished
- `/tmp/codex_stop.txt` → Android writes to signal shutdown
- `/tmp/codex_context.txt` → daemon maintains conversation history

**Architecture:**
```
┌─────────────────┐     file I/O      ┌──────────────────┐
│  CodexScreen    │ ◄────────────────► │  codex_daemon.sh │
│  (Android UI)   │   /tmp/codex_*.txt │  (inside distro) │
└─────────────────┘                    └──────────────────┘
         │                                      │
         │ startService                         │ codex -q
         ▼                                      ▼
┌──────────────────┐                  ┌──────────────────┐
│ TermuxIntentFactory│                 │   Codex CLI      │
│ buildLaunchCodex...│                 │   (OpenAI)       │
└──────────────────┘                  └──────────────────┘
```
