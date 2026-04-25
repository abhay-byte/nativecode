# Web Development Stack

*Script: `setup_webdev_debian.sh`*

---

## Overview

Installs Node.js, Python, VS Code, and browsers for web development.

---

## Node.js

| Component | Version | Source |
|-----------|---------|--------|
| Node.js | 23.x | NodeSource |
| npm | Bundled | - |

> **Why v23?** Latest LTS-adjacent with best ARM64 support and modern ESM features.

---

## Python

| Component | Version | Path |
|-----------|---------|------|
| Python | 3.12+ | `/usr/bin/python3` |
| pip | Latest | `/usr/bin/pip3` |
| venv | Built-in | - |

---

## VS Code

| Component | Path |
|-----------|------|
| Installation | `/usr/share/code` |
| Binary | `/usr/bin/code` |

> **Note:** VS Code can also be installed as a standalone IDE from the IDE Tools section. The webdev script and the standalone script share the same installation paths.

**Installation Notes:**
- Installed via tarball (avoids dpkg crashes in PRoot)
- Runs with `--no-sandbox` flag in PRoot
- Extension signature verification disabled

**Alias added to `.bashrc`:**
```bash
alias code='code --no-sandbox --unity-launch'
```

---

## Browsers

| Browser | Source |
|---------|--------|
| Firefox | Mozilla Official Repo |
| Chromium | Debian apt |

> Firefox uses Mozilla's official ARM64 repo for latest version.

---

## Packages Installed

```bash
curl wget git build-essential gnupg
firefox chromium
nodejs
python3 python3-pip python3-venv
```

---

## Verification

```bash
node --version
npm --version
python3 --version
code --version
firefox --version
```
