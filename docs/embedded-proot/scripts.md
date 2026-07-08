# Embedded Scripts Reference

Two scripts run inside the Alpine proot environment to set it up for development work. They are bundled in `app/src/main/assets/scripts/embedded/` and copied to `filesDir/` on first run.

## `setup_alpine_embedded.sh`

The main Alpine provisioning script. Runs after rootfs extraction to install packages and configure the environment.

### What it installs

| Category | Packages |
|----------|----------|
| Shells | `bash`, `coreutils` |
| Editors | `nano` |
| Network | `curl`, `wget` |
| Version control | `git` |
| Monitoring | `procps`, `htop` |
| Build tools | `build-base`, `make`, `cmake` |
| Languages | `python3`, `py3-pip`, `nodejs`, `npm` |

### Profile

Writes `/etc/profile.d/nativecode.sh` with:

```sh
export HOME=/root
export PATH=/usr/local/sbin:/usr/local/bin:/sbin:/usr/sbin:/bin:/usr/bin
export TERM=xterm-256color
export LANG=C.UTF-8
export LD_PRELOAD=/data/data/com.ivarna.nativecode/files/libmemfd_shim.so
```

### Flags

All `apk add` commands use:

| Flag | Purpose |
|------|---------|
| `--no-cache` | Skip local cache commit (avoids hardlink errors inside proot bind-mounts) |
| `--force-overwrite` | Overwrite files from conflicting packages without aborting |
| `--no-progress` | Keep terminal output compact |

## `setup_debian_proot.sh`

Debian 13 (trixie) bootstrap script. Not bundled — downloaded on first use. Must be run from inside the Alpine proot. Download ~25 MB.

Script location: `assets/scripts/embedded/setup_debian_proot.sh` (also copied to `filesDir/`)

### How to run

```sh
# Inside the Alpine proot
sh /data/data/com.ivarna.nativecode/files/setup_debian_proot.sh
```

## Alpine Repositories

Written to `/etc/apk/repositories` by `EmbeddedRuntime.writeAlpineRepositories()`:

```
https://dl-cdn.alpinelinux.org/alpine/v3.20/main
https://dl-cdn.alpinelinux.org/alpine/v3.20/community
```

## `common/setup_alpine_family.sh`

Shared setup script for Alpine-family distros (Alpine, Adélie). Installs XFCE4 desktop + TigerVNC.

### What it installs

- `shadow`, `sudo`, `bash`
- `dbus`, `dbus-x11`
- `xfce4`, `xfce4-terminal`, `xfconf`
- `tigervnc`, `font-dejavu`

### User Setup

- Creates `flux` user (password: `flux`)
- Adds `flux` to `wheel` group
- Configures passwordless sudo
- Writes VNC `xstartup` that launches `startxfce4` via `dbus-launch`

### Reference

Used by the Termux integration flow via `TermuxIntentFactory` and `DistroRepository` for the `alpine` distro entry.
