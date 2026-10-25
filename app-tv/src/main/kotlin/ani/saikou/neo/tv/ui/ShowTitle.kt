package ani.saikou.neo.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import ani.saikou.neo.shared.home.Logo
import ani.saikou.neo.tv.theme.NeoTheme
import coil3.PlatformContext
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.transformations

// From the design: the logo fits a 360 × 80 box.
internal val LogoWidth = 360.dp
internal val LogoHeight = 80.dp

/**
 * The size a [width] × [height] logo is drawn at, as big as fits the logo box. Its space is just that big, so the lines
 * above and below sit as close to a short, wide logo as to a title in words.
 */
internal fun logoSize(
    width: Int,
    height: Int,
): DpSize {
    if (width <= 0 || height <= 0) return DpSize(LogoWidth, LogoHeight)
    val aspect = width.toFloat() / height
    return if (aspect >= LogoWidth / LogoHeight) DpSize(LogoWidth, LogoWidth / aspect) else DpSize(LogoHeight * aspect, LogoHeight)
}

/**
 * A show's title logo, the same on Home and Details. Until the logo has loaded, [placeholder] shows; without a logo,
 * or when it won't load, [text] shows the title in words. `:shared` has already picked the logo by its size, so any
 * shape it sends is shown.
 */
@Composable
internal fun ShowTitle(
    title: String,
    logo: Logo,
    logoTag: String,
    placeholder: @Composable () -> Unit,
    text: @Composable () -> Unit,
) {
    var failed by remember(logo) { mutableStateOf(false) }
    // The size it's drawn at, once it has loaded.
    var drawn by remember(logo) { mutableStateOf<DpSize?>(null) }
    val loaded = drawn != null
    when {
        logo == Logo.Loading -> {
            placeholder()
        }

        logo is Logo.Image && !failed -> {
            val shown by animateFloatAsState(
                targetValue = if (loaded) 1f else 0f,
                animationSpec = tween(NeoTheme.dimensions.durationBackdropMillis / 2),
                label = "logo",
            )
            Box {
                if (!loaded) placeholder()
                val context = LocalPlatformContext.current
                AsyncImage(
                    model = remember(logo.url) { logoRequest(context, logo.url) },
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    onSuccess = { drawn = it.result.image.run { logoSize(width, height) } },
                    onError = { failed = true },
                    modifier = Modifier.size(drawn ?: DpSize(LogoWidth, LogoHeight)).alpha(shown).testTag(logoTag),
                )
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
