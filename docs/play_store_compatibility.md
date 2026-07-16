# Google Play Store Compatibility and Native Binary Execution Guide

This document explains the technical challenges, requirements, and workarounds for compiling, packaging, and distributing **FluxLinux** (which integrates Termux and X11 libraries) on the Google Play Store, given modern target SDK guidelines and Android OS security policies.

---

## 1. The Core Conflict: Target SDK vs. W^X Restrictions

### Google Play targetSdk Requirements
Google Play enforces annual updates to the minimum target SDK version:
- **API Level 35 (Android 15)**: Required for all updates and existing apps to remain discoverable.
- **API Level 36 (Android 16)**: Required for new submissions and major updates (effective August 31, 2026).

FluxLinux currently targets **API Level 28 (Android 9.0)** in `app/build.gradle.kts` and `modules/termux-app/gradle.properties`.

### W^X (Write XOR Execute) Security Policy
Starting with **Android 10 (API Level 29)**, Android enforces a W^X policy on the application sandbox:
- Writable directories (such as `/data/data/com.ivarna.nativecode/files/` or any local storage path) **cannot** contain executable files.
- Attempting to execute any binary located in these directories via `execve` throws a permission/SELinux error (`Permission Denied` / `untrusted_app` policy violation).

---

## 2. Play Store Compliance Workaround: Packaging Binaries in `jniLibs`

To publish on the Play Store, the application **must** target API Level 35+ and utilize the native library extraction mechanism:

### How it Works
1. **Rename Binaries**: Rename all compiled native executable binaries (e.g., `bash`, `proot`, `tar`) to follow the shared library naming convention: `lib<binary_name>.so` (e.g., `libbash.so`, `libproot.so`, `libtar.so`).
2. **Place in `jniLibs`**: Put these renamed files inside the app's `jniLibs` directory for each supported architecture:
   ```
   app/src/main/jniLibs/
   ├── arm64-v8a/
   │   ├── libbash.so
   │   └── libproot.so
   └── armeabi-v7a/
       ├── libbash.so
       └── libproot.so
   ```
3. **Automatic Extraction**: During app installation, the Android package manager extracts the `lib*.so` files to the app's native library directory (`nativeLibraryDir`).
4. **Read-Only / Executable Execution**:
   - The OS maps `nativeLibraryDir` (e.g., `/data/app/~~[random_hash]/com.ivarna.nativecode-[hash]/lib/[arch]/`) as **read-only but executable**.
   - Your application can invoke these binaries using their absolute paths:
     ```kotlin
     val binaryPath = "${context.applicationInfo.nativeLibraryDir}/libbash.so"
     Runtime.getRuntime().exec(binaryPath)
     ```

### Configuration Requirements
Ensure `android:extractNativeLibs` is not disabled in your `AndroidManifest.xml` (or build properties) to allow the OS to perform extraction.

---

## 3. Play Store Limitations of the Workaround

While the `jniLibs` approach allows the app to target API 35+ and pass Google Play checks, it introduces severe functional limitations:

1. **No Runtime Binary Downloads / Updates**:
   - Any package downloaded at runtime (e.g., using `apt`, `pkg`, or curl-ing a new rootfs tool) is written to a writable directory.
   - You **cannot** execute these downloaded binaries because they violate W^X rules.
   - All executable binaries/tools must be pre-packaged in the APK or App Bundle (AAB).
2. **Limited Linux Environments**:
   - Running full distributions (like Debian/Ubuntu rootfs) requires all binaries in the rootfs to be extracted/mapped as native libraries, which is impractical due to size limits on the Play Store (150MB APK size limit without asset packs).
   - Alternatively, you must run code inside an interpreted virtual machine or interpreter (like JavaScript/Python) that runs on top of a pre-compiled, static engine packaged in the APK.

---

## 4. Alternative Distribution (Recommended)

Because of the restrictions above, the standard way to distribute fully-functional terminal environments and Linux runners (like Termux) is via **F-Droid** or **GitHub Releases**:

- **No targetSdk Enforcements**: F-Droid and direct GitHub APK installs do not mandate upgrading the target SDK.
- **Maintain targetSdk 28**: Keeping targetSdk at 28 allows the app to run in compatibility mode, bypassing the W^X restrictions and permitting dynamic execution of downloaded native binaries (enabling full `apt` and local Linux distribution management).
