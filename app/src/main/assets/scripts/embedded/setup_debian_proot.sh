#!/bin/sh
# setup_debian_proot.sh
# Downloads + extracts a Debian 13 (trixie) aarch64 minirootfs and bootstraps
# a `flux` user (UID 1000, no password, NOPASSWD sudo) for the in-app proot
# runtime. Designed to be invoked from the NativeCode UI's "Setup Debian" button
# OR run manually from inside the app's Alpine proot:
#
#   sh /data/data/com.ivarna.nativecode/files/setup_debian_proot.sh
#
# The Alpine proot is the easiest host because it ships wget + tar. If you
# invoke this from a plain Android shell, the script falls back to a single
# APK asset embedded at assets/rootfs/debian-minirootfs.tar.xz (if present)
# so the user doesn't need network access.
#
# After it finishes, the EmbeddedRuntime can launch Debian by pointing
# its rootfsDir at <filesDir>/rootfs-debian.

set -e

# ── Paths (overridable for tests) ───────────────────────────────────────────
APP_FILES="${APP_FILES:-/data/data/com.ivarna.nativecode/files}"
DEBIAN_ROOTFS="${DEBIAN_ROOTFS:-$APP_FILES/rootfs-debian}"
WORK="${WORK:-/data/local/tmp/nativecode-debian-setup}"
ASSET_FALLBACK="${ASSET_FALLBACK:-/android_asset/rootfs/debian-minirootfs.tar.xz}"

# Mirror: debuerreotype's dist-arm64v8/trixie/minimal/rootfs.tar.xz (~25 MB)
DEBIAN_URL="${DEBIAN_URL:-https://github.com/debuerreotype/docker-debian-artifacts/raw/dist-arm64v8/trixie/minimal/rootfs.tar.xz}"

# Architecture
ARCH="$(uname -m)"
[ "$ARCH" = "aarch64" ] || [ "$ARCH" = "arm64" ] || {
    echo "[!] This script targets aarch64, you are on $ARCH."
    echo "    Set DEBIAN_URL to a ${ARCH} build of trixie minimal and re-run."
    exit 1
}

USERNAME="flux"
HOSTNAME="nativecode-debian"

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
mkdir -p "$APP_FILES" "$WORK"

# If a fully-extracted rootfs is already there, skip everything.
if [ -x "$DEBIAN_ROOTFS/bin/bash" ] && [ -x "$DEBIAN_ROOTFS/usr/bin/sudo" ]; then
    ok "Debian rootfs already provisioned at $DEBIAN_ROOTFS — nothing to do."
    exit 0
fi

# ── Step 1: acquire rootfs tarball ──────────────────────────────────────────
TARBALL="$WORK/rootfs.tar.xz"

if [ -f "$TARBALL" ]; then
    log "Reusing existing $TARBALL"
else
    log "Downloading Debian 13 (trixie) aarch64 minirootfs..."
    log "  $DEBIAN_URL"
    if command -v wget >/dev/null 2>&1; then
        wget -q --show-progress -O "$TARBALL.part" "$DEBIAN_URL" \
            && mv "$TARBALL.part" "$TARBALL" \
            || {
                rm -f "$TARBALL.part"
                warn "wget failed — trying APK asset fallback at $ASSET_FALLBACK"
                if [ -f "$ASSET_FALLBACK" ]; then
                    cp "$ASSET_FALLBACK" "$TARBALL"
                else
                    die "No network AND no APK asset at $ASSET_FALLBACK"
                fi
            }
    elif command -v curl >/dev/null 2>&1; then
        curl -fL -o "$TARBALL.part" "$DEBIAN_URL" \
            && mv "$TARBALL.part" "$TARBALL" \
            || {
                rm -f "$TARBALL.part"
                warn "curl failed — trying APK asset fallback at $ASSET_FALLBACK"
                if [ -f "$ASSET_FALLBACK" ]; then
                    cp "$ASSET_FALLBACK" "$TARBALL"
                else
                    die "No network AND no APK asset at $ASSET_FALLBACK"
                fi
            }
    else
        die "Neither wget nor curl is available — install one or ship the asset in the APK."
    fi
fi
ok "got $(du -h "$TARBALL" | cut -f1) tarball"

