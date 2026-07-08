# Embedded Proot Runtime

NativeCode ships a full Linux userspace inside the app using [proot](https://proot-me.github.io/) — no root required, no separate Termux install. Alpine Linux 3.20.0 is the default runtime, bundled as a mini rootfs tarball. Debian 13 (trixie) is supported as a remote-downloaded alternative.

## Quick Links

| Doc | What it covers |
|-----|---------------|
| [Architecture](./architecture.md) | System layers, data flow, component diagram |
| [Setup Guide](./setup.md) | Building proot from source, bundling rootfs, first-run extraction |
| [Runtime API](./runtime-api.md) | `EmbeddedRuntime` Kotlin API reference |
| [Scripts Reference](./scripts.md) | Embedded setup scripts, Alpine repositories config |

## Version Constants

Alpine version is defined in two places for the embedded runtime:

- **`EmbeddedRuntime.kt`**: `ALPINE_VERSION = "3.20.0"`, `ALPINE_BRANCH = "v3.20"` — controls repo URLs and display name
- **`setup_alpine_embedded.sh`**: echo messages reference `3.20` for status output

## Asset Layout

```
app/src/main/assets/rootfs/
├── alpine-minirootfs.tar.gz                # Active Alpine rootfs (unversioned)
└── alpine-minirootfs-3.20.0-backup.tar.gz   # Previous release backup
```
