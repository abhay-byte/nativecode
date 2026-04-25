#!/bin/bash
# setup_opencode_debian.sh
# Installs OpenCode (Terminal-based AI coding tool, Go binary)
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
        -d "nativecode://callback?result=failure&name=ai_tools_opencode" \
        > /dev/null 2>&1 || true
    read -p "Press Enter to close..."
    exit 1
}

set -eE
trap 'handle_error "Unexpected error on line $LINENO"' ERR

echo "NativeCode: Setting up OpenCode..."
echo "=================================================="

TARGET_USER="flux"
TARGET_GROUP="users"

# ────────────────────────────────────────────────────────────────
# Step 1: Install dependencies
# ────────────────────────────────────────────────────────────────
echo "NativeCode: Installing dependencies..."

apt update -y || handle_error "apt update"
apt install -y curl wget ca-certificates || handle_error "Dependency install"

# ────────────────────────────────────────────────────────────────
# Step 2: Download OpenCode binary (ARM64)
# OpenCode is a Go-based TUI available on GitHub releases.
# ────────────────────────────────────────────────────────────────
OPENCODE_ROOT="/opt/opencode"
OPENCODE_VERSION="0.3.0"
OPENCODE_ARCH="aarch64"
OPENCODE_TAR="opencode-linux-${OPENCODE_ARCH}-${OPENCODE_VERSION}.tar.gz"
OPENCODE_URL="https://github.com/sst/opencode/releases/download/v${OPENCODE_VERSION}/${OPENCODE_TAR}"

echo "NativeCode: Downloading OpenCode v${OPENCODE_VERSION}..."

# Check if already installed with correct version
if [ -f "$OPENCODE_ROOT/opencode" ]; then
    INSTALLED_VER="$($OPENCODE_ROOT/opencode --version 2>/dev/null | head -1 | grep -oP '[\d.]+' || echo 'unknown')"
    if [ "$INSTALLED_VER" = "$OPENCODE_VERSION" ]; then
        echo "NativeCode: OpenCode $OPENCODE_VERSION already installed."
        echo " [✅] OpenCode found at $OPENCODE_ROOT/opencode"

        mkdir -p "/home/$TARGET_USER/.nativecode"
        touch "/home/$TARGET_USER/.nativecode/opencode_installed"
        chown -R "$TARGET_USER:$TARGET_GROUP" "/home/$TARGET_USER/.nativecode"

        echo ""
        echo "=================================================="
        echo "🎉 NativeCode: OpenCode already installed!"
        echo "=================================================="
        echo " Command : opencode"

        set +eE
        trap - ERR
        read -p "" 2>/dev/null || true
        am start -a android.intent.action.VIEW \
            -d "nativecode://callback?result=success&name=ai_tools_opencode" \
            > /dev/null 2>&1 || true
        exit 0
    fi
    echo "NativeCode: Updating OpenCode from $INSTALLED_VER to $OPENCODE_VERSION..."
fi

mkdir -p "$OPENCODE_ROOT"

# Try download; fall back to building from source if binary unavailable
wget -q --show-progress "$OPENCODE_URL" -O /tmp/opencode.tar.gz 2>/dev/null || {
    echo "NativeCode: Pre-built binary not available, installing via Go..."
    
    # ── Fallback: Install Go and build from source ──────────────
    if ! command -v go > /dev/null 2>&1; then
        echo "NativeCode: Installing Go (build dependency)..."
        GO_VER="1.23.5"
        GO_DIST="go${GO_VER}.linux-arm64"
        GO_URL="https://go.dev/dl/${GO_DIST}.tar.gz"
        
        wget -q --show-progress "$GO_URL" -O /tmp/go.tar.gz || handle_error "Go Download"
        tar -C /usr/local -xzf /tmp/go.tar.gz || handle_error "Go Extraction"
        rm -f /tmp/go.tar.gz
        
        ln -sf /usr/local/go/bin/go /usr/local/bin/go
        
        # Add Go to PATH
        export PATH=$PATH:/usr/local/go/bin
        echo 'export PATH=$PATH:/usr/local/go/bin' >> "/home/$TARGET_USER/.bashrc"
        echo 'export PATH=$PATH:/usr/local/go/bin' > /etc/profile.d/go.sh
        chmod 644 /etc/profile.d/go.sh
        
        echo "NativeCode: Go $GO_VER installed."
    fi
    
    echo "NativeCode: Building OpenCode from source..."
    export PATH=$PATH:/usr/local/go/bin
    GOBIN=/usr/local/bin go install github.com/sst/opencode@latest || handle_error "OpenCode build from source"
    
    # Copy to expected location
    if [ -f "/usr/local/bin/opencode" ]; then
        cp /usr/local/bin/opencode "$OPENCODE_ROOT/opencode"
    fi
    
    echo "NativeCode: OpenCode built from source."
}