# ── Step 2: extract into $DEBIAN_ROOTFS ────────────────────────────────────
log "Extracting rootfs to $DEBIAN_ROOTFS ..."
rm -rf "$DEBIAN_ROOTFS"
mkdir -p "$DEBIAN_ROOTFS"
tar -xJf "$TARBALL" -C "$DEBIAN_ROOTFS" --numeric-owner
ok "extracted $(du -sh "$DEBIAN_ROOTFS" | cut -f1) of files"

# Clean up tarball to free space (we still have WORK for the bootstrap log)
rm -f "$TARBALL"

# ── Step 3: bind-mount /dev /proc /sys and bootstrap ───────────────────────
# We need a working mount to run apt inside the chroot. If we don't have
# root (typical in-app context), fall back to a pure-proot invocation.
log "Bootstrapping Debian chroot..."
BOOTSTRAP_LOG="$WORK/bootstrap.log"

# Path to proot on the device. The app's NativeCode ships libproot.so as
# /data/data/<pkg>/files/libproot.so (copied from the APK's native lib).
PROOT_BIN=""
for path in "$APP_FILES/libproot.so" /system/bin/proot /data/adb/magisk/proot; do
    [ -x "$path" ] && PROOT_BIN="$path" && break
done
[ -n "$PROOT_BIN" ] || die "proot not found — run this from inside the app's Alpine proot."

PROOT_LIB=""
for path in "$APP_FILES/libtalloc.so.2" /system/lib64/libtalloc.so.2; do
    [ -f "$path" ] && PROOT_LIB="$path" && break
done
[ -n "$PROOT_LIB" ] || die "libtalloc.so.2 not found — run this from inside the app's Alpine proot."

# Build env
export PROOT_TMP_DIR="$WORK/proot-tmp"
export LD_LIBRARY_PATH="$(dirname "$PROOT_LIB"):${LD_LIBRARY_PATH:-}"
export PROOT_LOADER="$APP_FILES/libproot-loader.so"
mkdir -p "$PROOT_TMP_DIR"

# Run all post-install steps inside a single proot invocation so the
# bind mounts persist for the duration of the bootstrap.
"$PROOT_BIN" \
    --rootfs="$DEBIAN_ROOTFS" \
    --bind=/dev --bind=/proc --bind=/sys \
    --bind="$APP_FILES:/host" \
    -0 -w /root \
    /bin/sh -c '
        set -e
        export DEBIAN_FRONTEND=noninteractive
        export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

        # 1) hostname
        echo "'"$HOSTNAME"'" > /etc/hostname

        # 2) apt sources (point at Debian mirrors; remove cdrom lines)
        cat > /etc/apt/sources.list <<EOF
deb https://deb.debian.org/debian trixie main contrib
deb https://deb.debian.org/debian trixie-updates main contrib
deb https://security.debian.org/debian-security trixie-security main contrib
EOF

        # 3) apt update + install core packages
        apt-get update -qq
        apt-get install -y --no-install-recommends \
            sudo bash coreutils ca-certificates curl wget

        # 4) create the `flux` user — UID 1000, no password, NOPASSWD sudo
        if ! id -u flux >/dev/null 2>&1; then
            useradd -m -u 1000 -G users,sudo,audio,video -s /bin/bash flux
        fi
        passwd -d flux                   # empty password (no prompt)
        passwd -u flux                   # unlock if locked
        echo "flux ALL=(ALL:ALL) NOPASSWD:ALL" > /etc/sudoers.d/flux
        chmod 0440 /etc/sudoers.d/flux

        # 5) a profile snippet that matches the Alpine one
        cat > /etc/profile.d/nativecode.sh <<EOF
export HOME=/home/flux
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
export TERM=xterm-256color
export LANG=C.UTF-8
EOF
        chown flux:flux /etc/profile.d/nativecode.sh

        # 6) mark installed (idempotent)
        touch /.nativecode-debian-provisioned
    ' 2>&1 | tee "$BOOTSTRAP_LOG" || die "bootstrap failed — see $BOOTSTRAP_LOG"

ok "Debian proot provisioned"
ok "User: flux (UID 1000, no password, NOPASSWD sudo)"

echo ""
ok "Setup complete."
echo "Rootfs:  $DEBIAN_ROOTFS"
echo "User:    flux (no password)"
echo "Try in app: open the Debian card on the home screen and run \`id\` or \`sudo whoami\`"
