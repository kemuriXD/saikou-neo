package ani.saikou.neo.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ani.saikou.neo.phone.theme.PhoneColors
import ani.saikou.neo.shared.account.AniListProfile

/**
 * 6.1 Account: the AniList profile and About. Sign out comes with #250. On a tablet the whole page, app bar too, sits
 * in the centred column, so Back stays next to what it closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountScreen(
    profile: AniListProfile,
    version: String,
    onBack: () -> Unit,
    onOpen: (url: String) -> Unit,
) {
    PhoneColumn {
        Scaffold(
            topBar = {
                LargeTopAppBar(
                    title = { Text(stringResource(R.string.account_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.icon_arrow_back), contentDescription = stringResource(R.string.account_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Profile(profile)
                About(profile, version, onOpen)
            }
        }
    }
}

@Composable
private fun Profile(profile: AniListProfile) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(PhoneColors.surface, RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(profile.name, size = 56.dp, style = MaterialTheme.typography.titleLarge)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(profile.name, style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.account_signed_in_with),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun About(
    profile: AniListProfile,
    version: String,
    onOpen: (url: String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.account_about),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        Column(Modifier.clip(RoundedCornerShape(16.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // Only ever open AniList's own pages from a value the server sent.
            profile.siteUrl?.takeIf { it.startsWith("https://anilist.co/") }?.let { url ->
                AboutRow(
                    label = stringResource(R.string.account_profile),
                    body = stringResource(R.string.account_profile_body),
                    modifier = Modifier.clickable { onOpen(url) },
                    trailing = {
                        Icon(painterResource(R.drawable.icon_open_external), contentDescription = null, Modifier.size(24.dp))
                    },
                )
            }
            AboutRow(label = stringResource(R.string.account_version), body = version)
        }
    }
}

/** A two-line list row on surface, 4 dp corners inside the list's 16 dp ones. */
@Composable
private fun AboutRow(
    label: String,
    body: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(PhoneColors.surface, RoundedCornerShape(4.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}
