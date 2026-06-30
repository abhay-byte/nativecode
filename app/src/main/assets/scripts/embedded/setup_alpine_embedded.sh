#!/bin/sh
# Alpine Linux 3.20 Embedded Proot Setup Script
# Flags:
#   --no-cache          — skip local apk cache commit (avoids the rename(.apk.new → .apk)
#                         hardlink error inside proot bind-mounts)
#   --force-overwrite   — overwrite files from other packages (e.g. busybox-utils) without
#                         aborting the whole transaction
#   --no-progress       — keep terminal output compact
echo "[✓] Alpine Linux 3.20 embedded proot detected"

echo "[*] Updating package index..."
apk update --no-progress 2>&1
if [ $? -ne 0 ]; then
    echo "[!] apk update failed — check network connection"
    exit 1
fi
echo "[✓] Package index updated"

echo "[*] Installing bash + coreutils..."
apk add --no-cache --force-overwrite --no-progress bash coreutils 2>&1

echo "[*] Installing nano..."
apk add --no-cache --force-overwrite --no-progress nano 2>&1

echo "[*] Installing network tools..."
apk add --no-cache --force-overwrite --no-progress curl wget 2>&1

echo "[*] Installing git..."
apk add --no-cache --force-overwrite --no-progress git 2>&1

echo "[*] Installing htop..."
apk add --no-cache --force-overwrite --no-progress procps htop 2>&1

echo "[*] Installing build tools..."
apk add --no-cache --force-overwrite --no-progress build-base make cmake 2>&1

echo "[*] Installing Python 3..."
apk add --no-cache --force-overwrite --no-progress python3 py3-pip 2>&1

echo "[*] Installing Node.js..."
apk add --no-cache --force-overwrite --no-progress nodejs npm 2>&1

echo "[*] Setting up environment..."
mkdir -p /root /home
cat > /etc/profile.d/nativecode.sh << 'EOF'
export HOME=/root
export PATH=/usr/local/sbin:/usr/local/bin:/sbin:/usr/sbin:/bin:/usr/bin
export TERM=xterm-256color
export LANG=C.UTF-8
EOF

echo ""
echo "[✓] Alpine 3.20 setup complete!"
echo "  bash · coreutils · nano · curl · wget · git"
echo "  htop · build-base · cmake · python3 · nodejs"
echo ""
echo "  Try: python3 --version  |  node --version  |  git --version"
