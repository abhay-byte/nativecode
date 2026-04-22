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

---

## 🔲 Pending

<!-- Add new tasks below as the plan evolves -->
