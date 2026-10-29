package io.github.vexpaer.openlistener.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class CredentialsStore(context: Context) {
    private val key = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    private val preferences = EncryptedSharedPreferences.create(
        context,
        "provider_credentials",
        key,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun get(provider: String, field: String): String? = preferences.getString("$provider.$field", null)
    fun set(provider: String, field: String, value: String) = preferences.edit().putString("$provider.$field", value).apply()
    fun remove(provider: String, field: String) = preferences.edit().remove("$provider.$field").apply()
}
