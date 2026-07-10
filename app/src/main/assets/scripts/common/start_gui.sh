#!/data/data/com.termux/files/usr/bin/bash
# start_gui.sh — XFCE4 + termux-x11 for debian (NativeCode bootstrap proot)
#
# Root causes fixed:
# 1) ANDROID_DATA/ANDROID_ROOT must be set — app_process exits silently under proot if unset
# 2) XKB_CONFIG_ROOT required — Lorie exits if missing
# 3) TERMUX_X11_OVERRIDE_PACKAGE=com.ivarna.nativecode — ACTION_START hits embedded MainActivity
# 4) TMPDIR = $PREFIX/tmp (proot bind) so socket is shared with proot-distro --shared-tmp
# 5) loader.apk via CLASSPATH (stock wrapper hardcodes com.termux paths)

DISTRO=${1:-debian}

REAL_PREFIX="/data/data/com.ivarna.nativecode/files/termux-prefix"
REAL_TMP="/data/data/com.ivarna.nativecode/files/termux-tmp"
CANON_PREFIX="/data/data/com.termux/files/usr"

if [ -d "$CANON_PREFIX/bin" ]; then
  PREFIX="$CANON_PREFIX"
else
  PREFIX="$REAL_PREFIX"
fi

# Android runtime env required by app_process (missing in bootstrap proot)
export ANDROID_DATA="${ANDROID_DATA:-/data}"
export ANDROID_ROOT="${ANDROID_ROOT:-/system}"
export ANDROID_STORAGE="${ANDROID_STORAGE:-/storage}"
export EXTERNAL_STORAGE="${EXTERNAL_STORAGE:-/sdcard}"

# X socket lives in Termux tmp (proot bind target for --shared-tmp)
export TMPDIR="${PREFIX}/tmp"
mkdir -p "$TMPDIR/.X11-unix" "$TMPDIR/runtime-flux-xfce" \
         "$REAL_TMP/.X11-unix" "$REAL_TMP/runtime-flux-xfce" 2>/dev/null
chmod 1777 "$TMPDIR" "$TMPDIR/.X11-unix" 2>/dev/null
chmod 700 "$TMPDIR/runtime-flux-xfce" 2>/dev/null
export XDG_RUNTIME_DIR="$TMPDIR/runtime-flux-xfce"

# XKB
ROOTFS=""
for cand in \
  "$PREFIX/var/lib/proot-distro/containers/$DISTRO/rootfs" \
  "$REAL_PREFIX/var/lib/proot-distro/containers/$DISTRO/rootfs" \
  "$PREFIX/var/lib/proot-distro/installed-rootfs/$DISTRO" \
  "$REAL_PREFIX/var/lib/proot-distro/installed-rootfs/$DISTRO"
do
  [ -d "$cand/usr/share/X11/xkb" ] && ROOTFS="$cand" && break
done
[ -n "$ROOTFS" ] || { echo "ERROR: debian xkb not found"; exit 1; }
export XKB_CONFIG_ROOT="$ROOTFS/usr/share/X11/xkb"
export TERMUX_X11_OVERRIDE_PACKAGE="com.ivarna.nativecode"

socket_ready() {
  [ -S "$TMPDIR/.X11-unix/X0" ] || [ -S "$REAL_TMP/.X11-unix/X0" ]
}

# ── 1. Kill only XFCE leftovers (keep healthy X if present) ───────────────────
ps -A -o pid= -o args= 2>/dev/null | while read -r pid args; do
  case "$args" in
    *xfce4-session*|*startxfce4*|*xfwm4*|*xfdesktop*|*xfce4-panel*)
      kill -9 "$pid" 2>/dev/null ;;
  esac
done

# If no socket, kill stale Loader then restart X
if ! socket_ready; then
  ps -A -o pid= -o args= 2>/dev/null | while read -r pid args; do
    case "$args" in
      app_process*\ com.termux.x11.Loader*|*/app_process*\ com.termux.x11.Loader*)
        kill -9 "$pid" 2>/dev/null ;;
    esac
  done
  am broadcast -a com.termux.x11.ACTION_STOP -p com.ivarna.nativecode >/dev/null 2>&1
  am broadcast -a com.termux.x11.ACTION_STOP -p com.termux.x11 >/dev/null 2>&1
  sleep 1
  rm -f "$TMPDIR/.X0-lock" "$TMPDIR/.X11-unix/X0" \
        "$REAL_TMP/.X0-lock" "$REAL_TMP/.X11-unix/X0" 2>/dev/null
