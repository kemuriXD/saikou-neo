package ani.saikou.neo.shared.account

import android.content.Context
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.security.GeneralSecurityException
import java.security.ProviderException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class KeystoreAccountStoreTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val kemuri = AniListProfile(5412345, "Kemuri", "https://anilist.co/user/5412345", "https://s4.anilist.co/avatar.png")

    @Test
    fun anAccountReadsBackAfterARestart() {
        KeystoreAccountStore(context, FlipCipher).saveAccount(SignedInAccount(kemuri, AniListToken("the-token", 5_000)))

        val read = KeystoreAccountStore(context, FlipCipher).readAccount()

        assertEquals(kemuri, read?.profile)
        assertEquals("the-token", read?.token?.accessToken)
        assertEquals(5_000L, read?.token?.expiresAtEpochSeconds)
    }

    @Test
    fun theTokenIsNeverSavedAsPlainText() {
        KeystoreAccountStore(context, FlipCipher).saveAccount(SignedInAccount(kemuri, AniListToken("the-token", 5_000)))

        val files =
            context
                .getSharedPreferences(KeystoreAccountStore.FILE, Context.MODE_PRIVATE)
                .all.values
                .joinToString()

        assertFalse("the-token" in files)
        assertFalse("Kemuri" in files)
    }

    @Test
    fun anAccountThatCanNoLongerBeReadIsForgotten() {
        KeystoreAccountStore(context, FlipCipher).saveAccount(SignedInAccount(kemuri, AniListToken("the-token", 5_000)))

        assertNull(KeystoreAccountStore(context, BrokenCipher).readAccount())
        assertNull(KeystoreAccountStore(context, FlipCipher).readAccount())
    }

    @Test
    fun aKeystoreThatFailsAnotherWayIsForgottenToo() {
        KeystoreAccountStore(context, FlipCipher).saveAccount(SignedInAccount(kemuri, AniListToken("the-token", 5_000)))

        assertNull(KeystoreAccountStore(context, ProviderFailCipher).readAccount())
        assertNull(KeystoreAccountStore(context, FlipCipher).readAccount())
    }

    @Test
    fun clearingForgetsTheAccount() {
        val store = KeystoreAccountStore(context, FlipCipher)
        store.saveAccount(SignedInAccount(kemuri, AniListToken("the-token", 5_000)))

        store.clearAccount()

        assertNull(store.readAccount())
    }

    @Test
    fun anAttemptReadsBackUntilCleared() {
        val store = KeystoreAccountStore(context, FlipCipher)
        store.saveAttempt(SignInAttempt("state-1", 1_000))

        assertEquals(SignInAttempt("state-1", 1_000), KeystoreAccountStore(context, FlipCipher).readAttempt())
        store.clearAttempt()
        assertNull(store.readAttempt())
    }
}

/** Stands in for the Keystore, which Robolectric doesn't have: flips every bit, so the text doesn't show. */
private object FlipCipher : AccountCipher {
    override fun encrypt(plain: ByteArray): ByteArray = plain.map { it.toInt().inv().toByte() }.toByteArray()

    override fun decrypt(sealed: ByteArray): ByteArray = encrypt(sealed)
}

/** A Keystore that fails with a runtime exception, as some phones' do. */
private object ProviderFailCipher : AccountCipher {
    override fun encrypt(plain: ByteArray): ByteArray = throw ProviderException("Keystore failed")

    override fun decrypt(sealed: ByteArray): ByteArray = throw ProviderException("Keystore failed")
}

/** A Keystore whose key is gone, as after restoring a backup on a new phone. */
private object BrokenCipher : AccountCipher {
    override fun encrypt(plain: ByteArray): ByteArray = throw GeneralSecurityException("No key")

    override fun decrypt(sealed: ByteArray): ByteArray = throw GeneralSecurityException("No key")
}
