# Embedded Linux Runtime + In-App X11 Display

**Goal**: Run a Linux userspace (`proot`/`proroot`) and an X11 display server *inside* NativeCode, eliminating the separate Termux and Termux:X11 apps while keeping target SDK 36 / API 34+ compatibility.

**Status**: Design & migration guide  
**Applies to**: `com.ivarna.nativecode` (Obsidian Nexus / Compose UI)

---

## 1. Why Move Away from the Orchestrator Model

The current architecture uses Android Intents to command external apps:

* `com.termux` — provides the shell, `proot-distro`, package manager
* `com.termux.x11` — provides the X server display surface

This works, but it has real UX and maintenance costs:

| Orchestrator Pain Point | Embedded Goal |
|------------------------|---------------|
| User must install 2–3 separate APKs | One APK, zero external installs |
| Deep-link callbacks are brittle and process-lifecycle dependent | In-process PTY/IPC |
| Termux version drift breaks scripts | Pin exact runtime versions |
| Modern Android restricts `RUN_COMMAND` / background services | In-app `ProcessBuilder` with foreground service |
| X11 configuration tied to companion app lifecycle | Direct X11 server control inside the app |

The blocker for naive embedding is Android's **W^X policy** (API 29+): apps cannot execute binaries downloaded/extracted into `getFilesDir()`. The workaround is to ship native executables as **Android native libraries** (`jniLibs/<abi>/lib*.so`), which the OS extracts into `lib/` and marks executable.

> **Important scope limit**: this only covers binaries baked into the APK at build time. Binaries downloaded at *runtime* — e.g. anything installed via `apt install` into the rootfs on-device — also land in the app's writable `filesDir` and are equally subject to W^X. This is the exact reason the real Termux app is pinned at targetSdk 28 and was removed from Play Store (see Termux issue #2155, still open). The `libproot_loader.so` entry in the architecture diagram represents the accepted fix — a custom binary loader that can exec guest binaries without violating W^X — but it is the single highest-risk, least-proven component of the plan. **Prototype and validate this on Android 14 / targetSdk 36 before committing to the architecture.**

---

## 2. Recommended Architecture

Use a **Native-Lib Packaging** strategy. All binaries that need `+x` are renamed to `lib<name>.so` and placed in `jniLibs/`. The rootfs and scripts live in `assets/` and are extracted to `filesDir/` (read-only data, not executed directly).

```
┌─────────────────────────────────────────────────────────────────────┐
│ NativeCode APK                                                      │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │ Android UI (Jetpack Compose)                                  │  │
│  │  • Home screen, install wizard, project browser               │  │
│  │  • TerminalView or X11/Surface composable                   │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                              │                                      │
│  ┌───────────────────────────▼──────────────────────────────────┐  │
│  │ Native Runtime Service (Kotlin + JNI)                         │  │
│  │  • Manages rootfs lifecycle                                   │  │
│  │  • Spawns proot/proroot via ProcessBuilder                    │  │
│  │  • Keeps PTY/shell/X server alive in foreground service       │  │
│  └───────────────────────────┬───────────────────────────────────┘  │
│                              │ JNI / sockets                        │
│  ┌───────────────────────────▼──────────────────────────────────┐  │
│  │ Native Binaries (jniLibs/arm64-v8a/)                          │  │
│  │  • libproot.so / libproroot.so  — userspace chroot engine     │  │
│  │  • libloader.so                 — proot loader (W^X bypass)   │  │
│  │  • libtalloc.so                 — proot dependency            │  │
│  │  • libXlorie.so                 — (optional) in-app X server  │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                              │                                      │
│  ┌───────────────────────────▼──────────────────────────────────┐  │
│  │ Rootfs & Data (extracted to filesDir/)                        │  │
│  │  • debian13-rootfs.tar.xz  — glibc distro                     │  │
│  │  • start scripts, wallpapers, fonts                           │  │
│  └──────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 3. Container Runtime: `proot` vs `proroot`

### 3.1 How They Work

| Engine | Mechanism | Pros | Cons |
|--------|-----------|------|------|
| **proot** (Termux) | `ptrace`-based syscall interception | Very compatible; catches direct syscalls | Slower; some ROMs restrict/block `ptrace` |
| **proroot** (coderredlab) | `LD_PRELOAD` + linker/bridge/stub-loader | Zero `ptrace` overhead; faster for Node/Python/Chromium | Can fail with statically linked binaries or direct syscalls; newer/less battle-tested |

For a general-purpose IDE/AI tool container, start with `proot` because it is what Termux packages and scripts are already validated against. Add `proroot` as an opt-in fallback for performance-sensitive workloads.

### 3.2 Embedded `proot` Build & Packaging

`proot` from the Termux fork depends on `libtalloc` and a small loader binary. To ship it inside the APK:

1. Build or obtain Termux ARM64 binaries:
   * `proot` → `libproot.so`
   * `proot-loader` → `libproot_loader.so`
   * `libtalloc.so` → `libtalloc.so`
2. Place them under `app/src/main/jniLibs/arm64-v8a/`.
3. Ensure `packaging.jniLibs.useLegacyPackaging = true` so the OS extracts them uncompressed and executable.

Runtime invocation:

```bash
libproot.so -r /data/data/com.ivarna.nativecode/files/rootfs \
            -b /dev -b /proc -b /sys \
            -w /root /bin/bash -l
