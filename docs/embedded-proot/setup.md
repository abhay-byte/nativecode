# Setup Guide

## Building Proot Binaries

Proot is cross-compiled from the [Termux fork](https://github.com/termux/proot) using the Android NDK. The build produces three files per ABI:

| File | Role |
|------|------|
| `libproot.so` | Proot engine binary |
| `libproot-loader.so` | Custom ELF loader (W^X bypass) |
| `libtalloc.so` | Talloc allocator dependency |

### Prerequisites

- Android NDK r26+ (set `ANDROID_NDK_HOME`)
- Python 3 (for talloc's WAF build)
- `git`, `curl`, `make`

### Build

The build-proot.sh script at the repo root handles everything:

```bash
# Build all ABIs (arm64-v8a, armeabi-v7a, x86_64)
./build-proot.sh

# Clean build artifacts
./build-proot.sh --clean
```

Output goes to `app/src/main/jniLibs/<abi>/`:

```
app/src/main/jniLibs/
├── arm64-v8a/
│   ├── libproot.so
│   ├── libproot-loader.so
│   ├── libproot-loader32.so
│   └── libtalloc.so
├── armeabi-v7a/
│   ├── libproot.so
│   ├── libproot-loader.so
│   └── libtalloc.so
└── x86_64/
    ├── libproot.so
    ├── libproot-loader.so
    └── libtalloc.so
```

Reference: [Kai build-proot.sh](https://github.com/SimonSchubert/Kai/blob/main/build-proot.sh) — the build process is adapted from this project.

## Bundling the Alpine Rootfs

### Download

Download the Alpine mini rootfs for the target architecture:

```bash
# ARM64 (primary target)
curl -LO https://dl-cdn.alpinelinux.org/alpine/v3.20/releases/aarch64/alpine-minirootfs-3.20.0-aarch64.tar.gz

# Place into assets
cp alpine-minirootfs-3.20.0-aarch64.tar.gz app/src/main/assets/rootfs/alpine-minirootfs.tar.gz
```

### Version Management

When updating Alpine versions:

1. Update `ALPINE_VERSION` and `ALPINE_BRANCH` constants in `EmbeddedRuntime.kt`
2. Replace the rootfs tarball in `assets/rootfs/`
3. Update version strings in `setup_alpine_embedded.sh`
4. Rename any backup tarball to match the new version

## First-Run Extraction (Automatic)

When the app starts the embedded runtime for the first time, `EmbeddedRuntime.ensureRootfs()`:

1. Copies the bootstrap scripts from `assets/scripts/embedded/` to `filesDir/`
2. Extracts the rootfs tarball to `filesDir/rootfs/` (supports `.tar.gz` and `.tar`)
3. Makes all directories writable
4. Writes `/etc/resolv.conf` (DNS: 8.8.8.8, 8.8.4.4)
5. Writes `/etc/apk/repositories` (Alpine v3.20 main + community mirrors)

## Adding a New Alpine Version

Follow the [Kai pattern](https://github.com/SimonSchubert/Kai) of single-source version constants:

| Location | What to change |
|----------|---------------|
| `EmbeddedRuntime.kt` | `ALPINE_VERSION`, `ALPINE_BRANCH`, `Distro.Alpine.displayName` |
| `setup_alpine_embedded.sh` | Echo messages with version string |
| `assets/rootfs/` | Replace rootfs tarball, rename backup |
