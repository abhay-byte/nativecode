# Graphic Design Stack

*Script: `setup_graphic_design_debian.sh`*

---

## Overview

Installs professional graphic design, digital art, and 3D creation tools.

---

## Raster & Vector Editors

| Tool | Category | Purpose |
|------|----------|---------|
| GIMP | Raster | Image manipulation (Photoshop alternative) |
| Inkscape | Vector | Vector graphics (Illustrator alternative) |
| Krita | Digital Art | Painting & animation |

---

## Photography & Publishing

| Tool | Purpose |
|------|---------|
| Darktable | RAW developer (Lightroom alternative) |
| Scribus | Desktop publishing (InDesign alternative) |

---

## 3D & Utilities

| Tool | Purpose |
|------|---------|
| Blender | 3D modeling, animation, rendering |
| ImageMagick | CLI image processing |
| FontForge | Font editor |

> ⚠️ **Note:** Blender does NOT work with VirGL. Use Turnip (Adreno) for GPU acceleration.

---

## Fonts

| Package | Type |
|---------|------|
| fonts-noto | Multi-language |
| fonts-liberation | MS-compatible |
| fonts-dejavu | Classic sans/serif |

---

## Packages Installed

```bash
gimp inkscape krita
darktable scribus
blender imagemagick fontforge
fonts-noto fonts-liberation fonts-dejavu
```

---

## GPU Acceleration

For best performance with GPU-intensive apps:
```bash
gpu-launch gimp      # GIMP with GPU
gpu-launch blender   # Blender (Turnip only)
gpu-launch krita     # Krita with GPU
```

---

## Verification

```bash
gimp --version
inkscape --version
blender --version
```
