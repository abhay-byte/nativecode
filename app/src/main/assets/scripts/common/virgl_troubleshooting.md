# VirGL Server Troubleshooting Guide

## Issue: "lost connection to rendering server"

This error occurs when the VirGL server isn't running or can't be reached.

## Quick Diagnostics

### 1. Check if VirGL server is installed
```bash
# In Termux
which virgl_test_server_android
ls -la $PREFIX/bin/virgl*
```

### 2. Check if server is running
```bash
# In Termux
ps aux | grep virgl
pgrep -f virgl_test_server
```

### 3. Manually start the server
```bash
# In Termux
pkill -9 -f virgl_test_server
nohup setsid virgl_test_server_android >/dev/null 2>&1 &
sleep 2
ps aux | grep virgl
```

### 4. Test from Debian
```bash
# Inside Debian container
gpu-launch glxinfo | grep "OpenGL renderer"
gpu-launch glmark2
```

## Common Issues

### Server not installed
**Solution:** Run setup_termux.sh to install virglrenderer-android

### Server dies immediately
**Possible causes:**
- Missing dependencies
- Conflicting X11 server
- Permission issues

**Solution:** Check logs:
```bash
# Try running server in foreground to see errors
virgl_test_server_android
```

### Connection refused from container
**Possible causes:**
- DISPLAY variable not set
- XDG_RUNTIME_DIR not set
- Socket permissions

**Solution:** Verify environment:
```bash
# Inside Debian
echo $DISPLAY
echo $XDG_RUNTIME_DIR
ls -la $XDG_RUNTIME_DIR/.X11-unix/
```

## Manual Server Management

### Start server
```bash
nohup setsid virgl_test_server_android >/dev/null 2>&1 &
```

### Stop server
```bash
pkill -9 -f virgl_test_server
```

### Check status
```bash
pgrep -f virgl_test_server && echo "Running" || echo "Not running"
```
