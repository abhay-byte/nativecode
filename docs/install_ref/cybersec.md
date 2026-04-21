# Cybersecurity Stack

*Script: `setup_cybersec_debian.sh`*

---

## Overview

Installs penetration testing and security analysis tools.

---

## Network Tools

| Tool | Purpose |
|------|---------|
| nmap | Network scanner |
| Wireshark/tshark | Packet analyzer |
| tcpdump | CLI packet capture |
| netcat-traditional | Networking utility (nc) |

---

## Cracking Tools

| Tool | Purpose |
|------|---------|
| john | Password cracker (John the Ripper) |
| hydra | Network logon cracker |
| hashcat | Advanced password recovery |
| aircrack-ng | WiFi security testing |

---

## Web Security

| Tool | Purpose |
|------|---------|
| sqlmap | SQL injection testing |
| nikto | Web vulnerability scanner |

---

## Metasploit Framework

| Component | Path |
|-----------|------|
| Installation | `/opt/metasploit-framework` |
| Console | `msfconsole` |

---

## Permissions

- User `flux` added to `wireshark` group for packet capture
- Wireshark configured for non-root capture

---

## Packages Installed

```bash
nmap netcat-traditional tcpdump
wireshark tshark
aircrack-ng
john hydra hashcat
sqlmap nikto
```

---

## Verification

```bash
nmap --version
wireshark --version
msfconsole --version
john --test
```