fi

# ── 2. PulseAudio ─────────────────────────────────────────────────────────────
pulseaudio --start \
  --load="module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1" \
  --exit-idle-time=-1 2>/dev/null || true

# ── 3. Start X if needed ──────────────────────────────────────────────────────
if ! socket_ready; then
  LOADER_APK="$PREFIX/libexec/termux-x11/loader.apk"
  [ -f "$LOADER_APK" ] || LOADER_APK="$REAL_PREFIX/libexec/termux-x11/loader.apk"
  [ -f "$LOADER_APK" ] || { echo "ERROR: loader.apk missing"; exit 1; }

  # Write outside-proot launcher (app_process under bootstrap proot dies / ghost-sockets)
  XLAUNCH="$REAL_TMP/x11_launch.sh"
  cat > "$XLAUNCH" <<EOF
#!/system/bin/sh
export ANDROID_DATA=/data
export ANDROID_ROOT=/system
export ANDROID_STORAGE=/storage
export EXTERNAL_STORAGE=/sdcard
export CLASSPATH="$LOADER_APK"
export TMPDIR="$REAL_TMP"
export XDG_RUNTIME_DIR="$REAL_TMP/runtime-flux-xfce"
export XKB_CONFIG_ROOT="$XKB_CONFIG_ROOT"
export TERMUX_X11_OVERRIDE_PACKAGE=com.ivarna.nativecode
mkdir -p "\$TMPDIR/.X11-unix" "\$XDG_RUNTIME_DIR"
chmod 1777 "\$TMPDIR/.X11-unix" 2>/dev/null
chmod 700 "\$XDG_RUNTIME_DIR" 2>/dev/null
rm -f "\$TMPDIR/.X11-unix/X0" "\$TMPDIR/.X0-lock"
unset LD_LIBRARY_PATH LD_PRELOAD
exec /system/bin/app_process -Xnoimage-dex2oat / \\
  --nice-name="termux-x11 com.termux.x11 :0" \\
  com.termux.x11.Loader :0 -ac
EOF
  chmod 755 "$XLAUNCH"

  # Try 1: direct (works if not broken by proot)
  env -u LD_LIBRARY_PATH -u LD_PRELOAD \
    ANDROID_DATA="$ANDROID_DATA" ANDROID_ROOT="$ANDROID_ROOT" \
    ANDROID_STORAGE="$ANDROID_STORAGE" EXTERNAL_STORAGE="$EXTERNAL_STORAGE" \
    CLASSPATH="$LOADER_APK" TMPDIR="$REAL_TMP" \
    XDG_RUNTIME_DIR="$REAL_TMP/runtime-flux-xfce" \
    XKB_CONFIG_ROOT="$XKB_CONFIG_ROOT" \
    TERMUX_X11_OVERRIDE_PACKAGE=com.ivarna.nativecode \
    /system/bin/app_process -Xnoimage-dex2oat / \
      --nice-name="termux-x11 com.termux.x11 :0" \
      com.termux.x11.Loader :0 -ac \
      >"$REAL_TMP/x11-start.log" 2>&1 &

  ok=0
  for _ in $(seq 1 16); do
    socket_ready && ok=1 && break
    sleep 0.5
  done

  # Try 2: re-exec launcher via /system/bin/sh (sometimes less proot interference)
  if [ "$ok" != 1 ]; then
    /system/bin/sh "$XLAUNCH" >"$REAL_TMP/x11-start.log" 2>&1 &
    for _ in $(seq 1 20); do
      socket_ready && ok=1 && break
      sleep 0.5
    done
  fi

  if [ "$ok" != 1 ]; then
    echo "ERROR: X socket not ready (TMPDIR=$TMPDIR ANDROID_DATA=$ANDROID_DATA)"
    echo "Bootstrap proot cannot host termux-x11 reliably. Log:"
    cat "$REAL_TMP/x11-start.log" 2>/dev/null
    ls -la "$TMPDIR/.X11-unix" "$REAL_TMP/.X11-unix" 2>&1
    exit 1
  fi
