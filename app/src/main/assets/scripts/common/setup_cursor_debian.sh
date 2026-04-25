#!/bin/bash
# setup_cursor_debian.sh
# Installs Cursor AI Code Editor (ARM64 Linux)
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
        -d "nativecode://callback?result=failure&name=ide_tools_cursor" \
        > /dev/null 2>&1 || true
    read -p "Press Enter to close..."
    exit 1
}

set -eE
trap 'handle_error "Unexpected error on line $LINENO"' ERR

echo "NativeCode: Setting up Cursor AI Editor..."
echo "=================================================="

TARGET_USER="flux"
TARGET_GROUP="users"

# ────────────────────────────────────────────────────────────────
# Step 1: Install dependencies
# ────────────────────────────────────────────────────────────────
echo "NativeCode: Installing Cursor dependencies..."

apt update -y || handle_error "apt update"
apt install -y libx11-xcb1 libxcb-dri3-0 libdrm2 libgbm1 libasound2 \
    dbus-x11 gnome-keyring libtcmalloc-minimal4 curl wget \
    libxss1 libgdk-pixbuf2.0-0 libgtk-3-0 libsecret-1-0 || handle_error "Cursor Deps"

# ────────────────────────────────────────────────────────────────
# Step 2: Check if already installed
# ────────────────────────────────────────────────────────────────
if [ -f "/opt/cursor/cursor" ] || command -v cursor > /dev/null 2>&1; then
    echo "NativeCode: Cursor is already installed."
    
    mkdir -p "/home/$TARGET_USER/.nativecode"
    touch "/home/$TARGET_USER/.nativecode/cursor_installed"
    chown -R "$TARGET_USER:$TARGET_GROUP" "/home/$TARGET_USER/.nativecode"
    
    echo ""
    echo "=================================================="
    echo "🎉 NativeCode: Cursor already installed!"
    echo "=================================================="
    
    set +eE
    trap - ERR
    read -p "" 2>/dev/null || true
    am start -a android.intent.action.VIEW \
        -d "nativecode://callback?result=success&name=ide_tools_cursor" \
        > /dev/null 2>&1 || true
    exit 0
fi

# ────────────────────────────────────────────────────────────────
# Step 3: Download Cursor
# Cursor provides an AppImage and tarball for Linux.
# We use the tarball approach for ARM64 compatibility.
# ────────────────────────────────────────────────────────────────
echo "NativeCode: Downloading Cursor (ARM64)..."

CURSOR_ROOT="/opt/cursor"
mkdir -p "$CURSOR_ROOT"

# Try ARM64 tarball first, fall back to x86_64
CURSOR_URL="https://downloader.cursor.sh/linux/appImage/arm64"

curl -L "$CURSOR_URL" -o /tmp/cursor.AppImage 2>&1 || {
    echo "NativeCode: ARM64 AppImage not available, trying tarball..."
    curl -L "https://downloader.cursor.sh/linux/arm64" -o /tmp/cursor.tar.gz 2>&1 || handle_error "Cursor Download"
}

# ────────────────────────────────────────────────────────────────
# Step 4: Extract and install
# ────────────────────────────────────────────────────────────────
echo "NativeCode: Extracting Cursor..."

if [ -f /tmp/cursor.AppImage ]; then
    # AppImage approach
    chmod +x /tmp/cursor.AppImage
    
    # Extract AppImage
    cd /tmp
    ./cursor.AppImage --appimage-extract > /dev/null 2>&1 || {
        # If extraction fails, try moving the AppImage directly
        mv /tmp/cursor.AppImage "$CURSOR_ROOT/cursor"
        chmod +x "$CURSOR_ROOT/cursor"
    }
    
    if [ -d /tmp/squashfs-root ]; then
        cp -r /tmp/squashfs-root/* "$CURSOR_ROOT/" 2>/dev/null || true
        rm -rf /tmp/squashfs-root
    fi
    rm -f /tmp/cursor.AppImage

elif [ -f /tmp/cursor.tar.gz ]; then
    tar -xzf /tmp/cursor.tar.gz -C "$CURSOR_ROOT" --strip-components=1 || handle_error "Cursor Extraction"
    rm -f /tmp/cursor.tar.gz
fi

# ────────────────────────────────────────────────────────────────
# Step 5: Create wrapper script (with --no-sandbox for PRoot)
# ────────────────────────────────────────────────────────────────
if [ -f "$CURSOR_ROOT/cursor" ]; then
    chmod +x "$CURSOR_ROOT/cursor"
elif [ -f "$CURSOR_ROOT/bin/cursor" ]; then
    chmod +x "$CURSOR_ROOT/bin/cursor"
fi

cat <<EOF > /usr/local/bin/cursor
#!/bin/bash
CURSOR_PATH="$CURSOR_ROOT"
export LD_LIBRARY_PATH="\$CURSOR_PATH:\$LD_LIBRARY_PATH"

if [ -f "/usr/lib/aarch64-linux-gnu/libtcmalloc_minimal.so.4" ]; then
    export LD_PRELOAD="/usr/lib/aarch64-linux-gnu/libtcmalloc_minimal.so.4"
fi

CURSOR_BIN="\$CURSOR_ROOT/cursor"
if [ ! -f "\$CURSOR_BIN" ]; then
    CURSOR_BIN="\$CURSOR_ROOT/bin/cursor"
fi

exec dbus-launch --exit-with-session "\$CURSOR_BIN" --no-sandbox --disable-gpu --disable-dev-shm-usage --password-store=basic "\$@"
EOF
chmod +x /usr/local/bin/cursor

# ────────────────────────────────────────────────────────────────
# Step 6: Create desktop entry
# ────────────────────────────────────────────────────────────────
mkdir -p /usr/share/applications
cat <<'EOF' > /usr/share/applications/cursor.desktop
[Desktop Entry]
Name=Cursor
Comment=The AI Code Editor
GenericName=Text Editor
Exec=/usr/local/bin/cursor %U
Icon=cursor
Type=Application
StartupNotify=true
StartupWMClass=Cursor
Categories=TextEditor;Development;IDE;
MimeType=text/plain;inode/directory;application/x-code-workspace;
EOF

# ────────────────────────────────────────────────────────────────
# Step 7: Mark as installed
# ────────────────────────────────────────────────────────────────
mkdir -p "/home/$TARGET_USER/.nativecode"
touch "/home/$TARGET_USER/.nativecode/cursor_installed"
chown -R "$TARGET_USER:$TARGET_GROUP" "/home/$TARGET_USER/.nativecode"
chown -R "$TARGET_USER:$TARGET_GROUP" "$CURSOR_ROOT" 2>/dev/null || true

# ────────────────────────────────────────────────────────────────
# Final Summary
# ────────────────────────────────────────────────────────────────
echo ""
echo "=================================================="
echo "🎉 NativeCode: Cursor Setup Complete!"
echo "=================================================="
echo " Tool    : Cursor AI Code Editor"
echo " Command : cursor"
echo " Path    : $CURSOR_ROOT"
echo "=================================================="
echo ""
echo "Press Enter to close..."

set +eE
trap - ERR
read -p "" 2>/dev/null || true

am start -a android.intent.action.VIEW \
    -d "nativecode://callback?result=success&name=ide_tools_cursor" \
    > /dev/null 2>&1 || true