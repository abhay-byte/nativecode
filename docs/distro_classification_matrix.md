# Distro Classification Matrix

This document outlines the classification of all Linux distributions tracked by FluxLinux, based on the `DistroSpec` architecture.

| Distro Name | ID | Family | Package Manager | Release Type | Implementation Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Debian** | `debian` | **DEBIAN** | APT | FIXED | ✅ **Implemented** |
| **Ubuntu** | `ubuntu` | **DEBIAN** | APT | FIXED | ✅ **Implemented** |
| **Adélie Linux** | `adelie` | ALPINE-like | APK | FIXED | 🚧 Planned |
| **Alpine Linux** | `alpine` | ALPINE | APK | FIXED | ✅ **Implemented** |
| **Arch Linux** | `arch` | ARCH | PACMAN | ROLLING | 🚧 Planned |
| **Artix Linux** | `artix` | ARCH | PACMAN | ROLLING | 🚧 Planned |
| **BackBox** | `backbox` | DEBIAN | APT | FIXED | 🚧 Planned |
| **CentOS Stream** | `centos_stream` | REDHAT | DNF | SEMI-ROLLING | 🚧 Planned |
| **Chimera Linux** | `chimera` | OTHER (BSD/Linux) | APK | ROLLING | 🚧 Planned |
| **Deepin** | `deepin` | DEBIAN | APT | FIXED | 🚧 Planned |
| **Fedora** | `fedora` | FEDORA | DNF | SEMI-ROLLING | 🚧 Planned |
| **Gentoo** | `gentoo` | GENTOO | PORTAGE | ROLLING | 🚧 Planned |
| **Kali Linux** | `kali` | DEBIAN | APT | ROLLING | 🚧 Planned |
| **Manjaro** | `manjaro` | ARCH | PACMAN | ROLLING | 🚧 Planned |
| **OpenKylin** | `openkylin` | DEBIAN | APT | FIXED | 🚧 Planned |
| **OpenSUSE** | `opensuse` | SUSE | ZYPPER | ROLLING/FIXED | 🚧 Planned |
| **Parrot OS** | `parrot` | DEBIAN | APT | ROLLING | 🚧 Planned |
| **Rocky Linux** | `rocky` | REDHAT | DNF | FIXED | 🚧 Planned |
| **Void Linux** | `void` | VOID | XBPS | ROLLING | 🚧 Planned |

## Classification Legend

*   **Family**: Grouping logic for setup scripts (e.g., `setup_debian_family.sh`).
*   **Package Manager**: Determines the commands for installing software (`apt`, `pacman`, etc.).
*   **Release Type**: Indicates stability model (Fixed Release vs Rolling Release).

## Implementation Roadmap

Currently, the `DEBIAN` family logic is fully implemented with a reusable `setup_debian_family.sh` script, and the `ALPINE` family is implemented with `setup_alpine_family.sh` (Alpine, Adélie). Alpine Linux 3.20.0 is the **default embedded proot runtime** — bundled in `assets/rootfs/alpine-minirootfs.tar.gz`. Future work will involve creating similar generic setup scripts for other major families:

1.  **ARCH Family**: `setup_arch_family.sh` (Arch, Artix, Manjaro)
2.  **FEDORA/REDHAT Family**: `setup_fedora_family.sh` (Fedora, Rocky, CentOS)
