package com.myfitnessmeals.app.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class OAuthToken(
    val accessToken: String,
    val refreshToken: String,
)

interface OAuthTokenStore {
    fun putToken(tokenRef: String, token: OAuthToken)

    fun getToken(tokenRef: String): OAuthToken?

    fun removeToken(tokenRef: String)
}

class EncryptedOAuthTokenStore(context: Context) : OAuthTokenStore {
    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (_: Exception) {
            // Fallback used only when encrypted storage cannot be initialized.
            context.getSharedPreferences(FILE_NAME_FALLBACK, Context.MODE_PRIVATE)
        }
    }

    override fun putToken(tokenRef: String, token: OAuthToken) {
        prefs.edit()
            .putString(keyAccess(tokenRef), token.accessToken)
            .putString(keyRefresh(tokenRef), token.refreshToken)
            .apply()
    }

    override fun getToken(tokenRef: String): OAuthToken? {
        val access = prefs.getString(keyAccess(tokenRef), null) ?: return null
        val refresh = prefs.getString(keyRefresh(tokenRef), null) ?: return null
        return OAuthToken(accessToken = access, refreshToken = refresh)
    }

    override fun removeToken(tokenRef: String) {
        prefs.edit()
            .remove(keyAccess(tokenRef))
            .remove(keyRefresh(tokenRef))
            .apply()
    }

    private fun keyAccess(tokenRef: String): String = "${tokenRef}_access"

    private fun keyRefresh(tokenRef: String): String = "${tokenRef}_refresh"

    private companion object {
        const val FILE_NAME = "secure_oauth_tokens"
        const val FILE_NAME_FALLBACK = "secure_oauth_tokens_fallback"
    }
}
