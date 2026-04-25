#!/bin/bash
# setup_nexus_debian.sh
# Installs Nexus IDE (NativeCode's own IDE)
# Target: Debian 13 (Trixie) ARM64
# NOTE: Replace the download URL and version with actual Nexus release info.

# ────────────────────────────────────────────────────────────────
# Error Handler
# ────────────────────────────────────────────────────────────────
handle_error() {
    local STEP="$1"
    echo ""
    echo "❌ NativeCode Error: Script failed at step: $STEP"
    echo "---------------------------------------------------"
    echo "Please scroll up to read the error output above."
    echo "---------------------------------------------------"
    am start -a android.intent.action.VIEW \
        -d "nativecode://callback?result=failure&name=ide_tools_nexus" \
        > /dev/null 2>&1 || true
    read -p "Press Enter to close..."
    exit 1
}

set -eE
trap 'handle_error "Unexpected error on line $LINENO"' ERR

echo "NativeCode: Setting up Nexus IDE..."
echo "=================================================="

TARGET_USER="flux"
TARGET_GROUP="users"

# ────────────────────────────────────────────────────────────────
# Step 1: Install dependencies
# ────────────────────────────────────────────────────────────────
echo "NativeCode: Installing Nexus IDE dependencies..."

apt update -y || handle_error "apt update"
apt install -y libx11-xcb1 libxcb-dri3-0 libdrm2 libgbm1 libasound2 \
    dbus-x11 gnome-keyring libtcmalloc-minimal4 curl wget || handle_error "Nexus Deps"

# ────────────────────────────────────────────────────────────────
# Step 2: Check if already installed
# ────────────────────────────────────────────────────────────────
if [ -f "/usr/bin/nexus" ] || [ -f "/opt/nexus/nexus" ]; then
    echo "NativeCode: Nexus IDE is already installed."
    
    mkdir -p "/home/$TARGET_USER/.nativecode"
    touch "/home/$TARGET_USER/.nativecode/nexus_installed"
    chown -R "$TARGET_USER:$TARGET_GROUP" "/home/$TARGET_USER/.nativecode"
    
    echo ""
    echo "=================================================="
    echo "🎉 NativeCode: Nexus IDE already installed!"
    echo "=================================================="
    
    set +eE
    trap - ERR
    read -p "" 2>/dev/null || true
    am start -a android.intent.action.VIEW \
        -d "nativecode://callback?result=success&name=ide_tools_nexus" \
        > /dev/null 2>&1 || true
    exit 0
fi

# ────────────────────────────────────────────────────────────────
# Step 3: Download Nexus IDE
# TODO: Replace with actual Nexus IDE download URL and version
# ────────────────────────────────────────────────────────────────
NEXUS_ROOT="/opt/nexus"
NEXUS_VERSION="1.0.0"
NEXUS_URL="https://github.com/ivarna/nexus/releases/download/v${NEXUS_VERSION}/nexus-linux-arm64-${NEXUS_VERSION}.tar.gz"

echo "NativeCode: Downloading Nexus IDE v${NEXUS_VERSION}..."

mkdir -p "$NEXUS_ROOT"

wget -q --show-progress "$NEXUS_URL" -O /tmp/nexus.tar.gz || handle_error "Nexus Download"

# ────────────────────────────────────────────────────────────────
# Step 4: Extract and install
# ────────────────────────────────────────────────────────────────
echo "NativeCode: Extracting Nexus IDE..."

tar -xzf /tmp/nexus.tar.gz -C "$NEXUS_ROOT" --strip-components=1 || handle_error "Nexus Extraction"
rm -f /tmp/nexus.tar.gz

chmod +x "$NEXUS_ROOT/nexus" 2>/dev/null || true
chmod +x "$NEXUS_ROOT/bin/nexus" 2>/dev/null || true

# ────────────────────────────────────────────────────────────────
# Step 5: Create wrapper script (with --no-sandbox for PRoot)
# ────────────────────────────────────────────────────────────────
cat <<EOF > /usr/local/bin/nexus
#!/bin/bash
NEXUS_PATH="$NEXUS_ROOT"
export LD_LIBRARY_PATH="\$NEXUS_PATH:\$LD_LIBRARY_PATH"

if [ -f "/usr/lib/aarch64-linux-gnu/libtcmalloc_minimal.so.4" ]; then
    export LD_PRELOAD="/usr/lib/aarch64-linux-gnu/libtcmalloc_minimal.so.4"
fi

NEXUS_BIN="\$NEXUS_PATH/nexus"
if [ ! -f "\$NEXUS_BIN" ]; then
    NEXUS_BIN="\$NEXUS_PATH/bin/nexus"
fi

exec dbus-launch --exit-with-session "\$NEXUS_BIN" --no-sandbox --disable-gpu --disable-dev-shm-usage "\$@"
EOF
chmod +x /usr/local/bin/nexus

ln -sf /usr/local/bin/nexus /usr/bin/nexus 2>/dev/null || true

# ────────────────────────────────────────────────────────────────
# Step 6: Create desktop entry
# ────────────────────────────────────────────────────────────────
mkdir -p /usr/share/applications
cat <<'EOF' > /usr/share/applications/nexus.desktop
[Desktop Entry]
Name=Nexus
Comment=NativeCode IDE
GenericName=Text Editor
Exec=/usr/local/bin/nexus %U
Icon=nexus
Type=Application
StartupNotify=true
StartupWMClass=Nexus
Categories=TextEditor;Development;IDE;
MimeType=text/plain;inode/directory;application/x-code-workspace;
EOF

# ────────────────────────────────────────────────────────────────
# Step 7: Mark as installed
# ────────────────────────────────────────────────────────────────
chown -R "$TARGET_USER:$TARGET_GROUP" "$NEXUS_ROOT" 2>/dev/null || true

mkdir -p "/home/$TARGET_USER/.nativecode"
touch "/home/$TARGET_USER/.nativecode/nexus_installed"
chown -R "$TARGET_USER:$TARGET_GROUP" "/home/$TARGET_USER/.nativecode"

# ────────────────────────────────────────────────────────────────
# Final Summary
# ────────────────────────────────────────────────────────────────
echo ""
echo "=================================================="
echo "🎉 NativeCode: Nexus IDE Setup Complete!"
echo "=================================================="
echo " Tool    : Nexus IDE"
echo " Command : nexus"
echo " Path    : $NEXUS_ROOT"
echo "=================================================="
echo ""
echo "Press Enter to close..."

set +eE
trap - ERR
read -p "" 2>/dev/null || true

am start -a android.intent.action.VIEW \
    -d "nativecode://callback?result=success&name=ide_tools_nexus" \
    > /dev/null 2>&1 || true