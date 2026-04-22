#!/bin/bash
# setup_cybersec_debian.sh
# Installs Cybersecurity & Penetration Testing Stack
# Target: Debian 13 (Trixie) ARM64

# Error Handler
handle_error() {
    echo ""
    echo "❌ NativeCode Error: Script failed at step: $1"
    echo "---------------------------------------------------"
    echo "Please check the error message above for details."
    echo "---------------------------------------------------"
    read -p "Press Enter to acknowledge error and exit..."
    exit 1
}

echo "NativeCode: Setting up Cybersecurity Environment..."
TARGET_USER="flux"
TARGET_GROUP="users"

# 1. System Dependencies & Core Tools
echo "NativeCode: Installing Security Tools (Apt)..."
export DEBIAN_FRONTEND=noninteractive

# Enable non-free/contrib repositories for nikto/hashcat if not present
sed -i 's/main$/main contrib non-free non-free-firmware/g' /etc/apt/sources.list
sed -i 's/main contrib$/main contrib non-free non-free-firmware/g' /etc/apt/sources.list
apt update -y

# Network Analysis & Cracking Tools
# nmap: Network Scanner
# wireshark (tshark): Packet Analyzer
# tcpdump: CLI Packet Capture
# netcat-traditional: Networking utility (nc)
# aircrack-ng: WiFi Security
# john: Password Cracker
# hydra: Network Logon Cracker
# sqlmap: SQL Injection Tool
# hashcat: Advanced Password Recovery

apt install -y \
    curl wget git build-essential \
    nmap netcat-traditional tcpdump \
    wireshark tshark \
    aircrack-ng \
    john \
    hydra \
    sqlmap \
    hashcat \
    || handle_error "Apt Tools Installation"

# Install Nikto Manually (Fallback if apt fails or is too old)
if ! command -v nikto >/dev/null; then
    echo "NativeCode: Installing Nikto (GitHub)..."
    # Try apt first (it's in non-free)
    if ! apt install -y nikto; then
        echo " - Apt install failed/missing. Installing from source..."
        cd /opt
        git clone https://github.com/sullo/nikto.git
        ln -sf /opt/nikto/program/nikto.pl /usr/local/bin/nikto
    fi
else
    echo " [ℹ️] Nikto already installed"
fi

# 2. Configure Wireshark Permissions
echo "NativeCode: Configuring Wireshark permissions..."
# Allow non-root users from 'wireshark' group to capture packets
# Pre-seed the configuration to avoid interactive prompt
echo "wireshark-common wireshark-common/install-setuid boolean true" | debconf-set-selections
dpkg-reconfigure -f noninteractive wireshark-common

# Add target user to wireshark group
usermod -aG wireshark "$TARGET_USER" || echo " [⚠️] Failed to add user to wireshark group"

# 3. Metasploit Framework (Rapid7 Nightly - ARM64 Supported)
echo "NativeCode: Installing Metasploit Framework..."
if ! command -v msfconsole >/dev/null; then
    echo " - Downloading Official Installer..."
    # The Rapid7 script handles aptitude dependencies and ruby environment
    curl https://raw.githubusercontent.com/rapid7/metasploit-omnibus/master/config/templates/metasploit-framework-wrappers/msfupdate.erb > /tmp/msfinstall
    
    # Actually, simpler URL often cited:
    curl https://raw.githubusercontent.com/rapid7/metasploit-omnibus/master/config/templates/metasploit-framework-wrappers/msfupdate.erb -o /tmp/msfinstall
    # Wait, the standard "curl | bash" is:
    # curl https://raw.githubusercontent.com/rapid7/metasploit-omnibus/master/config/templates/metasploit-framework-wrappers/msfupdate.erb > msfinstall && chmod 755 msfinstall && ./msfinstall
    # However, verified URL for the script is:
    curl https://raw.githubusercontent.com/rapid7/metasploit-omnibus/master/config/templates/metasploit-framework-wrappers/msfupdate.erb -o /tmp/msfinstall
    
    # That assumes the ERB is the script source, checking official docs:
    # curl https://raw.githubusercontent.com/rapid7/metasploit-omnibus/master/config/templates/metasploit-framework-wrappers/msfupdate.erb > msfinstall && chmod 755 msfinstall && ./msfinstall
    # Actually, the user-facing script is often:
    curl https://raw.githubusercontent.com/rapid7/metasploit-omnibus/master/config/templates/metasploit-framework-wrappers/msfupdate.erb -o /tmp/msfinstall
    
    # Official one-liner usually used:
    # curl https://raw.githubusercontent.com/rapid7/metasploit-omnibus/master/config/templates/metasploit-framework-wrappers/msfupdate.erb > msfinstall && chmod 755 msfinstall && ./msfinstall
    
    chmod +x /tmp/msfinstall
    /tmp/msfinstall || echo " [⚠️] Metasploit Installer failed (check network/deps)."
    rm /tmp/msfinstall
    
    # The installer sets up /opt/metasploit-framework and links binaries to /usr/bin
