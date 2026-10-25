package ani.saikou.neo.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.transformations
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

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
 * A show's title logo, the same on Home and Details. While the logo loads, [placeholder] shows; without a logo, when it
 * won't load, or when it hasn't loaded within [waitMillis], [text] shows the title in words. A logo too late stays
 * words until the show changes, so the title never swaps in front of the viewer. [onSettled] is called once it's one
 * or the other. `:shared` has already picked the logo
 * by its size, so any shape it sends is shown.
 */
@Composable
internal fun ShowTitle(
    title: String,
    logo: Logo,
    logoTag: String,
    waitMillis: Long,
    placeholder: @Composable () -> Unit,
    onSettled: () -> Unit = {},
    text: @Composable () -> Unit,
) {
    var failed by remember(logo) { mutableStateOf(false) }
    // The size it's drawn at, once it has loaded.
    var drawn by remember(logo) { mutableStateOf<DpSize?>(null) }
    val loaded = drawn != null
    // Settled once it's the logo or the title in words for good.
    val settled = logo == Logo.None || loaded || failed
    LaunchedEffect(settled) { if (settled) onSettled() }
    if (logo is Logo.Image) {
        LaunchedEffect(logo) {
            delay(waitMillis)
            if (drawn == null) failed = true
        }
    }
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
                // Its request has a size of its own, so it loads while it takes no space.
                AsyncImage(
                    model = remember(logo.url) { logoRequest(context, logo.url) },
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    onSuccess = { drawn = it.result.image.run { logoSize(width, height) } },
                    onError = { failed = true },
                    modifier = Modifier.size(drawn ?: DpSize.Zero).alpha(shown).testTag(logoTag),
                )
            }
        }

        else -> {
            text()
        }
    }
}

/** Starts loading [logo] into memory, so it's ready to show at once. */
@Composable
internal fun PreloadLogo(logo: Logo.Image?) {
    val context = LocalPlatformContext.current
    LaunchedEffect(logo) {
        logo?.let { SingletonImageLoader.get(context).enqueue(logoRequest(context, it.url)) }
    }
}

/**
 * A logo trimmed of empty margins, decoded no bigger than the logo box on this screen. Preloading uses the same
 * request, so the copy in memory is the one shown.
 */
internal fun logoRequest(
    context: PlatformContext,
    url: String,
): ImageRequest {
    val density = context.resources.displayMetrics.density
    return ImageRequest
        .Builder(context)
        .data(url)
        .size((LogoWidth.value * density).roundToInt(), (LogoHeight.value * density).roundToInt())
        .transformations(TrimTransparentEdges)
        .build()
}
