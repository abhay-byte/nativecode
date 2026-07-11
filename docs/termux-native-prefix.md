# Termux-like shell inside NativeCode

## Why stock Termux is faster

Real Termux (`com.termux`) starts:

```text
execve(/data/data/com.termux/files/usr/bin/bash, …)
```

NativeCode used to start:

```text
execve(/system/bin/linker64 → libproot.so → bind-mount → bash)
```

**Proot rewrites syscalls** → much slower interactive use and package tools.

## What we do now (default)

1. Extract official bootstrap under `files/termux-prefix`.
2. Rewrite **scripts/text** so `com.termux` paths point at  
   `/data/data/com.ivarna.nativecode/files/…`.
3. Start shell **like Termux**, without outer proot:

```text
/system/bin/linker64  <prefix>/bin/bash
  PREFIX=<prefix>
  HOME=<termux-home>
  LD_LIBRARY_PATH=<prefix>/lib
```

`linker64` is required because SELinux often blocks a plain `execve` of app-private PIE binaries on modern `targetSdk`.

If direct mode fails at startup, we **fall back to proot** automatically.

## Full “recompile for NativeCode package id”

Official debs **hardcode** `/data/data/com.termux/files/usr` in ELFs.  
Our package path is **longer**, so you cannot fully rewrite every binary in place.

To rebuild packages for this app (true stock-speed + working `apt` for custom prefix):

```bash
git clone https://github.com/termux/termux-packages.git
cd termux-packages
# scripts/properties.sh
# TERMUX_APP_PACKAGE="com.ivarna.nativecode"
# TERMUX_BASE_DIR="/data/data/${TERMUX_APP_PACKAGE}/files"
# TERMUX_PREFIX="${TERMUX_BASE_DIR}/usr"

# Then build bootstrap (Docker recommended, multi-hour):
./scripts/run-docker.sh ./scripts/build-bootstraps.sh -a aarch64
```

Host the resulting `bootstrap-aarch64.zip` and point  
`TermuxBootstrapManager.BOOTSTRAP_BASE_URL` at that release.

## Nested Debian

`proot-distro login debian` still uses **one** proot (guest).  
It should run **inside** the direct Termux shell, not under a second outer proot when possible.
