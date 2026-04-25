#!/bin/bash
# setup_antigravity_ide_debian.sh
# Installs Antigravity AI-First Code Editor (standalone, not as part of webdev)
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
        -d "nativecode://callback?result=failure&name=ide_tools_antigravity" \
        > /dev/null 2>&1 || true
    read -p "Press Enter to close..."
    exit 1
}

set -eE
trap 'handle_error "Unexpected error on line $LINENO"' ERR

echo "NativeCode: Setting up Antigravity IDE..."
echo "=================================================="

TARGET_USER="flux"
TARGET_GROUP="users"

# ────────────────────────────────────────────────────────────────
# Step 1: Install dependencies
# ────────────────────────────────────────────────────────────────
echo "NativeCode: Installing Antigravity dependencies..."

apt update -y || handle_error "apt update"
apt install -y libx11-xcb1 libxcb-dri3-0 libdrm2 libgbm1 libasound2 \
    dbus-x11 gnome-keyring libtcmalloc-minimal4 curl wget binutils || handle_error "Antigravity Deps"

# ────────────────────────────────────────────────────────────────
# Step 2: Check if already installed
# ────────────────────────────────────────────────────────────────
if command -v antigravity > /dev/null 2>&1 || [ -f "/usr/share/antigravity/antigravity" ]; then
    echo "NativeCode: Antigravity is already installed."
    
    mkdir -p "/home/$TARGET_USER/.nativecode"
    touch "/home/$TARGET_USER/.nativecode/antigravity_installed"
    chown -R "$TARGET_USER:$TARGET_GROUP" "/home/$TARGET_USER/.nativecode"
    
    echo ""
    echo "=================================================="
    echo "🎉 NativeCode: Antigravity already installed!"
    echo "=================================================="
    
    set +eE
    trap - ERR
    read -p "" 2>/dev/null || true
    am start -a android.intent.action.VIEW \
        -d "nativecode://callback?result=success&name=ide_tools_antigravity" \
        > /dev/null 2>&1 || true
    exit 0
fi

# ────────────────────────────────────────────────────────────────
# Step 3: Add Antigravity apt repository
# ────────────────────────────────────────────────────────────────
echo "NativeCode: Adding Antigravity repository..."

mkdir -p /etc/apt/keyrings
curl -fsSL https://us-central1-apt.pkg.dev/doc/repo-signing-key.gpg | \
    gpg --dearmor --yes -o /etc/apt/keyrings/antigravity-repo-key.gpg || handle_error "Antigravity Key"

echo "deb [signed-by=/etc/apt/keyrings/antigravity-repo-key.gpg] https://us-central1-apt.pkg.dev/projects/antigravity-auto-updater-dev/ antigravity-debian main" | \
    tee /etc/apt/sources.list.d/antigravity.list > /dev/null

apt update -y || handle_error "Antigravity Repo Update"

# ────────────────────────────────────────────────────────────────
# Step 4: Download and install Antigravity
# Using dpkg bypass (extract .deb manually) to avoid PRoot crashes
# ────────────────────────────────────────────────────────────────
echo "NativeCode: Downloading Antigravity..."

cd /tmp

LOCAL_DEB=$(find /home/$TARGET_USER/Downloads -name "antigravity*.deb" 2>/dev/null | head -n 1)

if [ -n "$LOCAL_DEB" ]; then
    echo "NativeCode: Found manual download: $LOCAL_DEB"
    cp "$LOCAL_DEB" /tmp/ || handle_error "Copy local deb"
else
    echo "NativeCode: Downloading from repository (attempting arm64)..."
    apt download antigravity:arm64 2>/dev/null || apt download antigravity || handle_error "Antigravity Download"
fi

DEB_FILE=$(ls /tmp/antigravity*.deb 2>/dev/null | head -n 1)

if [ -z "$DEB_FILE" ]; then
    echo "NativeCode: Trying alternative download..."
    curl -L "https://us-central1-apt.pkg.dev/projects/antigravity-auto-updater-dev/antigravity-debian/pool/main/a/antigravity/antigravity_latest_arm64.deb" -o /tmp/antigravity.deb 2>/dev/null || handle_error "Antigravity Alternative Download"
    DEB_FILE="/tmp/antigravity.deb"
fi

echo "NativeCode: Extracting Antigravity..."
ar x "$DEB_FILE" || handle_error "Antigravity Extraction (ar)"

