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
            
            # start_gui from assets (keep in sync with ScriptManager.LAUNCH_SCRIPTS)
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
            "alpine" -> "common/setup_alpine_family.sh"
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
    /**
     * Shell command for component/feature install inside **internal** bootstrap terminal
     * (or legacy external Termux). Prefer [buildRunFeatureScriptCommand] + internal terminal.
     */
    fun buildRunFeatureScriptIntent(distroId: String, scriptContent: String, callbackName: String? = null): Intent {
        return buildRunCommandIntent(
            buildRunFeatureScriptCommand(distroId, scriptContent, callbackName),
            runInBackground = false
        )
    }

    /**
     * Bash to install a feature script into proot/chroot. Safe for embedded Termux home.
     * Ends with "✅ Installation complete!" on success for TermuxTerminalScreen markers.
     */
    fun buildRunFeatureScriptCommand(
        distroId: String,
        scriptContent: String,
        callbackName: String? = null,
        extraEnv: Map<String, String> = emptyMap(),
    ): String {
        val safeScript = if (!scriptContent.endsWith("\n")) "$scriptContent\n" else scriptContent
        val envBlock = if (extraEnv.isEmpty()) "" else {
            extraEnv.entries.joinToString("\n") { "export ${it.key}=\"${it.value}\"" } + "\n"
        }
        val fullScript = envBlock + safeScript
        val scriptB64 = android.util.Base64.encodeToString(fullScript.toByteArray(), android.util.Base64.NO_WRAP)

        // Optional deep-link callback (legacy external Termux / queue)
        val callbackCmd = if (callbackName != null) {
            """am start -a android.intent.action.VIEW -d "nativecode://callback?result=success&name=$callbackName" >/dev/null 2>&1 || true"""
        } else ""

        if (distroId == "debian_chroot") {
            val termuxTmp = "/data/data/com.termux/files/usr/tmp"
            return """
                set -e
                echo "NativeCode: Installing feature in chroot..."
                su -c '
                mkdir -p $termuxTmp
                echo "$scriptB64" | base64 -d > $termuxTmp/flux_feature.sh
                chmod +x $termuxTmp/flux_feature.sh
                busybox chroot /data/local/tmp/chrootDebian /bin/su - root -c "bash /tmp/flux_feature.sh"
                rm -f $termuxTmp/flux_feature.sh
                '
                $callbackCmd
                echo -e "\n\033[1;32m✅ Installation complete!\033[0m"
            """.trimIndent()
        }

        if (distroId == "debian13_chroot") {
            val termuxTmp = "/data/data/com.termux/files/usr/tmp"
            return """
                set -e
                echo "NativeCode: Installing feature in Debian 13 chroot..."
                su -c '
                ROOT_RUNNER="/data/local/tmp/run_debian13_root.sh"
                mkdir -p $termuxTmp
                echo "$scriptB64" | base64 -d > $termuxTmp/flux_feature.sh
                chmod +x $termuxTmp/flux_feature.sh
                if [ -f "${'$'}ROOT_RUNNER" ]; then
                    sh "${'$'}ROOT_RUNNER" "bash /tmp/flux_feature.sh"
                else
                    mnt=/data/local/tmp/chrootDebian13
                    mount -o remount,dev,suid /data >/dev/null 2>&1
                    mount -t proc proc ${'$'}mnt/proc >/dev/null 2>&1
                    mount -t sysfs sysfs ${'$'}mnt/sys >/dev/null 2>&1
                    mount -o bind /dev ${'$'}mnt/dev >/dev/null 2>&1
                    mount -o bind /dev/pts ${'$'}mnt/dev/pts >/dev/null 2>&1
                    mkdir -p ${'$'}mnt/dev/shm ${'$'}mnt/tmp
                    mount -t tmpfs -o size=512M tmpfs ${'$'}mnt/dev/shm >/dev/null 2>&1
                    mount --bind $termuxTmp ${'$'}mnt/tmp >/dev/null 2>&1
                    busybox chroot ${'$'}mnt /bin/su - root -c "bash /tmp/flux_feature.sh"
                fi
                rm -f $termuxTmp/flux_feature.sh
                '
                $callbackCmd
                echo -e "\n\033[1;32m✅ Installation complete!\033[0m"
            """.trimIndent()
        }

        // PRoot (primary path for debian/ubuntu/etc. in NativeCode bootstrap)
        return """
            set -e
            echo "NativeCode: Installing feature into $distroId..."
            echo "$scriptB64" | base64 -d > ${'$'}HOME/flux_feature.sh
            chmod +x ${'$'}HOME/flux_feature.sh
            proot-distro login $distroId --shared-tmp -- /bin/bash -lc '
              export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
              export DEBIAN_FRONTEND=noninteractive
              bash /data/data/com.termux/files/home/flux_feature.sh
            '
            rm -f ${'$'}HOME/flux_feature.sh
            $callbackCmd
            echo -e "\n\033[1;32m✅ Installation complete!\033[0m"
        """.trimIndent()
    }

    /** Uninstall shell for internal terminal (PRoot remove / chroot wipe). */
    fun buildUninstallCommand(distroId: String): String {
        return when {
            distroId == "termux" -> {
                "pkg uninstall -y xfce4 xfce4-terminal tigervnc; echo -e '\\n\\033[1;32m✅ Installation complete!\\033[0m'"
            }
            distroId.contains("chroot") -> {
                val path = when (distroId) {
                    "debian13_chroot" -> "/data/local/tmp/chrootDebian13"
                    "debian_chroot" -> "/data/local/tmp/chrootDebian"
                    else -> "/data/local/tmp/chroot*"
                }
                """
                su -c '
                echo "Unmounting and removing $path..."
                for mnt in ${'$'}(grep "$path" /proc/mounts 2>/dev/null | awk "{print \${'$'}2}" | sort -r); do
                  umount -l "${'$'}mnt" 2>/dev/null
                done
                rm -rf $path
                echo "Chroot removed."
                '
                echo -e "\n\033[1;32m✅ Installation complete!\033[0m"
                """.trimIndent()
            }
            else -> {
                """
                echo "Removing $distroId (proot-distro)..."
                if proot-distro remove $distroId 2>/dev/null; then
                  echo "Removed via proot-distro."
                else
                  echo "Fallback: manual rootfs delete..."
                  rm -rf ${'$'}PREFIX/var/lib/proot-distro/installed-rootfs/$distroId
                  rm -rf ${'$'}PREFIX/var/lib/proot-distro/containers/$distroId
                  echo "Manual remove done."
                fi
                echo -e "\n\033[1;32m✅ Installation complete!\033[0m"
                """.trimIndent()
            }
        }
    }

    fun buildUninstallIntent(distroId: String): Intent {
        return buildRunCommandIntent(buildUninstallCommand(distroId), runInBackground = false)
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

    // ─────────────────────────────────────────────────────────────────────────
    // PROJECT MANAGEMENT (Git, Diff, APK, Directory)
    // ─────────────────────────────────────────────────────────────────────────

    private fun getNativeCodeOutputDir(): String = "$TERMUX_HOME_DIR/.nativecode_output"

    private fun buildOutputCallbackCommand(outputFile: String, callbackName: String): String {
        return "; am start -a android.intent.action.VIEW -d \"nativecode://callback?result=success&name=$callbackName\""
    }

    /**
     * Clone a GitHub repository to a local path.
     * Runs in background if [runInBackground] is true.
     */
    fun buildGitCloneIntent(repoUrl: String, targetPath: String, runInBackground: Boolean = true): Intent {
        val safeUrl = repoUrl.replace("\"", "\\\"")
        val safePath = targetPath.replace("\"", "\\\"")
        val callbackName = "git_clone_${System.currentTimeMillis()}"
        val outputFile = "${getNativeCodeOutputDir()}/$callbackName.txt"
        
        val command = """
            mkdir -p "${getNativeCodeOutputDir()}"
            echo "[NativeCode] Cloning repository..." > "$outputFile"
            if git clone "$safeUrl" "$safePath" 2>&1; then
                echo "[NativeCode] Clone completed successfully." >> "$outputFile"
                echo "PATH:$safePath" >> "$outputFile"
            else
                echo "[NativeCode] Clone failed." >> "$outputFile"
            fi
            ${buildOutputCallbackCommand(outputFile, callbackName)}
        """.trimIndent().replace("\n", " ")
        
        return buildRunCommandIntent(command, runInBackground = runInBackground)
    }

    /**
     * Run git diff in a project directory and write output to a file.
     * The app can then read the file and display the diff.
     */
    fun buildGitDiffIntent(projectPath: String, runInBackground: Boolean = true): Intent {
        val safePath = projectPath.replace("\"", "\\\"")
        val callbackName = "git_diff_${System.currentTimeMillis()}"
        val outputFile = "${getNativeCodeOutputDir()}/$callbackName.txt"
        
        val command = """
            mkdir -p "${getNativeCodeOutputDir()}"
            cd "$safePath" 2>/dev/null || cd /home/flux || cd /home
            if [ -d .git ]; then
                echo "=== GIT DIFF ===" > "$outputFile"
                git diff --no-color 2>&1 >> "$outputFile" || echo "No changes to display." >> "$outputFile"
                echo "" >> "$outputFile"
                echo "=== GIT STATUS ===" >> "$outputFile"
                git status --short 2>&1 >> "$outputFile"
            else
                echo "Not a git repository." > "$outputFile"
            fi
            ${buildOutputCallbackCommand(outputFile, callbackName)}
        """.trimIndent().replace("\n", " ")
        
        return buildRunCommandIntent(command, runInBackground = runInBackground)
    }

    /**
     * Find APK files in a project directory and write results to a file.
     */
    fun buildFindApksIntent(projectPath: String, runInBackground: Boolean = true): Intent {
        val safePath = projectPath.replace("\"", "\\\"")
        val callbackName = "find_apks_${System.currentTimeMillis()}"
        val outputFile = "${getNativeCodeOutputDir()}/$callbackName.txt"
        
        val command = """
            mkdir -p "${getNativeCodeOutputDir()}"
            echo "=== APK FILES ===" > "$outputFile"
            find "$safePath" -type f -name "*.apk" 2>/dev/null >> "$outputFile"
            echo "" >> "$outputFile"
            echo "=== BUILD OUTPUT DIRECTORIES ===" >> "$outputFile"
            find "$safePath" -type d -name "build" 2>/dev/null | head -10 >> "$outputFile"
            ${buildOutputCallbackCommand(outputFile, callbackName)}
        """.trimIndent().replace("\n", " ")
        
        return buildRunCommandIntent(command, runInBackground = runInBackground)
    }

    /**
     * List files in a project directory and write results to a file.
     */
    fun buildDirectoryListIntent(projectPath: String, runInBackground: Boolean = true): Intent {
        val safePath = projectPath.replace("\"", "\\\"")
        val callbackName = "dir_list_${System.currentTimeMillis()}"
        val outputFile = "${getNativeCodeOutputDir()}/$callbackName.txt"
        
        val command = """
            mkdir -p "${getNativeCodeOutputDir()}"
            echo "=== DIRECTORY LISTING ===" > "$outputFile"
            ls -la "$safePath" 2>/dev/null >> "$outputFile" || echo "Directory not accessible." >> "$outputFile"
            echo "" >> "$outputFile"
            echo "=== SUBDIRECTORIES ===" >> "$outputFile"
            find "$safePath" -maxdepth 2 -type d 2>/dev/null | head -50 >> "$outputFile"
            ${buildOutputCallbackCommand(outputFile, callbackName)}
        """.trimIndent().replace("\n", " ")
        
        return buildRunCommandIntent(command, runInBackground = runInBackground)
    }

    /**
     * Ensure a project's images directory exists.
     * Creates <projectPath>/images if it doesn't exist.
     */
    fun buildEnsureImagesDirIntent(projectPath: String): Intent {
        val safePath = projectPath.replace("\"", "\\\"")
        val command = "mkdir -p \"$safePath/images\" && echo \"Images directory ready: $safePath/images\""
        return buildRunCommandIntent(command, runInBackground = true)
    }

    /**
     * Copy a shared image URI into the project's images directory.
     * Since Termux can't directly access content URIs, we generate a script
     * that uses Android's content resolver via am or termux-api if available.
     * Fallback: the app should copy the file itself via ContentResolver.
     */
    fun buildCopyImageToProjectIntent(projectPath: String, sourcePath: String): Intent {
        val safePath = projectPath.replace("\"", "\\\"")
        val safeSource = sourcePath.replace("\"", "\\\"")
        val imagesDir = "$safePath/images"
        val timestamp = System.currentTimeMillis()
        
        val command = """
            mkdir -p "$imagesDir"
            cp "$safeSource" "$imagesDir/shared_image_$timestamp.jpg" 2>/dev/null || cp "$safeSource" "$imagesDir/shared_image_$timestamp.png" 2>/dev/null || echo "Failed to copy image"
            echo "Image copied to: $imagesDir"
        """.trimIndent().replace("\n", " ")
        
        return buildRunCommandIntent(command, runInBackground = true)
    }
}
