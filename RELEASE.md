# v1.7 — Embedded Alpine Linux Runtime

Shipped in this release:

- **In-app Alpine Linux 3.20 terminal** powered by an embedded `proot` userspace engine and `xterm.js` WebView renderer. Single APK, no Termux / Termux:X11 dependency.
- **`EmbeddedRuntime` + `ShellSession` core** — proot invocation, Alpine rootfs extraction (tar.gz), interactive PTY, ANSI streaming to the terminal.
- **Dashboard entry point** — new "Alpine Linux · Embedded Proot Runtime" card on the home screen, with `Setup Alpine` and `Update Pkgs` quick actions.
- **Bundled native binaries** (`libproot.so`, `libproot_loader.so`, `libtalloc.so`, `libandroid-shmem.so`) shipped as `jniLibs/arm64-v8a` so the OS marks them executable under Android 14+ W^X.
- **Alpine minirootfs + setup script** (`apk add` bash, coreutils, nano, curl, wget, git, htop, build-base, python3, nodejs) shipped in `assets/`.
- **JetBrainsMono Nerd Font** for proper glyph rendering in the terminal.
- **Commons Compress** dep for streaming the rootfs tarball.

## Commits
- `19f00f0` feat: embedded alpine runtime with proot-backed in-app terminal
- `b02ee28` chore: bump to v1.7 (versionCode 9)

## Install
```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

## Artifact
- `app-release.apk` — 27 MB, minSdk 26, targetSdk 36, arm64-v8a only.
