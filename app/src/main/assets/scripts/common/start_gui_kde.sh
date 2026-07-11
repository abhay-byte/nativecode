#!/data/data/com.termux/files/usr/bin/bash
# start_gui_kde.sh — KDE Plasma + termux-x11 (NativeCode bootstrap proot)
# Same embedding rules as start_gui.sh (no am-start X11 UI; no ACTION_STOP finish).

DISTRO=${1:-debian}

REAL_PREFIX="/data/data/com.ivarna.nativecode/files/termux-prefix"
REAL_TMP="/data/data/com.ivarna.nativecode/files/termux-tmp"
CANON_PREFIX="/data/data/com.termux/files/usr"

if [ -d "$CANON_PREFIX/bin" ]; then
  PREFIX="$CANON_PREFIX"
else
  PREFIX="$REAL_PREFIX"
fi

export ANDROID_DATA="${ANDROID_DATA:-/data}"
export ANDROID_ROOT="${ANDROID_ROOT:-/system}"
export ANDROID_STORAGE="${ANDROID_STORAGE:-/storage}"
export EXTERNAL_STORAGE="${EXTERNAL_STORAGE:-/sdcard}"

export TMPDIR="${PREFIX}/tmp"
mkdir -p "$TMPDIR/.X11-unix" "$TMPDIR/runtime-flux-kde" \
         "$REAL_TMP/.X11-unix" "$REAL_TMP/runtime-flux-kde" 2>/dev/null
chmod 1777 "$TMPDIR" "$TMPDIR/.X11-unix" 2>/dev/null
chmod 700 "$TMPDIR/runtime-flux-kde" 2>/dev/null
export XDG_RUNTIME_DIR="$TMPDIR/runtime-flux-kde"

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

# Kill Plasma leftovers only
ps -A -o pid= -o args= 2>/dev/null | while read -r pid args; do
  case "$args" in
    *startplasma*|*plasmashell*|*kwin_x11*|*kded5*)
      kill -9 "$pid" 2>/dev/null ;;
  esac
done

if ! socket_ready; then
  ps -A -o pid= -o args= 2>/dev/null | while read -r pid args; do
    case "$args" in
      app_process*\ com.termux.x11.Loader*|*com.termux.x11.CmdEntryPoint*)
        kill -9 "$pid" 2>/dev/null ;;
    esac
  done
  sleep 1
  rm -f "$TMPDIR/.X0-lock" "$TMPDIR/.X11-unix/X0" \
        "$REAL_TMP/.X0-lock" "$REAL_TMP/.X11-unix/X0" 2>/dev/null
fi

pulseaudio --start \
  --load="module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1" \
  --exit-idle-time=-1 2>/dev/null || true

if ! socket_ready; then
  LOADER_APK="$PREFIX/libexec/termux-x11/loader.apk"
  [ -f "$LOADER_APK" ] || LOADER_APK="$REAL_PREFIX/libexec/termux-x11/loader.apk"
  [ -f "$LOADER_APK" ] || { echo "ERROR: loader.apk missing"; exit 1; }

  env -u LD_LIBRARY_PATH -u LD_PRELOAD \
    ANDROID_DATA="$ANDROID_DATA" ANDROID_ROOT="$ANDROID_ROOT" \
    ANDROID_STORAGE="$ANDROID_STORAGE" EXTERNAL_STORAGE="$EXTERNAL_STORAGE" \
    CLASSPATH="$LOADER_APK" TMPDIR="$REAL_TMP" \
    XDG_RUNTIME_DIR="$REAL_TMP/runtime-flux-kde" \
    XKB_CONFIG_ROOT="$XKB_CONFIG_ROOT" \
    TERMUX_X11_OVERRIDE_PACKAGE=com.ivarna.nativecode \
    /system/bin/app_process -Xnoimage-dex2oat / \
      --nice-name="termux-x11 com.termux.x11 :0" \
      com.termux.x11.Loader :0 -ac \
      >"$REAL_TMP/x11-start.log" 2>&1 &

  ok=0
  for _ in $(seq 1 30); do
    socket_ready && ok=1 && break
    sleep 0.5
  done
  if [ "$ok" != 1 ]; then
    echo "ERROR: X socket not ready"
    cat "$REAL_TMP/x11-start.log" 2>/dev/null
    exit 1
  fi
fi

echo "X11 ready"

proot-distro login "$DISTRO" --shared-tmp -- /bin/bash -lc '
  set -e
  mkdir -p /tmp/runtime-flux-kde /tmp/.X11-unix
  chown -R flux:flux /tmp/runtime-flux-kde 2>/dev/null || true
  chmod 700 /tmp/runtime-flux-kde
  export DISPLAY=:0
  export PULSE_SERVER=tcp:127.0.0.1
  export XDG_RUNTIME_DIR=/tmp/runtime-flux-kde
  export GALLIUM_DRIVER=zink
  export MESA_LOADER_DRIVER_OVERRIDE=zink
  export TU_DEBUG=noconform
  export ZINK_NO_TIMELINES=1
  export KWIN_OPENGL_INTERFACE=egl
  export KWIN_COMPOSE=Q
  if [ ! -S /tmp/.X11-unix/X0 ]; then
    echo "ERROR: /tmp/.X11-unix/X0 missing in guest"
    exit 1
  fi
  exec su -s /bin/bash flux -c "
    export DISPLAY=:0
    export PULSE_SERVER=tcp:127.0.0.1
    export XDG_RUNTIME_DIR=/tmp/runtime-flux-kde
    export HOME=/home/flux
    export USER=flux
    export LOGNAME=flux
    export GALLIUM_DRIVER=zink
    export MESA_LOADER_DRIVER_OVERRIDE=zink
    export TU_DEBUG=noconform
    export ZINK_NO_TIMELINES=1
    export KWIN_OPENGL_INTERFACE=egl
    export KWIN_COMPOSE=Q
    export QT_QPA_PLATFORMTHEME=kde
    export QT_SCALE_FACTOR=1
    exec dbus-run-session -- startplasma-x11
  "
'

exit 0
