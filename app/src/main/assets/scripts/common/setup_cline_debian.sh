#!/bin/bash
# setup_cline_debian.sh
# Installs Cline AI Coding Agent (VS Code / Cursor extension)
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
        -d "nativecode://callback?result=failure&name=ai_tools_cline" \
        > /dev/null 2>&1 || true
    read -p "Press Enter to close..."
    exit 1
}

set -eE
trap 'handle_error "Unexpected error on line $LINENO"' ERR

echo "NativeCode: Setting up Cline AI Agent..."
echo "=================================================="

TARGET_USER="flux"
TARGET_GROUP="users"

# ────────────────────────────────────────────────────────────────
# Step 1: Check for a supported editor (VS Code or Cursor)
# Cline is a VS Code / Cursor extension. At least one must be
# installed before we can add the extension.
# ────────────────────────────────────────────────────────────────
EDITOR_BIN=""
EDITOR_NAME=""

if command -v code > /dev/null 2>&1; then
    EDITOR_BIN="$(which code)"
    EDITOR_NAME="VS Code"
elif [ -f "/usr/share/code/bin/code" ]; then
    EDITOR_BIN="/usr/share/code/bin/code"
    EDITOR_NAME="VS Code"
elif command -v cursor > /dev/null 2>&1; then
    EDITOR_BIN="$(which cursor)"
    EDITOR_NAME="Cursor"
elif [ -f "/usr/share/cursor/cursor" ]; then
    EDITOR_BIN="/usr/share/cursor/cursor"
    EDITOR_NAME="Cursor"
elif [ -f "/opt/cursor/cursor" ]; then
    EDITOR_BIN="/opt/cursor/cursor"
    EDITOR_NAME="Cursor"
fi

if [ -z "$EDITOR_BIN" ]; then
    echo ""
    echo "❌ NativeCode: No supported editor found."
    echo "Cline requires VS Code or Cursor to be installed first."
    echo "Please install VS Code or Cursor from the IDE Tools section."
    echo "=================================================="
    echo ""
    echo "Press Enter to close..."
    set +eE
    trap - ERR
    read -p "" 2>/dev/null || true
    am start -a android.intent.action.VIEW \
        -d "nativecode://callback?result=failure&name=ai_tools_cline" \
        > /dev/null 2>&1 || true
    exit 1
fi

echo "NativeCode: Found $EDITOR_NAME at $EDITOR_BIN"

# ────────────────────────────────────────────────────────────────
# Step 2: Install Cline extension
# Cline extension ID: saoudrizwan.claude-dev
# --no-sandbox is required in proot environments
# ────────────────────────────────────────────────────────────────
echo ""
echo "NativeCode: Installing Cline extension for $EDITOR_NAME..."

CLINE_EXT_ID="saoudrizwan.claude-dev"

# Build the editor command (need --no-sandbox in proot)
EDITOR_CMD="$EDITOR_BIN --no-sandbox"

$EDITOR_CMD --install-extension "$CLINE_EXT_ID" 2>&1 || handle_error "Cline extension install"

echo "NativeCode: Cline extension installed."

# ────────────────────────────────────────────────────────────────
# Step 3: Verify the extension
# ────────────────────────────────────────────────────────────────
echo ""
echo "NativeCode: Verifying Cline extension..."

CLINE_FOUND=false
EXT_LIST=$($EDITOR_CMD --list-extensions 2>/dev/null || true)

if echo "$EXT_LIST" | grep -q "saoudrizwan.claude-dev"; then
    CLINE_FOUND=true
    echo " [✅] Cline extension found in $EDITOR_NAME"
else
    echo " [⚠️] Cline extension not confirmed — check $EDITOR_NAME manually"
fi

# ────────────────────────────────────────────────────────────────
# Step 4: Mark as installed
# ────────────────────────────────────────────────────────────────
mkdir -p "/home/$TARGET_USER/.nativecode"
touch "/home/$TARGET_USER/.nativecode/cline_installed"
chown -R "$TARGET_USER:$TARGET_GROUP" "/home/$TARGET_USER/.nativecode"

# ────────────────────────────────────────────────────────────────
# Final Summary
# ────────────────────────────────────────────────────────────────
echo ""
echo "=================================================="
echo "🎉 NativeCode: Cline AI Agent Setup Complete!"
echo "=================================================="
echo " Tool    : Cline (AI Coding Agent)"
echo " Editor  : $EDITOR_NAME"
echo " Usage   : Open $EDITOR_NAME → Ctrl+Shift+X → Cline"
echo ""
echo "Note: Set your API key in Cline settings:"
echo "  Open $EDITOR_NAME → Extensions → Cline → Settings"
echo "=================================================="
echo ""
echo "Press Enter to close..."

set +eE
trap - ERR
read -p "" 2>/dev/null || true

am start -a android.intent.action.VIEW \
    -d "nativecode://callback?result=success&name=ai_tools_cline" \
    > /dev/null 2>&1 || true