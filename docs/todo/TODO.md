# NativeCode — TODO List

Tracking pending tasks for the FluxLinux → NativeCode upgrade.

---

## ✅ Done

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
