package com.ivarna.nativecode.core.termux

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Manages the Termux bootstrap environment.
 *
 * On first use, downloads the official Termux bootstrap zip for the device ABI,
 * extracts it to [prefixDir], and creates symlinks defined in the zip's SYMLINKS.txt.
 *
 * Termux binaries have hardcoded paths (/data/data/com.termux/files/usr), so we use
 * the bundled libproot.so to create a namespace that binds our prefixDir to the
 * canonical Termux path.
 *
 * No root required — everything runs in the app's private data dir.
 */
object TermuxBootstrapManager {

    private const val TAG = "TermuxBootstrapManager"

    // Official Termux bootstrap URL (GitHub releases, version-independent latest)
    private const val BOOTSTRAP_BASE_URL =
        "https://github.com/termux/termux-packages/releases/latest/download"

    // Where we extract the Termux bootstrap zip (relative to filesDir).
    // Contents mirror the canonical Termux prefix structure (usr/bin, etc, lib, ...).
    private const val TERMUX_PREFIX_RELATIVE = "termux-prefix"

    // Canonical Termux prefix path that proot binds to.
    private const val CANONICAL_TERMUX_PREFIX = "/data/data/com.termux/files/usr"

    fun prefixDir(context: Context): File =
        File(context.filesDir, TERMUX_PREFIX_RELATIVE)

    fun homeDir(context: Context): File =
        File(context.filesDir, "termux-home").also { it.mkdirs() }

    /** True if the bootstrap has been extracted and ready to use. */
    fun isInstalled(context: Context): Boolean {
        val prefix = prefixDir(context)
        val bash = File(prefix, "bin/bash")
        val marker = File(prefix, ".bootstrap-done")
        return bash.exists() && marker.exists()
    }

    /**
     * Path to the proot binary.
     * We copy it to filesDir so libtalloc.so.2 (also in filesDir) is found by the linker.
     * nativeLibraryDir is NOT usable as working directory for dependency resolution.
     */
    fun prootBinary(context: Context): File =
        File(context.filesDir, "libproot.so")

    /**
     * The actual executable to pass as shellPath to TerminalSession.
     *
     * On Android 10+ (SELinux Enforcing), the untrusted_app domain cannot execve files
     * from app_data_file (filesDir). The workaround: use /system/bin/linker64 (a system
     * binary with execute permission) and pass libproot.so as its first argument.
     * linker64 loads the shared object and jumps to its entry point — same effect as exec.
     */
    fun launcherPath(): String = "/system/bin/linker64"

    /**
     * Builds the argv array for launching a Termux shell via proot.
     *
     * Uses the extracted bootstrap as the rootfs. ELF RUNPATH is satisfied
     * by a symlink created during extraction (data/data/com.termux/files/usr → /).
     */
    fun buildProotArgs(context: Context): Array<String> {
        val prefix = prefixDir(context)
        val home = homeDir(context)
        val proot = prootBinary(context).absolutePath
        val tmp = File(context.filesDir, "termux-tmp").also {
            it.mkdirs()
            it.setReadable(true, false)
            it.setWritable(true, false)
            it.setExecutable(true, false)
        }
        // apt cache dir — mapped to /data/data/com.termux/cache inside proot
        val cache = File(context.filesDir, "termux-cache").also {
            File(it, "apt/archives/partial").mkdirs()
            it.setReadable(true, false)
            it.setWritable(true, false)
            it.setExecutable(true, false)
        }

        return arrayOf(
            proot,                          // args[0]: linker64 loads this .so
            proot,                          // args[1]: proot sees this as argv[0] (its own path)
            "--rootfs=/",
            "--bind=/dev", "--bind=/proc", "--bind=/sys",
            "--bind=/system",
            "--bind=${prefix.absolutePath}:$CANONICAL_TERMUX_PREFIX",
            "--bind=${home.absolutePath}:/data/data/com.termux/files/home",
            "--bind=${tmp.absolutePath}:$CANONICAL_TERMUX_PREFIX/tmp",
            "--bind=${cache.absolutePath}:/data/data/com.termux/cache",
            "-w", "/data/data/com.termux/files/home",
            "$CANONICAL_TERMUX_PREFIX/bin/bash",
        )
    }

