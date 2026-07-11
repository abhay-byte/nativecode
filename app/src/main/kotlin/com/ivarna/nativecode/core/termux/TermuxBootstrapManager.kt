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
 * Termux bootstrap for NativeCode — **Termux-like direct shell** when possible.
 *
 * ## Why stock Termux is faster
 * Real Termux (`com.termux`) execs `bash` natively. Official packages hardcode
 * `/data/data/com.termux/files/usr`. Our package id is longer, so a stock
 * bootstrap cannot be fully string-patched in-place for every ELF.
 *
 * ## What we do instead (best practical match)
 * 1. Extract official bootstrap under `files/termux-prefix`.
 * 2. Rewrite **text/scripts** to our real paths.
 * 3. Launch shell as: `/system/bin/linker64 <prefix>/bin/bash` with
 *    `PREFIX`/`HOME`/`PATH`/`LD_LIBRARY_PATH` pointing at **our** dirs —
 *    same idea as Termux session start, without an outer **proot** wrapper.
 * 4. Keep classic **proot bind** mode as fallback (`preferProot=true` or probe fail).
 *
 * Full “recompile every package for com.ivarna.nativecode” is documented in
 * `docs/termux-native-prefix.md` (termux-packages `TERMUX_APP_PACKAGE`).
 */
object TermuxBootstrapManager {

    private const val TAG = "TermuxBootstrapManager"

    private const val BOOTSTRAP_BASE_URL =
        "https://github.com/termux/termux-packages/releases/latest/download"

    private const val TERMUX_PREFIX_RELATIVE = "usr"

    /** Hardcoded in official Termux packages (path rewrite source). */
    const val STOCK_TERMUX_PREFIX = "/data/data/com.termux/files/usr"
    const val STOCK_TERMUX_HOME = "/data/data/com.termux/files/home"
    const val STOCK_TERMUX_BASE = "/data/data/com.termux/files"

    fun prefixDir(context: Context): File =
        File(context.filesDir, TERMUX_PREFIX_RELATIVE)

    fun homeDir(context: Context): File =
        File(context.filesDir, "termux-home").also { it.mkdirs() }

    fun tmpDir(context: Context): File =
        File(context.filesDir, "termux-tmp").also {
            it.mkdirs()
            it.setReadable(true, false)
            it.setWritable(true, false)
            it.setExecutable(true, false)
        }

    fun isInstalled(context: Context): Boolean {
        val prefix = prefixDir(context)
        return File(prefix, "bin/bash").exists() && File(prefix, ".bootstrap-done").exists()
    }

    fun prootBinary(context: Context): File =
        File(context.filesDir, "libproot.so")

    /** System linker used to load app-private PIE binaries (SELinux). */
    fun linkerPath(): String =
        if (File("/system/bin/linker64").exists()) "/system/bin/linker64"
        else "/system/bin/linker"

    /**
     * Real PREFIX path for this install (not the stock com.termux path).
     * Prefer canonical /data/data/ form when available for consistency with tools.
     */
    fun realPrefixPath(context: Context): String {
        val p = prefixDir(context).absolutePath
        // Normalize /data/user/0/ → /data/data/ when it's the same tree
        return p.replace("/data/user/0/", "/data/data/")
    }

    fun realHomePath(context: Context): String {
        val p = homeDir(context).absolutePath
        return p.replace("/data/user/0/", "/data/data/")
    }

    fun realBasePath(context: Context): String {
        val p = context.filesDir.absolutePath
        return p.replace("/data/user/0/", "/data/data/")
    }

    // -------------------------------------------------------------------------
    // Session launch — Termux-like (direct) vs proot fallback
    // -------------------------------------------------------------------------

    data class SessionLaunch(
        /** Path passed to exec (linker64 or bash). */
        val executable: String,
        /** argv for the process (argv[0] first). */
        val args: Array<String>,
        val cwd: String,
        val env: Array<String>,
        /** true = classic proot bind wrapper */
        val usedProot: Boolean,
    )