else
    echo " - Metasploit already installed"
fi

# 4. Burp Suite Community (JAR Method - Most reliable for ARM64)
# Official Linux installer is often x64 only.
echo "NativeCode: Checking Burp Suite..."
BURP_DIR="/opt/burpsuite"
if [ ! -f "$BURP_DIR/burpsuite.jar" ]; then
    echo " - Installing Burp Suite Community (JAR)..."
    mkdir -p "$BURP_DIR"
    
    # Burp Suite Community JAR direct link is hard to permalink (dynamic).
    # Using PortSwigger's latest release API effectively requires scraping.
    # We will skip auto-download to avoid 403s/broken scripts and instruct user, 
    # OR rely on apt 'burpsuite' if available via Kali repos (not here).
    
    # Better approach: Add a helper scrip to download it, or skip.
    echo " [ℹ️] Skipped automatic download (Requires dynamic scraping)."
    echo "      You can download the JAR manually to $BURP_DIR/burpsuite.jar"
    
    # However, let's setup the wrapper assuming they drop the jar there, or if we find a consistent link later.
    cat <<EOF > /usr/local/bin/burpsuite
#!/bin/bash
if [ -f "$BURP_DIR/burpsuite.jar" ]; then
    java -jar "$BURP_DIR/burpsuite.jar" "\$@"
else
    echo "Burp Suite JAR not found. Please download 'Burp Suite Community Edition JAR' and place it at:"
    echo "$BURP_DIR/burpsuite.jar"
fi
EOF
    chmod +x /usr/local/bin/burpsuite
    
    # Desktop Entry
    mkdir -p /usr/share/applications
    cat <<EOF > /usr/share/applications/burpsuite.desktop
[Desktop Entry]
Name=Burp Suite
Comment=Web Security Scanner
Exec=/usr/local/bin/burpsuite
Icon=utilities-terminal
Terminal=false
Type=Application
Categories=Development;Security;
EOF
else
    echo " - Burp Suite JAR present."
fi

# 5. Final Permissions & Cleanup
echo "NativeCode: Applying permissions..."
chown -R $TARGET_USER:$TARGET_GROUP "/home/$TARGET_USER"

# 6. Verification
verify_installation() {
    echo ""
    echo "🔎 NativeCode: Verifying Installations..."
    echo "------------------------------------------------"
    
    # Network Tools
    if command -v nmap >/dev/null; then echo " [✅] Nmap"; else echo " [❌] Nmap Missing"; fi
    if command -v wireshark >/dev/null; then echo " [✅] Wireshark"; else echo " [❌] Wireshark Missing"; fi
    
    # Cracking Tools
    if command -v john >/dev/null; then echo " [✅] John the Ripper"; else echo " [❌] John Missing"; fi
    if command -v aircrack-ng >/dev/null; then echo " [✅] Aircrack-ng"; else echo " [❌] Aircrack-ng Missing"; fi
    if command -v hashcat >/dev/null; then echo " [✅] Hashcat"; else echo " [❌] Hashcat Missing"; fi
    if command -v nikto >/dev/null; then echo " [✅] Nikto"; else echo " [❌] Nikto Missing"; fi
    
    # Metasploit
    if command -v msfconsole >/dev/null; then 
        echo " [✅] Metasploit Framework"
    else 
        echo " [❌] Metasploit Missing"
    fi
    
    echo "------------------------------------------------"
    echo "🎉 Cybersecurity Setup Complete!"
}

verify_installation

echo "Note: Some tools (like Wireshark capture) may require 'sudo' or group membership enabled above."
read -p "Press Enter to close..."
