package com.europartes.mobile.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import android.util.Base64
import com.google.gson.JsonParser

/** Stores JWTs encrypted at rest using an Android Keystore-backed master key. */
class TokenStore(context: Context) {
    val appContext: Context = context.applicationContext

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

    /**
     * Stable local partition key only. The claim is not validated here and MUST NOT grant
     * authorization; remote operations still require Django to validate the current session.
     */
    fun userIdForLocalPartition(): Int? {
        val segment = accessToken()?.split('.')?.getOrNull(1) ?: return null
        return runCatching {
            val payload = String(Base64.decode(segment, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8)
            JsonParser.parseString(payload).asJsonObject.get("user_id")?.asInt?.takeIf { it > 0 }
        }.getOrNull()
    }

    /** Functional role from the token claims, used only to decide what to read from Room. */
    fun roleForLocalPartition(): String? {
        val segment = accessToken()?.split('.')?.getOrNull(1) ?: return null
        return runCatching {
            val payload = String(Base64.decode(segment, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8)
            JsonParser.parseString(payload).asJsonObject.get("grupo")?.asString?.takeIf(String::isNotBlank)
        }.getOrNull()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val KEY_ACCESS = "jwt_access"
        const val KEY_REFRESH = "jwt_refresh"
    }
}