    fun buildEnvironment(context: Context): Array<String> {
        val nativeLib = context.applicationInfo.nativeLibraryDir
        val filesDir  = context.filesDir.absolutePath

        // LD_LIBRARY_PATH: filesDir first (has libtalloc.so.2, libproot_loader.so),
        // then nativeLibraryDir (has libXlorie.so etc.), then Termux prefix libs.
        val ldPath = "$filesDir:$nativeLib:$CANONICAL_TERMUX_PREFIX/lib"

        // PROOT_LOADER: proot uses this to exec guest binaries inside the proot namespace.
        // Must point to libproot_loader.so in nativeLibraryDir (SELinux: apk_data_file),
        // NOT filesDir (SELinux: app_data_file — not executable on Android 10+).
        val prootLoader = "$nativeLib/libproot_loader.so"

        return arrayOf(
            "HOME=/data/data/com.termux/files/home",
            "PREFIX=$CANONICAL_TERMUX_PREFIX",
            "TMPDIR=$CANONICAL_TERMUX_PREFIX/tmp",
            "TERM=xterm-256color",
            "LANG=en_US.UTF-8",
            "COLORTERM=truecolor",
            "PATH=$CANONICAL_TERMUX_PREFIX/bin:$CANONICAL_TERMUX_PREFIX/bin/applets:/system/bin:/system/xbin",
            "PROOT_TMP_DIR=$filesDir/termux-tmp",
            "PROOT_LOADER=$prootLoader",
            "LD_LIBRARY_PATH=$ldPath",
        )
    }

    /**
     * Copy proot and its dependencies to filesDir.
     *
     * libproot.so requires libtalloc.so.2 at link time. Android's linker resolves
     * dependencies relative to LD_LIBRARY_PATH, which we cannot set before exec.
     * Solution: put ALL proot-related .so files in the same directory (filesDir) so
     * the linker finds them via RUNPATH or the default search path.
     */
    fun ensureProotDeps(context: Context) {
        val nativeLib = context.applicationInfo.nativeLibraryDir
        listOf(
            "libproot.so"      to "libproot.so",        // main proot binary
            "libproot_loader.so" to "libproot_loader.so",
            "libtalloc.so"     to "libtalloc.so.2",
            "libmemfd_shim.so" to "libmemfd_shim.so",
        ).forEach { (src, dst) ->
            val srcFile = File(nativeLib, src)
            val dstFile = File(context.filesDir, dst)
            if (srcFile.exists()) {
                try {
                    srcFile.copyTo(dstFile, overwrite = true)
                    dstFile.setReadable(true, false)
                    dstFile.setExecutable(true, false)
                    Log.d(TAG, "Copied $src -> $dst (${dstFile.length()} bytes)")
                } catch (e: Exception) {
                    Log.w(TAG, "Copy $src failed", e)
                }
            } else {
                Log.w(TAG, "Missing native lib: $src in $nativeLib")
            }
        }
        // Create libtalloc symlink without .2 suffix as fallback
        val talloc2 = File(context.filesDir, "libtalloc.so.2")
        val tallocLink = File(context.filesDir, "libtalloc.so")
        if (talloc2.exists() && !tallocLink.exists()) {
            try {
                java.nio.file.Files.createSymbolicLink(tallocLink.toPath(), java.nio.file.Paths.get("libtalloc.so.2"))
            } catch (_: Exception) {}
        }

        // Fix missing APT dirs + keyring for already-installed bootstraps.
        // These may be absent if the bootstrap was extracted by an older version.
        ensureAptKeyring(context)
    }

    /**
     * Creates missing APT directories and downloads the Termux signing key if absent.
     * Safe to call on every launch — skips download if key file already exists and is non-empty.
     */
    private fun ensureAptKeyring(context: Context) {
        val prefix = prefixDir(context)
        listOf("etc/apt/apt.conf.d", "etc/apt/trusted.gpg.d", "etc/apt/preferences.d", "var/log/apt").forEach {
            File(prefix, it).apply { mkdirs(); setReadable(true, false); setExecutable(true, false) }
        }
        val keyFile = File(prefix, "etc/apt/trusted.gpg.d/termux-keyring.gpg")
        if (keyFile.exists() && keyFile.length() > 0) return
        try {
            // Copy bundled binary keyring from assets (key ID: 5A897D96E57CF20C)
            context.assets.open("termux-keyring.gpg").use { inp ->
                FileOutputStream(keyFile).use { inp.copyTo(it) }
            }
            keyFile.setReadable(true, false)
            Log.i(TAG, "Termux keyring installed from assets: ${keyFile.length()} bytes")
        } catch (e: Exception) {
            Log.w(TAG, "Keyring install failed", e)
        }
    }


