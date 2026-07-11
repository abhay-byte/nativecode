#!/data/data/com.termux/files/usr/bin/bash
# start_gui.sh — XFCE via proot-distro (NativeCode)
# Based on fluxlinux debian/proot/start/start_gui.sh (LinuxDroidMaster-style),
# adapted for embedded termux-x11 (com.ivarna.nativecode) + app-prestarted X.
#
# Fast path: app ensureXServer already has :0 → skip kill/restart → proot-distro XFCE.

DISTRO=${1:-debian}

REAL_PREFIX="/data/data/com.ivarna.nativecode/files/termux-prefix"
REAL_TMP="/data/data/com.ivarna.nativecode/files/termux-tmp"
CANON="/data/data/com.termux/files/usr"
if [ -d "$CANON/bin" ]; then PREFIX="$CANON"; else PREFIX="$REAL_PREFIX"; fi

export ANDROID_DATA="${ANDROID_DATA:-/data}"
export ANDROID_ROOT="${ANDROID_ROOT:-/system}"
export ANDROID_STORAGE="${ANDROID_STORAGE:-/storage}"
export EXTERNAL_STORAGE="${EXTERNAL_STORAGE:-/sdcard}"
export TMPDIR="${PREFIX}/tmp"
export TERMUX_X11_OVERRIDE_PACKAGE="com.ivarna.nativecode"

mkdir -p "$TMPDIR/.X11-unix" "$REAL_TMP/.X11-unix" \
         "$TMPDIR/runtime-flux-xfce" "$REAL_TMP/runtime-flux-xfce" 2>/dev/null
chmod 1777 "$TMPDIR" "$TMPDIR/.X11-unix" 2>/dev/null
chmod 700 "$TMPDIR/runtime-flux-xfce" 2>/dev/null

# XKB for Loader fallback only
for cand in \
  "$PREFIX/var/lib/proot-distro/containers/$DISTRO/rootfs/usr/share/X11/xkb" \
  "$REAL_PREFIX/var/lib/proot-distro/containers/$DISTRO/rootfs/usr/share/X11/xkb" \
  "$PREFIX/var/lib/proot-distro/installed-rootfs/$DISTRO/usr/share/X11/xkb" \
  "$REAL_PREFIX/var/lib/proot-distro/installed-rootfs/$DISTRO/usr/share/X11/xkb"
do
  [ -d "$cand" ] && export XKB_CONFIG_ROOT="$cand" && break
done

socket_ok() { [ -S "$TMPDIR/.X11-unix/X0" ] || [ -S "$REAL_TMP/.X11-unix/X0" ]; }

# ── Pulse (non-blocking) ─────────────────────────────────────────────────────
(
  pulseaudio --check 2>/dev/null && exit 0
  pulseaudio --start \
    --load="module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1" \
    --exit-idle-time=-1 2>/dev/null || true
) &

# ── VirGL optional (fluxlinux) — never block desktop ─────────────────────────
if command -v virgl_test_server_android >/dev/null 2>&1; then
  pgrep -f virgl_test_server >/dev/null 2>&1 || virgl_test_server_android >/dev/null 2>&1 &
fi

# ── X: keep app-prestarted socket (fluxlinux always restarts — we don't) ─────
if ! socket_ok; then
  # Only kill/restart when nothing is listening
  pkill -f 'com.termux.x11.Loader|CmdEntryPoint|termux.x11' 2>/dev/null || true
  rm -f "$TMPDIR/.X11-unix/X0" "$REAL_TMP/.X11-unix/X0" \
        "$TMPDIR/.X0-lock" "$REAL_TMP/.X0-lock" 2>/dev/null

  LOADER="$PREFIX/libexec/termux-x11/loader.apk"
  [ -f "$LOADER" ] || LOADER="$REAL_PREFIX/libexec/termux-x11/loader.apk"
  if [ -f "$LOADER" ]; then
    env -u LD_LIBRARY_PATH -u LD_PRELOAD \
      ANDROID_DATA=/data ANDROID_ROOT=/system \
      CLASSPATH="$LOADER" TMPDIR="$REAL_TMP" \
      XDG_RUNTIME_DIR="$REAL_TMP/runtime-flux-xfce" \
      XKB_CONFIG_ROOT="${XKB_CONFIG_ROOT:-}" \
      TERMUX_X11_OVERRIDE_PACKAGE=com.ivarna.nativecode \
      /system/bin/app_process -Xnoimage-dex2oat / \
        --nice-name="termux-x11 com.termux.x11 :0" \
        com.termux.x11.Loader :0 -ac \
        >"$REAL_TMP/x11-start.log" 2>&1 &
  fi
  # tight poll ≤2s (fluxlinux used fixed sleep 3)
  for _ in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do
    socket_ok && break
    sleep 0.1
  done
fi

# App may already show X11; still emit marker for TermuxTerminalScreen
echo "X11 ready"

# ── XFCE (fluxlinux core — no xdpyinfo, no long waits) ───────────────────────
if [ "$DISTRO" = "termux" ]; then
  export DISPLAY=:0 PULSE_SERVER=127.0.0.1 XDG_RUNTIME_DIR="$TMPDIR/runtime-flux-xfce"
  exec startxfce4
fi

# Same shape as fluxlinux start_gui.sh proot-distro block
exec proot-distro login "$DISTRO" --shared-tmp -- /bin/bash -c '
  export DISPLAY=:0
  export PULSE_SERVER=tcp:127.0.0.1
  # Private runtime (fluxlinux used /tmp; ICE/uid issues under multi-user)
  mkdir -p /tmp/runtime-flux-xfce
  chown -R flux:flux /tmp/runtime-flux-xfce 2>/dev/null || true
  chmod 700 /tmp/runtime-flux-xfce
  export XDG_RUNTIME_DIR=/tmp/runtime-flux-xfce
  export VTEST_SOCKET_NAME=/tmp/.virgl_test
  exec su - flux -c "
    export DISPLAY=:0
    export PULSE_SERVER=tcp:127.0.0.1
    export XDG_RUNTIME_DIR=/tmp/runtime-flux-xfce
    export VTEST_SOCKET_NAME=/tmp/.virgl_test
    export HOME=/home/flux
    export USER=flux
    # Black-screen fix (fluxlinux Turnip note)
    xfconf-query -c xfwm4 -p /general/use_compositing -s false 2>/dev/null || true
    exec dbus-launch --exit-with-session startxfce4
  "
'
