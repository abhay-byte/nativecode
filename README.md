<div align="center">
  <img src="assets/logo/logo.webp" width="180" />
  <h1>FluxLinux</h1>
  <p><strong>Run full Linux desktop environments on your Android device</strong></p>

<a href="https://play.google.com/store/apps/details?id=com.zenithblue.fluxlinux">
  <img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="80"/>
</a>

<a href="https://f-droid.org/packages/com.zenithblue.fluxlinux">
  <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="80"/>
</a>

---

[![GitHub Downloads](https://img.shields.io/github/downloads/abhay-byte/fluxlinux/total?style=for-the-badge&logo=github&logoColor=white&labelColor=24292e&color=success)](https://github.com/abhay-byte/fluxlinux/releases)
[![GitHub Stars](https://img.shields.io/github/stars/abhay-byte/fluxlinux?style=for-the-badge&logo=github&logoColor=white&labelColor=24292e&color=yellow)](https://github.com/abhay-byte/fluxlinux/stargazers)
[![GitHub Forks](https://img.shields.io/github/forks/abhay-byte/fluxlinux?style=for-the-badge&logo=github&logoColor=white&labelColor=24292e&color=blue)](https://github.com/abhay-byte/fluxlinux/network/members)
[![License](https://img.shields.io/badge/License-GPLv3-blue.svg?style=for-the-badge&logo=gnu&logoColor=white)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg?style=for-the-badge&logo=android&logoColor=white)](https://www.android.com)

</div>

---

## 📱 Screenshots

<div align="center">
  <table>
    <tr>
      <td align="center"><img src="assets/screenshots/home.png" width="200" /><br/><b>Home</b></td>
      <td align="center"><img src="assets/screenshots/distros.png" width="200" /><br/><b>Distros</b></td>
      <td align="center"><img src="assets/screenshots/install.png" width="200" /><br/><b>Install</b></td>
    </tr>
    <tr>
      <td align="center"><img src="assets/screenshots/settings.png" width="200" /><br/><b>Settings</b></td>
      <td align="center"><img src="assets/screenshots/desktop.png" width="200" /><br/><b>Desktop</b></td>
      <td align="center"><img src="assets/screenshots/terminal.png" width="200" /><br/><b>Terminal</b></td>
    </tr>
  </table>
</div>

---

## 🚀 Vision

Modern Android hardware is powerful enough to run desktop workloads, but the software ecosystem limits it. **FluxLinux** bridges this gap, enabling:

- 🌐 **Full-Stack Web Development** — Node.js, Python, React, VS Code
- 🎮 **Desktop Gaming** — Box64/Wine *(coming soon)*
- 🔐 **Cybersecurity** — Nmap, Wireshark, Metasploit
- 📊 **Data Science** — Jupyter, TensorFlow, PyTorch
- 🎨 **Creative Tools** — GIMP, Blender, Inkscape
- 📄 **Productivity** — LibreOffice, Firefox Desktop

---

## ✨ Key Features

| Feature | Description |
|---------|-------------|
| 🐧 **One-Tap Install** | Install Debian with XFCE4 desktop in minutes |
| 🔓 **Rootless Mode** | Works on any Android 8+ device via PRoot |
| ⚡ **Turbo Mode** | Native chroot performance for rooted devices |
| 🎮 **GPU Acceleration** | Turnip (Adreno) + VirGL for graphics |
| 🎨 **Custom Themes** | Beautiful XFCE4 with Space theme |
| 📦 **Dev Stacks** | Pre-configured environments for coding |

---

## 🖼️ Desktop Experience

<div align="center">
  <img src="assets/screenshots/xfce_desktop.png" width="700" />
  <p><em>Full XFCE4 desktop with hardware acceleration</em></p>
</div>

### 🚀 Development in Action

<div align="center">
<table>
<tr>
<td align="center"><img src="assets/screenshots/flutter.png" width="350" /><br/><b>Flutter Development</b></td>
<td align="center"><img src="assets/screenshots/react.png" width="350" /><br/><b>React Web App</b></td>
</tr>
<tr>
<td align="center"><img src="assets/screenshots/jupyter_tf.png" width="350" /><br/><b>Jupyter + TensorFlow</b></td>
<td align="center"><img src="assets/screenshots/kotlin.png" width="350" /><br/><b>Kotlin/Gradle Build</b></td>
</tr>
<tr>
<td align="center"><img src="assets/screenshots/gimp.png" width="350" /><br/><b>GIMP Image Editor</b></td>
<td align="center"><img src="assets/screenshots/libre-writer.png" width="350" /><br/><b>LibreOffice Writer</b></td>
</tr>
<tr>
<td align="center" colspan="2"><img src="assets/screenshots/pitivi.png" width="500" /><br/><b>Pitivi Video Editor</b></td>
</tr>
</table>
</div>

### Included Development Stacks

<div align="center">
  <table>
    <tr>
      <td align="center">🌐<br/><b>Web Dev</b><br/>Node.js, React, VS Code</td>
      <td align="center">📱<br/><b>App Dev</b><br/>Flutter, Kotlin, Android SDK</td>
      <td align="center">🧬<br/><b>Data Science</b><br/>Jupyter, TensorFlow</td>
    </tr>
    <tr>
      <td align="center">🎮<br/><b>Game Dev</b><br/>Godot Engine</td>
      <td align="center">🔐<br/><b>Security</b><br/>Kali Tools</td>
      <td align="center">🎨<br/><b>Graphics</b><br/>GIMP, Blender</td>
    </tr>
  </table>
</div>

---

## 🛠 Architecture

```mermaid
flowchart TB
    subgraph Android["📱 Android Device"]
        FluxLinux["🚀 FluxLinux App<br/>(Kotlin + Jetpack Compose)"]
        
        subgraph Termux["🔧 Termux Environment"]
            TermuxHost["Terminal Host"]
            
            subgraph Container["Linux Container"]
                PRoot["🔓 PRoot<br/>(Rootless)"]
                Chroot["⚡ Chroot<br/>(Rooted)"]
            end
            
            subgraph Distro["🐧 Debian 13 Trixie"]
                XFCE["XFCE4 Desktop"]
                DevTools["Development Tools"]
            end
        end
        
        subgraph Display["🖥️ Display System"]
            X11["Termux:X11"]
            GPU["GPU Acceleration<br/>(Turnip/VirGL)"]
        end
    end
    
    FluxLinux --> TermuxHost
    TermuxHost --> PRoot
    TermuxHost --> Chroot
    PRoot --> Distro
    Chroot --> Distro
    Distro --> X11
    X11 --> GPU
```

---

## 📚 Documentation

| Document | Description |
|----------|-------------|
| [**Installation Reference**](docs/install_ref/) | Packages, paths, versions, environments |
| [**Scripts Reference**](docs/scripts_reference.md) | All installation and setup scripts |
| [**Hardware Acceleration**](docs/hardware_acceleration.md) | GPU setup guide (Turnip/VirGL) |
| [**Script Execution Workflow**](docs/script_execution_workflow.md) | How scripts are executed |
| [**Testing Reference**](docs/testing_reference.md) | Sample projects for testing |
| [**Assets Reference**](docs/assets_reference.md) | Themes, icons, wallpapers |
| [**Architecture**](docs/architecture.md) | System design overview |
| [**Roadmap**](docs/roadmap.md) | Development roadmap |

---

## 📦 Installation

### Requirements

- Android 8.0+ (API 26+)
- [Termux](https://f-droid.org/packages/com.termux/) (from F-Droid)
- [Termux:X11](https://github.com/termux/termux-x11) (for GUI)

### Install

1. Download FluxLinux from [Releases](https://github.com/abhay-byte/fluxlinux/releases)
2. Install Termux from F-Droid
3. Install Termux:X11
4. Open FluxLinux and follow setup wizard

<div align="center">
  <img src="assets/screenshots/setup_wizard.png" width="250" />
  <p><em>Easy setup wizard</em></p>
</div>

---

## 🎮 GPU Acceleration

FluxLinux supports hardware-accelerated graphics:

<table>
<tr>
<td width="50%">

| GPU Type | Driver | Performance |
|----------|--------|-------------|
| Adreno (Qualcomm) | Turnip + Zink | 🟢 Excellent |
| Mali (ARM) | VirGL | 🟡 Good |
| Mali/PowerVR (MediaTek) | VirGL | 🟡 Good |
| Other | VirGL | 🟡 Good |

📖 [Hardware Acceleration Guide](docs/hardware_acceleration.md)

</td>
<td width="50%" align="center">

<img src="assets/screenshots/hardware_acceleration/1.png" width="300" />
<br/><em>GPU Driver Selection</em>

</td>
</tr>
</table>

---

## 🤝 Contributing

Contributions are welcome! Please check the [Roadmap](docs/roadmap.md) to see active development phases.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📄 License

This project is licensed under the **GNU General Public License v3.0 (GPLv3)**.

See [LICENSE](LICENSE) for details.

---

<div align="center">
  <p>Made with ❤️ by <a href="https://github.com/abhay-byte">Abhay Raj</a></p>
  <p>
    <a href="https://github.com/abhay-byte/fluxlinux">GitHub</a> •
    <a href="https://github.com/abhay-byte/fluxlinux/issues">Issues</a> •
    <a href="docs/">Documentation</a>
  </p>
</div>
