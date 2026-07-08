# Runtime API

The `EmbeddedRuntime` class in `app/src/main/kotlin/com/ivarna/nativecode/core/runtime/EmbeddedRuntime.kt` manages the full lifecycle of the embedded Linux userspace.

## Distro Enum

```kotlin
enum class Distro(val rootfsDirName: String, val displayName: String) {
    Alpine("rootfs", "Alpine Linux 3.20.0"),
    Debian("rootfs-debian", "Debian 13 (trixie)")
}
```

| Property | Purpose |
|----------|---------|
| `rootfsDirName` | Subdirectory under `filesDir/` for the rootfs |
| `displayName` | Human-readable label used in UI |
| `setupScriptName` | Maps to the asset script under `scripts/embedded/` |

## Construction

```kotlin
val runtime = EmbeddedRuntime(context)              // Default: Alpine
val runtime = EmbeddedRuntime(context, Distro.Debian) // Debian
```

## Core Methods

### `ensureRootfs(): Result<File>`

Ensures the rootfs is extracted and ready. Call this before any shell operation. Returns the rootfs directory on success.

- **Alpine**: Extracts from the bundled `assets/rootfs/alpine-minirootfs.tar.gz`
- **Debian**: Throws `IllegalStateException` — must be bootstrapped via `setup_debian_proot.sh` first

```kotlin
val result = runtime.ensureRootfs()
result.onSuccess { rootfsDir ->
    // rootfsDir = filesDir/rootfs/
}
```

### `isAvailable(): Boolean`

Checks whether the proot binary exists in `nativeLibraryDir` and is accessible.

### `isRootfsReady(): Boolean`

Checks for a shell binary (`/bin/sh`, `/bin/bash`, or `usr/bin/sudo`) in the rootfs as a readiness indicator.

### `exec(command: List<String>): Flow<String>`

Runs a command inside the proot environment. Returns stdout/stderr as a coroutine `Flow`.

```kotlin
runtime.exec(listOf("/bin/uname", "-a")).collect { line ->
    Log.d("Runtime", line)
}
```

### `execShell(script: String): Flow<String>`

Runs a shell script string inside proot. Equivalent to `exec(listOf("/bin/sh", "-c", script))`.

```kotlin
runtime.execShell("apk add --no-cache python3").collect { line ->
    Log.d("Runtime", line)
}
```

### `loginShell(): Flow<String>`

Starts an interactive login shell (`/bin/sh -l`). Used by the terminal UI.

### `startInteractiveShell(): Process`

Starts a persistent interactive process and returns the raw `Process` handle. Used for the terminal session where the UI connects to the process streams directly.

- **Alpine**: Root shell as `/bin/sh -l`
- **Debian**: Drops to `su - flux` (non-root user)

## Internal Architecture

### Proot Command Construction

`buildProotCommand()` assembles the proot invocation:

```
libproot.so
  --rootfs=<filesDir/rootfs>
  --bind=/dev --bind=/proc --bind=/sys
  --bind=/sdcard/Android/data/com.ivarna.nativecode/files:/sdcard
  --bind=<homeDir>:/root
  --bind=<tmpDir>:/tmp
  --bind=<appFilesDir>:/data/data/com.ivarna.nativecode
  -0 -w /root
  <guest command>
```

### Environment

| Variable | Value |
|----------|-------|
| `HOME` | `/root` (Alpine) or `/home/flux` (Debian) |
| `PATH` | `/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin` |
| `TERM` | `xterm-256color` |
| `LANG` | `C.UTF-8` |
| `LD_LIBRARY_PATH` | `filesDir` + `nativeLibraryDir` |
| `PROOT_TMP_DIR` | `<filesDir>/tmp` |
| `PROOT_LOADER` | `<nativeLibDir>/libproot-loader.so` |

### Alpine Repositories

Written to `/etc/apk/repositories` during setup:

```
https://dl-cdn.alpinelinux.org/alpine/v3.20/main
https://dl-cdn.alpinelinux.org/alpine/v3.20/community
```
