package ani.saikou.neo.phone

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ani.saikou.neo.phone.theme.PhoneTheme
import ani.saikou.neo.shared.account.AniListAccountClient
import ani.saikou.neo.shared.account.KeystoreAccountStore
import ani.saikou.neo.shared.account.PhoneAccountViewModel

class MainActivity : ComponentActivity() {
    private val accounts: PhoneAccountViewModel by viewModels {
        viewModelFactory {
            initializer {
                val client = AniListAccountClient()
                PhoneAccountViewModel(
                    clientId = BuildConfig.ANILIST_CLIENT_ID,
                    redirectUri = aniListRedirect(applicationContext),
                    store = KeystoreAccountStore(applicationContext),
                    viewer = client::viewer,
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The phone is always on ink, so the system bars keep light icons even when the phone is in light mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splash -> fadeOutSplash(splash) { splash.remove() } }
        }
        takeCallback(intent)
        val version = versionName()
        setContent { PhoneTheme { PhoneApp(accounts, version, ::openInBrowser) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeCallback(intent)
    }

    /** Hands the browser's answer to sign-in once, so it isn't read again when the activity is recreated. */
    private fun takeCallback(intent: Intent) {
        val callback = intent.getStringExtra(EXTRA_ANILIST_CALLBACK) ?: return
        intent.removeExtra(EXTRA_ANILIST_CALLBACK)
        setIntent(intent)
        accounts.finishSignIn(callback)
    }

    private fun openInBrowser(url: String) {
        val view = Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
        try {
            startActivity(view)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_browser, Toast.LENGTH_LONG).show()
        }
    }

    private fun versionName(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName
        }.orEmpty()

    internal companion object {
        /** The browser's answer to sign-in, passed on by [AniListCallbackActivity]. */
        const val EXTRA_ANILIST_CALLBACK = "ani.saikou.neo.phone.ANILIST_CALLBACK"
    }
}

/** Where AniList sends the browser back to. Each app ID has its own, registered with its AniList client. */
internal fun aniListRedirect(context: Context): String = "${context.packageName}://oauth/anilist"
