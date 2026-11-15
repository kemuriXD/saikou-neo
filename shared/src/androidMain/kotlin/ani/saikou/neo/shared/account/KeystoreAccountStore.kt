package ani.saikou.neo.shared.account

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONException
import org.json.JSONObject
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Seals and opens the saved account. */
internal interface AccountCipher {
    fun encrypt(plain: ByteArray): ByteArray

    fun decrypt(sealed: ByteArray): ByteArray
}

/**
 * Keeps the AniList account in the app's private storage, sealed with AES-GCM under a key that lives in the Android
 * Keystore and never leaves it. If the key is gone, such as after a backup is restored on another phone, the account
 * can't be read and is forgotten, so the phone just asks to sign in again.
 */
public class KeystoreAccountStore internal constructor(
    context: Context,
    private val cipher: AccountCipher,
) : AccountStore {
    public constructor(context: Context) : this(context, KeystoreCipher(KEY_ALIAS))

    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun readAccount(): SignedInAccount? {
        val sealed = preferences.getString(ACCOUNT, null) ?: return null
        return try {
            val json = JSONObject(cipher.decrypt(Base64.decode(sealed, Base64.NO_WRAP)).decodeToString())
            SignedInAccount(
                AniListProfile(json.getInt("id"), json.getString("name"), json.optString("siteUrl").ifEmpty { null }),
                AniListToken(json.getString("token"), json.getLong("expires")),
            )
        } catch (_: GeneralSecurityException) {
            null.also { clearAccount() }
        } catch (_: JSONException) {
            null.also { clearAccount() }
        } catch (_: IllegalArgumentException) {
            null.also { clearAccount() }
        }
    }

    override fun saveAccount(account: SignedInAccount) {
        val json =
            JSONObject()
                .put("id", account.profile.id)
                .put("name", account.profile.name)
                .put("siteUrl", account.profile.siteUrl ?: "")
                .put("token", account.token.accessToken)
                .put("expires", account.token.expiresAtEpochSeconds)
        val sealed = Base64.encodeToString(cipher.encrypt(json.toString().encodeToByteArray()), Base64.NO_WRAP)
        preferences.edit().putString(ACCOUNT, sealed).commit()
    }

    override fun clearAccount() {
        preferences.edit().remove(ACCOUNT).commit()
    }

    override fun readAttempt(): SignInAttempt? {
        val state = preferences.getString(ATTEMPT_STATE, null) ?: return null
        return SignInAttempt(state, preferences.getLong(ATTEMPT_STARTED, 0))
    }

    override fun saveAttempt(attempt: SignInAttempt) {
        preferences
            .edit()
            .putString(ATTEMPT_STATE, attempt.state)
            .putLong(ATTEMPT_STARTED, attempt.startedAtEpochSeconds)
            .commit()
    }

    override fun clearAttempt() {
        preferences
            .edit()
            .remove(ATTEMPT_STATE)
            .remove(ATTEMPT_STARTED)
            .commit()
    }

    internal companion object {
        const val FILE = "anilist_account"
        private const val KEY_ALIAS = "anilist_account"
        private const val ACCOUNT = "account"
        private const val ATTEMPT_STATE = "attempt_state"
        private const val ATTEMPT_STARTED = "attempt_started"
    }
}

/** AES-256-GCM with a key the Android Keystore makes and keeps. The 12-byte IV goes in front of the sealed bytes. */
private class KeystoreCipher(
    private val alias: String,
) : AccountCipher {
    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key(create = true)) }
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun decrypt(sealed: ByteArray): ByteArray {
        if (sealed.size <= IV_BYTES) throw GeneralSecurityException("Too short")
        val cipher =
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key(create = false), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
            }
        return cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
    }

    private fun key(create: Boolean): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        if (!create) throw GeneralSecurityException("No key")
        val spec =
            KeyGenParameterSpec
                .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        const val IV_BYTES = 12
    }
}
