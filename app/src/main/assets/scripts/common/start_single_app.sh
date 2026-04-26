#!/data/data/com.termux/files/usr/bin/bash
# start_single_app.sh - Launch a single GUI app in PRoot Distro via Termux-X11
# Usage: start_single_app.sh <distro> <app_command> [app_args...]
# Based on start_gui.sh: https://github.com/LinuxDroidMaster/Termux-Desktops

DISTRO=${1:-debian}
shift
APP_COMMAND="$*"

echo "========================================"
echo "NativeCode: Launching App via Termux-X11"
echo "Distro: $DISTRO"
echo "Command: $APP_COMMAND"
echo "========================================"

# Kill open X11 processes
kill -9 $(pgrep -f "termux.x11") 2>/dev/null

# Start VirGL (Turnip GPU) if available
if [ -x "$PREFIX/bin/virgl_test_server_android" ]; then
    nohup setsid $PREFIX/bin/virgl_test_server_android >/dev/null 2>&1 &
    sleep 1
    echo "[OK] VirGL server started"
fi

# Enable PulseAudio over Network
pulseaudio --start --load="module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1" --exit-idle-time=-1

# Prepare termux-x11 session
export XDG_RUNTIME_DIR=${TMPDIR}
termux-x11 :0 >/dev/null &

# Wait a bit until termux-x11 gets started.
sleep 3

# Launch Termux X11 main activity
am start --user 0 -n com.termux.x11/com.termux.x11.MainActivity > /dev/null 2>&1
sleep 1

# Run the single app inside the distro
if [ "$DISTRO" == "termux" ]; then
    export PULSE_SERVER=127.0.0.1
    env DISPLAY=:0 $APP_COMMAND &
else
    proot-distro login $DISTRO --shared-tmp -- /bin/bash -c '
      export DISPLAY=:0
      export PULSE_SERVER=tcp:127.0.0.1
      export XDG_RUNTIME_DIR=${TMPDIR}
      su - flux -c "
        export DISPLAY=:0
        export PULSE_SERVER=tcp:127.0.0.1
        export XDG_RUNTIME_DIR=${TMPDIR}
        nohup '"$APP_COMMAND"' >/dev/null 2>&1 &
      "
    '
fi

sleep 2
echo "[NativeCode] App running. Session active..."
while true; do
    sleep 5
    # Try to detect if app is still running
    APP_NAME=$(echo "'$APP_COMMAND'" | awk '{print $1}')
    if pidof "$APP_NAME" >/dev/null 2>&1; then continue; fi
    if ps -eo comm= 2>/dev/null | grep -v grep | grep -q "$APP_NAME"; then continue; fi
    continue
done

echo "========================================"
echo "NativeCode: App session ended."
echo "========================================"
exit 0