if [ -f data.tar.zst ]; then
    tar -xvf data.tar.zst -C / || handle_error "Antigravity Extraction (zst)"
elif [ -f data.tar.xz ]; then
    python3 -c "import tarfile; t=tarfile.open('data.tar.xz'); t.extractall('/'); t.close()" || handle_error "Antigravity Extraction (xz)"
elif [ -f data.tar.gz ]; then
    python3 -c "import tarfile; t=tarfile.open('data.tar.gz'); t.extractall('/'); t.close()" || handle_error "Antigravity Extraction (gz)"
else
    handle_error "Unknown data archive format"
fi

rm -f "$DEB_FILE" debian-binary control.tar* data.tar* 2>/dev/null || true

# ────────────────────────────────────────────────────────────────
# Step 5: Post-install fixes
# ────────────────────────────────────────────────────────────────
echo "NativeCode: Applying runtime fixes..."

ANTIGRAVITY_PATH="/usr/share/antigravity"

if [ ! -d "$ANTIGRAVITY_PATH" ]; then
    RESOURCES_DIR=$(find / -type d -name "resources" -path "*/antigravity*" 2>/dev/null | head -1)
    if [ -n "$RESOURCES_DIR" ]; then
        APP_ROOT=$(dirname "$RESOURCES_DIR")
        if [ "$APP_ROOT" != "$ANTIGRAVITY_PATH" ]; then
            rm -rf "$ANTIGRAVITY_PATH" 2>/dev/null || true
            mkdir -p "$ANTIGRAVITY_PATH"
            cp -r "$APP_ROOT"/* "$ANTIGRAVITY_PATH/" 2>/dev/null || true
        fi
    fi
fi

chmod +x "$ANTIGRAVITY_PATH/antigravity" 2>/dev/null || true
chmod +x "$ANTIGRAVITY_PATH/bin/antigravity" 2>/dev/null || true

cat <<EOF > /usr/bin/antigravity
#!/bin/bash
ANTIGRAVITY_PATH="$ANTIGRAVITY_PATH"
export LD_LIBRARY_PATH="\$ANTIGRAVITY_PATH:\$LD_LIBRARY_PATH"

if [ -f "/usr/lib/aarch64-linux-gnu/libtcmalloc_minimal.so.4" ]; then
    export LD_PRELOAD="/usr/lib/aarch64-linux-gnu/libtcmalloc_minimal.so.4"
fi

if [ -f "\$ANTIGRAVITY_PATH/bin/antigravity" ]; then
    TARGET_BIN="\$ANTIGRAVITY_PATH/bin/antigravity"
else
    TARGET_BIN="\$ANTIGRAVITY_PATH/antigravity"
fi

exec dbus-launch --exit-with-session "\$TARGET_BIN" --no-sandbox --disable-gpu --disable-dev-shm-usage --password-store=basic "\$@"
EOF
chmod +x /usr/bin/antigravity

mkdir -p /usr/share/applications
cat <<'EOF' > /usr/share/applications/antigravity.desktop
[Desktop Entry]
Name=Antigravity
Comment=AI-First Code Editor
GenericName=Text Editor
Exec=/usr/bin/antigravity %U
Icon=antigravity
Type=Application
StartupNotify=true
StartupWMClass=Antigravity
Categories=TextEditor;Development;IDE;
MimeType=text/plain;inode/directory;application/x-code-workspace;
EOF

# ────────────────────────────────────────────────────────────────
# Step 6: Mark as installed
# ────────────────────────────────────────────────────────────────
mkdir -p "/home/$TARGET_USER/.nativecode"
touch "/home/$TARGET_USER/.nativecode/antigravity_installed"
chown -R "$TARGET_USER:$TARGET_GROUP" "/home/$TARGET_USER/.nativecode"

# ────────────────────────────────────────────────────────────────
# Final Summary
# ────────────────────────────────────────────────────────────────
echo ""
echo "=================================================="
echo "🎉 NativeCode: Antigravity IDE Setup Complete!"
echo "=================================================="
echo " Tool    : Antigravity (AI-First Code Editor)"
echo " Command : antigravity"
echo " Path    : $ANTIGRAVITY_PATH"
echo "=================================================="
echo ""
echo "Press Enter to close..."

set +eE
trap - ERR
read -p "" 2>/dev/null || true

am start -a android.intent.action.VIEW \
    -d "nativecode://callback?result=success&name=ide_tools_antigravity" \
    > /dev/null 2>&1 || true