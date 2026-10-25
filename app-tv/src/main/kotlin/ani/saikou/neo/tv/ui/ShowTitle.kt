package ani.saikou.neo.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ani.saikou.neo.shared.home.Logo
import ani.saikou.neo.tv.theme.NeoTheme
import coil3.Image
import coil3.PlatformContext
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.toBitmap

// From the design: the logo fits a 360 × 80 box, sitting on the line below it.
internal val LogoWidth = 360.dp
internal val LogoHeight = 80.dp

// Title logos are wide. Squarer images filed as logos are pictures of a character, and a narrower logo can't be read
// at the logo's height. Demon Slayer's logo is 1.23 times as wide as it is tall; the pictures are 1.08 at most.
private const val MIN_LOGO_ASPECT = 1.2f

// Key art filed as a logo fills its rectangle. Real logos are mostly see-through: the most solid one is 91% opaque.
private const val MAX_OPAQUE_SHARE = 0.95f

/** Without [checkShape], for a logo people picked as the title, any shape will do. */
internal fun looksLikeALogo(
    width: Int,
    height: Int,
    opaqueShare: Float,
    checkShape: Boolean = true,
) = height > 0 && (!checkShape || width >= height * MIN_LOGO_ASPECT) && opaqueShare < MAX_OPAQUE_SHARE

/** Reads the loaded logo's pixels. Coil never puts a transformed image in GPU memory, so they can be read. */
private fun Image.looksLikeALogo(checkShape: Boolean): Boolean {
    val bitmap = toBitmap()
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    return looksLikeALogo(bitmap.width, bitmap.height, opaqueShare(pixels), checkShape)
}

internal class LogoSource(
    val url: String,
    val checkShape: Boolean,
)

/** The logos to try in turn: AniZip's, then fanart.tv's, which people picked as the title, so its shape isn't checked. */
internal fun Logo.Image.sources() =
    listOfNotNull(
        aniZip?.let {
            LogoSource(it, checkShape = true)
        },
        fanartTv?.let { LogoSource(it, checkShape = false) },
    )

/**
 * A show's title logo, the same on Home and Details. Until a logo has loaded and looks like one, [placeholder] shows.
 * A logo that won't load or isn't one gives way to the next source; without any, [text] shows the title in words.
 */
@Composable
internal fun ShowTitle(
    title: String,
    logo: Logo,
    logoTag: String,
    placeholder: @Composable () -> Unit,
    text: @Composable () -> Unit,
) {
    val sources = remember(logo) { (logo as? Logo.Image)?.sources().orEmpty() }
    var tried by remember(logo) { mutableIntStateOf(0) }
    var loaded by remember(logo) { mutableStateOf(false) }
    when {
        logo == Logo.Loading -> {
            placeholder()
        }

        tried < sources.size -> {
            val source = sources[tried]
            val shown by animateFloatAsState(
                targetValue = if (loaded) 1f else 0f,
                animationSpec = tween(NeoTheme.dimensions.durationBackdropMillis / 2),
                label = "logo",
            )
            Box {
                if (!loaded) placeholder()
                val context = LocalPlatformContext.current
                key(source.url) {
                    AsyncImage(
                        model = remember { logoRequest(context, source.url) },
                        contentDescription = title,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.BottomStart,
                        onSuccess = {
                            if (it.result.image.looksLikeALogo(source.checkShape)) loaded = true else tried++
                        },
                        onError = { tried++ },
                        modifier = Modifier.size(LogoWidth, LogoHeight).alpha(shown).testTag(logoTag),
                    )
                }
            }
        }

        else -> {
            text()
        }
    }
}

/** A logo trimmed of empty margins. Preloading uses the same request, so the cached copy is the one shown. */
internal fun logoRequest(
    context: PlatformContext,
    url: String,
) = ImageRequest
    .Builder(context)
    .data(url)
    .transformations(TrimTransparentEdges)
    .build()