    // -------------------------------------------------------------------------
    // Bootstrap download + extraction
    // -------------------------------------------------------------------------

    sealed class BootstrapState {
        data object NotInstalled : BootstrapState()
        data class Downloading(val percent: Int) : BootstrapState()
        data class Extracting(val count: Int) : BootstrapState()
        data object Done : BootstrapState()
        data class Error(val message: String) : BootstrapState()
    }

    /**
     * Downloads and extracts the Termux bootstrap for the current ABI,
     * then sets up proot dependencies. Calls [onProgress] on each state change.
     */
    suspend fun install(
        context: Context,
        onProgress: (BootstrapState) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val abi = primaryAbi()
            val url = "$BOOTSTRAP_BASE_URL/bootstrap-$abi.zip"
            Log.i(TAG, "Bootstrap URL: $url")

            onProgress(BootstrapState.Downloading(0))

            val zipFile = File(context.cacheDir, "bootstrap-$abi.zip")
            download(url, zipFile, onProgress)

            onProgress(BootstrapState.Extracting(0))
            val prefix = prefixDir(context)
            prefix.deleteRecursively()
            prefix.mkdirs()
            extract(zipFile, prefix, onProgress)
            zipFile.delete()

            ensureProotDeps(context)

            onProgress(BootstrapState.Done)
        } catch (e: Exception) {
            Log.e(TAG, "Bootstrap install failed", e)
            onProgress(BootstrapState.Error(e.message ?: "Unknown error"))
        }
    }

    private fun primaryAbi(): String {
        val supported = Build.SUPPORTED_ABIS
        return when {
            supported.any { it == "arm64-v8a" }  -> "aarch64"
            supported.any { it == "armeabi-v7a" } -> "arm"
            supported.any { it == "x86_64" }      -> "x86_64"
            supported.any { it == "x86" }         -> "i686"
            else -> "aarch64"
        }
    }

    private fun download(
        urlStr: String,
        dest: File,
        onProgress: (BootstrapState) -> Unit
    ) {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 30_000
        conn.readTimeout    = 60_000
        conn.connect()

        if (conn.responseCode != 200) {
            throw RuntimeException("Download failed: HTTP ${conn.responseCode} from $urlStr")
        }

        val total = conn.contentLengthLong
        var downloaded = 0L

        conn.inputStream.use { inp ->
            FileOutputStream(dest).use { out ->
                val buf = ByteArray(8 * 1024)
                var n: Int
                while (inp.read(buf).also { n = it } != -1) {
                    out.write(buf, 0, n)
                    downloaded += n
                    if (total > 0) {
                        val pct = (downloaded * 100 / total).toInt()
                        onProgress(BootstrapState.Downloading(pct))
                    }
                }
            }
        }
    }

    /**
     * Extracts the Termux bootstrap zip.
     * The zip contents are relative to $PREFIX (i.e. "usr/").
     * SYMLINKS.txt entries use "target←source" format.
     */
    private fun extract(
        zipFile: File,
        prefix: File,
        onProgress: (BootstrapState) -> Unit
    ) {
        val symlinkLines = mutableListOf<String>()
        var count = 0

        ZipInputStream(zipFile.inputStream().buffered()).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                val name = entry.name.trimStart('/')

                when {
                    name == "SYMLINKS.txt" -> {
                        symlinkLines.addAll(
                            zin.bufferedReader().readText()
                                .lines()
                                .filter { it.contains("←") }
                        )
                    }
                    !entry.isDirectory && name.isNotEmpty() -> {
                        val dest = File(prefix, name)
                        dest.parentFile?.mkdirs()
                        dest.parentFile?.setExecutable(true, false)
                        dest.parentFile?.setReadable(true, false)
                        FileOutputStream(dest).use { out -> zin.copyTo(out) }
                        // Make all files readable; binaries + libs executable
                        dest.setReadable(true, false)
                        if (name.endsWith(".so") ||
                            name.startsWith("bin/") ||
                            name.startsWith("usr/bin/") ||
                            name.startsWith("usr/libexec/") ||
                            name.startsWith("lib/") ||
                            name.startsWith("usr/lib/")) {
                            dest.setExecutable(true, false)
                        }
                        count++
                    }
                }

                try { zin.closeEntry() } catch (_: Exception) {}
                entry = zin.nextEntry
            }
        }

        // Create symlinks from manifest
        // SYMLINKS.txt format: TARGET←SOURCE (target is relative to source's parent dir)
        var symlinksCreated = 0
        for (line in symlinkLines) {
            val parts = line.split("←")
            if (parts.size == 2) {
                val target = parts[0].trim()
                val sourcePath = parts[1].trim().trimStart('.', '/')
                val sourceFile = File(prefix, sourcePath)
                val symlinkDir = sourceFile.parentFile ?: continue
                symlinkDir.mkdirs()
                symlinkDir.setExecutable(true, false)
                symlinkDir.setReadable(true, false)

                // The target is relative to the symlink's directory
                val resolvedTarget = File(symlinkDir, target)
                if (resolvedTarget.exists() && !sourceFile.exists()) {
                    try {
                        java.nio.file.Files.createSymbolicLink(
                            sourceFile.toPath(),
                            java.nio.file.Paths.get(target)
                        )
                        symlinksCreated++
                    } catch (e: Exception) {
                        // Fallback: run ln from the symlink's parent dir
                        try {
                            val proc = Runtime.getRuntime().exec(
                                arrayOf("/system/bin/ln", "-sf", target, sourceFile.name),
                                null,
                                symlinkDir
                            )
                            proc.waitFor()
                            if (proc.exitValue() == 0) symlinksCreated++
                        } catch (e2: Exception) {
                            Log.w(TAG, "Symlink failed: $line", e2)
                        }
                    }
                }
            }
        }
        Log.i(TAG, "Created $symlinksCreated symlinks")

        // Ensure required dirs exist
        listOf("usr/var", "usr/tmp", "home",
               "etc/apt/apt.conf.d", "etc/apt/trusted.gpg.d").forEach {
            File(prefix, it).apply { mkdirs(); setReadable(true, false); setExecutable(true, false) }
        }

        // Download Termux signing key if missing (key ID: 5A897D96E57CF20C).
        // The bootstrap zip doesn't include the keyring package; fetch it from packages.termux.dev.
        val keyFile = File(prefix, "etc/apt/trusted.gpg.d/termux-keyring.gpg")
        if (!keyFile.exists() || keyFile.length() == 0L) {
            try {
                val keyUrl = "https://packages.termux.dev/apt/termux-main/dists/stable/InRelease"
                // Prefer the binary keyring from the termux-keyring package on GitHub
                val gpgUrl = "https://raw.githubusercontent.com/termux/termux-app/master/app/src/main/res/raw/termux_keyring.gpg"
                val conn = java.net.URL(gpgUrl).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout    = 15_000
                conn.instanceFollowRedirects = true
                conn.connect()
                if (conn.responseCode == 200) {
                    conn.inputStream.use { inp -> FileOutputStream(keyFile).use { inp.copyTo(it) } }
                    keyFile.setReadable(true, false)
                    Log.i(TAG, "Downloaded Termux keyring: ${keyFile.length()} bytes")
                } else {
                    Log.w(TAG, "Keyring download: HTTP ${conn.responseCode}")
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Keyring download failed (pkg may show NO_PUBKEY)", e)
            }
        }

        // Create RUNPATH symlink: Termux ELFs have RUNPATH /data/data/com.termux/files/usr/lib
        // which doesn't exist inside our rootfs. Symlink it to / so /lib resolves.
        val runpathDir = File(prefix, "data/data/com.termux/files")
        runpathDir.mkdirs()
        runpathDir.setReadable(true, false)
        runpathDir.setExecutable(true, false)
        val runpathLink = File(runpathDir, "usr")
        if (!runpathLink.exists()) {
            try {
                java.nio.file.Files.createSymbolicLink(
                    runpathLink.toPath(),
                    java.nio.file.Paths.get("/")
                )
            } catch (e: Exception) {
                Log.w(TAG, "RUNPATH symlink failed", e)
            }
        }

        // Fix permissions — all files must be readable, dirs traversable for proot
        fixPermissions(prefix)

        // Write marker so isInstalled() returns true on next launch
        File(prefix, ".bootstrap-done").writeText("1")

        Log.i(TAG, "Extracted $count files + $symlinksCreated symlinks")
    }

    /** Recursively make all files readable and dirs traversable. */
    private fun fixPermissions(dir: File) {
        dir.walkBottomUp().forEach { f ->
            if (f.isDirectory) {
                f.setExecutable(true, false)
                f.setReadable(true, false)
            } else {
                f.setReadable(true, false)
            }
        }
    }
}