```

### 3.3 `proroot` Packaging (Prebuilt Binaries Only)

> **⚠️ Source not public — proprietary license.** proroot's source code is not published ("still stabilising the implementation," per the repo). Only prebuilt binaries are available from a single developer's GitHub Releases. The license permits use but prohibits redistribution of modified binaries — a materially different risk from proot (GPLv2). The project had its first release this year (~9 GitHub stars, one contributor). Treat as **opt-in / experimental**; maintain proot as the primary engine.

`proroot` ships as **four** interdependent `.so` files that auto-discover each other from the same directory (`/proc/self/exe` dirname):

* `libproroot.so` — launcher
* `libproroot-runtime.so`
* `libproroot-bridge.so`
* `libldlinux.so` — patched dynamic linker

Place all four under `jniLibs/arm64-v8a/`. No `LD_*` or `PROROOT_*_PATH` exports are required because the launcher resolves siblings automatically.

Runtime invocation:

```bash
PROROOT_TMP_DIR=/data/data/com.ivarna.nativecode/files \
libproroot.so -r /data/data/com.ivarna.nativecode/files/rootfs \
              --link2symlink -w /root /bin/sh -c '<command>'
```

CLI options (only those documented in the official README are marked verified):

| Option | Status | Description |
|--------|--------|-------------|
| `-r <rootfs>` | ✅ Verified | Guest root directory (required) |
| `-w <dir>` | ✅ Verified | Working directory inside the guest |
| `--link2symlink` | ✅ Verified | Emulate hardlinks via anchor + symlink groups |
| `-b <host>` / `-b <host>:<guest>` | ⚠️ Unverified | Bind-mount (not in README; mirrors proot convention) |
| `-0` | ⚠️ Unverified | Fake `uid=0` / `gid=0` (not in README; mirrors proot convention) |
| `--static-loader` / `--no-static-loader` | ⚠️ Unverified | Not documented anywhere in the repo |



### 3.4 Rootfs

Both engines need a **glibc** rootfs. Termux itself uses Bionic, so existing Termux-native binaries cannot be reused. A minimal Debian 13 (Trixie) ARM64 rootfs (~85 MB compressed) is a good starting point. Extract it on first run to `filesDir/rootfs/`.

Rootfs contents are data, not executables, so they are not subject to W^X. Only the runtime engine and any extra native helpers need to live in `jniLibs/`.

---

## 4. X11 Display Server Options (No VNC)

The requirement is **X11 only**. The following paths are available, ordered from highest fidelity to easiest integration.

### 4.1 Option A — Embedded Xlorie (Termux-X11)

Termux-X11 is not just a protocol relay; it contains a real native DDX X server called **Xlorie** that renders to an `ANativeWindow` via JNI.

* Build `libXlorie.so` and companion JNI from the Termux-X11 sources.
* Provide an `ANativeWindow` from a Compose `AndroidView` / `SurfaceView`.
* Linux apps connect with `DISPLAY=:0`.

**Pros**: Lowest latency, direct shared-memory path, same protocol as desktop Linux.  
**Cons**: Heavy NDK/C porting, must track upstream Termux-X11 changes, needs careful foreground-service lifecycle for the X server process.

This is the architectural end-state for NativeCode but should be treated as a v1.8+ milestone, not the first embedded release.

### 4.2 Option B — Java/android-xserver (Embedded View)

`nwrkbiz/android-xserver` is a maintained Java implementation of an X11 server that runs inside an Android `View` subclass.

* Include the library AAR / source module in the app.
* Add the `View` to a Compose screen via `AndroidView`.
* Forward `DISPLAY` to the device's loopback IP (e.g. `DISPLAY=127.0.0.1:0`) or a Unix socket if supported.

**Pros**: No native X server build, easiest to embed inside your own UI, works with newer Android versions.  
**Cons**: Pure-Java X server is slower than Xlorie, limited extension support, may struggle with GPU/compositing-heavy apps.

This is the fastest path to a single-APK, in-app X11 display.

### 4.3 Option C — FDE-X11 (OpenFDE)

`openfde/FDE-X11` is an X11 server designed for the OpenFDE ecosystem. It can display Linux applications as Android windows and supports shared filesystem paths.

* Can be launched as an Android service (`adb shell am startservice -n com.fde.x11/.XWindowService`).
* Designed for multi-window, desktop-like integration.

**Pros**: Multi-window experience, no full-screen SurfaceView required.  
**Cons**: Tightly coupled to OpenFDE AOSP customizations; unclear how easily it can be repackaged into a generic app targeting SDK 36.

Treat as a research option if multi-window integration becomes a priority.

### 4.4 Option D — XServer XSDL (Standalone, Not Embedded)

`pelya/xserver-xsdl` is a long-standing X.Org server ported to Android using SDL. It runs as a separate app, not as an embeddable library.

**Pros**: Mature, robust, includes PulseAudio forwarding, very compatible.  
**Cons**: Not embeddable; user still installs a second APK, which contradicts the "single APK" goal.

Use only as a stopgap during development or if embedding proves too expensive.

### 4.5 Decision Matrix

| Approach | Embedded? | Latency | Effort | Recommendation |
|----------|-----------|---------|--------|----------------|
| Xlorie (Termux-X11) | Yes | Low | High | Long-term target |
| android-xserver | Yes | Medium | Low | First X11 milestone |
| FDE-X11 | Partial | Low-Medium | High | Multi-window research |
| XServer XSDL | No | Medium | Low | Stopgap only |

**Recommendation**: Build the runtime first with no display, then add **android-xserver** for the first in-app X11 milestone, and migrate to **Xlorie** if/when performance demands it.

---

## 5. Native Library Packaging Details

### 5.1 Directory Layout

```
app/src/main/
├── jniLibs/
│   └── arm64-v8a/
│       ├── libproot.so              # or libproroot.so + siblings
│       ├── libproot_loader.so       # proot only
│       ├── libtalloc.so             # proot only
│       └── libXlorie.so             # future Xlorie path
├── assets/
│   └── rootfs/
│       └── debian13_minimal.tar.xz
└── cpp/
    └── runtime-bridge.cpp           # optional JNI glue for Xlorie
