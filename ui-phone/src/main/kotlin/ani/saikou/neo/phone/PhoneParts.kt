package ani.saikou.neo.phone

import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ani.saikou.neo.phone.theme.PhoneColors
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter

/** Below this width the window is compact (a phone) and screens use all of it. */
private val COMPACT_WIDTH = 600.dp

/** The widest a screen's content gets on a tablet or an unfolded foldable, centred (#72). */
private val COLUMN_WIDTH = 480.dp

/** Holds a screen's content: the whole width on a phone, a centred column of at most 480 dp on anything wider. */
@Composable
internal fun PhoneColumn(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val width = if (maxWidth < COMPACT_WIDTH) Dp.Unspecified else COLUMN_WIDTH
        Box(Modifier.fillMaxHeight().widthIn(max = width)) { content() }
    }
}

/**
 * The AniList profile picture in a circle: 32 dp in the app bar, 56 dp on Account, 96 dp after signing in. The first
 * letter on pink shows until [pictureUrl] loads, or if there's no picture or it can't load. Once it loads, nothing is
 * left behind it, so a see-through picture stays see-through.
 */
@Composable
internal fun Avatar(
    name: String,
    pictureUrl: String?,
    size: Dp,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    var loaded by remember(pictureUrl) { mutableStateOf(false) }
    Box(
        modifier.size(size).clip(CircleShape).then(if (loaded) Modifier else Modifier.background(PhoneColors.pink)),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Text(
                text = name.firstOrNull()?.uppercase().orEmpty(),
                style = style,
                color = PhoneColors.ink,
            )
        }
        // Only a web address: Coil would also open file and content addresses.
        if (pictureUrl != null && pictureUrl.startsWith("https://")) {
            AsyncImage(
                model = pictureUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onState = { loaded = it is AsyncImagePainter.State.Success },
            )
        }
    }
}

/** False when the system's animations are off (animator duration scale 0): then nothing moves and carets stay solid. */
@Composable
internal fun rememberAnimationsOn(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f }
}

@Composable
internal fun Status(
    modifier: Modifier,
    @StringRes title: Int,
    @StringRes body: Int,
    icon: @Composable () -> Unit,
) = Status(modifier, stringResource(title), stringResource(body), icon = icon)

/** A centred icon, heading and line of help, as on 3.2 Camera access is off. */
@Composable
internal fun Status(
    modifier: Modifier,
    title: String,
    body: String,
    titleModifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        Spacer(Modifier.height(24.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = titleModifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun Badge(
    @DrawableRes icon: Int,
    tint: Color,
    background: Color,
) {
    Box(Modifier.size(72.dp).background(background, CircleShape), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = null, Modifier.size(36.dp), tint = tint)
    }
}

@Composable
internal fun Actions(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
internal fun ButtonLabel(
    @StringRes text: Int,
    @DrawableRes icon: Int? = null,
) {
    if (icon != null) {
        Icon(painterResource(icon), contentDescription = null, Modifier.size(24.dp))
        Spacer(Modifier.size(8.dp))
    }
    Text(stringResource(text), style = MaterialTheme.typography.titleMedium)
}

/** The full-width 56 dp button at the foot of a status screen. */
internal val BIG_BUTTON = Modifier.fillMaxWidth().height(56.dp)

/**
 * The waiting ring, as on Connecting and Checking with AniList. With the system's animations off, Material's ring
 * would stand still, so it turns slowly on the frame clock instead, which the animation setting doesn't scale (#72).
 */
@Composable
internal fun Spinner() {
    if (rememberAnimationsOn()) {
        CircularProgressIndicator(Modifier.size(40.dp), trackColor = PhoneColors.line)
        return
    }
    var degrees by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) withFrameMillis { degrees = (it - start) % SLOW_TURN_MILLIS * 360f / SLOW_TURN_MILLIS }
    }
    CircularProgressIndicator(progress = { 0.25f }, modifier = Modifier.size(40.dp).rotate(degrees), trackColor = PhoneColors.line)
}

private const val SLOW_TURN_MILLIS = 4_000L
