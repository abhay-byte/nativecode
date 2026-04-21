# Hardware Acceleration

*Script: `setup_hw_accel_debian.sh`*

---

## Overview

Sets up GPU acceleration for graphics-intensive applications using Turnip or VirGL.

---

## GPU Drivers

| Driver | GPU Support | Performance |
|--------|-------------|-------------|
| Turnip + Zink | Adreno (Qualcomm) | 🟢 Excellent |
| VirGL | All GPUs | 🟡 Good |

---

## gpu-launch Wrapper

| Component | Path |
|-----------|------|
| Wrapper Script | `/usr/local/bin/gpu-launch` |

---

## Usage

```bash
# Run any app with GPU acceleration
gpu-launch <application>

# Examples
gpu-launch glxgears      # Test GPU
gpu-launch blender       # Run Blender
gpu-launch gimp          # Run GIMP
gpu-launch godot         # Run Godot
```

---

## Compatibility

| Application | VirGL | Turnip |
|-------------|-------|--------|
| GIMP | 🟢 | 🟢 |
| Inkscape | 🟢 | 🟢 |
| Kdenlive | 🟢 | 🟢 |
| Blender | 🔴 | 🟢 |
| Godot | 🔴 | 🟢 |
| XFCE4 | 🟢 | 🟢 |

> ⚠️ Blender and Godot require Turnip (Adreno GPUs only) for GPU acceleration.

---

## Environment Variables

```bash
# Set by gpu-launch automatically
MESA_GL_VERSION_OVERRIDE=4.6
MESA_GLSL_VERSION_OVERRIDE=460
GALLIUM_DRIVER=zink  # or virgl
```

---

## Verification

```bash
gpu-launch glxinfo | grep "OpenGL renderer"
gpu-launch glxgears
```
