package com.ivarna.nativecode.core.runtime

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Paths

/**
 * In-app Linux userspace runtime backed by an embedded proot binary.
 *
 * Native binaries (proot, loader, libtalloc, libandroid-shmem) are shipped as
 * jniLibs/<abi>/lib*.so so Android extracts them into the executable native-lib
 * directory. The rootfs is a compressed tar archive in assets/ that is extracted
 * into the app's filesDir on first use.
 *
 * Implementation closely follows Kai (https://github.com/SimonSchubert/Kai) which
 * has a proven working Alpine + proot setup on Android:
 *  - Symlinks created with java.nio.file.Files.createSymbolicLink() (not ln -sf)
 *  - makeWritable() pass after extraction so apk can write package metadata
 *  - Env vars passed via Runtime.exec(envp) array matching Kai's ProotExecutor
 *  - No PROOT_NO_SECCOMP / PROOT_ASSUME_MEMFD_UNSUPPORTED / -k flags (not needed)
 */
class EmbeddedRuntime(private val context: Context) {

    private val rootfsDir: File
        get() = File(context.filesDir, "rootfs")

    private val tmpDir: File
        get() = File(context.filesDir, "tmp")

    private val homeDir: File
        get() = File(context.filesDir, "home").also { it.mkdirs() }

    private val nativeLibDir: File
        get() = File(context.applicationInfo.nativeLibraryDir)

    // libtalloc must be accessible as "libtalloc.so.2" (soname) for proot to dlopen it.
    // We place a copy next to the rootfs so it doesn't pollute nativeLibraryDir.
    private val tallocCopy: File
        get() = File(context.filesDir, "libtalloc.so.2")

    private val proot: File
        get() = File(nativeLibDir, "libproot.so")

    // Kai names the loader "libproot-loader.so" — our APK ships it as "libproot_loader.so".
    // Support both names so we work regardless of how the jniLib was built.
    private val loader: File
        get() = File(nativeLibDir, "libproot-loader.so").takeIf { it.exists() }
            ?: File(nativeLibDir, "libproot_loader.so")

    /**
     * Returns true when all native runtime pieces are present in the APK.
     */
    fun isAvailable(): Boolean = proot.exists() && loader.exists()

    /**
     * Ensure the rootfs has been extracted from assets. Returns the rootfs
     * directory on success.
     */
    suspend fun ensureRootfs(): Result<File> = withContext(Dispatchers.IO) {
        // bin/sh in Alpine is a symlink → /bin/busybox (absolute path).
        // File.exists() follows the symlink and fails on the Android host.
        // Use NOFOLLOW_LINKS to check the symlink inode itself.
        if (Files.exists(rootfsDir.resolve("bin/sh").toPath(), LinkOption.NOFOLLOW_LINKS)) {
            ensureLibtalloc()
            return@withContext Result.success(rootfsDir)
        }

        runCatching {
            rootfsDir.mkdirs()
            tmpDir.mkdirs()
            homeDir.mkdirs()
            ensureLibtalloc()

            // AAPT should keep .tar.gz intact thanks to noCompress = ["gz"] in
            // build.gradle.kts, but older AGP versions still decompress .gz assets
            // and store only the raw .tar bytes. Detect which form is present and
            // open accordingly so the code is robust to both packaging behaviours.
            val availableAssets = context.assets.list("rootfs") ?: emptyArray()
            val useTarGz = "alpine-minirootfs.tar.gz" in availableAssets
            val useTar   = "alpine-minirootfs.tar"    in availableAssets

            Log.i(TAG, "Assets in rootfs/: ${availableAssets.toList()}, " +
                       "useTarGz=$useTarGz, useTar=$useTar")

            when {
                useTarGz -> {
                    // BufferedInputStream is required because GzipCompressorInputStream
                    // (Apache Commons Compress 1.21+) calls mark/reset on the stream,
                    // and AssetManager streams do not support mark/reset by default.
                    context.assets.open("rootfs/alpine-minirootfs.tar.gz").use { assetStream ->
                        BufferedInputStream(assetStream).use { bufferedStream ->
                            GzipCompressorInputStream(bufferedStream).use { gzipStream ->
                                extractTar(TarArchiveInputStream(gzipStream))
                            }
                        }
                    }
                }
                useTar -> {
                    // AAPT stripped the gzip wrapper — open the raw tar directly.
                    context.assets.open("rootfs/alpine-minirootfs.tar").use { assetStream ->
                        BufferedInputStream(assetStream).use { bufferedStream ->
                            extractTar(TarArchiveInputStream(bufferedStream))
                        }
                    }
                }
                else -> throw IllegalStateException(
                    "Neither alpine-minirootfs.tar.gz nor alpine-minirootfs.tar " +
                    "found in assets/rootfs/. Available: ${availableAssets.toList()}"
                )
            }

            // Make all directories writable so apk can create its lock files and
            // write package metadata (mirrors Kai's makeWritable() pass).
            makeWritable(rootfsDir)

            // Write DNS config so network works inside proot.
            writeResolvConf(rootfsDir)

            // Write Alpine package repos.
            writeRepositories(rootfsDir)

            // Same NOFOLLOW_LINKS check — bin/sh is a dangling symlink from
            // the host perspective (target /bin/busybox resolves only inside proot).
            if (!Files.exists(rootfsDir.resolve("bin/sh").toPath(), LinkOption.NOFOLLOW_LINKS)) {
                // Fallback: busybox itself is a real file — check that too.
                if (!rootfsDir.resolve("bin/busybox").exists()) {
                    throw IllegalStateException(
                        "Rootfs extraction failed: neither bin/sh nor bin/busybox found. " +
                        "rootfsDir=${rootfsDir.absolutePath}"
                    )
                }
            }

            Log.i(TAG, "Rootfs extracted to ${rootfsDir.absolutePath}")
            rootfsDir
        }
    }