    /**
     * Build how to start a bootstrap shell.
     *
     * Always proot-bind: stock bootstrap ELF RUNPATH is com.termux, and Android
     * app linker namespaces ignore LD_LIBRARY_PATH — so "direct" linker64+bash
     * cannot resolve libandroid-support.so under our package id.
     *
     * proot is exec'd from [nativeLibraryDir] (apk lib path) so DT_NEEDED
     * (libtalloc / libandroid-shmem) resolve next to the binary.
     *
     * @param forceProot kept for API compat (always proot)
     */
    /**
     * @param execCommand if non-null, run `bash -lc <cmd>` instead of interactive shell
     *   (GUI launch: no prompt wait / no write delay).
     */
    fun buildSessionLaunch(
        context: Context,
        forceProot: Boolean = false,
        execCommand: String? = null,
    ): SessionLaunch {
        ensureProotDeps(context)
        // A NativeCode-built bootstrap has ELF paths for this package and can be
        // started directly. Stock downloaded bootstraps still need the fallback.
        if (!forceProot && isBundledBootstrap(context)) {
            Log.i(TAG, "SessionLaunch DIRECT (bundled bootstrap, no proot)")
            return buildDirectLaunch(context, execCommand = execCommand)
        }
        return buildProotLaunch(context, execCommand = execCommand)
    }

