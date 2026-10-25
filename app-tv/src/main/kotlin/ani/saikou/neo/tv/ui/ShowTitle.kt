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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ani.saikou.neo.shared.home.Logo
import ani.saikou.neo.tv.theme.NeoTheme
import coil3.PlatformContext
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.transformations

// From the design: the logo fits a 360 × 80 box, sitting on the line below it.
internal val LogoWidth = 360.dp
internal val LogoHeight = 80.dp

// Title logos are wide. Anything squarer is key art filed as a logo.
private const val MIN_LOGO_ASPECT = 1.3f

internal fun looksLikeALogo(
    width: Int,
    height: Int,
) = height > 0 && width >= height * MIN_LOGO_ASPECT

/**
 * A show's title logo, the same on Home and Details. Until the logo has loaded and looks like one, [placeholder]
 * shows; without a logo, or when it won't load or isn't one, [text] shows the title in words.
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
    var loaded by remember(logo) { mutableStateOf(false) }
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
                    alignment = Alignment.BottomStart,
                    onSuccess = {
                        if (looksLikeALogo(it.result.image.width, it.result.image.height)) loaded = true else failed = true
                    },
                    onError = { failed = true },
                    modifier = Modifier.size(LogoWidth, LogoHeight).alpha(shown).testTag(logoTag),
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
