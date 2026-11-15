package ani.saikou.neo

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** The override a debug build was given, or null. Release builds ignore it. */
fun forcedUi(
    extra: String?,
    isDebug: Boolean,
): Ui? = if (isDebug) Ui.fromExtra(extra) else null

/**
 * The launcher entry on TVs and phones. It picks the UI once, opens it and closes before drawing, so the splash its
 * theme shows carries straight on into the screens (ADR 0038).
 *
 * The screens are named by class name, so the TV's classes aren't even loaded on a phone, nor the phone's on a TV.
 */
class EntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ui = deviceUi(forcedUi(intent.getStringExtra(EXTRA_UI), BuildConfig.DEBUG))
        val screens =
            when (ui) {
                Ui.Tv -> TV_SCREENS
                Ui.Phone -> PHONE_SCREENS
            }
        startActivity(Intent().setClassName(this, screens))
        finish()
    }

    companion object {
        /** Debug builds only: `adb shell am start -n <package>/ani.saikou.neo.EntryActivity --es ui tv` (or `phone`). */
        const val EXTRA_UI = "ui"

        private const val TV_SCREENS = "ani.saikou.neo.tv.MainActivity"
        private const val PHONE_SCREENS = "ani.saikou.neo.phone.MainActivity"
    }
}