    /** True when the installed bootstrap was extracted from our bundled asset (correct paths). */
    fun isBundledBootstrap(context: Context): Boolean {
        val marker = File(prefixDir(context), ".direct-mode")
        return try {
            marker.isFile && marker.readText().lineSequence().any { it == "bundled=true" }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Direct linker64+bash — linker loads bash from app data dir, and
     * libtermux-exec.so (DT_NEEDED) intercepts child execve() calls.
     */
    fun buildDirectLaunch(context: Context, execCommand: String? = null): SessionLaunch {
        val prefix = realPrefixPath(context)
        val home = realHomePath(context)
        val bash = File(prefixDir(context), "bin/bash").absolutePath
        val linker = linkerPath()
        val nativeLib = context.applicationInfo.nativeLibraryDir
        val filesDir = context.filesDir.absolutePath
        val tmp = tmpDir(context).absolutePath

        val args = if (!execCommand.isNullOrBlank()) {
            arrayOf(linker, bash, "--login", "-c", execCommand)
        } else {
            arrayOf(linker, bash, "--login", "-i")
        }
        val env = arrayOf(
            "HOME=$home",
            "PREFIX=$prefix",
            "TMPDIR=$prefix/tmp",
            "TERM=xterm-256color",
            "LANG=en_US.UTF-8",
            "COLORTERM=truecolor",
            "PATH=$prefix/bin:$prefix/bin/applets:/system/bin:/system/xbin",
            "LD_LIBRARY_PATH=$prefix/lib:$filesDir:$nativeLib",
            "TERMUX_PREFIX=$prefix",
            "TERMUX_HOME=$home",
            "ANDROID_DATA=/data",
            "ANDROID_ROOT=/system",
            "EXTERNAL_STORAGE=/sdcard",
            "PROOT_TMP_DIR=$tmp",
            "PROOT_LOADER=$nativeLib/libproot_loader.so",
        )
        Log.i(TAG, "SessionLaunch DIRECT linker=$linker bash=$bash prefix=$prefix")
        return SessionLaunch(
            executable = linker,
            args = args,
            cwd = homeDir(context).absolutePath,
            env = env,
            usedProot = false,
        )
    }

    /**
     * proot bind-mount: maps our termux-prefix → stock com.termux paths so
     * guest ELF RUNPATH works. Executable must be the apk [nativeLibraryDir]
     * copy — filesDir copies fail DT_NEEDED under app linker rules.
     */
    fun buildProotLaunch(context: Context, execCommand: String? = null): SessionLaunch {
        val prefix = prefixDir(context)
        val home = homeDir(context)
        val nativeLib = context.applicationInfo.nativeLibraryDir
        // Prefer apk-native proot (deps resolve via $ORIGIN / lib dir namespace)
        val prootNative = File(nativeLib, "libproot.so")
        val proot = if (prootNative.canExecute() || prootNative.exists()) {
            prootNative.absolutePath
        } else {
            prootBinary(context).absolutePath
        }
        val loader = File(nativeLib, "libproot_loader.so").let {
            if (it.exists()) it.absolutePath else File(context.filesDir, "libproot_loader.so").absolutePath
        }
        val tmp = tmpDir(context)
        val cache = File(context.filesDir, "termux-cache").also {
            File(it, "apt/archives/partial").mkdirs()
            it.setReadable(true, false)
            it.setWritable(true, false)
            it.setExecutable(true, false)
        }
        val filesDir = context.filesDir.absolutePath
        val stockPrefix = STOCK_TERMUX_PREFIX

        // JNI: execvp(linker64, argv) — argv[0]=ELF path to load, argv[1]=argv0 for proot
        val base = mutableListOf(
            proot,
            proot,
            "--rootfs=/",
            "--bind=/dev", "--bind=/proc", "--bind=/sys",
            "--bind=/system",
            "--bind=${prefix.absolutePath}:$stockPrefix",
            "--bind=${home.absolutePath}:$STOCK_TERMUX_HOME",
            "--bind=${tmp.absolutePath}:$stockPrefix/tmp",
            "--bind=${cache.absolutePath}:/data/data/com.termux/cache",
            "-w", STOCK_TERMUX_HOME,
            "$stockPrefix/bin/bash",
        )
        // GUI / one-shot: bash -lc <cmd> — no interactive prompt + session.write delay
        if (!execCommand.isNullOrBlank()) {
            base.add("-lc")
            base.add(execCommand)
        }
        val args = base.toTypedArray()
        val env = arrayOf(
            "HOME=$STOCK_TERMUX_HOME",
            "PREFIX=$stockPrefix",
            "TMPDIR=$stockPrefix/tmp",
            "TERM=xterm-256color",
            "LANG=en_US.UTF-8",
            "COLORTERM=truecolor",
            "PATH=$stockPrefix/bin:$stockPrefix/bin/applets:/system/bin:/system/xbin",
            "PROOT_TMP_DIR=$filesDir/termux-tmp",
            "PROOT_LOADER=$loader",
            "PROOT_NO_SECCOMP=1",
            // Best-effort; modern Android often ignores this for app UIDs
            "LD_LIBRARY_PATH=$nativeLib:$filesDir:$stockPrefix/lib",
        )
        Log.i(TAG, "SessionLaunch PROOT proot=$proot loader=$loader")
        return SessionLaunch(
            executable = linkerPath(),
            args = args,
            cwd = home.absolutePath,
            env = env,
            usedProot = true,
        )
    }

    /** @deprecated use [buildSessionLaunch] */
    fun launcherPath(): String = linkerPath()

    /** @deprecated use [buildSessionLaunch] */
    fun buildProotArgs(context: Context): Array<String> =
        buildProotLaunch(context).args

    /** @deprecated use [buildSessionLaunch] */
    fun buildEnvironment(context: Context): Array<String> =
        buildProotLaunch(context).env

    /** Direct shell disabled — package path ≠ com.termux, LD_LIBRARY_PATH ignored. */
    private fun shouldUseDirectShell(context: Context): Boolean = false

    // -------------------------------------------------------------------------
    // Proot deps + keyring
    // -------------------------------------------------------------------------

    fun ensureProotDeps(context: Context) {
        val nativeLib = context.applicationInfo.nativeLibraryDir
        listOf(
            "libproot.so" to "libproot.so",
            "libproot_loader.so" to "libproot_loader.so",
            "libtalloc.so" to "libtalloc.so.2",
            "libmemfd_shim.so" to "libmemfd_shim.so",
        ).forEach { (src, dst) ->
            val srcFile = File(nativeLib, src)
            val dstFile = File(context.filesDir, dst)
            // Skip rewrite when same size — copyTo every session open was ~1–2s on storage
            if (srcFile.exists() &&
                (!dstFile.exists() || dstFile.length() != srcFile.length())
            ) {
                try {
                    srcFile.copyTo(dstFile, overwrite = true)
                    dstFile.setReadable(true, false)
                    dstFile.setExecutable(true, false)
                } catch (e: Exception) {
                    Log.w(TAG, "Copy $src failed", e)
                }
            }
        }
        val talloc2 = File(context.filesDir, "libtalloc.so.2")
        val tallocLink = File(context.filesDir, "libtalloc.so")
        if (talloc2.exists() && !tallocLink.exists()) {
            try {
                java.nio.file.Files.createSymbolicLink(
                    tallocLink.toPath(),
                    java.nio.file.Paths.get("libtalloc.so.2")
                )
            } catch (_: Exception) {}
        }
        ensureAptKeyring(context)
        // Ensure tmp under prefix for direct mode
        File(prefixDir(context), "tmp").mkdirs()
        // One-time / cheap re-run: relocate scripts to NativeCode paths for direct shell
        val marker = File(prefixDir(context), ".direct-mode")
        if (isInstalled(context) && !marker.exists()) {
            try {
                relocateBootstrapToNativeCode(context)
            } catch (e: Exception) {
                Log.w(TAG, "Relocate on ensure failed", e)
            }
        }
    }

    private fun ensureAptKeyring(context: Context) {
        val prefix = prefixDir(context)
        listOf("etc/apt/apt.conf.d", "etc/apt/trusted.gpg.d", "etc/apt/preferences.d", "var/log/apt").forEach {
            File(prefix, it).apply { mkdirs(); setReadable(true, false); setExecutable(true, false) }
        }
        val keyFile = File(prefix, "etc/apt/trusted.gpg.d/termux-keyring.gpg")
        if (keyFile.exists() && keyFile.length() > 0) return
        try {
            context.assets.open("termux-keyring.gpg").use { inp ->
                FileOutputStream(keyFile).use { inp.copyTo(it) }
            }
            keyFile.setReadable(true, false)
        } catch (e: Exception) {
            Log.w(TAG, "Keyring install failed", e)
        }
    }

    // -------------------------------------------------------------------------
    // Bootstrap download + extraction + path relocate
    // -------------------------------------------------------------------------

    sealed class BootstrapState {
        data object NotInstalled : BootstrapState()
        data class Downloading(val percent: Int) : BootstrapState()
        data class Extracting(val count: Int) : BootstrapState()
        data class Relocating(val message: String) : BootstrapState()
        data object Done : BootstrapState()
        data class Error(val message: String) : BootstrapState()
    }

    /**
     * Install bootstrap.
     *
     * **Fast path**: if `assets/bootstrap-<abi>.zip` is bundled (compiled for
     * com.ivarna.nativecode), extract it directly — no path relocation needed.
     *
     * **Fallback**: download from GitHub and run [relocateBootstrapToNativeCode].
     */
    suspend fun install(
        context: Context,
        onProgress: (BootstrapState) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val abi = primaryAbi()
            val assetName = "bootstrap-$abi.zip"
            val hasBundled = try {
                context.assets.open(assetName).use { true }
            } catch (_: Exception) { false }

            val prefix = prefixDir(context)
            prefix.deleteRecursively()
            prefix.mkdirs()

            if (hasBundled) {
                // Pre-compiled for com.ivarna.nativecode — extract directly.
                Log.i(TAG, "Using bundled bootstrap asset: $assetName")
                onProgress(BootstrapState.Extracting(0))
                context.assets.open(assetName).use { stream ->
                    extractStream(stream, prefix, onProgress)
                }
                onProgress(BootstrapState.Relocating("Relocating paths…"))
                relocateBootstrapToNativeCode(context)
                // Mark direct mode (paths already correct)
                val newPrefix = realPrefixPath(context)
                val newHome  = realHomePath(context)
                File(prefix, "tmp").apply { mkdirs(); setReadable(true,false); setWritable(true,false); setExecutable(true,false) }
                File(prefix, ".direct-mode").writeText("1\nprefix=$newPrefix\nhome=$newHome\nbundled=true\n")
                File(prefix, ".bootstrap-done").writeText("1")
            } else {
                // Fallback: download stock bootstrap + relocate paths.
                val url = "$BOOTSTRAP_BASE_URL/bootstrap-$abi.zip"
                Log.i(TAG, "Bundled asset not found — downloading: $url")
                onProgress(BootstrapState.Downloading(0))
                val zipFile = File(context.cacheDir, "bootstrap-$abi.zip")
                download(url, zipFile, onProgress)

                onProgress(BootstrapState.Extracting(0))
                extract(zipFile, prefix, onProgress)
                zipFile.delete()

                onProgress(BootstrapState.Relocating("Rewriting paths for NativeCode…"))
                relocateBootstrapToNativeCode(context)
            }

            ensureProotDeps(context)
            onProgress(BootstrapState.Done)
        } catch (e: Exception) {
            Log.e(TAG, "Bootstrap install failed", e)
            onProgress(BootstrapState.Error(e.message ?: "Unknown error"))
        }
    }

    /**
     * Rewrite stock `com.termux` paths to this app's real filesDir paths.
     * - Text/scripts: full replace (any length).
     * - ELF: same-length replace only (stock base path padded tricks not used;
     *   direct mode relies on linker64 + LD_LIBRARY_PATH + PREFIX env).
     *
     * Safe to re-run.
     */
    fun relocateBootstrapToNativeCode(context: Context) {
        val prefix = prefixDir(context)
        if (!prefix.isDirectory) return

        val newPrefix = realPrefixPath(context)
        val newHome = realHomePath(context)
        val newBase = realBasePath(context)

        val replacements = listOf(
            STOCK_TERMUX_PREFIX to newPrefix,
            STOCK_TERMUX_HOME to newHome,
            STOCK_TERMUX_BASE to newBase,
        )

        var textFiles = 0
        var binaryPatches = 0

        prefix.walkTopDown().forEach { f ->
            if (!f.isFile || f.length() == 0L) return@forEach
            if (f.name == ".bootstrap-done" || f.name == ".direct-mode") return@forEach
            // Skip huge caches
            if (f.path.contains("/var/cache/")) return@forEach

            val hasShebang = try {
                f.inputStream().use { inp ->
                    val b = ByteArray(2)
                    inp.read(b) == 2 && b[0] == '#'.toByte() && b[1] == '!'.toByte()
                }
            } catch (_: Exception) { false }

            val isProbablyText = hasShebang ||
                f.extension in TEXT_EXTS ||
                f.name in TEXT_NAMES ||
                f.path.contains("/etc/") ||
                f.path.contains("/share/") ||
                f.name.endsWith(".sh") ||
                f.name.endsWith(".bash")

            if (isProbablyText) {
                try {
                    val raw = f.readText(Charsets.UTF_8)
                    if (!raw.contains("com.termux")) return@forEach
                    var out = raw
                    for ((old, new) in replacements) {
                        out = out.replace(old, new)
                    }
                    if (out != raw) {
                        f.writeText(out, Charsets.UTF_8)
                        f.setReadable(true, false)
                        if (f.canExecute() || f.path.contains("/bin/")) f.setExecutable(true, false)
                        textFiles++
                    }
                } catch (_: Exception) {
                    // binary mis-detected as text — try binary patch
                    binaryPatches += binaryReplacePaths(f, replacements)
                }
            } else {
                binaryPatches += binaryReplacePaths(f, replacements)
            }
        }

        // Ensure tmp exists under prefix for direct TMPDIR
        File(prefix, "tmp").apply { mkdirs(); setReadable(true, false); setWritable(true, false); setExecutable(true, false) }

        File(prefix, ".direct-mode").writeText("1\nprefix=$newPrefix\nhome=$newHome\n")
        File(prefix, ".bootstrap-done").writeText("1")
        Log.i(TAG, "Relocated bootstrap: textFiles=$textFiles binaryPatches=$binaryPatches → $newPrefix")
    }

    /**
     * In-place byte replace only when [new] length == [old] length (ELF-safe).
     * Returns number of files modified.
     */
    private fun binaryReplacePaths(file: File, replacements: List<Pair<String, String>>): Int {
        // Only equal-length pairs can be applied in-place to ELF string tables.
        val equal = replacements.filter { it.first.length == it.second.length }
        if (equal.isEmpty()) return 0
        return try {
            val bytes = file.readBytes()
            var changed = false
            val buf = bytes
            for ((old, new) in equal) {
                val oldB = old.toByteArray(Charsets.US_ASCII)
                val newB = new.toByteArray(Charsets.US_ASCII)
                var i = 0
                while (i <= buf.size - oldB.size) {
                    var match = true
                    for (j in oldB.indices) {
                        if (buf[i + j] != oldB[j]) { match = false; break }
                    }
                    if (match) {
                        System.arraycopy(newB, 0, buf, i, newB.size)
                        changed = true
                        i += newB.size
                    } else i++
                }
            }
            if (changed) {
                file.writeBytes(buf)
                file.setReadable(true, false)
                1
            } else 0
        } catch (_: Exception) {
            0
        }
    }

    private val TEXT_EXTS = setOf(
        "sh", "bash", "zsh", "txt", "conf", "cfg", "list", "desktop", "service",
        "cmake", "pc", "la", "in", "ac", "am", "py", "pl", "rb", "lua", "js",
        "json", "xml", "yml", "yaml", "toml", "ini", "properties", "sub", "sed"
    )
    private val TEXT_NAMES = setOf(
        "Makefile", "PKGBUILD", "APKBUILD", "Control", "rules", "SYMLINKS.txt"
    )

    private fun primaryAbi(): String {
        val supported = Build.SUPPORTED_ABIS
        return when {
            supported.any { it == "arm64-v8a" } -> "aarch64"
            supported.any { it == "armeabi-v7a" } -> "arm"
            supported.any { it == "x86_64" } -> "x86_64"
            supported.any { it == "x86" } -> "i686"
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
        conn.readTimeout = 60_000
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
                        onProgress(BootstrapState.Downloading((downloaded * 100 / total).toInt()))
                    }
                }
            }
        }
    }

    /** Extract from an [InputStream] (e.g. assets) — no temp file needed. */
    private fun extractStream(
        inputStream: java.io.InputStream,
        prefix: File,
        onProgress: (BootstrapState) -> Unit
    ) = extractZipStream(ZipInputStream(inputStream.buffered()), prefix, onProgress)

    private fun extract(
        zipFile: File,
        prefix: File,
        onProgress: (BootstrapState) -> Unit
    ) = extractZipStream(ZipInputStream(zipFile.inputStream().buffered()), prefix, onProgress)

    private fun extractZipStream(
        zin: ZipInputStream,
        prefix: File,
        onProgress: (BootstrapState) -> Unit
    ) {
        val symlinkLines = mutableListOf<String>()
        var count = 0
        zin.use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                val name = entry.name.trimStart('/')
                when {
                    name == "SYMLINKS.txt" -> {
                        symlinkLines.addAll(
                            zin.bufferedReader().readText().lines().filter { it.contains("←") }
                        )
                    }
                    !entry.isDirectory && name.isNotEmpty() -> {
                        val dest = File(prefix, name)
                        dest.parentFile?.mkdirs()
                        dest.parentFile?.setExecutable(true, false)
                        dest.parentFile?.setReadable(true, false)
                        FileOutputStream(dest).use { out -> zin.copyTo(out) }
                        dest.setReadable(true, false)
                        if (name.endsWith(".so") ||
                            name.startsWith("bin/") ||
                            name.startsWith("lib/") ||
                            name.startsWith("libexec/")
                        ) {
                            dest.setExecutable(true, false)
                        }
                        count++
                        if (count % 50 == 0) onProgress(BootstrapState.Extracting(count))
                    }
                }
                try { zin.closeEntry() } catch (_: Exception) {}
                entry = zin.nextEntry
            }
        }

        var symlinksCreated = 0
        for (line in symlinkLines) {
            val parts = line.split("←")
            if (parts.size != 2) continue
            val target = parts[0].trim()
            val sourcePath = parts[1].trim().trimStart('.', '/')
            val sourceFile = File(prefix, sourcePath)
            val symlinkDir = sourceFile.parentFile ?: continue
            symlinkDir.mkdirs()
            if (sourceFile.exists()) continue
            try {
                java.nio.file.Files.createSymbolicLink(
                    sourceFile.toPath(),
                    java.nio.file.Paths.get(target)
                )
                symlinksCreated++
            } catch (_: Exception) {
                try {
                    Runtime.getRuntime().exec(
                        arrayOf("/system/bin/ln", "-sf", target, sourceFile.name),
                        null,
                        symlinkDir
                    ).waitFor()
                    symlinksCreated++
                } catch (_: Exception) {}
            }
        }
        Log.i(TAG, "Extracted $count files, $symlinksCreated symlinks")

        listOf("tmp", "var", "var/log/apt", "etc/apt/apt.conf.d", "etc/apt/trusted.gpg.d").forEach {
            File(prefix, it).apply { mkdirs(); setReadable(true, false); setExecutable(true, false) }
        }

        // Stock ELF RUNPATH helper for proot fallback (rootfs=/)
        val runpathDir = File(prefix, "data/data/com.termux/files")
        runpathDir.mkdirs()
        val runpathLink = File(runpathDir, "usr")
        if (!runpathLink.exists()) {
            try {
                java.nio.file.Files.createSymbolicLink(
                    runpathLink.toPath(),
                    java.nio.file.Paths.get("/")
                )
            } catch (_: Exception) {}
        }

        fixPermissions(prefix)
        File(prefix, ".bootstrap-done").writeText("1")
    }

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
