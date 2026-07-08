package com.ivarna.nativecode.core.runtime

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Paths

/**
 * Alpine Linux version constants — single source of truth.
 * Update these to pin a different Alpine release.
 */
private const val ALPINE_VERSION = "3.20.0"
private const val ALPINE_BRANCH = "v3.20"

/**
 * In-app Linux userspace runtime backed by an embedded proot binary.
 *
 * Supports Alpine (the original, bundled in `assets/rootfs/alpine-minirootfs.tar.gz`)
 * and Debian 13 (trixie, downloaded on first use by `setup_debian_proot.sh` to
 * `rootfs-debian/`). Each distro has its own rootfs dir, but they share the
 * same proot binary, loader, and libtalloc.
 */
enum class Distro(val rootfsDirName: String, val displayName: String) {
    Alpine("rootfs", "Alpine Linux $ALPINE_VERSION"),
    Debian("rootfs-debian", "Debian 13 (trixie)");

    val scriptAssetPath: String
        get() = "scripts/embedded/${setupScriptName}"

    val setupScriptName: String
        get() = when (this) {
            Alpine -> "setup_alpine_embedded.sh"
            Debian -> "setup_debian_proot.sh"
        }
}

class EmbeddedRuntime(
    private val context: Context,
    val distro: Distro = Distro.Alpine,
) {

    val rootfsDir: File
        get() = File(context.filesDir, distro.rootfsDirName)

    private val tmpDir: File
        get() = File(context.filesDir, "tmp")

    private val homeDir: File
        get() = File(context.filesDir, "home").also { it.mkdirs() }

    private val nativeLibDir: File
        get() = File(context.applicationInfo.nativeLibraryDir)

    private val tallocCopy: File
        get() = File(context.filesDir, "libtalloc.so.2")

    private val memfdShimCopy: File
        get() = File(context.filesDir, "libmemfd_shim.so")

    val prootPath: String
        get() = proot.absolutePath

    private val proot: File
        get() = File(nativeLibDir, "libproot.so")

    private val loader: File
        get() = File(nativeLibDir, "libproot-loader.so").takeIf { it.exists() }
            ?: File(nativeLibDir, "libproot_loader.so")

    fun isAvailable(): Boolean = proot.exists() && loader.exists()

    fun isRootfsReady(): Boolean =
        Files.exists(rootfsDir.resolve("bin/sh").toPath(), LinkOption.NOFOLLOW_LINKS) ||
        Files.exists(rootfsDir.resolve("bin/bash").toPath(), LinkOption.NOFOLLOW_LINKS) ||
        rootfsDir.resolve("usr/bin/sudo").exists()

    suspend fun ensureRootfs(): Result<File> = withContext(Dispatchers.IO) {
        ensureEmbeddedScripts()
        // Fast path: any of these markers means the rootfs is usable.
        if (isRootfsReady()) {
            ensureLibtalloc()
            ensureMemfdShim()
            return@withContext Result.success(rootfsDir)
        }

        runCatching {
            rootfsDir.mkdirs()
            tmpDir.mkdirs()
            homeDir.mkdirs()
            ensureLibtalloc()
            ensureMemfdShim()

            when (distro) {
                Distro.Alpine -> extractAlpineRootfs()
                Distro.Debian  -> throw IllegalStateException(
                    "Debian rootfs is not bundled — run 'sh /data/data/${context.packageName}/files/${Distro.Debian.setupScriptName}' " +
                    "from inside the Alpine proot first. The script downloads ~25 MB from " +
                    "debuerreotype and bootstraps the 'flux' user."
                )
            }

            makeWritable(rootfsDir)
            if (distro == Distro.Alpine) {
                writeResolvConf(rootfsDir)
                writeAlpineRepositories(rootfsDir)
            }

            if (!Files.exists(rootfsDir.resolve("bin/sh").toPath(), LinkOption.NOFOLLOW_LINKS) &&
                !rootfsDir.resolve("bin/busybox").exists() &&
                !rootfsDir.resolve("bin/bash").exists()
            ) {
                throw IllegalStateException(
                    "Rootfs extraction failed: no bin/sh, bin/bash, or bin/busybox in ${rootfsDir.absolutePath}"
                )
            }

            Log.i(TAG, "${distro.displayName} rootfs extracted to ${rootfsDir.absolutePath}")
            rootfsDir
        }
    }

    private fun extractAlpineRootfs() {
        val assets = context.assets.list("rootfs") ?: emptyArray()
        val useTarGz = "alpine-minirootfs.tar.gz" in assets
        val useTar   = "alpine-minirootfs.tar"    in assets

        Log.i(TAG, "Alpine assets: ${assets.toList()}, useTarGz=$useTarGz, useTar=$useTar")

        when {
            useTarGz -> context.assets.open("rootfs/alpine-minirootfs.tar.gz").use { s ->
                BufferedInputStream(s).use { bs ->
                    GzipCompressorInputStream(bs).use { gz ->
                        extractTar(TarArchiveInputStream(gz))
                    }
                }
            }
            useTar -> context.assets.open("rootfs/alpine-minirootfs.tar").use { s ->
                BufferedInputStream(s).use { bs ->
                    extractTar(TarArchiveInputStream(bs))
                }
            }
            else -> throw IllegalStateException(
                "Neither alpine-minirootfs.tar.gz nor alpine-minirootfs.tar found in assets/rootfs/."
            )
        }
    }

    private fun extractTar(tarStream: TarArchiveInputStream) {
        val hardlinks = mutableListOf<Pair<File, String>>()
        var entry = tarStream.nextTarEntry
        while (entry != null) {
            val outFile = File(rootfsDir, entry.name)
            if (!outFile.canonicalPath.startsWith(rootfsDir.canonicalPath)) {
                entry = tarStream.nextTarEntry; continue
            }
            when {
                entry.isDirectory -> outFile.mkdirs()
                entry.isSymbolicLink -> {
                    outFile.parentFile?.mkdirs()
                    try {
                        if (outFile.exists() || Files.isSymbolicLink(outFile.toPath())) outFile.delete()
                        Files.createSymbolicLink(outFile.toPath(), Paths.get(entry.linkName))
                    } catch (_: Exception) {}
                }
                entry.isLink -> hardlinks.add(outFile to entry.linkName)
                else -> {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { output -> tarStream.copyTo(output) }
                    val mode = entry.mode
                    if (mode and 0b001_001_001 != 0) outFile.setExecutable(true, false)
                }
            }
            entry = tarStream.nextTarEntry
        }
        for ((dest, srcPath) in hardlinks) {
            val src = File(rootfsDir, srcPath)
            dest.parentFile?.mkdirs()
            if (src.exists()) src.copyTo(dest, overwrite = true)
        }
    }

    private fun makeWritable(dir: File) {
        dir.walkTopDown().forEach { file ->
            if (file.isDirectory && !file.canWrite()) file.setWritable(true, true)
        }
    }

    private fun writeResolvConf(rootfs: File) {
        val etc = File(rootfs, "etc").also { it.mkdirs() }
        File(etc, "resolv.conf").writeText("nameserver 8.8.8.8\nnameserver 8.8.4.4\n")
    }

    private fun writeAlpineRepositories(rootfs: File) {
        val apkDir = File(rootfs, "etc/apk").also { it.mkdirs() }
        File(apkDir, "repositories").writeText(
            "https://dl-cdn.alpinelinux.org/alpine/$ALPINE_BRANCH/main\n" +
            "https://dl-cdn.alpinelinux.org/alpine/$ALPINE_BRANCH/community\n"
        )
    }

    private fun ensureLibtalloc() {
        if (tallocCopy.exists()) return
        val source = File(nativeLibDir, "libtalloc.so")
        if (source.exists()) {
            try { source.copyTo(tallocCopy, overwrite = true) }
            catch (e: Exception) { Log.w(TAG, "Could not copy libtalloc: ${e.message}") }
        }
    }

    private fun ensureMemfdShim() {
        val source = File(nativeLibDir, "libmemfd_shim.so")
        if (source.exists()) {
            try {
                source.copyTo(memfdShimCopy, overwrite = true)
                memfdShimCopy.setReadable(true, false)
                memfdShimCopy.setExecutable(true, false)
            }
            catch (e: Exception) { Log.w(TAG, "Could not copy libmemfd_shim: ${e.message}") }
        }
    }

    private val extraEmbeddedScripts = listOf(
        "start_gui_alpine.sh"
    )

    private fun ensureEmbeddedScripts() {
        for (distroVal in Distro.values()) {
            val scriptFile = File(context.filesDir, distroVal.setupScriptName)
            try {
                context.assets.open(distroVal.scriptAssetPath).use { input ->
                    scriptFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                scriptFile.setExecutable(true, false)
                Log.d(TAG, "Copied script ${distroVal.setupScriptName} to ${scriptFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy script ${distroVal.setupScriptName}: ${e.message}", e)
            }
        }
        for (scriptName in extraEmbeddedScripts) {
            val scriptFile = File(context.filesDir, scriptName)
            if (scriptFile.exists()) continue
            try {
                context.assets.open("scripts/embedded/$scriptName").use { input ->
                    scriptFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                scriptFile.setExecutable(true, false)
                Log.d(TAG, "Copied extra script $scriptName to ${scriptFile.absolutePath}")
            } catch (e: Exception) {
                Log.w(TAG, "Could not copy extra script $scriptName: ${e.message}")
            }
        }
    }

    fun exec(command: List<String>): Flow<String> = callbackFlow {
        withContext(Dispatchers.IO) {
            val cmd   = buildProotCommand(command).toTypedArray()
            val envp  = buildEnvArray()
            val workDir = rootfsDir.parentFile ?: context.filesDir
            @Suppress("DEPRECATION")
            val process = java.lang.Runtime.getRuntime().exec(cmd, envp, workDir)
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
            if (exitCode != 0) trySend("[NativeCode] process exited with code $exitCode")
        }
        close()
    }

    fun execShell(script: String): Flow<String> = exec(listOf("/bin/sh", "-c", script))
    fun loginShell(): Flow<String> = exec(listOf("/bin/sh", "-l"))

    /**
     * Start a persistent process. For Alpine, an interactive root shell.
     * For Debian, an interactive shell running as `flux` (uid 1000) via `su`,
     * so commands like `sudo` work without password prompts.
     */
    fun startInteractiveShell(): Process {
        val cmd = when (distro) {
            Distro.Alpine -> buildProotCommand(listOf("/bin/sh", "-l"))
            Distro.Debian  -> buildProotCommand(listOf("/bin/sh", "-c", "exec su - flux")
                .let { listOf("/bin/sh", "-c", "exec su - flux") })
        }
        val envp = buildEnvArray()
        val workDir = rootfsDir.parentFile ?: context.filesDir
        @Suppress("DEPRECATION")
        return java.lang.Runtime.getRuntime().exec(cmd.toTypedArray(), envp, workDir)
    }

    private fun buildProotCommand(guestCommand: List<String>): List<String> =
        mutableListOf<String>().apply {
            add(proot.absolutePath)
            add("--rootfs=${rootfsDir.absolutePath}")
            add("--bind=/dev"); add("--bind=/proc"); add("--bind=/sys")
            add("--bind=/sdcard/Android/data/com.ivarna.nativecode/files:/sdcard")
            add("--bind=${homeDir.absolutePath}:/root")
            if (distro == Distro.Debian) {
                add("--bind=${homeDir.absolutePath}:/home/flux")
            }
            add("--bind=${tmpDir.absolutePath}:/tmp")
            // Bind Termux:X11 socket so XFCE4 can connect to DISPLAY=:0
            val x11Socket = "/data/data/com.termux/files/usr/tmp/.X11-unix"
            val x11Target = "${rootfsDir.absolutePath}/tmp/.X11-unix"
            if (File(x11Socket).exists()) {
                add("--bind=$x11Socket:$x11Target")
            }
            val parentFile = context.filesDir.parentFile
            if (parentFile != null && parentFile.exists()) {
                add("--bind=${parentFile.absolutePath}:/data/data/${context.packageName}")
            }
            add("-0")
            add("-w"); add(if (distro == Distro.Debian) "/home/flux" else "/root")
            addAll(guestCommand)
        }

    private fun buildEnvArray(): Array<String> = arrayOf(
        "HOME=${if (distro == Distro.Debian) "/home/flux" else "/root"}",
        "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
        "TERM=xterm-256color",
        "LANG=C.UTF-8",
        "LD_LIBRARY_PATH=${context.filesDir.absolutePath}:${nativeLibDir.absolutePath}",
        "PROOT_TMP_DIR=${tmpDir.absolutePath}",
        "PROOT_LOADER=${loader.absolutePath}",
    )

    companion object {
        private const val TAG = "EmbeddedRuntime"
    }
}
