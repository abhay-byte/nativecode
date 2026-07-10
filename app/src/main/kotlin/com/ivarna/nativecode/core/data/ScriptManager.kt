package com.ivarna.nativecode.core.data

import android.content.Context
import android.util.Log
import com.ivarna.nativecode.core.termux.TermuxBootstrapManager
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class ScriptManager(private val context: Context) {

    companion object {
        private const val TAG = "ScriptManager"

        /** Launch scripts always kept in termux $HOME */
        val LAUNCH_SCRIPTS = listOf(
            "common/start_gui.sh" to "start_gui.sh",
            "common/start_gui_kde.sh" to "start_gui_kde.sh",
            "common/stop_gui.sh" to "stop_gui.sh",
        )
    }

    /**
     * Reads a script file from the assets folder and returns it as a single String.
     * Useful for passing small scripts directly to 'bash -c'.
     */
    fun getScriptContent(fileName: String): String {
        return try {
            val inputStream = context.assets.open("scripts/$fileName")
            val reader = BufferedReader(InputStreamReader(inputStream))
            reader.use { it.readText() }
        } catch (e: Exception) {
            e.printStackTrace()
            "echo 'Error executing script: ${e.message}'"
        }
    }

    /**
     * Write launch scripts from app assets into the internal Termux home
     * (`files/termux-home` → `/data/data/com.termux/files/home` via proot bind).
     *
     * Call on every XFCE/KDE/Terminal launch and after base install so the device
     * never keeps a stale `~/start_gui.sh` from an older APK.
     */
    fun deployLaunchScriptsToHome(): Boolean {
        return try {
            val home = TermuxBootstrapManager.homeDir(context)
            home.mkdirs()
            for ((assetPath, homeName) in LAUNCH_SCRIPTS) {
                val content = getScriptContent(assetPath)
                if (content.startsWith("echo 'Error executing script:")) {
                    Log.w(TAG, "Skip deploy $homeName — asset missing: $assetPath")
                    continue
                }
                val out = File(home, homeName)
                out.writeText(content)
                out.setExecutable(true, false)
                Log.i(TAG, "Deployed $homeName (${content.length} bytes) → ${out.absolutePath}")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "deployLaunchScriptsToHome failed: ${e.message}", e)
            false
        }
    }

    /**
     * Shell snippet: base64-decode launch scripts into $HOME (for install runners).
     */
    fun buildDeployLaunchScriptsShell(): String {
        val sb = StringBuilder()
        sb.appendLine("# Deploy launch scripts from app assets (embedded base64)")
        for ((assetPath, homeName) in LAUNCH_SCRIPTS) {
            val content = getScriptContent(assetPath)
            if (content.startsWith("echo 'Error executing script:")) continue
            val b64 = android.util.Base64.encodeToString(
                content.toByteArray(Charsets.UTF_8),
                android.util.Base64.NO_WRAP
            )
            sb.appendLine("echo '$b64' | base64 -d > \"\$HOME/$homeName\" && chmod +x \"\$HOME/$homeName\"")
        }
        return sb.toString()
    }
}
