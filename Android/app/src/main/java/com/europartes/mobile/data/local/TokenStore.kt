package com.europartes.mobile.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Stores JWTs encrypted at rest using an Android Keystore-backed master key. */
class TokenStore(context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context.applicationContext,
        "europartes_secure_session",
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun save(access: String, refresh: String?) {
        preferences.edit()
            .putString(KEY_ACCESS, access)
            .apply {
                if (refresh.isNullOrBlank()) remove(KEY_REFRESH)
                else putString(KEY_REFRESH, refresh)
            }
            .apply()
    }

    fun accessToken(): String? = preferences.getString(KEY_ACCESS, null)

    fun refreshToken(): String? = preferences.getString(KEY_REFRESH, null)

    fun clear() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val KEY_ACCESS = "jwt_access"
        const val KEY_REFRESH = "jwt_refresh"
    }
}
