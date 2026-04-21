# Video Editing Stack

*Script: `setup_video_editing_debian.sh`*

---

## Overview

Installs video editing and processing tools.

---

## Applications

| Tool | Purpose |
|------|---------|
| Kdenlive | Professional non-linear video editor |
| Pitivi | Simple, beginner-friendly video editor |
| FFmpeg | CLI video processing |

---

## GPU Acceleration

```bash
# Run video editors with GPU
gpu-launch kdenlive
gpu-launch pitivi
```

---

## FFmpeg Usage

```bash
# Convert format
ffmpeg -i input.mp4 output.avi

# Extract audio
ffmpeg -i video.mp4 -vn audio.mp3

# Resize video
ffmpeg -i input.mp4 -vf scale=1280:720 output.mp4

# Cut video
ffmpeg -i input.mp4 -ss 00:00:30 -to 00:01:00 -c copy output.mp4
```

---

## Verification

```bash
kdenlive --version
pitivi --version
ffmpeg -version
```
