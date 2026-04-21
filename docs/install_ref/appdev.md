# App Development Stack

*Script: `setup_appdev_debian.sh`*

---

## Overview

Installs a complete Android/Flutter/Kotlin development environment on Debian ARM64.

---

## Java Development Kit

| Component | Version | Path |
|-----------|---------|------|
| OpenJDK | 21 (or 17 fallback) | `/usr/lib/jvm/java-21-openjdk-arm64` |

> **Why JDK 21?** Debian 13 Trixie ships with OpenJDK 21 as default. Required for Android Gradle Plugin 8.x and Flutter.

---

## Android SDK

| Component | Version | Path |
|-----------|---------|------|
| SDK Root | - | `/opt/android-sdk` |
| Command Line Tools | 11076708 | `/opt/android-sdk/cmdline-tools/latest` |

> **Why this version?** Command Line Tools 11076708 is the stable release compatible with ARM64.

---

## SDK Components Installed via sdkmanager

| Component | Version | Ships x86? | Fix Applied |
|-----------|---------|------------|-------------|
| platform-tools | Latest | ✅ Yes (adb, fastboot) | Wrapper → `/usr/bin/adb` |
| cmdline-tools | latest | ❌ No | - |
| platforms;android-34 | 34 | ❌ No | - |
| platforms;android-35 | 35 | ❌ No | - |
| platforms;android-36 | 36 | ❌ No | - |
| build-tools;35.0.0 | 35.0.0 | ✅ Yes (aapt, aapt2, aidl, zipalign) | ARM64 from lzhiyong |
| build-tools;36.0.0 | 36.0.0 | ✅ Yes | ARM64 from lzhiyong |
| cmake;3.22.1 | 3.22.1 | ✅ Yes (cmake, ninja) | Wrapper → `/usr/bin/cmake` |

---

## x86 Binary Fixes

### Platform Tools (adb, fastboot)

The SDK downloads x86 `adb` and `fastboot`. These are replaced with wrapper scripts:

```bash
# /opt/android-sdk/platform-tools/adb
#!/bin/sh
exec /usr/bin/adb "$@"

# /opt/android-sdk/platform-tools/fastboot
#!/bin/sh
exec /usr/bin/fastboot "$@"
```

> Uses Debian's ARM64-native `adb` and `fastboot` (installed via apt).

### CMake & Ninja

The SDK's CMake package ships with x86 binaries. These are replaced:

```bash
# Wrapper script
#!/bin/sh
exec /usr/bin/cmake "$@"
```

> Uses system CMake/Ninja installed via apt.

### Build Tools (aapt, aapt2, aidl, zipalign)

ARM64-native build tools from [lzhiyong/android-sdk-tools](https://github.com/lzhiyong/android-sdk-tools) are used:

| Tool | Original | Replacement |
|------|----------|-------------|
| aapt | x86_64 | ARM64 static |
| aapt2 | x86_64 | ARM64 static |
| aidl | x86_64 | ARM64 static |
| zipalign | x86_64 | ARM64 static |

---

## Android NDK

> ⚠️ **Important:** NDK is NOT installed via sdkmanager because it ships x86-only binaries.

ARM64 NDKs from [HomuHomu833/android-ndk-custom](https://github.com/HomuHomu833/android-ndk-custom) are used:

| Version | Version Code | Location |
|---------|--------------|----------|
| r27d | 27.3.13750724 | `/opt/android-sdk/ndk/27.3.13750724` |
| r29 | 29.0.14206865 | `/opt/android-sdk/ndk/29.0.14206865` |

**Why custom NDKs?**
- Official NDK ships with x86_64 clang/llvm toolchains
- Custom NDKs are statically linked (musl-based)
- Work natively on ARM64 Linux without emulation

---

## Flutter SDK

| Component | Version | Path |
|-----------|---------|------|
| Flutter SDK | Latest stable | `/opt/flutter` |
| Dart SDK | Bundled | `/opt/flutter/bin/cache/dart-sdk` |

---

## Gradle

| Component | Version | Path |
|-----------|---------|------|
| Gradle | 8.10.2+ | `/opt/gradle` |

> **Why not apt?** Debian ships ancient Gradle 4.4.1. Modern Android requires Gradle 8.x.

---

## Other Tools Installed

| Tool | Source | Purpose |
|------|--------|---------|
| IntelliJ IDEA CE | JetBrains | IDE |
| cmake | apt | Native builds |
| ninja-build | apt | Build system |
| clang | apt | C/C++ compiler |
| chromium | apt | Chrome DevTools |
| adb | apt | Android Debug Bridge (ARM64) |
| fastboot | apt | Android Fastboot (ARM64) |
| aapt | apt | Android Asset Packaging |

---

## Environment Variables

```bash
export ANDROID_HOME=/opt/android-sdk
export FLUTTER_ROOT=/opt/flutter
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
export PATH=$PATH:$ANDROID_HOME/platform-tools
export PATH=$PATH:/opt/flutter/bin
export PATH=$PATH:/opt/gradle/bin
```

---

## Verification

```bash
flutter doctor
java -version
gradle --version
adb version
sdkmanager --list_installed
```

---

## Summary Table

| What | Ships x86? | Solution |
|------|------------|----------|
| platform-tools (adb, fastboot) | ✅ | Wrapper to apt ARM64 binaries |
| build-tools (aapt, aapt2, aidl, zipalign) | ✅ | ARM64 from lzhiyong |
| cmake | ✅ | Wrapper to `/usr/bin/cmake` |
| ninja | ✅ | Wrapper to `/usr/bin/ninja` |
| NDK (clang, llvm) | ✅ | ARM64 NDK from HomuHomu833 |
| platforms (android-34/35/36) | ❌ | Pure Java/resources |
| cmdline-tools (sdkmanager) | ❌ | Java-based |
