# IDE & Code Editors

*Scripts: `setup_vscode_debian.sh`, `setup_cursor_debian.sh`, `setup_antigravity_ide_debian.sh`, `setup_nexus_debian.sh`, `setup_windsurf_debian.sh`*

---

## Overview

Installs AI-powered code editors and IDEs on Debian ARM64. All editors are Electron-based and require `--no-sandbox` and `--disable-gpu` flags when running under PRoot.

---

## Common Dependencies

All IDE scripts install these shared dependencies:

```bash
libx11-xcb1 libxcb-dri3-0 libdrm2 libgbm1 libasound2
dbus-x11 gnome-keyring libtcmalloc-minimal4
```

> **Why these?** Electron apps on ARM64 Linux need these X11/GPU/libc libraries. `libtcmalloc_minimal` prevents crashes in PRoot environments. `dbus-x11` + `gnome-keyring` fix IPC and credential storage.

---

## Visual Studio Code

| Component | Version | Path |
|-----------|---------|------|
| VS Code | Latest stable | `/usr/share/code` |
| Binary | `/usr/share/code/bin/code` | Symlinked to `/usr/bin/code` |
| Wrapper | `/usr/local/bin/code-wrapper` | Adds `--no-sandbox --unity-launch` |

**Script:** `setup_vscode_debian.sh`

**Install Method:** Official ARM64 tarball (avoids dpkg crashes in PRoot)

**Wrapper Script:**
```bash
#!/bin/bash
exec /usr/share/code/bin/code --no-sandbox --unity-launch "$@"
```

**Shell Alias:**
```bash
alias code='code --no-sandbox --unity-launch'
```

**Settings:** Extension signature verification disabled (`extensions.verifySignature: false`)

---

## Cursor

| Component | Path |
|-----------|------|
| Installation | `/opt/cursor` |
| Wrapper | `/usr/local/bin/cursor` |

**Script:** `setup_cursor_debian.sh`

**Install Method:** Downloads ARM64 AppImage or tarball from Cursor CDN. Falls back to x86_64 if ARM64 unavailable.

**Wrapper Script:**
```bash
#!/bin/bash
CURSOR_PATH="/opt/cursor"
export LD_LIBRARY_PATH="$CURSOR_PATH:$LD_LIBRARY_PATH"
if [ -f "/usr/lib/aarch64-linux-gnu/libtcmalloc_minimal.so.4" ]; then
    export LD_PRELOAD="/usr/lib/aarch64-linux-gnu/libtcmalloc_minimal.so.4"
fi
exec dbus-launch --exit-with-session "$CURSOR_PATH/cursor" --no-sandbox --disable-gpu --disable-dev-shm-usage --password-store=basic "$@"
```

> **Note:** Cursor may not have ARM64 Linux builds available. The script attempts ARM64 first and falls back to x86_64.

---

## Antigravity

| Component | Path |
|-----------|------|
| Installation | `/usr/share/antigravity` |
| Wrapper | `/usr/bin/antigravity` |

**Script:** `setup_antigravity_ide_debian.sh`

**Install Method:** Downloads from Antigravity's apt repository, extracts .deb manually to bypass PRoot dpkg crashes.

**APT Repository:**
```
deb [signed-by=/etc/apt/keyrings/antigravity-repo-key.gpg] https://us-central1-apt.pkg.dev/projects/antigravity-auto-updater-dev/ antigravity-debian main
```

**Wrapper Script:**
```bash
#!/bin/bash
ANTIGRAVITY_PATH="/usr/share/antigravity"
export LD_LIBRARY_PATH="$ANTIGRAVITY_PATH:$LD_LIBRARY_PATH"
if [ -f "/usr/lib/aarch64-linux-gnu/libtcmalloc_minimal.so.4" ]; then
    export LD_PRELOAD="/usr/lib/aarch64-linux-gnu/libtcmalloc_minimal.so.4"
fi
exec dbus-launch --exit-with-session "$ANTIGRAVITY_PATH/bin/antigravity" --no-sandbox --disable-gpu --disable-dev-shm-usage --password-store=basic "$@"
```

---

## Nexus (NativeCode IDE)