    /**
     * Extract all entries from [tarStream] into [rootfsDir].
     * Handles directories, symlinks, hardlinks, and regular files.
     * Uses java.nio.file.Files.createSymbolicLink() for symlinks — this is the
     * proven approach from Kai and is far more reliable than shelling out to ln.
     */
    private fun extractTar(tarStream: TarArchiveInputStream) {
        val hardlinks = mutableListOf<Pair<File, String>>()
        var entry = tarStream.nextTarEntry
        while (entry != null) {
            val outFile = File(rootfsDir, entry.name)

            // Path traversal guard
            if (!outFile.canonicalPath.startsWith(rootfsDir.canonicalPath)) {
                entry = tarStream.nextTarEntry
                continue
            }

            when {
                entry.isDirectory -> {
                    outFile.mkdirs()
                }
                entry.isSymbolicLink -> {
                    outFile.parentFile?.mkdirs()
                    try {
                        if (outFile.exists() || Files.isSymbolicLink(outFile.toPath())) {
                            outFile.delete()
                        }
                        // Use NIO symlink creation — this is what Kai does and it's reliable.
                        Files.createSymbolicLink(
                            outFile.toPath(),
                            Paths.get(entry.linkName),
                        )
                    } catch (_: Exception) {
                        // On some ROMs NIO symlinks may fail — skip silently.
                    }
                }
                entry.isLink -> {
                    // Defer hardlinks until all regular files are written.
                    hardlinks.add(outFile to entry.linkName)
                }
                else -> {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { output ->
                        tarStream.copyTo(output)
                    }
                    // Set executable bit if any execute bit is set in the tar mode.
                    val mode = entry.mode
                    if (mode and 0b001_001_001 != 0) {
                        outFile.setExecutable(true, false)
                    }
                }
            }
            entry = tarStream.nextTarEntry
        }
        // Resolve hardlinks after all regular files exist.
        for ((dest, srcPath) in hardlinks) {
            val src = File(rootfsDir, srcPath)
            dest.parentFile?.mkdirs()
            if (src.exists()) {
                src.copyTo(dest, overwrite = true)
            }
        }
    }

    /** Make all directories writable so apk can write package metadata. */
    private fun makeWritable(dir: File) {
        dir.walkTopDown().forEach { file ->
            if (file.isDirectory && !file.canWrite()) {
                file.setWritable(true, true)
            }
        }
    }

    /** Write /etc/resolv.conf so DNS works inside proot. */
    private fun writeResolvConf(rootfs: File) {
        val etc = File(rootfs, "etc").also { it.mkdirs() }
        File(etc, "resolv.conf").writeText("nameserver 8.8.8.8\nnameserver 8.8.4.4\n")
    }