fi
# App watches this line and opens embedded com.termux.x11.MainActivity
echo "X11 ready"

# Optional re-raise from shell (app Intent is primary; REORDER_TO_FRONT only)
launch_x11_ui() {
  am start --user 0 \
    -n com.ivarna.nativecode/com.termux.x11.MainActivity \
    -f 0x20000000 \
    >/dev/null 2>&1 || true
}
( sleep 1; launch_x11_ui ) &
( sleep 4; launch_x11_ui ) &

# ── 5. XFCE (blocks this shell; terminal stays under X11) ─────────────────────
if [ "$DISTRO" = "termux" ]; then
  export DISPLAY=:0 PULSE_SERVER=127.0.0.1
  exec startxfce4
fi

proot-distro login "$DISTRO" --shared-tmp -- /bin/bash -lc '
  set -e
  mkdir -p /tmp/runtime-flux-xfce /tmp/.X11-unix
  # flux owns runtime dir (avoids ICE auth / XDG_RUNTIME_DIR uid mismatch black-screen)
  chown -R flux:flux /tmp/runtime-flux-xfce 2>/dev/null || true
  chmod 700 /tmp/runtime-flux-xfce
  rm -f /tmp/runtime-flux-xfce/ICEauthority /tmp/.ICEauthority 2>/dev/null || true
  export DISPLAY=:0
  export PULSE_SERVER=tcp:127.0.0.1
  export XDG_RUNTIME_DIR=/tmp/runtime-flux-xfce
  if [ ! -S /tmp/.X11-unix/X0 ]; then
    echo "ERROR: /tmp/.X11-unix/X0 missing in guest"
    ls -la /tmp/.X11-unix 2>&1 || true
    exit 1
  fi
  # Wait until X answers (MainActivity may still be attaching screen)
  ok=0
  for _ in $(seq 1 40); do
    if su -s /bin/bash flux -c "DISPLAY=:0 xdpyinfo" >/tmp/xdpy-start.txt 2>&1; then
      ok=1
      break
    fi
    sleep 0.5
  done
  if [ "$ok" != 1 ]; then
    echo "ERROR: X display :0 not usable"
    cat /tmp/xdpy-start.txt 2>/dev/null || true
    exit 1
  fi
  # Drop stale session leftovers that cause "Could not find a screen" / black desktop
  su -s /bin/bash flux -c "killall -9 xfwm4 xfdesktop xfce4-panel xfsettingsd xfce4-session 2>/dev/null" || true
  sleep 1
  exec su -s /bin/bash flux -c "
    mkdir -p /tmp/runtime-flux-xfce
    chmod 700 /tmp/runtime-flux-xfce
    export DISPLAY=:0
    export PULSE_SERVER=tcp:127.0.0.1
    export XDG_RUNTIME_DIR=/tmp/runtime-flux-xfce
    export HOME=/home/flux
    export USER=flux
    export LOGNAME=flux
    # Compositor often black-screens under termux-x11/proot
    xfconf-query -c xfwm4 -p /general/use_compositing -n -t bool -s false 2>/dev/null || true
    # Prefer known wallpaper path if present
    if [ -f /home/flux/Pictures/Wallpapers/nativecode-dark.png ]; then
      xfconf-query -c xfce4-desktop -p /backdrop/screen0/monitor0/workspace0/last-image \
        -n -t string -s /home/flux/Pictures/Wallpapers/nativecode-dark.png 2>/dev/null || true
      xfconf-query -c xfce4-desktop -p /backdrop/screen0/monitorbuiltin/workspace0/last-image \
        -n -t string -s /home/flux/Pictures/Wallpapers/nativecode-dark.png 2>/dev/null || true
    fi
    exec dbus-launch --exit-with-session startxfce4
  "
'

exit 0
