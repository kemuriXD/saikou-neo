package ani.saikou.neo.phone

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Where the browser comes back to after signing in to AniList. It passes the answer to [MainActivity], which checks it
 * against the sign-in it started, and closes. It shows nothing and keeps no history.
 */
class AniListCallbackActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val answer = intent.dataString
        intent.data = null
        if (answer != null) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_ANILIST_CALLBACK, answer)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
        finish()
    }
}
