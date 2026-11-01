package ani.saikou.neo.tv.sources

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import java.io.File

/** The size of a saved logo: enough for the 40 dp tile on a 1080p TV (ADR 0028). */
internal const val ICON_PIXELS = 96

/**
 * Draws [icon] into a square PNG of [ICON_PIXELS] at [file], or deletes [file] and returns false when it can't be drawn.
 * An adaptive icon is drawn square, without the launcher's mask: its layers' middle two thirds, which is the part a
 * launcher always shows. The tile on screen rounds the corners.
 */
internal fun saveIcon(
    icon: Drawable,
    file: File,
): Boolean {
    val bitmap = Bitmap.createBitmap(ICON_PIXELS, ICON_PIXELS, Bitmap.Config.ARGB_8888)
    return try {
        val canvas = Canvas(bitmap)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && icon is AdaptiveIconDrawable) {
            // A layer is 108 dp, of which the middle 72 dp is safe, so it's drawn half as big again and centred.
            val layer = ICON_PIXELS * 3 / 2
            val inset = (layer - ICON_PIXELS) / 2
            for (part in listOfNotNull(icon.background, icon.foreground)) {
                part.setBounds(-inset, -inset, layer - inset, layer - inset)
                part.draw(canvas)
            }
        } else {
            icon.setBounds(0, 0, ICON_PIXELS, ICON_PIXELS)
            icon.draw(canvas)
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    } catch (_: RuntimeException) {
        // A source's own drawable can throw while it draws.
        file.delete()
        false
    } finally {
        bitmap.recycle()
    }
}
