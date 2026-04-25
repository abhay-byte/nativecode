package com.ivarna.nativecode.core.codex

import android.content.Context
import com.ivarna.nativecode.core.data.TermuxIntentFactory
import kotlinx.coroutines.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Bridge for receiving Codex responses via deep-link intents.
 * Termux wrapper scripts call `am start` with a nativecode://codex-response URI
 * after Codex finishes. This singleton maps request IDs to CompletableDeferreds
 * so that [CodexSessionManager.sendPrompt] can suspend until the response arrives.
 */
object CodexResponseBridge {
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Result<String>>>()

    fun register(id: String, deferred: CompletableDeferred<Result<String>>) {
        pending[id] = deferred
    }

    fun complete(id: String, result: Result<String>): Boolean {
        return pending.remove(id)?.complete(result) ?: false
    }
}

/**
 * Manages a Codex CLI session by bridging the Android UI with the
 * Codex process running inside a Linux container (PRoot/Chroot).
 *
 * Communication is **intent-based** to avoid relying on shared storage:
 * - The app embeds the base64 prompt in the RUN_COMMAND intent.
 * - Inside PRoot Debian, Codex runs in `~/.nativecode/codex` (Debian home).
 * - A Termux wrapper script reads the response from the container home
 *   (host filesystem path) and sends it back via `am start` deep link.
 */
