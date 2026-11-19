package ani.saikou.neo.tv.signin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Text
import ani.saikou.neo.shared.account.AniListProfile
import ani.saikou.neo.tv.theme.NeoTheme
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter

/**
 * The AniList profile picture in a circle. The name's first letter on pink-deep shows until it loads, or if it can't.
 * Once it loads, nothing is left behind it, so a see-through picture stays see-through.
 */
@Composable
internal fun ProfileAvatar(
    profile: AniListProfile,
    size: Dp,
    initialStyle: TextStyle,
) {
    val colors = NeoTheme.colors
    val url = profile.avatarUrl
    var loaded by remember(url) { mutableStateOf(false) }
    Box(
        Modifier.size(size).clip(CircleShape).then(if (loaded) Modifier else Modifier.background(colors.pinkDeep)),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) Text(profile.name.take(1).uppercase(), style = initialStyle, color = colors.paper)
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onState = { loaded = it is AsyncImagePainter.State.Success },
            )
        }
    }
}
