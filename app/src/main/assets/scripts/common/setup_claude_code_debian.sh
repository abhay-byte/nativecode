#!/bin/bash
# setup_claude_code_debian.sh
# Installs Anthropic Claude Code CLI (AI coding agent in the terminal)
# Target: Debian 13 (Trixie) ARM64

# ────────────────────────────────────────────────────────────────
# Error Handler — prints error, shows Termux log, then returns
# callback to the app with failure
# ────────────────────────────────────────────────────────────────
handle_error() {
    local STEP="$1"
    echo ""
    echo "❌ NativeCode Error: Script failed at step: $STEP"
    echo "---------------------------------------------------"
    echo "Please scroll up to read the error output above."
    echo "---------------------------------------------------"
    am start -a android.intent.action.VIEW \
        -d "nativecode://callback?result=failure&name=ai_tools_claude_code" \
        > /dev/null 2>&1 || true
    read -p "Press Enter to close..."
    exit 1
}

# ────────────────────────────────────────────────────────────────
# Trap all unexpected errors
# ────────────────────────────────────────────────────────────────
set -eE
trap 'handle_error "Unexpected error on line $LINENO"' ERR

echo "NativeCode: Setting up Claude Code CLI..."
echo "=================================================="

TARGET_USER="flux"
TARGET_GROUP="users"

# ────────────────────────────────────────────────────────────────
# Step 1: Ensure Node.js is available
# Claude Code requires Node >= 22 (LTS). Same pattern as Codex.
# ────────────────────────────────────────────────────────────────
NODE_ROOT="/opt/nodejs"
NODE_VER="v22.13.1"
NODE_DIST="node-${NODE_VER}-linux-arm64"
NODE_URL="https://nodejs.org/dist/${NODE_VER}/${NODE_DIST}.tar.xz"

echo "NativeCode: Checking Node.js..."

if [ -f "$NODE_ROOT/bin/node" ]; then
    INSTALLED_VER="$("$NODE_ROOT/bin/node" -v 2>/dev/null)"
    echo "NativeCode: Found Node.js $INSTALLED_VER at $NODE_ROOT"
else
    echo "NativeCode: Node.js not found at $NODE_ROOT — installing $NODE_VER..."

    rm -f /etc/apt/sources.list.d/nodesource.list 2>/dev/null || true
    rm -f /etc/apt/sources.list.d/nodesource.list.save 2>/dev/null || true
    rm -f /usr/share/keyrings/nodesource.gpg 2>/dev/null || true
    rm -f /etc/apt/keyrings/nodesource.gpg 2>/dev/null || true
    sed -i '/nodesource/d' /etc/apt/sources.list 2>/dev/null || true

    mkdir -p "$NODE_ROOT"
    wget -q --show-progress "$NODE_URL" -O /tmp/node.tar.xz || handle_error "Node.js Download"
    tar -xJvf /tmp/node.tar.xz -C "$NODE_ROOT" --strip-components=1 > /dev/null 2>&1 || handle_error "Node.js Extraction"
    rm -f /tmp/node.tar.xz

    ln -sf "$NODE_ROOT/bin/node" /usr/local/bin/node
    ln -sf "$NODE_ROOT/bin/npm"  /usr/local/bin/npm
    ln -sf "$NODE_ROOT/bin/npx"  /usr/local/bin/npx

    echo "NativeCode: Node.js $NODE_VER installed."
fi

export PATH="$NODE_ROOT/bin:$PATH"

NODE_ACTUAL="$(node -v 2>/dev/null)" || handle_error "Node.js not executable"
NPM_ACTUAL="$(npm -v 2>/dev/null)" || handle_error "npm not executable"
echo "NativeCode: Node $NODE_ACTUAL  |  npm $NPM_ACTUAL"

# ────────────────────────────────────────────────────────────────
# Step 2: Install Claude Code CLI globally
# ────────────────────────────────────────────────────────────────
echo ""
echo "NativeCode: Installing @anthropic-ai/claude-code..."

export npm_config_unsafe_perm=true

sudo npm install -g @anthropic-ai/claude-code || handle_error "Claude Code npm install"

echo "NativeCode: Claude Code CLI installed."

# ────────────────────────────────────────────────────────────────
# Step 3: Verify the install
# ────────────────────────────────────────────────────────────────
echo ""
echo "NativeCode: Verifying Claude Code CLI..."

CLAUDE_BIN=""
if command -v claude > /dev/null 2>&1; then
    CLAUDE_BIN="$(which claude)"
elif [ -f "$NODE_ROOT/bin/claude" ]; then
    CLAUDE_BIN="$NODE_ROOT/bin/claude"
    ln -sf "$CLAUDE_BIN" /usr/local/bin/claude 2>/dev/null || true
fi

if [ -n "$CLAUDE_BIN" ]; then
    echo " [✅] Claude Code CLI found at $CLAUDE_BIN"
else
    echo " [⚠️] claude binary not found in PATH — check npm global prefix"
fi

# ────────────────────────────────────────────────────────────────
# Step 4: Add /opt/nodejs/bin to shell profiles
# ────────────────────────────────────────────────────────────────
BASHRC="/home/$TARGET_USER/.bashrc"
if ! grep -q "/opt/nodejs/bin" "$BASHRC" 2>/dev/null; then
    echo 'export PATH=$PATH:/opt/nodejs/bin' >> "$BASHRC"
    echo "NativeCode: Added /opt/nodejs/bin to .bashrc"
fi

if command -v zsh > /dev/null 2>&1; then
    ZSHRC="/home/$TARGET_USER/.zshrc"
    [ ! -f "$ZSHRC" ] && touch "$ZSHRC" && chown "$TARGET_USER:$TARGET_GROUP" "$ZSHRC"
    if ! grep -q "/opt/nodejs/bin" "$ZSHRC" 2>/dev/null; then
        echo 'export PATH=$PATH:/opt/nodejs/bin' >> "$ZSHRC"
        echo "NativeCode: Added /opt/nodejs/bin to .zshrc"
    fi
fi

echo 'export PATH=$PATH:/opt/nodejs/bin' > /etc/profile.d/nodejs.sh
chmod 644 /etc/profile.d/nodejs.sh

# ────────────────────────────────────────────────────────────────
# Step 5: Mark as installed (idempotency marker)
# ────────────────────────────────────────────────────────────────
mkdir -p "/home/$TARGET_USER/.nativecode"
touch "/home/$TARGET_USER/.nativecode/claude_code_installed"
chown -R "$TARGET_USER:$TARGET_GROUP" "/home/$TARGET_USER/.nativecode"

# ────────────────────────────────────────────────────────────────
# Final Summary
# ────────────────────────────────────────────────────────────────
echo ""
echo "=================================================="
echo "🎉 NativeCode: Claude Code CLI Setup Complete!"
echo "=================================================="
echo " Tool    : Claude Code (Anthropic AI Coding Agent)"
echo " Command : claude"
echo " Usage   : claude  (interactive)  |  claude -p 'task'"
echo ""
echo "Note: Set your ANTHROPIC_API_KEY environment variable:"
echo "  export ANTHROPIC_API_KEY=sk-ant-..."
echo "=================================================="
echo ""
echo "Press Enter to close..."

set +eE
trap - ERR
read -p "" 2>/dev/null || true

# ────────────────────────────────────────────────────────────────
# Callback: Tell the app installation succeeded
# ────────────────────────────────────────────────────────────────
am start -a android.intent.action.VIEW \
    -d "nativecode://callback?result=success&name=ai_tools_claude_code" \
    > /dev/null 2>&1 || true