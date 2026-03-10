package com.example.corepostemergencybutton.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureConfigStore(context: Context) {
    private val appContext = context.applicationContext

    private val preferences: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            PREFERENCES_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun load(): CorePostConfig =
        CorePostConfig(
            baseUrl = preferences.getString(KEY_BASE_URL, "").orEmpty(),
            emergencyId = preferences.getString(KEY_EMERGENCY_ID, "").orEmpty(),
            panicSecret = preferences.getString(KEY_PANIC_SECRET, "").orEmpty(),
        )

    fun save(config: CorePostConfig) {
        preferences.edit()
            .putString(KEY_BASE_URL, config.normalizedBaseUrl)
            .putString(KEY_EMERGENCY_ID, config.emergencyId.trim())
            .putString(KEY_PANIC_SECRET, config.panicSecret.trim())
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "corepost_secure_config"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_EMERGENCY_ID = "emergency_id"
        private const val KEY_PANIC_SECRET = "panic_secret"
    }
}
