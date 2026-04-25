#!/bin/bash
# setup_vscode_debian.sh
# Installs Visual Studio Code (ARM64) as a standalone IDE
# Target: Debian 13 (Trixie) ARM64
# Note: Uses tarball method to avoid dpkg crashes in PRoot

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
        -d "nativecode://callback?result=failure&name=ide_tools_vscode" \
        > /dev/null 2>&1 || true
    read -p "Press Enter to close..."
    exit 1
}

set -eE
trap 'handle_error "Unexpected error on line $LINENO"' ERR

echo "NativeCode: Setting up Visual Studio Code..."
echo "=================================================="

TARGET_USER="flux"
TARGET_GROUP="users"

# ────────────────────────────────────────────────────────────────
# Step 1: Install dependencies
# ────────────────────────────────────────────────────────────────
echo "NativeCode: Installing VS Code dependencies..."

apt update -y || handle_error "apt update"
apt install -y libx11-xcb1 libxcb-dri3-0 libdrm2 libgbm1 libasound2 \
    dbus-x11 gnome-keyring libtcmalloc-minimal4 curl wget || handle_error "VS Code Deps"

# ────────────────────────────────────────────────────────────────
# Step 2: Check if VS Code is already installed
# ────────────────────────────────────────────────────────────────
if [ -f "/usr/share/code/bin/code" ] || command -v code > /dev/null 2>&1; then
    echo "NativeCode: VS Code is already installed."
    
    mkdir -p "/home/$TARGET_USER/.nativecode"
    touch "/home/$TARGET_USER/.nativecode/vscode_installed"
    chown -R "$TARGET_USER:$TARGET_GROUP" "/home/$TARGET_USER/.nativecode"
    
    echo ""
    echo "=================================================="
    echo "🎉 NativeCode: VS Code already installed!"
    echo "=================================================="
    echo " Command : code"
    echo "=================================================="
    
    set +eE
    trap - ERR
    read -p "" 2>/dev/null || true
    am start -a android.intent.action.VIEW \
        -d "nativecode://callback?result=success&name=ide_tools_vscode" \
        > /dev/null 2>&1 || true
    exit 0
fi

# ────────────────────────────────────────────────────────────────
# Step 3: Download VS Code ARM64 tarball
# ────────────────────────────────────────────────────────────────
echo "NativeCode: Downloading VS Code (ARM64)..."

rm -f /etc/apt/sources.list.d/vscode.list 2>/dev/null || true
rm -f /tmp/code_arm64.deb 2>/dev/null || true

curl -L 'https://update.code.visualstudio.com/latest/linux-arm64/stable' -o /tmp/vscode.tar.gz || handle_error "VS Code Download"

# ────────────────────────────────────────────────────────────────
# Step 4: Extract and install
# ────────────────────────────────────────────────────────────────
echo "NativeCode: Extracting VS Code..."

rm -rf /usr/share/code
mkdir -p /usr/share/code

tar -xzf /tmp/vscode.tar.gz -C /usr/share/code --strip-components=1 || handle_error "VS Code Extraction"
rm -f /tmp/vscode.tar.gz

ln -sf /usr/share/code/bin/code /usr/bin/code

# ────────────────────────────────────────────────────────────────
# Step 5: Create wrapper script for PRoot (--no-sandbox)
# ────────────────────────────────────────────────────────────────
cat <<'EOF' > /usr/local/bin/code-wrapper
#!/bin/bash
exec /usr/share/code/bin/code --no-sandbox --unity-launch "$@"
EOF
chmod +x /usr/local/bin/code-wrapper

# ────────────────────────────────────────────────────────────────
# Step 6: Create desktop entry
# ────────────────────────────────────────────────────────────────
mkdir -p /usr/share/applications
cat <<'EOF' > /usr/share/applications/code.desktop
[Desktop Entry]
Name=Visual Studio Code
Comment=Code Editing. Redefined.
GenericName=Text Editor
Exec=/usr/local/bin/code-wrapper %F
Icon=com.visualstudio.code
Type=Application
StartupNotify=false
StartupWMClass=Code
Categories=TextEditor;Development;IDE;
MimeType=text/plain;inode/directory;application/x-code-workspace;
EOF

# ────────────────────────────────────────────────────────────────
# Step 7: Configure settings (disable signature verification)
# ────────────────────────────────────────────────────────────────
echo "NativeCode: Configuring VS Code settings..."
mkdir -p /home/$TARGET_USER/.config/Code/User
cat <<'VSCODE_SETTINGS' > /home/$TARGET_USER/.config/Code/User/settings.json
{
    "extensions.verifySignature": false
}
VSCODE_SETTINGS
chown -R "$TARGET_USER:$TARGET_GROUP" /home/$TARGET_USER/.config

# ────────────────────────────────────────────────────────────────
# Step 8: Add alias to .bashrc
# ────────────────────────────────────────────────────────────────
BASHRC="/home/$TARGET_USER/.bashrc"
if ! grep -q "code --no-sandbox" "$BASHRC" 2>/dev/null; then
    echo "alias code='code --no-sandbox --unity-launch'" >> "$BASHRC"
    echo "NativeCode: Added VS Code alias to .bashrc"
fi

# ────────────────────────────────────────────────────────────────
# Step 9: Mark as installed
# ────────────────────────────────────────────────────────────────
mkdir -p "/home/$TARGET_USER/.nativecode"
touch "/home/$TARGET_USER/.nativecode/vscode_installed"
chown -R "$TARGET_USER:$TARGET_GROUP" "/home/$TARGET_USER/.nativecode"

# ────────────────────────────────────────────────────────────────
# Final Summary
# ────────────────────────────────────────────────────────────────
echo ""
echo "=================================================="
echo "🎉 NativeCode: VS Code Setup Complete!"
echo "=================================================="
echo " Tool    : Visual Studio Code (ARM64)"
echo " Command : code"
echo " Path    : /usr/share/code"
echo "=================================================="
echo ""
echo "Press Enter to close..."

set +eE
trap - ERR
read -p "" 2>/dev/null || true

am start -a android.intent.action.VIEW \
    -d "nativecode://callback?result=success&name=ide_tools_vscode" \
    > /dev/null 2>&1 || true