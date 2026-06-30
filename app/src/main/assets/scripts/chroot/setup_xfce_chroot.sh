#!/bin/sh
# setup_xfce_chroot.sh
# Installs XFCE4 desktop into an existing Debian 13 (Trixie) chroot.
# Run from a root shell on the device, after `setup_debian13_chroot.sh` has
# already extracted the rootfs.
#
# Usage:
#   sh setup_xfce_chroot.sh                  # install + autostart on next login
#   sh setup_xfce_chroot.sh --minimal        # xfce4-core + xfce4-terminal only
#   DEBIANPATH=/path/chrootDebian13 sh ...   # override chroot path
#
# Notes
#   * DEBIAN_FRONTEND=noninteractive stops apt from prompting.
#   * --no-install-recommends keeps the install small (≈ 1.2 GB vs ≈ 2.4 GB).
#   * xfce4-goodies adds thunar-volman, mousepad, ristretto, etc.
#   * The start_xfce_chroot.sh helper assumes Termux:X11 (or termux-x11) is
#     already serving DISPLAY=:0. PulseAudio is expected at PULSE_SERVER.

set -e

DEBIANPATH="${DEBIANPATH:-/data/local/tmp/chrootDebian13}"
USERNAME="${USERNAME:-flux}"
MINIMAL=0
[ "$1" = "--minimal" ] && MINIMAL=1

# Colors
if [ -t 1 ]; then
    C_CYAN='\033[1;36m'; C_GREEN='\033[1;32m'; C_RED='\033[1;31m'; C_YELLOW='\033[1;33m'; C_OFF='\033[0m'
else
    C_CYAN=''; C_GREEN=''; C_RED=''; C_YELLOW=''; C_OFF=''
fi
log()  { printf "${C_CYAN}[+] %s${C_OFF}\n" "$*"; }
ok()   { printf "${C_GREEN}[✓] %s${C_OFF}\n" "$*"; }
warn() { printf "${C_YELLOW}[!] %s${C_OFF}\n" "$*"; }
err()  { printf "${C_RED}[✗] %s${C_OFF}\n" "$*" >&2; }
die()  { err "$*"; exit 1; }

# ── Preflight ────────────────────────────────────────────────────────────────
[ "$(id -u)" = "0" ] || die "Must be run as root (needs mount + chroot)."

# Locate a root-capable busybox (Termux's $PREFIX/bin/busybox won't chroot).
BB=""
for path in /data/adb/magisk/busybox \
            /data/adb/modules/busybox-ndk/system/bin/busybox \
            /sbin/busybox /system/xbin/busybox /system/bin/busybox; do
    [ -x "$path" ] && BB="$path" && break
done
[ -n "$BB" ] || die "No root-capable busybox found in standard paths."

[ -d "$DEBIANPATH" ] || die "Chroot not found at $DEBIANPATH — run setup_debian13_chroot.sh first."
[ -x "$DEBIANPATH/bin/bash" ] || die "$DEBIANPATH/bin/bash missing — rootfs not extracted?"

# Stop any running XFCE session so apt can replace its files cleanly.
pkill -9 -f "xfce4-session\|xfwm4\|xfdesktop\|xfce4-panel\|dbus-launch" 2>/dev/null || true

# ── Mount chroot filesystems ─────────────────────────────────────────────────
log "Mounting chroot filesystems..."
$BB mount -o remount,dev,suid /data 2>/dev/null || true
$BB mount --bind /dev     "$DEBIANPATH/dev"     || die "bind /dev failed"
$BB mount --bind /sys     "$DEBIANPATH/sys"     || { $BB umount "$DEBIANPATH/dev"; die "bind /sys failed"; }
$BB mount -t proc proc    "$DEBIANPATH/proc"    || die "mount proc failed"
$BB mount -t devpts devpts "$DEBIANPATH/dev/pts" || die "mount devpts failed"
mkdir -p "$DEBIANPATH/dev/shm"
$BB mount -t tmpfs -o size=512M tmpfs "$DEBIANPATH/dev/shm" || die "mount tmpfs failed"
trap '
    log "Unmounting chroot filesystems..."
    $BB umount "$DEBIANPATH/dev/shm" 2>/dev/null
    $BB umount "$DEBIANPATH/dev/pts" 2>/dev/null
    $BB umount "$DEBIANPATH/proc"    2>/dev/null
    $BB umount "$DEBIANPATH/sys"     2>/dev/null
    $BB umount "$DEBIANPATH/dev"     2>/dev/null
' EXIT INT TERM

# ── Ensure the user exists (create if setup_debian13_chroot.sh was skipped) ─
log "Ensuring user $USERNAME exists..."
$BB chroot "$DEBIANPATH" /bin/bash -c "
    export DEBIAN_FRONTEND=noninteractive
    groupadd storage 2>/dev/null || true
    groupadd wheel   2>/dev/null || true
    if ! id -u $USERNAME >/dev/null 2>&1; then
        useradd -m -g users -G wheel,audio,video,storage -s /bin/bash $USERNAME
        echo '$USERNAME:$USERNAME' | chpasswd
    fi
" || die "user setup failed"
ok "user $USERNAME ready"

# ── apt update + install XFCE4 ───────────────────────────────────────────────
log "Running apt update inside chroot..."
$BB chroot "$DEBIANPATH" /bin/bash -c "
    export DEBIAN_FRONTEND=noninteractive
    export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
    apt-get update -qq
" || die "apt update failed"

