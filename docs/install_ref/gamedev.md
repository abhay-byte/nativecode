# Game Development Stack

*Script: `setup_gamedev_debian.sh`*

---

## Overview

Installs game development tools including the Godot Engine.

---

## Godot Engine

| Component | Source |
|-----------|--------|
| Godot 4.x | apt |

---

## GPU Acceleration

> ⚠️ **Important:** Godot does NOT work properly with VirGL. Use Turnip (Adreno GPUs only) for GPU acceleration.

```bash
# Run with GPU (Turnip only)
gpu-launch godot

# Without GPU (software rendering)
godot
```

---

## Verification

```bash
godot --version
```
