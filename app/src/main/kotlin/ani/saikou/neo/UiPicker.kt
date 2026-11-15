package ani.saikou.neo

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration

/** Which screens the app opens: the TV app, or the phone companion (ADR 0038). */
enum class Ui {
    Tv,
    Phone,
    ;

    companion object {
        /** The UI a debug build was told to open with `--es ui tv` or `--es ui phone`, or null for anything else. */
        fun fromExtra(value: String?): Ui? = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

/**
 * TV screens on a television, or on a device with leanback or Fire TV's feature. Everything else, tablets included,
 * gets the phone screens. A [forced] UI wins.
 */
fun pickUi(
    isTelevision: Boolean,
    hasLeanback: Boolean,
    isFireTv: Boolean,
    forced: Ui? = null,
): Ui = forced ?: if (isTelevision || hasLeanback || isFireTv) Ui.Tv else Ui.Phone

/** The UI for this device, read once at launch. */
fun Context.deviceUi(forced: Ui? = null): Ui {
    val uiMode = getSystemService(UiModeManager::class.java)
    return pickUi(
        isTelevision = uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION,
        hasLeanback = packageManager.hasSystemFeature("android.software.leanback"),
        isFireTv = packageManager.hasSystemFeature("amazon.hardware.fire_tv"),
        forced = forced,
    )
}