| Component | Path |
|-----------|------|
| Installation | `/opt/nexus` |
| Wrapper | `/usr/local/bin/nexus` |

**Script:** `setup_nexus_debian.sh`

**Install Method:** Downloads ARM64 tarball from GitHub releases.

> **Note:** Download URL and version are placeholders. Replace with actual Nexus release details.

**Wrapper Script:**
```bash
#!/bin/bash
NEXUS_PATH="/opt/nexus"
export LD_LIBRARY_PATH="$NEXUS_PATH:$LD_LIBRARY_PATH"
if [ -f "/usr/lib/aarch64-linux-gnu/libtcmalloc_minimal.so.4" ]; then
    export LD_PRELOAD="/usr/lib/aarch64-linux-gnu/libtcmalloc_minimal.so.4"
fi
exec dbus-launch --exit-with-session "$NEXUS_PATH/nexus" --no-sandbox --disable-gpu --disable-dev-shm-usage "$@"
```

---

## Windsurf (Codeium)

| Component | Path |
|-----------|------|
| Installation | `/opt/windsurf` |
| Wrapper | `/usr/local/bin/windsurf` |

**Script:** `setup_windsurf_debian.sh`

**Install Method:** Downloads ARM64 tarball from Windsurf CDN. Falls back to x86_64 if ARM64 unavailable.

**Wrapper Script:**
```bash
#!/bin/bash
WINDSURF_PATH="/opt/windsurf"
export LD_LIBRARY_PATH="$WINDSURF_PATH:$LD_LIBRARY_PATH"
if [ -f "/usr/lib/aarch64-linux-gnu/libtcmalloc_minimal.so.4" ]; then
    export LD_PRELOAD="/usr/lib/aarch64-linux-gnu/libtcmalloc_minimal.so.4"
fi
exec dbus-launch --exit-with-session "$WINDSURF_PATH/windsurf" --no-sandbox --disable-gpu --disable-dev-shm-usage --password-store=basic "$@"
```

**Settings:** Extension signature verification disabled.

---

## Common Patterns

All IDE install scripts follow this structure:

1. **Error Handler** — Prints step, sends callback to app
2. **Dependencies** — Installs X11/GPU/libc libraries via apt
3. **Already-Installed Check** — Skips download if binary exists
4. **Download** — ARM64 tarball, .deb, or AppImage
5. **Extract** — To `/opt/<name>` or `/usr/share/<name>`
6. **Wrapper Script** — Adds `--no-sandbox --disable-gpu --disable-dev-shm-usage --password-store=basic` and `LD_PRELOAD=libtcmalloc_minimal`
7. **Desktop Entry** — `.desktop` file in `/usr/share/applications`
8. **Idempotency Marker** — `/home/flux/.nativecode/<name>_installed`
9. **Callback** — `nativecode://callback?result=success&name=ide_tools_<name>`

### PRoot Compatibility Flags

All Electron-based editors require these runtime flags under PRoot:

| Flag | Purpose |
|------|---------|
| `--no-sandbox` | Disables Chrome sandbox (required in PRoot) |
| `--disable-gpu` | Prevents GPU-related crashes |
| `--disable-dev-shm-usage` | Avoids /dev/shm size issues |
| `--password-store=basic` | Fixes keyring issues without gnome-keyring |
| `dbus-launch --exit-with-session` | Provides D-Bus session for IPC |

### Memory Crash Fix

All wrapper scripts preload `libtcmalloc_minimal.so.4` to prevent MmapAligned/SIGABRT crashes:
```bash
export LD_PRELOAD="/usr/lib/aarch64-linux-gnu/libtcmalloc_minimal.so.4"
```

---

## Summary Table

| IDE | Ships ARM64? | Install Method | PRoot Flags |
|-----|-------------|---------------|-------------|
| VS Code | ✅ | Official tarball | --no-sandbox |
| Cursor | ⚠️ Partial | AppImage/tarball | --no-sandbox --disable-gpu |
| Antigravity | ✅ | apt repo (.deb extraction) | --no-sandbox --disable-gpu |
| Nexus | ✅ (custom) | GitHub tarball | --no-sandbox --disable-gpu |
| Windsurf | ⚠️ Partial | CDN tarball | --no-sandbox --disable-gpu |