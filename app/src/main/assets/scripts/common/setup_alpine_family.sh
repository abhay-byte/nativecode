#!/bin/sh
# setup_alpine_family.sh
# Installs XFCE4 desktop inside Alpine for use with Termux:X11 (no VNC).
# Uses Alpine-native adduser/addgroup (BusyBox), not shadow useradd.

DISTRO_NAME="${1:-alpine}"

echo "NativeCode: Configuring ${DISTRO_NAME} (Alpine Family)..."

# 1. Update package repositories and install XFCE4 + X11 deps
apk update || exit 1

apk add sudo bash dbus dbus-x11 \
  xfce4 xfce4-terminal xfconf \
  font-dejavu || exit 1

# 2. Create User 'flux' if not exists
# Alpine uses BusyBox adduser, not shadow useradd
if ! id -u flux >/dev/null 2>&1; then
    adduser -D -s /bin/bash flux
    echo "flux:flux" | chpasswd
    adduser flux wheel 2>/dev/null || true
fi

# 3. Configure Sudo — passwordless for flux
mkdir -p /etc/sudoers.d
echo "flux ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/flux
chmod 0440 /etc/sudoers.d/flux

# 4. Configure XFCE4 — disable compositor for Turnip GPU
mkdir -p /home/flux
cat > /root/start_xfce4.sh << 'XEOF'
#!/bin/sh
export DISPLAY=:0
export PULSE_SERVER=tcp:127.0.0.1
export XDG_RUNTIME_DIR=${TMPDIR:-/tmp}
xfconf-query -c xfwm4 -p /general/use_compositing -s false 2>/dev/null
dbus-launch --exit-with-session startxfce4
XEOF
chmod +x /root/start_xfce4.sh

echo "NativeCode: ${DISTRO_NAME} Setup Complete!"
