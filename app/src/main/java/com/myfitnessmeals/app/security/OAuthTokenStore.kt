package com.myfitnessmeals.app.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class OAuthToken(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long? = null,
)

fun OAuthToken.isExpired(nowEpochSeconds: Long): Boolean {
    val expiry = expiresAtEpochSeconds ?: return false
    return nowEpochSeconds >= expiry
}

interface OAuthTokenStore {
    fun putToken(tokenRef: String, token: OAuthToken)

    fun getToken(tokenRef: String): OAuthToken?

    fun removeToken(tokenRef: String)

    fun removeAllTokens()
}

class EncryptedOAuthTokenStore(context: Context) : OAuthTokenStore {
    private val prefs: SharedPreferences? = runCatching {
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
    }.getOrNull()

    override fun putToken(tokenRef: String, token: OAuthToken) {
        val safeTokenRef = tokenRef.takeIf(::isValidTokenRef)
            ?: throw IllegalArgumentException("Invalid token reference format")
        val securePrefs = prefs ?: throw IllegalStateException("Encrypted token storage unavailable")
        securePrefs.edit()
            .putString(keyAccess(safeTokenRef), token.accessToken)
            .putString(keyRefresh(safeTokenRef), token.refreshToken)
            .putLong(keyExpires(safeTokenRef), token.expiresAtEpochSeconds ?: EXPIRES_NOT_SET)
            .commit()
    }

    override fun getToken(tokenRef: String): OAuthToken? {
        val safeTokenRef = tokenRef.takeIf(::isValidTokenRef) ?: return null
        val securePrefs = prefs ?: return null
        val access = securePrefs.getString(keyAccess(safeTokenRef), null) ?: return null
        val refresh = securePrefs.getString(keyRefresh(safeTokenRef), null) ?: return null
        val expiresRaw = securePrefs.getLong(keyExpires(safeTokenRef), EXPIRES_NOT_SET)
        return OAuthToken(
            accessToken = access,
            refreshToken = refresh,
            expiresAtEpochSeconds = expiresRaw.takeIf { it > 0L },
        )
    }

    override fun removeToken(tokenRef: String) {
        val safeTokenRef = tokenRef.takeIf(::isValidTokenRef) ?: return
        val securePrefs = prefs ?: return
        securePrefs.edit()
            .remove(keyAccess(safeTokenRef))
            .remove(keyRefresh(safeTokenRef))
            .remove(keyExpires(safeTokenRef))
            .commit()
    }

    override fun removeAllTokens() {
        prefs?.edit()?.clear()?.commit()
    }

    private fun keyAccess(tokenRef: String): String = "${tokenRef}_access"

    private fun keyRefresh(tokenRef: String): String = "${tokenRef}_refresh"

    private fun keyExpires(tokenRef: String): String = "${tokenRef}_expires"

    private fun isValidTokenRef(tokenRef: String): Boolean = TOKEN_REF_REGEX.matches(tokenRef)

    private companion object {
        const val FILE_NAME = "secure_oauth_tokens"
        const val EXPIRES_NOT_SET = -1L
        val TOKEN_REF_REGEX = Regex("^[a-fA-F0-9\\-]{36}$")
    }
}
