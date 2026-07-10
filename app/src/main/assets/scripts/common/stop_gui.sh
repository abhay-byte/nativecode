#!/data/data/com.termux/files/usr/bin/bash
# stop_gui.sh - Stop XFCE4 Desktop Environment (PRoot + embedded NativeCode X11)

DISTRO=${1:-debian}

echo "========================================"
echo "NativeCode: Stopping GUI for $DISTRO"
echo "========================================"

# Step 1: Kill XFCE processes inside proot
echo "[1/3] Stopping XFCE4 processes..."
if [ "$DISTRO" = "termux" ]; then
    killall -9 xfce4-session xfwm4 xfdesktop xfce4-panel dbus-launch 2>/dev/null
else
    proot-distro login "$DISTRO" -- bash -c \
      'killall -9 xfce4-session xfwm4 xfdesktop xfce4-panel startxfce4 2>/dev/null' 2>/dev/null
fi
# Also host-side names (proot processes visible on host)
for p in $(ps -A -o pid= -o args= 2>/dev/null | awk '/xfce4-session|xfwm4|xfdesktop|xfce4-panel|startxfce4/{print $1}'); do
  kill -9 "$p" 2>/dev/null
done

# Step 2: Stop X11 display activity + X server
echo "[2/3] Stopping X11..."
# Embedded NativeCode package first
am broadcast -a com.termux.x11.ACTION_STOP -p com.ivarna.nativecode >/dev/null 2>&1
am broadcast -a com.termux.x11.ACTION_STOP -p com.termux.x11 >/dev/null 2>&1
# Kill X server processes (CmdEntryPoint / Loader)
for p in $(ps -A -o pid= -o args= 2>/dev/null | awk '/com.termux.x11.CmdEntryPoint|com.termux.x11.Loader|termux-x11 com.termux/{print $1}'); do
  kill -9 "$p" 2>/dev/null
done
rm -f "${TMPDIR:-/data/data/com.termux/files/usr/tmp}/.X11-unix/X0" 2>/dev/null
rm -f /data/data/com.ivarna.nativecode/files/termux-tmp/.X11-unix/X0 2>/dev/null

# Step 3: PulseAudio (optional)
echo "[3/3] PulseAudio..."
pulseaudio --kill 2>/dev/null || true

echo ""
echo "✅ GUI stopped successfully!"
echo "========================================"
exit 0