```

### 5.2 Gradle Config

`app/build.gradle.kts`:

```kotlin
android {
    defaultConfig {
        ndk {
            abiFilters += setOf("arm64-v8a")
        }
    }

    packaging {
        jniLibs {
            // Keep native libs extracted so they are executable
            useLegacyPackaging = true
            pickFirsts += listOf("libproot.so", "libtalloc.so")
        }
    }
}
```

> `useLegacyPackaging = true` is important: it forces the APK to store uncompressed `.so` files so the OS can `mmap` and execute them directly.

### 5.3 Discovering the Native Library Directory

```kotlin
val nativeLibDir = context.applicationInfo.nativeLibraryDir
val prootPath = File(nativeLibDir, "libproot.so").absolutePath
```

---

## 6. Core Kotlin Components

### 6.1 `EmbeddedRuntime` Service

```kotlin
class EmbeddedRuntime(private val context: Context) {

    private val rootfsDir: File
        get() = File(context.filesDir, "rootfs")

    private val proot: File
        get() = File(context.applicationInfo.nativeLibraryDir, "libproot.so")

    private val proroot: File
        get() = File(context.applicationInfo.nativeLibraryDir, "libproroot.so")

    private fun engine(): File = if (proroot.exists()) proroot else proot

    suspend fun ensureRootfs(): Result<Unit> = withContext(Dispatchers.IO) {
        if (rootfsDir.resolve("bin/bash").exists()) {
            return@withContext Result.success(Unit)
        }
        runCatching {
            rootfsDir.mkdirs()
            context.assets.open("rootfs/debian13_minimal.tar.xz").use { input ->
                XZInputStream(input).use { xz ->
                    TarArchiveInputStream(xz).use { tar ->
                        // extract entries ...
                    }
                }
            }
        }
    }