# If we downloaded a tarball, extract it
if [ -f "/tmp/opencode.tar.gz" ]; then
    # Try tar extraction (may be a single binary)
    mkdir -p /tmp/opencode_extract
    tar -xzf /tmp/opencode.tar.gz -C /tmp/opencode_extract 2>/dev/null || {
        # Might be a single binary
        cp /tmp/opencode.tar.gz /tmp/opencode_test
        chmod +x /tmp/opencode_test 2>/dev/null || true
        if [ -x /tmp/opencode_test ]; then
            mv /tmp/opencode_test "$OPENCODE_ROOT/opencode"
        else
            handle_error "OpenCode binary extraction"
        fi
    }
    
    # Find the binary in extracted files
    if [ -f "/tmp/opencode_extract/opencode" ]; then
        mv /tmp/opencode_extract/opencode "$OPENCODE_ROOT/opencode"
    elif [ -f "/tmp/opencode_extract/opencode-linux-aarch64" ]; then
        mv /tmp/opencode_extract/opencode-linux-aarch64 "$OPENCODE_ROOT/opencode"
    fi
    
    rm -rf /tmp/opencode_extract /tmp/opencode.tar.gz
fi

chmod +x "$OPENCODE_ROOT/opencode" 2>/dev/null || true

# ────────────────────────────────────────────────────────────────
# Step 3: Create symlink
# ────────────────────────────────────────────────────────────────
ln -sf "$OPENCODE_ROOT/opencode" /usr/local/bin/opencode 2>/dev/null || true

# ────────────────────────────────────────────────────────────────
# Step 4: Verify the install
# ────────────────────────────────────────────────────────────────
echo ""
echo "NativeCode: Verifying OpenCode..."

if command -v opencode > /dev/null 2>&1; then
    echo " [✅] OpenCode found at $(which opencode)"
elif [ -f "$OPENCODE_ROOT/opencode" ]; then
    echo " [✅] OpenCode found at $OPENCODE_ROOT/opencode"
else
    echo " [⚠️] opencode binary not found"
fi

# ────────────────────────────────────────────────────────────────
# Step 5: Mark as installed
# ────────────────────────────────────────────────────────────────
mkdir -p "/home/$TARGET_USER/.nativecode"
touch "/home/$TARGET_USER/.nativecode/opencode_installed"
chown -R "$TARGET_USER:$TARGET_GROUP" "/home/$TARGET_USER/.nativecode"

# ────────────────────────────────────────────────────────────────
# Final Summary
# ────────────────────────────────────────────────────────────────
echo ""
echo "=================================================="
echo "🎉 NativeCode: OpenCode Setup Complete!"
echo "=================================================="
echo " Tool    : OpenCode (Terminal AI Coding Agent)"
echo " Command : opencode"
echo " Usage   : opencode         (interactive TUI)"
echo "           opencode -p 'task' (one-shot)"
echo ""
echo "Note: Configure your API key in opencode settings:"
echo "  opencode config set provider <provider>"
echo "=================================================="
echo ""
echo "Press Enter to close..."

set +eE
trap - ERR
read -p "" 2>/dev/null || true

am start -a android.intent.action.VIEW \
    -d "nativecode://callback?result=success&name=ai_tools_opencode" \
    > /dev/null 2>&1 || true