    /** Write /etc/apk/repositories pointing to Alpine 3.21 mirrors. */
    private fun writeRepositories(rootfs: File) {
        val apkDir = File(rootfs, "etc/apk").also { it.mkdirs() }
        File(apkDir, "repositories").writeText(
            "https://dl-cdn.alpinelinux.org/alpine/v3.20/main\n" +
            "https://dl-cdn.alpinelinux.org/alpine/v3.20/community\n"
        )
    }

    /**
     * Copy libtalloc.so → libtalloc.so.2 in filesDir so proot can dlopen it
     * by soname. This mirrors Kai's copyLibtalloc() approach.
     */
    private fun ensureLibtalloc() {
        if (tallocCopy.exists()) return
        val source = File(nativeLibDir, "libtalloc.so")
        if (source.exists()) {
            try {
                source.copyTo(tallocCopy, overwrite = true)
            } catch (e: Exception) {
                Log.w(TAG, "Could not copy libtalloc: ${e.message}")
            }
        }
    }

    /**
     * Run [command] inside the embedded rootfs, streaming stdout/stderr lines.
     * Uses Runtime.exec(cmd, envp, dir) to pass a clean environment array,
     * exactly as Kai's ProotExecutor does — avoids inheriting host env vars.
     */
    fun exec(command: List<String>): Flow<String> = callbackFlow {
        withContext(Dispatchers.IO) {
            val cmd   = buildProotCommand(command).toTypedArray()
            val envp  = buildEnvArray()
            val workDir = rootfsDir.parentFile ?: context.filesDir

            @Suppress("DEPRECATION")
            val process = java.lang.Runtime.getRuntime().exec(cmd, envp, workDir)

            // Drain stderr on a background thread so it doesn't block stdout.
            val stderrThread = Thread {
                process.errorStream.bufferedReader().useLines { lines ->
                    lines.forEach { trySend(it) }
                }
            }.also { it.isDaemon = true; it.start() }

            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { trySend(it) }
            }
            stderrThread.join(2000)

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                trySend("[NativeCode] process exited with code $exitCode")
            }
        }
        close()
    }

    /** Run a shell command string inside the rootfs. */
    fun execShell(script: String): Flow<String> = exec(listOf("/bin/sh", "-c", script))

    /** Run a login shell inside the rootfs. */
    fun loginShell(): Flow<String> = exec(listOf("/bin/sh", "-l"))

    /**
     * Start a persistent process for interactive terminal use.
     */
    fun startInteractiveShell(): Process {
        val cmd = buildProotCommand(listOf("/bin/sh", "-l")).toTypedArray()
        val envp = buildEnvArray()
        val workDir = rootfsDir.parentFile ?: context.filesDir
        @Suppress("DEPRECATION")
        return java.lang.Runtime.getRuntime().exec(cmd, envp, workDir)
    }

    /**
     * Build the proot command args — matches Kai's buildProcessArgs() exactly.
     */
    private fun buildProotCommand(guestCommand: List<String>): List<String> {
        return mutableListOf<String>().apply {
            add(proot.absolutePath)
            add("--rootfs=${rootfsDir.absolutePath}")
            add("--bind=/dev")
            add("--bind=/proc")
            add("--bind=/sys")
            add("--bind=${homeDir.absolutePath}:/root")
            add("--bind=${tmpDir.absolutePath}:/tmp")
            add("-0")           // fake root (UID/GID 0)
            add("-w"); add("/root")
            addAll(guestCommand)
        }
    }

    /**
     * Build the environment variable array for proot — matches Kai's buildEnvVars().
     * Passing a complete envp array to Runtime.exec prevents host env vars from
     * leaking into the guest (e.g. LD_PRELOAD from Termux).
     */
    private fun buildEnvArray(): Array<String> = arrayOf(
        "HOME=/root",
        "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
        "TERM=xterm-256color",
        "LANG=C.UTF-8",
        // libtalloc.so.2 lives in filesDir; nativeLibDir has the rest.
        "LD_LIBRARY_PATH=${context.filesDir.absolutePath}:${nativeLibDir.absolutePath}",
        "PROOT_TMP_DIR=${tmpDir.absolutePath}",
        "PROOT_LOADER=${loader.absolutePath}",
    )

    companion object {
        private const val TAG = "EmbeddedRuntime"
    }
}
