package ani.saikou.neo.tv.signin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
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

/** The AniList profile picture in a circle, over the name's first letter on pink-deep, which shows until it loads or if it can't. */
@Composable
internal fun ProfileAvatar(
    profile: AniListProfile,
    size: Dp,
    initialStyle: TextStyle,
) {
    val colors = NeoTheme.colors
    Box(Modifier.size(size).clip(CircleShape).background(colors.pinkDeep), contentAlignment = Alignment.Center) {
        Text(profile.name.take(1).uppercase(), style = initialStyle, color = colors.paper)
        profile.avatarUrl?.let { url ->
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}
