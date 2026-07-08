#!/data/data/com.termux/files/usr/bin/bash
# start_gui_alpine.sh — Launch XFCE4 inside embedded Alpine proot via Termux:X11
# Run this from Termux after "Setup XFCE4" completes inside the Alpine terminal.
# Mirrors the Debian PRoot pattern from common/start_gui.sh

NATIVECODE_PKG="com.ivarna.nativecode"
NATIVECODE_DATA="/data/data/${NATIVECODE_PKG}"
PROOT="${NATIVECODE_DATA}/lib/libproot.so"
ROOTFS="${NATIVECODE_DATA}/files/rootfs"
LOADER="${NATIVECODE_DATA}/lib/libproot-loader.so"
TMPDIR="${NATIVECODE_DATA}/files/tmp"
TALLOC="${NATIVECODE_DATA}/files/libtalloc.so.2"

# Kill open X11 processes
kill -9 $(pgrep -f "termux.x11") 2>/dev/null

# Enable PulseAudio over Network
pulseaudio --start --load="module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1" --exit-idle-time=-1

# Prepare termux-x11 session
export XDG_RUNTIME_DIR=${TMPDIR}
termux-x11 :0 >/dev/null &
sleep 3

# Launch Termux X11 main activity
am start --user 0 -n com.termux.x11/com.termux.x11.MainActivity > /dev/null 2>&1
sleep 1

# Check that proot and rootfs exist
if [ ! -f "$PROOT" ]; then
    echo "[!] Proot binary not found at $PROOT"
    echo "    Is NativeCode installed?"
    exit 1
fi
if [ ! -d "$ROOTFS" ]; then
    echo "[!] Alpine rootfs not found at $ROOTFS"
    echo "    Open the Alpine terminal in NativeCode first to extract it."
    exit 1
fi

# Launch XFCE4 inside Alpine embedded proot
PROOT_LOADER=$LOADER \
LD_LIBRARY_PATH="${NATIVECODE_DATA}/files:${NATIVECODE_DATA}/lib" \
$PROOT \
  --rootfs="$ROOTFS" \
  --bind=/dev --bind=/proc --bind=/sys \
  --bind=/data/data/com.termux/files/usr/tmp/.X11-unix:"$ROOTFS"/tmp/.X11-unix \
  --bind=/sdcard/Android/data/${NATIVECODE_PKG}/files:/sdcard \
  --bind="$TMPDIR":/tmp \
  --bind="${NATIVECODE_DATA}/files/home":/root \
  -0 -w /root \
  /bin/su - flux -c '
    export DISPLAY=:0
    export PULSE_SERVER=tcp:127.0.0.1
    export XDG_RUNTIME_DIR=/tmp
    sh /home/flux/start_xfce4.sh
  '

exit 0
