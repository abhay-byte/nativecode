package com.ivarna.nativecode.core.data

import android.content.Intent
import android.content.Context
import com.ivarna.nativecode.core.data.Distro
import com.ivarna.nativecode.core.data.ScriptManager

object TermuxIntentFactory {

    private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
    private const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    private const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"

    private const val TERMUX_BASH_PATH = "/data/data/com.termux/files/usr/bin/bash"
    private const val TERMUX_HOME_DIR = "/data/data/com.termux/files/home"

    /**
     * Creates an intent to execute a bash script string in Termux.
     */
    fun buildRunCommandIntent(
        scriptContent: String,
        runInBackground: Boolean = false
    ): Intent {
        return Intent(ACTION_RUN_COMMAND).apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra(EXTRA_COMMAND_PATH, TERMUX_BASH_PATH)
            putExtra(EXTRA_ARGUMENTS, arrayOf("-c", scriptContent))
            putExtra(EXTRA_WORKDIR, TERMUX_HOME_DIR)
            putExtra(EXTRA_BACKGROUND, runInBackground)
            // 0 = ACTION_FAIL_ON_SESSION_EXIT (keep session open if it fails?)
            // let's default to just running.
        }
    }

    /**
     * A simple "Ping" command to check if connection works.
     */
    fun buildTestConnectionIntent(): Intent {
        return buildRunCommandIntent("echo 'NativeCode: Connection Established!' && sleep 2")
    }

    /**
     * Generates the install command string for manual execution.
     */
    fun getInstallCommand(distroId: String, setupScript: String? = null, installScriptContent: String, guiScriptContent: String): String {
        // Enforce newline termination for safety
        val safeInstallScript = if (!installScriptContent.endsWith("\n")) "$installScriptContent\n" else installScriptContent
        val safeGuiScript = if (!guiScriptContent.endsWith("\n")) "$guiScriptContent\n" else guiScriptContent
        
        val installScriptB64 = android.util.Base64.encodeToString(safeInstallScript.toByteArray(), android.util.Base64.NO_WRAP)
        val guiScriptB64 = android.util.Base64.encodeToString(safeGuiScript.toByteArray(), android.util.Base64.NO_WRAP)
        
        val setupB64 = if (!setupScript.isNullOrEmpty()) {
            android.util.Base64.encodeToString(setupScript.toByteArray(), android.util.Base64.NO_WRAP)
        } else {
            "null"
        }
        
        // Use Base64 decoding to write files. This avoids fragile 'cat << EOF' constructs in terminals
        // and handles special characters safely.
        return """
            echo "$installScriptB64" | base64 -d > ${'$'}HOME/flux_install.sh
            chmod +x ${'$'}HOME/flux_install.sh
            
            echo "$guiScriptB64" | base64 -d > ${'$'}HOME/start_gui.sh
            chmod +x ${'$'}HOME/start_gui.sh
            
            bash ${'$'}HOME/flux_install.sh $distroId "$setupB64"
        """.trimIndent()
    }

    /**
     * Just opens Termux (launcher intent).
     */
    fun buildOpenTermuxIntent(context: android.content.Context): Intent? {
        return context.packageManager.getLaunchIntentForPackage("com.termux")
    }

    /**
     * Installs a specific distro... (Deprecated: User Manual Fallback Preferred)
     */
    fun buildInstallIntent(distroId: String, setupScript: String? = null): Intent {
        // Use the native helper script we created in setup_termux.sh
        // Usage: bash ~/flux_install.sh <distro> <base64_setup>
        
        val setupB64 = if (!setupScript.isNullOrEmpty()) {
            android.util.Base64.encodeToString(setupScript.toByteArray(), android.util.Base64.NO_WRAP)
        } else {
            "null"
        }
        
        val command = "bash $TERMUX_HOME_DIR/flux_install.sh $distroId \"$setupB64\""
        return buildRunCommandIntent(command)
    }

    /**
     * Uninstalls/Removes a specific distro.
     */
    fun buildUninstallIntent(distroId: String): Intent {
        val callbackUrl = "nativecode://callback?result=success&name=distro_uninstall_$distroId"
        
        val command = when {
            distroId == "termux" -> {
                "pkg uninstall -y xfce4 xfce4-terminal tigervnc && echo 'NativeCode: Termux Native Desktop Removed.' && sleep 1 && am start -a android.intent.action.VIEW -d \"$callbackUrl\""
            }
            distroId == "debian13_chroot" -> {
                // Chroot: Inline uninstall logic (unmount, remove, callback)
                // This avoids dependency on pre-deployed script files
                """
                su -c '
                DEBIANPATH="/data/local/tmp/chrootDebian13"
                echo "Unmounting filesystems..."
                for mnt in $(grep "${'$'}DEBIANPATH" /proc/mounts | awk "{print \${'$'}2}" | sort -r); do
                    umount -l "${'$'}mnt" 2>/dev/null
                done
                echo "Removing chroot directory..."
                rm -rf "${'$'}DEBIANPATH"
                rm -f /data/local/tmp/start_debian13*.sh /data/local/tmp/enter_debian13.sh /data/local/tmp/run_debian13_root.sh /data/local/tmp/stop_debian13*.sh /data/local/tmp/uninstall_debian13.sh
                echo "Chroot removed successfully!"
                am start -a android.intent.action.VIEW -d "$callbackUrl"
                '
                """.trimIndent()
            }
            distroId == "debian_chroot" -> {
                // Chroot: Inline uninstall logic
                """
                su -c '
                DEBIANPATH="/data/local/tmp/chrootDebian"
                echo "Unmounting filesystems..."
                for mnt in $(grep "${'$'}DEBIANPATH" /proc/mounts | awk "{print \${'$'}2}" | sort -r); do
                    umount -l "${'$'}mnt" 2>/dev/null
                done
                echo "Removing chroot directory..."
                rm -rf "${'$'}DEBIANPATH"
                rm -f /data/local/tmp/start_debian*.sh /data/local/tmp/enter_debian.sh /data/local/tmp/stop_debian*.sh /data/local/tmp/uninstall_debian*.sh
                echo "Chroot removed successfully!"
                am start -a android.intent.action.VIEW -d "$callbackUrl"
                '
                """.trimIndent()
            }
            distroId.contains("chroot") -> {
                // Generic chroot fallback
                "su -c \"rm -rf /data/local/tmp/chroot*\" && echo 'Chroot removed.' && am start -a android.intent.action.VIEW -d \"$callbackUrl\""
            }
            else -> {
                // PRoot: Try proot-distro remove, retry once if it fails, then fallback to manual removal
                """
                echo "Attempting to remove $distroId..."
                if proot-distro remove $distroId 2>/dev/null; then
                    echo "NativeCode: $distroId Uninstalled."
                else
                    echo "First attempt failed, retrying..."
                    sleep 1
                    if proot-distro remove $distroId 2>/dev/null; then
                        echo "NativeCode: $distroId Uninstalled."
                    else
                        echo "proot-distro command failed, using manual removal..."
                        rm -rf ${'$'}PREFIX/var/lib/proot-distro/installed-rootfs/$distroId
                        echo "NativeCode: $distroId manually removed."
                    fi
                fi
                sleep 2
                am start -a android.intent.action.VIEW -d "$callbackUrl"
                """.trimIndent()
            }
        }
        return buildRunCommandIntent(command)
    }

    /**
     * EXTENDED INSTALL: Generates a compound script to install base + components
     */
    /**
     * Generates a raw bash script string for installing the base distro.
     * This is intended to be copied to the clipboard.
     */
    fun getBaseInstallScript(context: Context, distro: Distro): String {
        val scriptManager = ScriptManager(context)
        
        // 1. Select Base Script
        val baseScriptName = when (distro.id) {
            "debian13_chroot" -> "chroot/setup_debian13_chroot.sh"
            "debian_chroot" -> "chroot/setup_debian_chroot.sh"
            "termux" -> "common/setup_termux.sh"
            "archlinux" -> "common/setup_arch_family.sh"
            else -> "common/setup_debian_family.sh"
        }
        
        var fullScript = ""
        
        // --- STEP LOGGING HELPER ---
        fullScript += """
            CURRENT_STEP=1
            log_step() {
                echo -e "\n\033[1;36m[STEP ${'$'}{CURRENT_STEP}] ${'$'}1\033[0m"
                ((CURRENT_STEP++))
            }
        """.trimIndent() + "\n\n"
        
        // 0. Prepend Termux Setup (Dependency Check) if not running it directly AND not Chroot
        if (distro.id != "termux" && !distro.id.contains("chroot")) {
             val termuxSetup = scriptManager.getScriptContent("common/setup_termux.sh")
             // Strip the shebang and exit/callback from setup_termux
             var cleanSetup = termuxSetup.replace("#!/bin/bash", "")
             cleanSetup = cleanSetup.replace("exit 0", "# exit 0 deferred from setup_termux")
             cleanSetup = cleanSetup.replace("am start -a android.intent.action.VIEW", "# Deferred callback from setup_termux")
             
             // Remove the "Skipping" check to ensure dependencies are verified
             cleanSetup = cleanSetup.replace(Regex("if \\[ -f \"\\\$MARKER_FILE\" ]; then[\\s\\S]*?fi"), "# Marker check removed for full install")

             fullScript += "# --- NATIVECODE TERMUX SETUP (Dependencies) ---\n"
             fullScript += "log_step \"Installing Termux Dependencies (Proot, X11)...\"\n"
             fullScript += cleanSetup
             fullScript += "\n\n# --- DISTRO INSTALLATION ---\n"
        }
        
        fullScript += "log_step \"Installing Base System (${distro.name})...\"\n"
        
        // --- BASE INSTALL LOGIC SPLIT ---
        val isChroot = distro.id.contains("chroot")
        val isTermux = distro.id == "termux"
        
        if (isTermux) {
             // Termux: Dependencies were installed in Step 1 (setup_termux.sh)
             // We might just echo success or run a specific termux desktop setup found in setup_debian_family?
             // Actually setup_debian_family is for Debian. Termux needs its own.
             // Currently setup_termux does most work. We can just append baseScriptName if it's not setup_termux (which it is).
             if (baseScriptName != "common/setup_termux.sh") {
                 fullScript += scriptManager.getScriptContent(baseScriptName)
             } else {
                 fullScript += "echo 'Termux Environment Ready.'\n"
             }
        } else if (isChroot) {
             // Chroot: Script is self-contained (runs on host). Run it directly.
             // Chroot scripts handle their own internal steps.
             fullScript += scriptManager.getScriptContent(baseScriptName)
        } else {
             // Proot: Needs 'proot-distro install' FIRST, then wrap the config script.
             
             // 1. Install Distro Image
             fullScript += "log_step \"Downloading & Installing Proot Image...\"\n"
             fullScript += "proot-distro install ${distro.id} || echo 'Distro already installed or warning'\n"
             
             // 2. Wrap Configuration Script
             fullScript += "log_step \"Configuring Distro Environment...\"\n"
             val baseConfig = scriptManager.getScriptContent(baseScriptName)
             val baseConfigB64 = android.util.Base64.encodeToString(baseConfig.toByteArray(), android.util.Base64.NO_WRAP)
             
             // Use echo with base64 - avoids all heredoc/trimIndent issues
             fullScript += "echo '$baseConfigB64' | base64 -d > \$HOME/flux_base_setup.sh\n"
             fullScript += "chmod +x \$HOME/flux_base_setup.sh\n"
             fullScript += "\n# Run inside Proot (with fixed PATH)\n"
             fullScript += "proot-distro login ${distro.id} --shared-tmp -- bash -c \"export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin && bash /data/data/com.termux/files/home/flux_base_setup.sh\"\n"
             fullScript += "rm -f \$HOME/flux_base_setup.sh\n"
              
             // Deploy start_gui.sh separately using echo
             val guiScriptB64 = android.util.Base64.encodeToString(scriptManager.getScriptContent("common/start_gui.sh").toByteArray(), android.util.Base64.NO_WRAP)
             fullScript += "\n# Deploy Start GUI Script\nlog_step \"Updating Launch Scripts...\"\n"
             fullScript += "echo '$guiScriptB64' | base64 -d > \$HOME/start_gui.sh\n"
             fullScript += "chmod +x \$HOME/start_gui.sh\n"
             
             // Deploy stop_gui.sh as well
             val stopGuiScriptB64 = android.util.Base64.encodeToString(scriptManager.getScriptContent("common/stop_gui.sh").toByteArray(), android.util.Base64.NO_WRAP)
             fullScript += "echo '$stopGuiScriptB64' | base64 -d > \$HOME/stop_gui.sh\n"
             fullScript += "chmod +x \$HOME/stop_gui.sh\n"

             // Deploy start_gui_kde.sh (KDE Plasma launcher)
             val kdeGuiScriptB64 = android.util.Base64.encodeToString(scriptManager.getScriptContent("common/start_gui_kde.sh").toByteArray(), android.util.Base64.NO_WRAP)
             fullScript += "echo '$kdeGuiScriptB64' | base64 -d > \$HOME/start_gui_kde.sh\n"
             fullScript += "chmod +x \$HOME/start_gui_kde.sh\n"
        }
        
        // 2. Modify Base Script to defer exit/callback if present (mostly for chroot scripts that have it)
        fullScript = fullScript.replace("exit 0", "# exit 0 deferred")
        // Remove the old AM callback
        fullScript = fullScript.replace("am start -a android.intent.action.VIEW -d \"nativecode://callback?result=success", "# Deferred callback")
        
        // 5. Wrap the entire script in a self-extracting runner
        // GZIP COMPRESSION OPTIMIZATION to reduce Clipboard size.
        val safeScript = if (!fullScript.endsWith("\n")) "$fullScript\n" else fullScript
        
        val byteArrayOutputStream = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(byteArrayOutputStream).use { it.write(safeScript.toByteArray()) }
        val fullScriptGzipB64 = android.util.Base64.encodeToString(byteArrayOutputStream.toByteArray(), android.util.Base64.NO_WRAP)
        
        // Generate a random ID for the EOF marker to avoid collisions
        val eofMarker = "EOF_FLUX_INSTALL_${System.currentTimeMillis()}"
        val targetPath = if (isChroot) "/data/local/tmp/flux_full_install.sh" else "$TERMUX_HOME_DIR/flux_full_install.sh"
        val runnerCmd = if (isChroot) "sh" else "bash"
        
        // Command to decode: echo "..." | base64 -d | gunzip > script.sh
        
        val runnerScript = StringBuilder()
        
        if (isChroot) {
            runnerScript.append("su -c '\n")
        }
        
        runnerScript.append("cat << '$eofMarker' > $targetPath.b64\n")
        runnerScript.append(fullScriptGzipB64)
        runnerScript.append("\n$eofMarker\n")
        
        // Decode GZIP
        runnerScript.append("base64 -d $targetPath.b64 | gunzip > $targetPath\n")
        runnerScript.append("rm $targetPath.b64\n")
        runnerScript.append("chmod +x $targetPath\n")
        runnerScript.append("$runnerCmd $targetPath\n")
        
        // --- ADD CALLBACK ---
        // --- ADD CALLBACK ---
        val callbackName = "base_install"
        val callbackUrl = "nativecode://callback?result=success&name=$callbackName"
        val errorUrl = "nativecode://callback?result=failure&name=$callbackName"
        
        runnerScript.append("if [ $? -eq 0 ]; then\n")
        runnerScript.append("    am start -a android.intent.action.VIEW -d \"$callbackUrl\"\n")
        runnerScript.append("else\n")
        runnerScript.append("    echo \"NativeCode: Installation Failed!\"\n")
        runnerScript.append("    am start -a android.intent.action.VIEW -d \"$errorUrl\"\n")
        runnerScript.append("fi\n")
        
        if (isChroot) {
            runnerScript.append("'")
        }
        
        return runnerScript.toString()
    }

    /**
     * EXTENDED INSTALL: Generates a compound script to install base + components
     * DEPRECATED: Use getCompoundInstallScript and Manual Flow.
     */
    // Deprecated buildCompoundInstallIntent removed.

    /**
     * Launches a specific distro in CLI mode (login as flux user).
     */
    fun buildLaunchCliIntent(distroId: String): Intent {
        if (distroId == "termux") {
             return buildRunCommandIntent("echo 'You are already in Termux Native environment!' && sleep 2")
        }
        
        if (distroId == "debian_chroot") {
            // Launch Chroot CLI using Android Root (su)
            return buildRunCommandIntent("su -c \"sh /data/local/tmp/enter_debian.sh\"", runInBackground = false)
        }

        if (distroId == "debian13_chroot") {
            // Launch Debian 13 Chroot CLI using Android Root (su)
            return buildRunCommandIntent("su -c \"sh /data/local/tmp/enter_debian13.sh\"", runInBackground = false)
        }
        
        if (distroId == "arch_chroot") {
            // Launch Arch Chroot CLI (via generated script)
            return buildRunCommandIntent("su -c \"sh /data/local/tmp/enter_arch.sh\"", runInBackground = false)
        }
        
        // Default to 'flux' user if setup, fallback to root if not (proot-distro handles login)
        val command = "proot-distro login $distroId --user flux"
        return buildRunCommandIntent(command, runInBackground = false)
    }

    /**
     * Launches a CLI session inside the distro with the working directory
     * set to [projectPath] and Codex on PATH. Codex starts automatically in
     * interactive mode.
     */
    fun buildLaunchCodexCliIntent(distroId: String, projectPath: String): Intent {
        val pathEscaped = projectPath.replace("\"", "\\\"")
        val banner = "\\n\\033[1;36m[NativeCode Codex]\\033[0m Project: $pathEscaped\\n\\033[1;32mLaunching Codex...\\033[0m\\n"

        val dollar = "${'$'}"
        val innerCommand = (
            "cd \"$pathEscaped\" 2>/dev/null || cd /home/flux || cd /home; " +
            "export PATH=\"${dollar}PATH:/opt/nodejs/bin:/usr/local/bin\"; " +
            "echo -e \"$banner\"; " +
            "exec codex"
        )

        return when {
            distroId == "termux" -> {
                buildRunCommandIntent(
                    "cd \"$pathEscaped\" 2>/dev/null || cd /home; export PATH=\"${dollar}PATH:/opt/nodejs/bin:/usr/local/bin\"; echo -e \"$banner\"; exec codex",
                    runInBackground = false
                )
            }
            distroId.contains("chroot") -> {
                // Chroot: write a temp script, run it via chroot entry helper
                val chrootPath = when (distroId) {
                    "debian13_chroot" -> "/data/local/tmp/chrootDebian13"
                    "debian_chroot" -> "/data/local/tmp/chrootDebian"
                    else -> "/data/local/tmp/chrootDebian13"
                }
                val termuxTmp = "/data/data/com.termux/files/usr/tmp"
                val script = (
                    "cd \"$pathEscaped\" 2>/dev/null || cd /root || cd /home; " +
                    "export PATH=\"${dollar}PATH:/opt/nodejs/bin:/usr/local/bin\"; " +
                    "echo -e \"$banner\"; " +
                    "exec codex"
                )
                val scriptB64 = android.util.Base64.encodeToString(script.toByteArray(), android.util.Base64.NO_WRAP)
                val command = (
                    "su -c '" +
                    "mkdir -p $termuxTmp; " +
                    "echo \"$scriptB64\" | base64 -d > $termuxTmp/codex_cli.sh; " +
                    "chmod +x $termuxTmp/codex_cli.sh; " +
                    "busybox chroot $chrootPath /bin/su - root -c \"bash /tmp/codex_cli.sh\"; " +
                    "rm -f $termuxTmp/codex_cli.sh; " +
                    "'"
                )
                buildRunCommandIntent(command, runInBackground = false)
            }
            else -> {
                // PRoot: pass the inner command to proot-distro login
                val command = "proot-distro login $distroId --user flux -- bash -c '$innerCommand'"
                buildRunCommandIntent(command, runInBackground = false)
            }
        }
    }

    /**
     * Launches a generic CLI tool inside the distro with the working directory
     * set to [projectPath]. The tool starts automatically in interactive mode.
     */
    fun buildLaunchToolCliIntent(distroId: String, projectPath: String, toolName: String, toolCommand: String): Intent {
        val pathEscaped = projectPath.replace("\"", "\\\"")
        val banner = "\\n\\033[1;36m[NativeCode]\\033[0m Project: $pathEscaped\\n\\033[1;32mLaunching $toolName...\\033[0m\\n"

        val dollar = "${'$'}"
        val innerCommand = (
            "cd \"$pathEscaped\" 2>/dev/null || cd /home/flux || cd /home; " +
            "export PATH=\"${dollar}PATH:/opt/nodejs/bin:/usr/local/bin:/usr/local/sbin\"; " +
            "echo -e \"$banner\"; " +
            "exec $toolCommand"
        )

        return when {
            distroId == "termux" -> {
                buildRunCommandIntent(
                    "cd \"$pathEscaped\" 2>/dev/null || cd /home; export PATH=\"${dollar}PATH:/opt/nodejs/bin:/usr/local/bin:/usr/local/sbin\"; echo -e \"$banner\"; exec $toolCommand",
                    runInBackground = false
                )
            }
            distroId.contains("chroot") -> {
                val chrootPath = when (distroId) {
                    "debian13_chroot" -> "/data/local/tmp/chrootDebian13"
                    "debian_chroot" -> "/data/local/tmp/chrootDebian"
                    else -> "/data/local/tmp/chrootDebian13"
                }
                val termuxTmp = "/data/data/com.termux/files/usr/tmp"
                val script = (
                    "cd \"$pathEscaped\" 2>/dev/null || cd /root || cd /home; " +
                    "export PATH=\"${dollar}PATH:/opt/nodejs/bin:/usr/local/bin:/usr/local/sbin\"; " +
                    "echo -e \"$banner\"; " +
                    "exec $toolCommand"
                )
                val scriptB64 = android.util.Base64.encodeToString(script.toByteArray(), android.util.Base64.NO_WRAP)
                val command = (
                    "su -c '" +
                    "mkdir -p $termuxTmp; " +
                    "echo \"$scriptB64\" | base64 -d > $termuxTmp/${toolCommand}_cli.sh; " +
                    "chmod +x $termuxTmp/${toolCommand}_cli.sh; " +
                    "busybox chroot $chrootPath /bin/su - root -c \"bash /tmp/${toolCommand}_cli.sh\"; " +
                    "rm -f $termuxTmp/${toolCommand}_cli.sh; " +
                    "'"
                )
                buildRunCommandIntent(command, runInBackground = false)
            }
            else -> {
                val command = "proot-distro login $distroId --user flux -- bash -c '$innerCommand'"
                buildRunCommandIntent(command, runInBackground = false)
            }
        }
    }

    /**
     * Launches an IDE (GUI editor) inside the distro with the project path as argument.
     * Sets up Termux-X11 display server so the IDE renders properly.
     * e.g. code /project/path, cursor /project/path
     */
    fun buildLaunchIdeIntent(distroId: String, projectPath: String, ideCommand: String): Intent {
        val pathEscaped = projectPath.replace("\"", "\\\"")
        val banner = "\\n\\033[1;36m[NativeCode IDE]\\033[0m Launching $ideCommand...\\n"

        val dollar = "${'$'}"

        // Inner script that runs inside the distro and keeps the session alive
        val innerScript = """
            export DISPLAY=:0
            export PULSE_SERVER=tcp:127.0.0.1
            export XDG_RUNTIME_DIR=${dollar}{TMPDIR}
            su - flux -c "
                export DISPLAY=:0
                export PULSE_SERVER=tcp:127.0.0.1
                export XDG_RUNTIME_DIR=${dollar}{TMPDIR}
                nohup $ideCommand \\"$pathEscaped\\" >/dev/null 2>&1 &
            "
            sleep 2
            echo '[NativeCode] IDE running. Session active...'
            while true; do
                sleep 5
                if pidof $ideCommand >/dev/null 2>&1; then continue; fi
                if ps -eo comm= 2>/dev/null | grep -v grep | grep -q $ideCommand; then continue; fi
                # Fallback: keep alive for safety
                continue
            done
        """.trimIndent()
        val innerB64 = android.util.Base64.encodeToString(innerScript.toByteArray(), android.util.Base64.NO_WRAP)

        return when {
            distroId == "termux" -> {
                val command = """
                    echo -e "$banner"

                    # Kill existing X11
                    kill -9 ${dollar}(pgrep -f "termux.x11") 2>/dev/null

                    # Start VirGL (Turnip GPU) if available
                    if [ -x "${dollar}PREFIX/bin/virgl_test_server_android" ]; then
                        nohup setsid ${dollar}PREFIX/bin/virgl_test_server_android >/dev/null 2>&1 &
                        sleep 1
                        echo "[OK] VirGL server started"
                    fi

                    # Start PulseAudio
                    pulseaudio --start --load="module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1" --exit-idle-time=-1

                    # Start termux-x11
                    export XDG_RUNTIME_DIR=${dollar}{TMPDIR}
                    termux-x11 :0 >/dev/null &
                    sleep 3

                    # Launch Termux X11 activity
                    am start --user 0 -n com.termux.x11/com.termux.x11.MainActivity > /dev/null 2>&1
                    sleep 1

                    # Run IDE
                    export PULSE_SERVER=127.0.0.1
                    env DISPLAY=:0 $ideCommand "$pathEscaped" &
                    sleep 2
                    echo "[NativeCode] IDE running. Session active..."
                    while true; do
                        sleep 5
                        if pidof $ideCommand >/dev/null 2>&1; then continue; fi
                        if ps -eo comm= 2>/dev/null | grep -v grep | grep -q $ideCommand; then continue; fi
                        continue
                    done
                """.trimIndent()
                buildRunCommandIntent(command, runInBackground = false)
            }
            distroId.contains("chroot") -> {
                val chrootPath = when (distroId) {
                    "debian13_chroot" -> "/data/local/tmp/chrootDebian13"
                    "debian_chroot" -> "/data/local/tmp/chrootDebian"
                    else -> "/data/local/tmp/chrootDebian13"
                }

                val command = if (distroId == "debian13_chroot") {
                    """
                    echo -e "$banner"
                    echo "NativeCode: Starting services in Termux context..."

                    # VirGL server (Turnip GPU)
                    if [ -x "${dollar}PREFIX/bin/virgl_test_server_android" ]; then
                        nohup setsid ${dollar}PREFIX/bin/virgl_test_server_android >/dev/null 2>&1 &
                        sleep 1
                        echo "[OK] VirGL server started"
                    fi

                    # PulseAudio server
                    ${dollar}PREFIX/bin/pulseaudio --start --load="module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1" --exit-idle-time=-1 2>/dev/null
                    ${dollar}PREFIX/bin/pacmd load-module module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1 >/dev/null 2>&1 || true
                    echo "[OK] PulseAudio started"

                    # Start termux-x11
                    export XDG_RUNTIME_DIR=${dollar}{TMPDIR}
                    termux-x11 :0 >/dev/null &
                    sleep 3

                    # Launch Termux X11 activity
                    am start --user 0 -n com.termux.x11/com.termux.x11.MainActivity > /dev/null 2>&1
                    sleep 1

                    echo "NativeCode: Launching IDE inside Chroot..."
                    su -c '
                        mount -o remount,dev,suid /data >/dev/null 2>&1
                        mount -t proc proc $chrootPath/proc >/dev/null 2>&1
                        mount -t sysfs sysfs $chrootPath/sys >/dev/null 2>&1
                        mount -o bind /dev $chrootPath/dev >/dev/null 2>&1
                        mount -o bind /dev/pts $chrootPath/dev/pts >/dev/null 2>&1
                        mkdir -p $chrootPath/dev/shm
                        mount -t tmpfs -o size=512M tmpfs $chrootPath/dev/shm >/dev/null 2>&1
                        mkdir -p $chrootPath/tmp
                        mount --bind /data/data/com.termux/files/usr/tmp $chrootPath/tmp >/dev/null 2>&1
                        busybox chroot $chrootPath /bin/bash -c "echo \"$innerB64\" | base64 -d | bash"
                    '
                    """.trimIndent()
                } else {
                    """
                    echo -e "$banner"
                    echo "NativeCode: Starting services in Termux context..."

                    # VirGL server (Turnip GPU)
                    if [ -x "${dollar}PREFIX/bin/virgl_test_server_android" ]; then
                        nohup setsid ${dollar}PREFIX/bin/virgl_test_server_android >/dev/null 2>&1 &
                        sleep 1
                        echo "[OK] VirGL server started"
                    fi

                    # PulseAudio server
                    pulseaudio --start --load="module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1" --exit-idle-time=-1 2>/dev/null
                    echo "[OK] PulseAudio started"

                    # Start termux-x11
                    export XDG_RUNTIME_DIR=${dollar}{TMPDIR}
                    termux-x11 :0 >/dev/null &
                    sleep 3

                    # Launch Termux X11 activity
                    am start --user 0 -n com.termux.x11/com.termux.x11.MainActivity > /dev/null 2>&1
                    sleep 1

                    echo "NativeCode: Launching IDE inside Chroot..."
                    su -c '
                        mount -o remount,dev,suid /data >/dev/null 2>&1
                        mount -t proc proc $chrootPath/proc >/dev/null 2>&1
                        mount -t sysfs sysfs $chrootPath/sys >/dev/null 2>&1
                        mount -o bind /dev $chrootPath/dev >/dev/null 2>&1
                        mount -o bind /dev/pts $chrootPath/dev/pts >/dev/null 2>&1
                        mkdir -p $chrootPath/dev/shm
                        mount -t tmpfs -o size=512M tmpfs $chrootPath/dev/shm >/dev/null 2>&1
                        mkdir -p $chrootPath/tmp
                        mount --bind /data/data/com.termux/files/usr/tmp $chrootPath/tmp >/dev/null 2>&1
                        busybox chroot $chrootPath /bin/bash -c "echo \"$innerB64\" | base64 -d | bash"
                    '
                    """.trimIndent()
                }
                buildRunCommandIntent(command, runInBackground = false)
            }
            else -> {
                // PRoot
                val command = """
                    echo -e "$banner"

                    # Kill existing X11
                    kill -9 ${dollar}(pgrep -f "termux.x11") 2>/dev/null

                    # Start VirGL (Turnip GPU) if available
                    if [ -x "${dollar}PREFIX/bin/virgl_test_server_android" ]; then
                        nohup setsid ${dollar}PREFIX/bin/virgl_test_server_android >/dev/null 2>&1 &
                        sleep 1
                        echo "[OK] VirGL server started"
                    fi

                    # Start PulseAudio
                    pulseaudio --start --load="module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1" --exit-idle-time=-1

                    # Start termux-x11
                    export XDG_RUNTIME_DIR=${dollar}{TMPDIR}
                    termux-x11 :0 >/dev/null &
                    sleep 3

                    # Launch Termux X11 activity
                    am start --user 0 -n com.termux.x11/com.termux.x11.MainActivity > /dev/null 2>&1
                    sleep 1

                    # Run IDE inside PRoot via base64-decoded inner script
                    proot-distro login $distroId --shared-tmp -- bash -c "echo '$innerB64' | base64 -d | bash"
                """.trimIndent()
                buildRunCommandIntent(command, runInBackground = false)
            }
        }
    }

    /**
     * Launches a specific chroot distro in CLI mode as ROOT user.
     * Only works for chroot distros (debian13_chroot, debian_chroot, arch_chroot).
     */
    fun buildLaunchRootCliIntent(distroId: String): Intent {
        if (distroId == "debian_chroot") {
            // Launch Chroot CLI as Root
            return buildRunCommandIntent("su -c \"sh /data/local/tmp/enter_debian_root.sh\"", runInBackground = false)
        }

        if (distroId == "debian13_chroot") {
            // Launch Debian 13 Chroot CLI as Root
            return buildRunCommandIntent("su -c \"sh /data/local/tmp/enter_debian13_root.sh\"", runInBackground = false)
        }
        
        if (distroId == "arch_chroot") {
            // Launch Arch Chroot CLI as Root
            return buildRunCommandIntent("su -c \"sh /data/local/tmp/enter_arch_root.sh\"", runInBackground = false)
        }
        
        // For non-chroot distros, fall back to regular CLI (proot doesn't support true root)
        return buildLaunchCliIntent(distroId)
    }

    /**
     * Launches a specific distro in GUI mode (XFCE4).
     */
    fun buildLaunchGuiIntent(distroId: String): Intent {
        if (distroId == "debian_chroot") {
            // Launch Chroot GUI as User (Wrapper handles su for Chroot entry)
            return buildRunCommandIntent("sh /data/local/tmp/start_debian_gui.sh", runInBackground = false)
        }

        if (distroId == "debian13_chroot") {
            // Launch Debian 13 Chroot GUI using Android Root (su)
            // IMPORTANT: Start VirGL and PulseAudio in Termux context FIRST (not root)
            // This fixes socket/permission issues
            val command = """
                echo "NativeCode: Starting services in Termux context..."
                
                # VirGL server
                if [ -x "${'$'}PREFIX/bin/virgl_test_server_android" ]; then
                    nohup setsid ${'$'}PREFIX/bin/virgl_test_server_android >/dev/null 2>&1 &
                    sleep 1
                    echo "[OK] VirGL server started"
                fi
                
                # PulseAudio server
                ${'$'}PREFIX/bin/pulseaudio --start --load="module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1" --exit-idle-time=-1 2>/dev/null
                ${'$'}PREFIX/bin/pacmd load-module module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1 >/dev/null 2>&1 || true
                echo "[OK] PulseAudio started"
                
                sleep 1
                echo "NativeCode: Launching Chroot GUI..."
                su -c "sh /data/local/tmp/start_debian13_gui.sh"
            """.trimIndent()
            return buildRunCommandIntent(command, runInBackground = false)
        }
        
        if (distroId == "arch_chroot") {
            // Launch Arch Chroot GUI (Hyprland via VirGL)
            return buildRunCommandIntent("su -c \"sh /data/local/tmp/start_arch_gui.sh\"", runInBackground = false)
        }
        
        // Standard Proot Launch
        // Execute the helper script created during setup
        val command = "bash $TERMUX_HOME_DIR/start_gui.sh $distroId"
        return buildRunCommandIntent(command, runInBackground = false)
    }

    /**
     * Stops the GUI for a specific distro.
     */
    fun buildStopGuiIntent(distroId: String): Intent {
        if (distroId == "debian13_chroot" || distroId == "debian_chroot") {
            // Stop Chroot GUI using root
            val scriptPath = if (distroId == "debian13_chroot") {
                "/data/local/tmp/stop_debian13_gui.sh"
            } else {
                "/data/local/tmp/stop_debian_gui.sh"
            }
            return buildRunCommandIntent("su -c \"sh $scriptPath\"", runInBackground = false)
        }
        
        if (distroId == "arch_chroot") {
            return buildRunCommandIntent("su -c \"sh /data/local/tmp/stop_arch_gui.sh\"", runInBackground = false)
        }
        
        // Standard Proot Stop
        val command = "bash $TERMUX_HOME_DIR/stop_gui.sh $distroId"
        return buildRunCommandIntent(command, runInBackground = false)
    }

    /**
     * Runs a specific feature script inside the distro.
     * Uses Base64 injection to avoid quoting/escape issues.
     */
    fun buildRunFeatureScriptIntent(distroId: String, scriptContent: String, callbackName: String? = null): Intent {
        val safeScript = if (!scriptContent.endsWith("\n")) "$scriptContent\n" else scriptContent
        val scriptB64 = android.util.Base64.encodeToString(safeScript.toByteArray(), android.util.Base64.NO_WRAP)
        
        // Callback command (run by Termux)
        val callbackCmd = if (callbackName != null) {
            "am start -a android.intent.action.VIEW -d \"nativecode://callback?result=success&name=$callbackName\""
        } else ""
        
        if (distroId == "debian_chroot") {
            // For Chroot, we must decode the script on the HOST (Android)
            // Write to Termux's tmp directory since that's what gets mounted into the chroot
            val termuxTmp = "/data/data/com.termux/files/usr/tmp"
            val innerCommand = """
                su -c '
                mkdir -p $termuxTmp;
                echo "$scriptB64" | base64 -d > $termuxTmp/flux_feature.sh;
                chmod +x $termuxTmp/flux_feature.sh;
                busybox chroot /data/local/tmp/chrootDebian /bin/su - root -c "bash /tmp/flux_feature.sh";
                rm -f $termuxTmp/flux_feature.sh;
                ';
                sleep 1; $callbackCmd
            """.trimIndent().replace("\n", " ")
            
            return buildRunCommandIntent(innerCommand, runInBackground = false)
        }

        if (distroId == "debian13_chroot") {
            // Debian 13 Chroot Feature Script
            // Debian 13 Chroot Feature Script
            // Uses generated helper for robustness, falls back to inline mounts if missing.
            // Write to Termux's tmp directory since that's what gets mounted into the chroot
            // This ensures the script is visible inside the chroot after /tmp is mounted
            val termuxTmp = "/data/data/com.termux/files/usr/tmp"
            val innerCommand = """
                su -c '
                ROOT_RUNNER="/data/local/tmp/run_debian13_root.sh";
                if [ -f "${'$'}ROOT_RUNNER" ]; then
                    mkdir -p $termuxTmp;
                    echo "$scriptB64" | base64 -d > $termuxTmp/flux_feature.sh;
                    chmod +x $termuxTmp/flux_feature.sh;
                    sh "${'$'}ROOT_RUNNER" "bash /tmp/flux_feature.sh";
                    rm -f $termuxTmp/flux_feature.sh;
                else
                    mnt=/data/local/tmp/chrootDebian13;
                    mkdir -p $termuxTmp;
                    mount -o remount,dev,suid /data >/dev/null 2>&1;
                    mount -t proc proc ${'$'}mnt/proc >/dev/null 2>&1;
                    mount -t sysfs sysfs ${'$'}mnt/sys >/dev/null 2>&1;
                    mount -o bind /dev ${'$'}mnt/dev >/dev/null 2>&1;
                    mount -o bind /dev/pts ${'$'}mnt/dev/pts >/dev/null 2>&1;
                    mkdir -p ${'$'}mnt/dev/shm;
                    mount -t tmpfs -o size=512M tmpfs ${'$'}mnt/dev/shm >/dev/null 2>&1;
                    mkdir -p ${'$'}mnt/tmp;
                    mount --bind $termuxTmp ${'$'}mnt/tmp >/dev/null 2>&1;
                    echo "$scriptB64" | base64 -d > $termuxTmp/flux_feature.sh;
                    chmod +x $termuxTmp/flux_feature.sh;
                    busybox chroot ${'$'}mnt /bin/su - root -c "bash /tmp/flux_feature.sh";
                    rm -f $termuxTmp/flux_feature.sh;
                fi;
                ';
                sleep 1; $callbackCmd
            """.trimIndent().replace("\n", " ")
            
            return buildRunCommandIntent(innerCommand, runInBackground = false)
        }
        
        // Command to run inside Termux (Proot):
        // 1. Run script inside Proot
        val innerCommand = "echo \"$scriptB64\" | base64 -d > /tmp/flux_feature.sh && bash /tmp/flux_feature.sh; rm -f /tmp/flux_feature.sh"
        // 2. Append Callback to outer command (Termux runs this after Proot exits)
        val command = "proot-distro login $distroId --shared-tmp -- bash -c '$innerCommand'; $callbackCmd"
        
        return buildRunCommandIntent(command, runInBackground = false) // Foreground to see progress
    }

    /**
     * Runs a script as Android Root (su).
     * Used for uninstalling/managing Chroot environments.
     */
    fun buildRunRootScriptIntent(scriptContent: String): Intent {
        val safeScript = if (!scriptContent.endsWith("\n")) "$scriptContent\n" else scriptContent
        val scriptB64 = android.util.Base64.encodeToString(safeScript.toByteArray(), android.util.Base64.NO_WRAP)
        
        // Write to tmp, execute, then remove.
        // We use /data/local/tmp as it is writable by shell and accessible by root.
        val command = """
            su -c '
            echo "$scriptB64" | base64 -d > /data/local/tmp/flux_root_task.sh
            chmod +x /data/local/tmp/flux_root_task.sh
            sh /data/local/tmp/flux_root_task.sh
            rm -f /data/local/tmp/flux_root_task.sh
            '
        """.trimIndent().replace("\n", " ")
        
        return buildRunCommandIntent(command, runInBackground = false)
    }

    /**
     * Generates a safe command string that detects if it's running as root,
     * and if not, prompts the user to type 'su'.
     * Used for Clipboard copy-paste interactions.
     */
    fun getSafeRootManualCommand(scriptContent: String, scriptName: String): String {
        val safeScript = if (!scriptContent.endsWith("\n")) "$scriptContent\n" else scriptContent
        val scriptB64 = android.util.Base64.encodeToString(safeScript.toByteArray(), android.util.Base64.DEFAULT)
        // Use Heredoc with wrapped Base64 to prevent terminal freeze (Line length limits)
        val chunkedEchos = "cat << 'EOF_B64' > \"\${S}.b64\"\n$scriptB64\nEOF_B64\n"

        return """
            S="/data/local/tmp/$scriptName"
            if [ "${'$'}(id -u)" != "0" ]; then S="${'$'}HOME/$scriptName"; fi
            $chunkedEchos
            base64 -d "${'$'}S.b64" > "${'$'}S"
            rm -f "${'$'}S.b64"
            chmod +x "${'$'}S"
            if [ "${'$'}(id -u)" = "0" ]; then
                sh "${'$'}S"
            else
                echo "⚠️ PLEASE RUN AS ROOT ⚠️"
                echo "Type su and press Enter."
                echo "Then paste this command again."
            fi
        """.trimIndent() + "\n"
    }

    // ─────────────────────────────────────────────────────────────────────────
    // KDE Plasma launch / stop
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Launches KDE Plasma for a given distro.
     * PRoot: runs start_gui_kde.sh (startplasma-x11)
     * Chroot (debian13_chroot): starts VirGL + PulseAudio in Termux context first,
     *   then su into the chroot's start_debian13_kde_gui.sh.
     */
    fun buildLaunchKdeGuiIntent(context: android.content.Context, distroId: String): Intent {
        if (distroId == "debian13_chroot") {
            val command = """
                echo "NativeCode: Starting services in Termux context for KDE..."

                # VirGL server
                if [ -x "${'$'}PREFIX/bin/virgl_test_server_android" ]; then
                    nohup setsid ${'$'}PREFIX/bin/virgl_test_server_android >/dev/null 2>&1 &
                    sleep 1
                    echo "[OK] VirGL server started"
                fi

                # PulseAudio server
                ${'$'}PREFIX/bin/pulseaudio --start --load="module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1" --exit-idle-time=-1 2>/dev/null
                ${'$'}PREFIX/bin/pacmd load-module module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1 >/dev/null 2>&1 || true
                echo "[OK] PulseAudio started"

                sleep 1
                echo "NativeCode: Launching Chroot KDE GUI..."
                su -c "sh /data/local/tmp/start_debian13_kde_gui.sh"
            """.trimIndent()
            return buildRunCommandIntent(command, runInBackground = false)
        }

        if (distroId == "debian_chroot") {
            return buildRunCommandIntent("sh /data/local/tmp/start_debian_kde_gui.sh", runInBackground = false)
        }

        // Standard PRoot launch — deploy start_gui_kde.sh inline then run it
        val scriptManager = ScriptManager(context)
        val kdeGuiScriptContent = scriptManager.getScriptContent("common/start_gui_kde.sh")
        val kdeGuiScriptB64 = android.util.Base64.encodeToString(kdeGuiScriptContent.toByteArray(), android.util.Base64.NO_WRAP)
        val command = """
            echo '$kdeGuiScriptB64' | base64 -d > ${'$'}HOME/start_gui_kde.sh
            chmod +x ${'$'}HOME/start_gui_kde.sh
            bash ${'$'}HOME/start_gui_kde.sh $distroId
        """.trimIndent()
        return buildRunCommandIntent(command, runInBackground = false)
    }

    /**
     * Stops the KDE Plasma session for a given distro.
     * Kills plasmashell, kwin_x11, kded5 instead of xfce4 processes.
     */
    fun buildStopKdeGuiIntent(distroId: String): Intent {
        if (distroId == "debian13_chroot" || distroId == "debian_chroot") {
            val scriptPath = if (distroId == "debian13_chroot") {
                "/data/local/tmp/stop_debian13_kde_gui.sh"
            } else {
                "/data/local/tmp/stop_debian_kde_gui.sh"
            }
            val command = """
                su -c 'if [ -f "$scriptPath" ]; then sh "$scriptPath"; else pkill -f plasmashell; pkill -f kwin_x11; pkill -f kded6; pkill -f Xwayland; fi'
            """.trimIndent()
            return buildRunCommandIntent(command, runInBackground = false)
        }

        // PRoot stop — mirror XFCE4 stop_gui.sh approach:
        // proot-distro login runs killall INSIDE proot namespace, then kill X11 from Termux.
        // Do NOT use pkill from Termux directly — it kills the Termux session itself.
        val command = """
            echo "NativeCode: Stopping KDE Plasma..."

            # Step 1: Kill KDE session processes inside proot (same as XFCE4 stop approach)
            proot-distro login ${'$'}{1:-${distroId}} -- bash -c \
                'killall -9 plasmashell kwin_x11 kded6 plasma_session startplasma-x11 dbus-launch 2>/dev/null; sleep 1' \
                2>/dev/null

            # Step 2: Stop Termux:X11
            am broadcast -a com.termux.x11.ACTION_STOP -p com.termux.x11 >/dev/null 2>&1
            killall -9 Xwayland termux-x11 2>/dev/null
            kill -9 $(pgrep -f "termux.x11") 2>/dev/null

            # Step 3: Stop PulseAudio
            pulseaudio --kill 2>/dev/null

            echo "NativeCode: KDE Plasma stopped."
        """.trimIndent()
        return buildRunCommandIntent(command, runInBackground = false)
    }
}