class CodexSessionManager(
    private val context: Context,
    private val distroId: String,
    private val projectPath: String
) {
    companion object {
        private const val TIMEOUT_MS = 120_000L // 2 minutes
        private const val OAUTH_TIMEOUT_MS = 20_000L

        @Suppress("unused")
        fun clearIpcFiles() {
            // Intent-based IPC does not use shared files.
            // Kept for API compatibility.
        }
    }

    var isRunning = false
        private set

    /**
     * Send a prompt to Codex and wait for the response.
     * @param apiKey Optional OpenAI API key to inject into the Codex environment.
     */
    suspend fun sendPrompt(prompt: String, apiKey: String? = null): Result<String> = withContext(Dispatchers.IO) {
        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<Result<String>>()
        CodexResponseBridge.register(requestId, deferred)

        try {
            isRunning = true
            val command = buildCodexCommand(requestId, prompt, apiKey)
            val intent = TermuxIntentFactory.buildRunCommandIntent(command, runInBackground = true)
            context.startService(intent)

            withTimeout(TIMEOUT_MS) {
                deferred.await()
            }
        } catch (e: TimeoutCancellationException) {
            val err = Result.failure<String>(Exception("Codex timed out after ${TIMEOUT_MS / 1000} seconds."))
            CodexResponseBridge.complete(requestId, err)
            err
        } catch (e: Exception) {
            val err = Result.failure<String>(e)
            CodexResponseBridge.complete(requestId, err)
            err
        } finally {
            isRunning = false
        }
    }

    /**
     * Trigger Codex OAuth authentication.
     * Starts Codex in the background, selects "Sign in with ChatGPT" (option 1),
     * and captures the OAuth URL from the output.
     */
    suspend fun triggerOAuth(): Result<String> = withContext(Dispatchers.IO) {
        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<Result<String>>()
        CodexResponseBridge.register(requestId, deferred)

        try {
            val command = buildOAuthCommand(requestId)
            val intent = TermuxIntentFactory.buildRunCommandIntent(command, runInBackground = true)
            context.startService(intent)

            withTimeout(OAUTH_TIMEOUT_MS) {
                deferred.await()
            }
        } catch (e: TimeoutCancellationException) {
            val err = Result.failure<String>(Exception("OAuth URL capture timed out."))
            CodexResponseBridge.complete(requestId, err)
            err
        } catch (e: Exception) {
            val err = Result.failure<String>(e)
            CodexResponseBridge.complete(requestId, err)
            err
        }
    }

    private fun buildCodexCommand(requestId: String, prompt: String, apiKey: String?): String {
        val promptB64 = android.util.Base64.encodeToString(
            prompt.toByteArray(Charsets.UTF_8),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
        )
        val projectPathB64 = android.util.Base64.encodeToString(
            projectPath.toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        )
        val apiKeyLine = if (!apiKey.isNullOrBlank()) "export OPENAI_API_KEY=\"$apiKey\"" else ""

        // Inner script runs inside the container using Debian home for temp files
        val innerScript = """
            IPC="${'$'}HOME/.nativecode/codex"
            mkdir -p "${'$'}IPC"
            PROMPT_B64="$promptB64"
            PROMPT=${'$'}(echo "${'$'}PROMPT_B64" | tr '_-' '/+' | base64 -d)
            PROJECT=${'$'}(echo "$projectPathB64" | base64 -d)
            cd "${'$'}PROJECT" || cd /home/flux || cd /home
            export PATH="${'$'}PATH:/opt/nodejs/bin:/usr/local/bin"
            ${if (apiKeyLine.isNotBlank()) apiKeyLine else ""}
            if command -v codex >/dev/null 2>&1; then
                codex -q "${'$'}PROMPT" > "${'$'}IPC/response.txt" 2>&1
                echo "done" > "${'$'}IPC/status.txt"
            else
                echo "Codex CLI not found. Please install it from AI Tools first." > "${'$'}IPC/response.txt"
                echo "error" > "${'$'}IPC/status.txt"
            fi
        """.trimIndent()

        return wrapForDistro(requestId, innerScript, isOAuth = false)
    }

    private fun buildOAuthCommand(requestId: String): String {
        val projectPathB64 = android.util.Base64.encodeToString(
            projectPath.toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        )

        val innerScript = """
            IPC="${'$'}HOME/.nativecode/codex"
            mkdir -p "${'$'}IPC"
            PROJECT=${'$'}(echo "$projectPathB64" | base64 -d)
            cd "${'$'}PROJECT" || cd /home/flux || cd /home
            export PATH="${'$'}PATH:/opt/nodejs/bin:/usr/local/bin"
            LOG="${'$'}IPC/oauth.log"
            URL_FILE="${'$'}IPC/oauth_url.txt"
            rm -f "${'$'}LOG" "${'$'}URL_FILE"
            nohup bash -c '(sleep 2; echo 1; sleep 2; echo 1) | codex > "${'$'}LOG" 2>&1' > /dev/null 2>&1 &
            CODEX_PID=${'$'}!
            for i in ${'$'}(seq 1 30); do
                sleep 0.5
                if [ -f "${'$'}LOG" ]; then
                    URL=${'$'}(grep -oE 'https://auth[.]openai[.]com/oauth/authorize[^"<>[:space:]]+' "${'$'}LOG" | head -1)
                    if [ -n "${'$'}URL" ]; then
                        echo "${'$'}URL" > "${'$'}URL_FILE"
                        break
                    fi
                fi
            done
            wait ${'$'}CODEX_PID 2>/dev/null || true
        """.trimIndent()

        return wrapForDistro(requestId, innerScript, isOAuth = true)
    }

    /**
     * Wraps the inner script for execution inside the target distro.
     *
     * For PRoot/Termux, after the container command finishes the outer Termux shell
     * reads the response files from the container's home directory (host filesystem
     * path) and sends them back to the app via `am start` deep link.
     *
     * This avoids any reliance on /sdcard inside the container.
     */
    private fun wrapForDistro(requestId: String, innerScript: String, isOAuth: Boolean): String {
        val callbackUrl = if (isOAuth) "codex-oauth" else "codex-response"

        return when {
            distroId == "termux" -> {
                // Termux direct: run script, then am start from Termux
                """
                $innerScript
                IPC="${'$'}HOME/.nativecode/codex"
                ${if (isOAuth) {
                    """
                    for i in ${'$'}(seq 1 40); do
                        sleep 0.5
                        if [ -f "${'$'}IPC/oauth_url.txt" ]; then
                            URL=${'$'}(cat "${'$'}IPC/oauth_url.txt")
                            URL_B64=${'$'}(echo -n "${'$'}URL" | base64 -w0 2>/dev/null | tr '+/' '-_' | tr -d '=' || echo "")
                            am start --user 0 -n com.ivarna.nativecode/.MainActivity -a android.intent.action.VIEW -d "nativecode://$callbackUrl?id=$requestId&status=done&response=${'$'}URL_B64" >/dev/null 2>&1
                            exit 0
                        fi
                    done
                    am start --user 0 -n com.ivarna.nativecode/.MainActivity -a android.intent.action.VIEW -d "nativecode://$callbackUrl?id=$requestId&status=error&response=" >/dev/null 2>&1
                    """.trimIndent()
                } else {
                    """
                    RESPONSE=${'$'}(cat "${'$'}IPC/response.txt" 2>/dev/null || echo "No response file")
                    STATUS=${'$'}(cat "${'$'}IPC/status.txt" 2>/dev/null || echo "error")
                    RESPONSE_B64=${'$'}(echo -n "${'$'}RESPONSE" | base64 -w0 2>/dev/null | tr '+/' '-_' | tr -d '=' || echo "")
                    am start --user 0 -n com.ivarna.nativecode/.MainActivity -a android.intent.action.VIEW -d "nativecode://$callbackUrl?id=$requestId&status=${'$'}STATUS&response=${'$'}RESPONSE_B64" >/dev/null 2>&1
                    """.trimIndent()
                }}
                """.trimIndent()
            }
            distroId.contains("chroot") -> {
                // Chroot fallback (rarely used) — no deep-link bridge, just run script
                val chrootPath = when (distroId) {
                    "debian13_chroot" -> "/data/local/tmp/chrootDebian13"
                    "debian_chroot" -> "/data/local/tmp/chrootDebian"
                    else -> "/data/local/tmp/chrootDebian13"
                }
                val termuxTmp = "/data/data/com.termux/files/usr/tmp"
                """
                su -c '
                mkdir -p $termuxTmp
                echo "$innerScript" > $termuxTmp/codex_runner.sh
                chmod +x $termuxTmp/codex_runner.sh
                busybox chroot $chrootPath /bin/su - root -c "bash /tmp/codex_runner.sh"
                rm -f $termuxTmp/codex_runner.sh
                '
                """.trimIndent()
            }
            else -> {
                // PRoot distro: run inner script in container, then read from host FS and am start
                val rootfsBase = "/data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs/$distroId"

                """
                ROOTFS="$rootfsBase"
                if [ -d "${'$'}ROOTFS/home/flux" ]; then
                    CONTAINER_HOME="${'$'}ROOTFS/home/flux"
                elif [ -d "${'$'}ROOTFS/root" ]; then
                    CONTAINER_HOME="${'$'}ROOTFS/root"
                else
                    CONTAINER_HOME="${'$'}ROOTFS/home"
                fi
                CONTAINER_IPC="${'$'}CONTAINER_HOME/.nativecode/codex"
                mkdir -p "${'$'}CONTAINER_IPC"

                # Run codex inside container (writes to container home)
                proot-distro login $distroId --shared-tmp -- bash -c '$innerScript'

                # Read results from host filesystem path and send back to app
                ${if (isOAuth) {
                    """
                    for i in ${'$'}(seq 1 40); do
                        sleep 0.5
                        if [ -f "${'$'}CONTAINER_IPC/oauth_url.txt" ]; then
                            URL=${'$'}(cat "${'$'}CONTAINER_IPC/oauth_url.txt")
                            URL_B64=${'$'}(echo -n "${'$'}URL" | base64 -w0 2>/dev/null | tr '+/' '-_' | tr -d '=' || echo "")
                            am start --user 0 -n com.ivarna.nativecode/.MainActivity -a android.intent.action.VIEW -d "nativecode://$callbackUrl?id=$requestId&status=done&response=${'$'}URL_B64" >/dev/null 2>&1
                            exit 0
                        fi
                    done
                    am start --user 0 -n com.ivarna.nativecode/.MainActivity -a android.intent.action.VIEW -d "nativecode://$callbackUrl?id=$requestId&status=error&response=" >/dev/null 2>&1
                    """.trimIndent()
                } else {
                    """
                    RESPONSE=${'$'}(cat "${'$'}CONTAINER_IPC/response.txt" 2>/dev/null || echo "No response file")
                    STATUS=${'$'}(cat "${'$'}CONTAINER_IPC/status.txt" 2>/dev/null || echo "error")
                    RESPONSE_B64=${'$'}(echo -n "${'$'}RESPONSE" | base64 -w0 2>/dev/null | tr '+/' '-_' | tr -d '=' || echo "")
                    am start --user 0 -n com.ivarna.nativecode/.MainActivity -a android.intent.action.VIEW -d "nativecode://$callbackUrl?id=$requestId&status=${'$'}STATUS&response=${'$'}RESPONSE_B64" >/dev/null 2>&1
                    """.trimIndent()
                }}
                """.trimIndent()
            }
        }
    }

    fun cancel() {
        isRunning = false
    }
}

/**
 * Data class representing a chat message in the Codex UI.
 */
data class CodexMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: CodexRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class CodexRole {
    USER,
    ASSISTANT,
    SYSTEM
}