if [ "$MINIMAL" = "1" ]; then
    log "Installing XFCE4 (minimal: xfce4 + xfce4-terminal only)..."
    PKGS="xfce4 xfce4-terminal dbus-x11"
else
    log "Installing XFCE4 (full: xfce4 + goodies + terminal + common apps)..."
    PKGS="xfce4 xfce4-goodies xfce4-terminal dbus-x11 pulseaudio pulseaudio-module-dbus-x11"
fi

$BB chroot "$DEBIANPATH" /bin/bash -c "
    export DEBIAN_FRONTEND=noninteractive
    export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
    apt-get install -y --no-install-recommends $PKGS
    apt-get install -y --no-install-recommends sudo 2>/dev/null || true
    # Passwordless sudo for the chroot user (matches setup_debian13_chroot.sh)
    echo '$USERNAME ALL=(ALL:ALL) NOPASSWD:ALL' > /etc/sudoers.d/$USERNAME
    chmod 0440 /etc/sudoers.d/$USERNAME
" || die "apt install failed"
ok "XFCE4 installed"

# ── XFCE tweaks (run once as the user) ──────────────────────────────────────
log "Applying XFCE tweaks for $USERNAME..."
$BB chroot "$DEBIANPATH" /bin/su - "$USERNAME" -c '
    export DISPLAY=:0
    export XDG_RUNTIME_DIR=/tmp
    # Disable compositor — much smoother on phone GPUs (mirrors setup_debian13)
    xfconf-query -c xfwm4 -p /general/use_compositing -s false 2>/dev/null || true
    # Disable screensaver / lock — annoying on touch
    xfconf-query -c xfce4-screensaver -p /lock/enabled -s false 2>/dev/null || true
    # Default panel layout for portrait phones
    xfconf-query -c xfce4-panel -p /panels/panel-1/size -s 32 2>/dev/null || true
' 2>/dev/null || warn "xfconf tweaks failed (harmless — first boot will use defaults)"

# ── Generate start_xfce_chroot.sh ───────────────────────────────────────────
START_SCRIPT="/data/local/tmp/start_xfce_chroot.sh"
log "Writing launch helper: $START_SCRIPT"
cat > "$START_SCRIPT" <<EOF
#!/bin/sh
# start_xfce_chroot.sh — start XFCE4 inside the Debian 13 chroot
# Usage:  sh /data/local/tmp/start_xfce_chroot.sh
# Requires: termux-x11 (or Termux:X11) running on DISPLAY=:0,
#           PulseAudio reachable at PULSE_SERVER=tcp:127.0.0.1

DEBIANPATH="$DEBIANPATH"
BB="$BB"
USERNAME="$USERNAME"

\$BB mount -o remount,dev,suid /data 2>/dev/null
\$BB mount --bind /dev     \$DEBIANPATH/dev
\$BB mount --bind /sys     \$DEBIANPATH/sys
\$BB mount -t proc proc    \$DEBIANPATH/proc
\$BB mount -t devpts devpts \$DEBIANPATH/dev/pts
mkdir -p \$DEBIANPATH/dev/shm
\$BB mount -t tmpfs -o size=512M tmpfs \$DEBIANPATH/dev/shm

# Make sure no stale session is holding the display
\$BB chroot \$DEBIANPATH /bin/su - root -c "killall -9 xfce4-session xfwm4 xfdesktop xfce4-panel dbus-launch dbus-daemon" 2>/dev/null

echo "Starting XFCE4 as \$USERNAME..."
exec \$BB chroot \$DEBIANPATH /bin/su - \$USERNAME -c '
    export DISPLAY=:0
    export PULSE_SERVER=tcp:127.0.0.1
    export XDG_RUNTIME_DIR=/tmp
    export DBUS_SESSION_BUS_ADDRESS=unix:path=/tmp/dbus-session
    dbus-launch --exit-with-session startxfce4
'
EOF
chmod +x "$START_SCRIPT"
ok "wrote $START_SCRIPT"

# ── Generate stop helper ─────────────────────────────────────────────────────
STOP_SCRIPT="/data/local/tmp/stop_xfce_chroot.sh"
log "Writing stop helper: $STOP_SCRIPT"
cat > "$STOP_SCRIPT" <<EOF
#!/bin/sh
DEBIANPATH="$DEBIANPATH"
BB="$BB"
echo "Stopping XFCE4..."
\$BB chroot \$DEBIANPATH /bin/su - root -c "killall -9 xfce4-session xfwm4 xfdesktop xfce4-panel dbus-launch dbus-daemon" 2>/dev/null
\$BB umount \$DEBIANPATH/dev/shm 2>/dev/null
\$BB umount \$DEBIANPATH/dev/pts 2>/dev/null
\$BB umount \$DEBIANPATH/proc    2>/dev/null
\$BB umount \$DEBIANPATH/sys     2>/dev/null
\$BB umount \$DEBIANPATH/dev     2>/dev/null
echo "done."
EOF
chmod +x "$STOP_SCRIPT"
ok "wrote $STOP_SCRIPT"

# ── Mark installed (idempotent) ─────────────────────────────────────────────
touch "$DEBIANPATH/.xfce_installed"

echo ""
ok "XFCE4 setup complete in $DEBIANPATH"
echo ""
echo "Next steps:"
echo "  1. Start Termux:X11 on the device (gives you DISPLAY=:0)"
echo "  2. sh $START_SCRIPT"
echo ""
echo "To uninstall XFCE (keep rootfs):"
echo "  sh uninstall_xfce_chroot.sh"
