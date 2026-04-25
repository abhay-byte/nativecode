package com.ivarna.nativecode.core.codex

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manages Codex authentication (OpenAI API key) securely.
 * Uses Android's EncryptedSharedPreferences to store the API key.
 */
class CodexAuthManager(context: Context) {

    companion object {
        private const val PREFS_FILE = "codex_auth_prefs"
        private const val KEY_API_KEY = "openai_api_key"
    }

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * Save the OpenAI API key securely.
     */
    fun saveApiKey(apiKey: String) {
        encryptedPrefs.edit().putString(KEY_API_KEY, apiKey.trim()).apply()
    }

    /**
     * Retrieve the stored OpenAI API key, or null if not set.
     */
    fun getApiKey(): String? {
        return encryptedPrefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() }
    }

    /**
     * Check if the API key has been configured.
     */
    fun isLoggedIn(): Boolean {
        return getApiKey() != null
    }

    /**
     * Clear the stored API key.
     */
    fun clearApiKey() {
        encryptedPrefs.edit().remove(KEY_API_KEY).apply()
    }
}
