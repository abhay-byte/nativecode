package com.ivarna.nativecode.core.termux

import android.content.Context
import com.ivarna.nativecode.core.runtime.Distro
import com.ivarna.nativecode.core.runtime.EmbeddedRuntime
import com.termux.shared.shell.ShellEnvironmentClient
import java.io.File

class FluxShellEnvironmentClient(
    private val context: Context,
    private val distro: Distro = Distro.Alpine,
) : ShellEnvironmentClient {
    private val runtime = EmbeddedRuntime(context, distro)

    override fun getDefaultWorkingDirectoryPath(): String = "/root"

    override fun getDefaultBinPath(): String = "/system/bin"

    override fun buildEnvironment(
        currentPackageContext: Context,
        isFailSafe: Boolean,
        workingDirectory: String,
    ): Array<String> {
        val rootfs = runtime.rootfsDir.absolutePath
        val home = File(context.filesDir, "home").also { it.mkdirs() }.absolutePath
        val tmp = File(context.filesDir, "tmp").also { it.mkdirs() }.absolutePath
        return arrayOf(
            "HOME=/root",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM=xterm-256color",
            "LANG=C.UTF-8",
            "LD_LIBRARY_PATH=${context.filesDir.absolutePath}:${context.applicationInfo.nativeLibraryDir}",
            "PROOT_TMP_DIR=$tmp",
            "PROOT_LOADER=${context.applicationInfo.nativeLibraryDir}/libproot_loader.so",
        )
    }

    override fun setupProcessArgs(fileToExecute: String, arguments: Array<out String>?): Array<String> {
        val proot = runtime.prootPath
        val rootfs = runtime.rootfsDir.absolutePath
        val home = File(context.filesDir, "home").also { it.mkdirs() }.absolutePath
        val tmp = File(context.filesDir, "tmp").also { it.mkdirs() }.absolutePath
        val parent = context.filesDir.parentFile?.absolutePath ?: "/data/data/${context.packageName}"

        val args = mutableListOf(
            "--rootfs=$rootfs",
            "--bind=/dev", "--bind=/proc", "--bind=/sys",
            "--bind=/sdcard/Android/data/${context.packageName}/files:/sdcard",
            "--bind=$home:/root",
            "--bind=$tmp:/tmp",
            "-0", "-w", "/root",
            fileToExecute,
        )
        if (!arguments.isNullOrEmpty()) args.addAll(arguments)

        val x11Socket = "/data/data/com.termux/files/usr/tmp/.X11-unix"
        if (File(x11Socket).exists()) args.add("--bind=$x11Socket:$rootfs/tmp/.X11-unix")
        args.add("--bind=$parent:/data/data/${context.packageName}")

        val result = arrayOfNulls<String>(args.size + 1)
        result[0] = proot
        System.arraycopy(args.toTypedArray(), 0, result, 1, args.size)
        @Suppress("UNCHECKED_CAST")
        return result as Array<String>
    }
}
