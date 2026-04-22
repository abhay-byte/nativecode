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

---

## 🔲 Pending

<!-- Add new tasks below as the plan evolves -->