    fun exec(
        command: List<String>,
        env: Map<String, String> = emptyMap()
    ): Flow<String> = callbackFlow {
        val pb = ProcessBuilder(command).apply {
            directory(rootfsDir)
            environment().apply {
                put("PROOT_TMP_DIR", context.filesDir.absolutePath)
                put("LD_LIBRARY_PATH", context.applicationInfo.nativeLibraryDir)
                env.forEach { (k, v) -> put(k, v) }
            }
            redirectErrorStream(true)
        }

        val process = pb.start()
        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { trySend(it) }
        }
        process.waitFor()
        close()
    }

    fun loginShell(): Flow<String> = exec(
        listOf(
            engine().absolutePath,
            "-r", rootfsDir.absolutePath,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-w", "/root",
            "/bin/bash", "-l"
        )
    )
}
```

### 6.2 Foreground Service for Sessions

Android kills background processes. Any long-running shell or X server should live in a foreground service.

```kotlin
class RuntimeService : Service() {

    private val binder = RuntimeBinder()
    private val sessions = mutableMapOf<String, Process>()

    override fun onBind(intent: Intent?) = binder

    fun startSession(id: String, command: List<String>): Flow<String> = callbackFlow {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        sessions[id] = process
        // stream output...
    }

    fun stopSession(id: String) {
        sessions.remove(id)?.destroyForcibly()
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(
            RUNTIME_NOTIFICATION_ID,
            buildRuntimeNotification()
        )
    }
}
```

Manifest additions:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<service
    android:name=".core.runtime.RuntimeService"
    android:exported="false"
    android:foregroundServiceType="dataSync" />
```

---

## 7. X11 Integration Sketches

### 7.1 android-xserver Milestone

Add the library module, place the view in Compose, and start the server inside the rootfs:

```bash
export DISPLAY=127.0.0.1:0
xfce4-session &
```

Compose:

```kotlin
@Composable
fun X11Display(modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            XServerView(ctx).apply {
                start(displayNumber = 0)
            }
        },
        modifier = modifier
    )
}
```

### 7.2 Xlorie Milestone

Xlorie requires IPC-based wiring between the host app and the native X server process. Key architectural constraint: **JNI only works within the same process**. A true child process (`libXlorie.so :0`) cannot receive a raw `ANativeWindow` via JNI. The way the real Termux-X11 solves this:

1. The X server runs in its **own process** (as a bound `Service`).
2. The host app shares a `Surface` (which is `Parcelable`) across the process boundary via an AIDL interface on the bound service — not a raw `ANativeWindow`.
3. The X server renders into that `Surface`; the host app displays it in a `SurfaceView` / `TextureView`.

Implementation sketch:

1. Build `libXlorie.so` from Termux-X11 sources with the app's package name.
2. Expose a bound `XlorieService` that holds the X server process and accepts a `Surface` token via AIDL.
3. From the Compose UI, obtain a `SurfaceView` via `AndroidView`, pass its `Surface` to the bound service.
4. Linux apps connect to `DISPLAY=:0`.



---

## 8. Migrating from Current Termux Scripts

The existing shell scripts in `app/src/main/assets/scripts/` assume a Termux environment. They cannot run verbatim inside the embedded rootfs because:

* Termux uses Bionic libc and `/data/data/com.termux/...` paths.
* The embedded rootfs uses glibc and standard FHS paths.

Migration strategy:

| Current Termux Script | Embedded Replacement |
|----------------------|----------------------|
| `setup_termux.sh` | Remove; replace with rootfs extraction + package install inside Debian |
| `setup_debian_family.sh` | Run unchanged inside the rootfs via `EmbeddedRuntime.exec` |
| `start_gui.sh` | Replace with X11 launcher (android-xserver or Xlorie) |
| `setup_hw_accel_debian.sh` | Run inside rootfs; VirGL server started by app service |
| `setup_*_debian.sh` (AI/IDE tools) | Run inside rootfs; remove Termux-path patches |
| `TermuxIntentFactory` | Replace with `EmbeddedRuntime` calls |
| `StateManager` callback logic | Replace with `Flow`/coroutine state |

**Key rule**: scripts that only touch files inside the rootfs need minimal changes. Scripts that depend on Termux paths or the Termux package manager must be rewritten.

