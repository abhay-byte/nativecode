# Architecture

The embedded runtime runs Linux userspace inside the app via proot — a ptrace-based syscall interceptor that translates paths, fakes root, and sandboxes the guest OS.

## System Layers

```
┌─────────────────────────────────────────────────────┐
│ NativeCode App Process                                │
│  ┌────────────────────────────────────────────────┐ │
│  │ Compose UI                                      │ │
│  │  HomeScreen → EmbeddedRuntimeScreen → Terminal │ │
│  └────────────────────┬───────────────────────────┘ │
│                       │ callbacks / state            │
│  ┌────────────────────▼───────────────────────────┐ │
│  │ EmbeddedRuntime (Kotlin)                        │ │
│  │  • ensureRootfs() — extract + configure        │ │
│  │  • exec() / execShell() / loginShell()         │ │
│  │  • startInteractiveShell() — foreground shell  │ │
│  │  • writeAlpineRepositories() — sets /etc/apk   │ │
│  └────────────────────┬───────────────────────────┘ │
│                       │ ProcessBuilder               │
│  ┌────────────────────▼───────────────────────────┐ │
│  │ libproot.so (jniLibs/<abi>/)                    │ │
│  │  • proot loader                                 │ │
│  │  • libtalloc.so (allocator)                     │ │
│  │  • libproot-loader.so (W^X bypass)              │ │
│  └────────────────────┬───────────────────────────┘ │
│                       │ proot ptrace                 │
│  ┌────────────────────▼───────────────────────────┐ │
│  │ Rootfs (filesDir/rootfs/)                       │ │
│  │  • Alpine 3.20.0 mini rootfs                    │ │
│  │  • Extracted from assets/rootfs/*.tar.gz        │ │
│  │  • Bind-mounted /dev, /proc, /sys, /sdcard      │ │
│  └────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────┘
```

## Data Flow

### First Run (Rootfs Extraction)

```
App start
  → ensureRootfs()
  → rootfs ready? No
  → extractAlpineRootfs()   (reads from assets/rootfs/alpine-minirootfs.tar.gz)
  → makeWritable()
  → writeResolvConf()       (/etc/resolv.conf → 8.8.8.8 / 8.8.4.4)
  → writeAlpineRepositories (/etc/apk/repositories → v3.20/main + /community)
  → setup_alpine_embedded.sh runs
```

### Shell Session

```
User taps "Open Terminal"
  → startInteractiveShell()
  → buildProotCommand(["/bin/sh", "-l"])
  → exec() via Runtime.exec()
  → callbackFlow streams stdout/stderr to UI
```

## Distro Support

| Distro | Rootfs Source | Script | Notes |
|--------|--------------|--------|-------|
| Alpine 3.20.0 | Bundled (`assets/rootfs/*.tar.gz`) | `setup_alpine_embedded.sh` | Default, runs in-process |
| Debian 13 (trixie) | Downloaded on first use | `setup_debian_proot.sh` | Requires Alpine proot first |

## Key Design Decisions

- **Bundled rootfs**: Alpine rootfs ships inside the APK (~4 MB compressed). No network needed for first boot.
- **jniLibs packaging**: Proot and its dependencies are renamed `lib*.so` and placed in `jniLibs/<abi>/` so Android extracts them into the executable `lib/` directory — the only way to bypass W^X on modern Android.
- **Version constants**: `ALPINE_VERSION` and `ALPINE_BRANCH` in `EmbeddedRuntime.kt` are the single source of truth — inspired by the [Kai project](https://github.com/SimonSchubert/Kai) pattern.
