#!/bin/bash
# setup_windsurf_debian.sh
# Installs Windsurf (Codeium) AI Code Editor (ARM64 Linux)
# Target: Debian 13 (Trixie) ARM64

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
        -d "nativecode://callback?result=failure&name=ide_tools_windsurf" \
        > /dev/null 2>&1 || true
    read -p "Press Enter to close..."
    exit 1
}

set -eE
trap 'handle_error "Unexpected error on line $LINENO"' ERR

echo "NativeCode: Setting up Windsurf Editor..."
echo "=================================================="

TARGET_USER="flux"
TARGET_GROUP="users"

# ────────────────────────────────────────────────────────────────
# Step 1: Install dependencies
# ────────────────────────────────────────────────────────────────
echo "NativeCode: Installing Windsurf dependencies..."

apt update -y || handle_error "apt update"
apt install -y libx11-xcb1 libxcb-dri3-0 libdrm2 libgbm1 libasound2 \
    dbus-x11 gnome-keyring libtcmalloc-minimal4 curl wget \
    libxss1 libgdk-pixbuf2.0-0 libgtk-3-0 libsecret-1-0 || handle_error "Windsurf Deps"

# ────────────────────────────────────────────────────────────────
# Step 2: Check if already installed
# ────────────────────────────────────────────────────────────────
if [ -f "/opt/windsurf/windsurf" ] || command -v windsurf > /dev/null 2>&1; then
    echo "NativeCode: Windsurf is already installed."
    
    mkdir -p "/home/$TARGET_USER/.nativecode"
    touch "/home/$TARGET_USER/.nativecode/windsurf_installed"
    chown -R "$TARGET_USER:$TARGET_GROUP" "/home/$TARGET_USER/.nativecode"
    
    echo ""
    echo "=================================================="
    echo "🎉 NativeCode: Windsurf already installed!"
    echo "=================================================="
    
    set +eE
    trap - ERR
    read -p "" 2>/dev/null || true
    am start -a android.intent.action.VIEW \
        -d "nativecode://callback?result=success&name=ide_tools_windsurf" \
        > /dev/null 2>&1 || true
    exit 0
fi

# ────────────────────────────────────────────────────────────────
# Step 3: Download Windsurf
# Windsurf provides Linux builds via their download site.
# ────────────────────────────────────────────────────────────────
echo "NativeCode: Downloading Windsurf (ARM64)..."

WINDSURF_ROOT="/opt/windsurf"
mkdir -p "$WINDSURF_ROOT"

# Try ARM64 tarball first
WINDSURF_URL="https://windsurf-stable.codeium.com/linux-arm64/stable"

curl -L "$WINDSURF_URL" -o /tmp/windsurf.tar.gz || {
    echo "NativeCode: ARM64 build not available, trying x86_64..."
    WINDSURF_URL="https://windsurf-stable.codeium.com/linux-x64/stable"
    curl -L "$WINDSURF_URL" -o /tmp/windsurf.tar.gz || handle_error "Windsurf Download"
}

# ────────────────────────────────────────────────────────────────
# Step 4: Extract and install
# ────────────────────────────────────────────────────────────────
echo "NativeCode: Extracting Windsurf..."

rm -rf "$WINDSURF_ROOT"
mkdir -p "$WINDSURF_ROOT"

tar -xzf /tmp/windsurf.tar.gz -C "$WINDSURF_ROOT" --strip-components=1 || handle_error "Windsurf Extraction"
rm -f /tmp/windsurf.tar.gz

# Make binary executable
chmod +x "$WINDSURF_ROOT/windsurf" 2>/dev/null || true
chmod +x "$WINDSURF_ROOT/bin/windsurf" 2>/dev/null || true

# ────────────────────────────────────────────────────────────────
# Step 5: Create wrapper script (--no-sandbox for PRoot)
# ────────────────────────────────────────────────────────────────
cat <<EOF > /usr/local/bin/windsurf
#!/bin/bash
WINDSURF_PATH="$WINDSURF_ROOT"
export LD_LIBRARY_PATH="\$WINDSURF_PATH:\$LD_LIBRARY_PATH"

if [ -f "/usr/lib/aarch64-linux-gnu/libtcmalloc_minimal.so.4" ]; then
    export LD_PRELOAD="/usr/lib/aarch64-linux-gnu/libtcmalloc_minimal.so.4"
fi

WINDSURF_BIN="\$WINDSURF_PATH/windsurf"
if [ ! -f "\$WINDSURF_BIN" ]; then
    WINDSURF_BIN="\$WINDSURF_PATH/bin/windsurf"
fi

exec dbus-launch --exit-with-session "\$WINDSURF_BIN" --no-sandbox --disable-gpu --disable-dev-shm-usage --password-store=basic "\$@"
EOF
chmod +x /usr/local/bin/windsurf

ln -sf /usr/local/bin/windsurf /usr/bin/windsurf 2>/dev/null || true

# ────────────────────────────────────────────────────────────────
# Step 6: Disable extension signature verification
# ────────────────────────────────────────────────────────────────
echo "NativeCode: Configuring Windsurf settings..."
mkdir -p "/home/$TARGET_USER/.config/Windsurf/User"
cat <<'WINDSURF_SETTINGS' > "/home/$TARGET_USER/.config/Windsurf/User/settings.json"
{
    "extensions.verifySignature": false
}
WINDSURF_SETTINGS
chown -R "$TARGET_USER:$TARGET_GROUP" "/home/$TARGET_USER/.config"

# ────────────────────────────────────────────────────────────────
# Step 7: Create desktop entry
# ────────────────────────────────────────────────────────────────
mkdir -p /usr/share/applications
cat <<'EOF' > /usr/share/applications/windsurf.desktop
[Desktop Entry]
Name=Windsurf
Comment=The AI Code Editor by Codeium
GenericName=Text Editor
Exec=/usr/local/bin/windsurf %U
Icon=windsurf
Type=Application
StartupNotify=true
StartupWMClass=Windsurf
Categories=TextEditor;Development;IDE;
MimeType=text/plain;inode/directory;application/x-code-workspace;
EOF

# ────────────────────────────────────────────────────────────────
# Step 8: Mark as installed
# ────────────────────────────────────────────────────────────────
chown -R "$TARGET_USER:$TARGET_GROUP" "$WINDSURF_ROOT" 2>/dev/null || true

mkdir -p "/home/$TARGET_USER/.nativecode"
touch "/home/$TARGET_USER/.nativecode/windsurf_installed"
chown -R "$TARGET_USER:$TARGET_GROUP" "/home/$TARGET_USER/.nativecode"

# ────────────────────────────────────────────────────────────────
# Final Summary
# ────────────────────────────────────────────────────────────────
echo ""
echo "=================================================="
echo "🎉 NativeCode: Windsurf Setup Complete!"
echo "=================================================="
echo " Tool    : Windsurf (Codeium AI Code Editor)"
echo " Command : windsurf"
echo " Path    : $WINDSURF_ROOT"
echo "=================================================="
echo ""
echo "Press Enter to close..."

set +eE
trap - ERR
read -p "" 2>/dev/null || true

am start -a android.intent.action.VIEW \
    -d "nativecode://callback?result=success&name=ide_tools_windsurf" \
    > /dev/null 2>&1 || true