---

## 9. Related Projects & Why We Do Not Fork Them

| Project | What It Does | Why Not Use It Directly |
|---------|--------------|------------------------|
| **Alevap** (`Ilan12346-maya/alevap`) | Standalone Termux + X11 app framework | Targets SDK 28 and relies on binary path patching (`com.termux` → package name). **Critical constraint**: its raw-string replacement works only if your package name is exactly **10 characters** — the same byte length as `com.alevap` — because the patch does not adjust binary offsets. Our package `com.ivarna.nativecode` is a different length, so the approach cannot be used directly. Useful as a conceptual reference only. |
| **termux-shared / terminal-view** (JitPack) | Official Termux library modules for embedding a terminal view and shared utilities | Still assumes Termux-style Bionic userland and Termux-specific paths. Our embedded rootfs is glibc-based, so most integration points do not map cleanly. *(No separate SDK named "libtermux-android" was found; this entry refers to `com.termux:termux-shared` and `com.termux:terminal-view` on JitPack.)* |
| **Termux:X11** | Companion X server app | We want the X server inside our app, not a separate APK. The Xlorie DDX code can be extracted and reused. |


---

## 10. Testing Plan

1. **Native lib extraction test**
   * Verify `libproot.so` (or `libproroot.so`) exists in `nativeLibraryDir` and is executable.
   * Run `libproot.so --help` / `libproroot.so --help` and assert non-crash output.

2. **Rootfs boot test**
   * Extract rootfs, run `/bin/uname -a` through the engine.
   * Assert output contains `Linux` and expected architecture.

3. **⚡ Spike — Runtime W^X validation (must do before architecture commit)**
   * Run `apt update && apt install -y htop` inside rootfs on a real device at **targetSdk 36 / Android 14+**.
   * Attempt to execute the `htop` binary (downloaded at runtime into `filesDir`) through the proot loader.
   * **Expected blocker**: Android's W^X / "safer dynamic code loading" policy may block execution even under proot — the same unresolved issue that keeps Termux at targetSdk 28 (issue #2155). Validate before committing to the architecture.
   * Mitigation path: `libproot_loader.so` (shipped in `jniLibs/`) acts as the exec relay — confirm it can trampoline into guest binaries without triggering W^X enforcement on the target OS.


4. **Display test (android-xserver path)**
   * Start the Java X server, run `xclock` or `xfce4-session` inside rootfs.
   * Verify a framebuffer/window is received and rendered.

5. **Engine fallback test**
   * If `libproroot.so` is present, run the same command through both engines and compare output.

6. **Lifecycle test**
   * Background the app, ensure foreground service keeps shell alive.
   * Return to app, reconnect to existing session.

---

## 11. Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| W^X blocks APK-baked native binaries | Binaries live in `jniLibs/` as `.so`; OS extracts them into executable `lib/` dir |
| **W^X blocks runtime-downloaded binaries** ⚡ (HIGH — spike required) | `libproot_loader.so` acts as exec relay for guest binaries; must validate on Android 14 / targetSdk 36 before architecture commitment; this is Termux issue #2155 |
| Proot ptrace blocked by SELinux on some ROMs | Ship proroot as opt-in fallback; warn users on first boot |
| **proroot is closed-source, proprietary, single-maintainer** | Keep proot as default engine for all initial releases; only add proroot after the project matures and redistribution terms are confirmed |
| APK size blowup from rootfs | Use a minimal Alpine/Debian rootfs; download larger rootfs on demand |
| GPU acceleration breaks | Keep VirGL server as native lib; fallback to llvmpipe |
| Xlorie porting cost + IPC complexity | Ship android-xserver first (same-process); migrate to Xlorie (bound Service + AIDL Surface) later |
| proroot CLI flags undocumented (source not public) | Test each flag empirically against the prebuilt binary; do not assume full proot parity |
| GPLv3 from Termux/Xlorie | Keep runtime libraries separate; publish modified sources |


---

## 12. Decision Summary

* **Best first target**: embedded `proot`/`proroot` + **android-xserver** in-app X11 display
* **Eliminates**: separate Termux and Termux:X11 installs
* **Keeps**: modern target SDK 36, F-Droid / Play Store compliance
* **Future upgrade**: swap android-xserver for embedded Xlorie
* **Migration order**: runtime → scripts → X11 display → polish

This document is the starting point for the `feature/embedded-runtime` branch.
