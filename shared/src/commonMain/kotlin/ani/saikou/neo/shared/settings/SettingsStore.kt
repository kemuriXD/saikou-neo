package ani.saikou.neo.shared.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath

private val UnairedAnime = booleanPreferencesKey("show_unaired_anime")
private val UpcomingEpisodes = booleanPreferencesKey("show_upcoming_episodes")
private val SlowTv = booleanPreferencesKey("slow_tv")
private val SubOrDubKey = stringPreferencesKey("sub_or_dub")
private val SubtitleSizeKey = stringPreferencesKey("subtitle_size")
private val SubtitleColourKey = stringPreferencesKey("subtitle_colour")
private val SubtitleEdgeKey = stringPreferencesKey("subtitle_edge")
private val SubtitlePositionKey = stringPreferencesKey("subtitle_position")

/**
 * Keeps [Settings] in a file, so they're there after the app restarts. Only one store may have a file open at a time,
 * so the app makes one and shares it.
 */
public class SettingsStore private constructor(
    private val dataStore: DataStore<Preferences>,
) {
    /** The settings now, and again each time one changes. A setting never changed reads as its default. */
    public val settings: Flow<Settings> =
        dataStore.data.map { it.toSettings() }

    /** Saves the settings [change] makes to the ones now. */
    public suspend fun update(change: (Settings) -> Settings) {
        dataStore.edit { saved ->
            val changed = change(saved.toSettings())
            saved[UnairedAnime] = changed.showUnairedAnime
            saved[UpcomingEpisodes] = changed.showUpcomingEpisodes
            saved[SlowTv] = changed.slowTv
            saved[SubOrDubKey] = changed.subOrDub.name
            saved[SubtitleSizeKey] = changed.subtitleStyle.size.name
            saved[SubtitleColourKey] = changed.subtitleStyle.colour.name
            saved[SubtitleEdgeKey] = changed.subtitleStyle.edge.name
            saved[SubtitlePositionKey] = changed.subtitleStyle.position.name
        }
    }

    public companion object {
        /** A store kept in the file at [path], which reads and writes in [scope] until that ends. */
        public fun at(
            path: String,
            scope: CoroutineScope,
        ): SettingsStore = SettingsStore(PreferenceDataStoreFactory.createWithPath(scope = scope) { path.toPath() })
    }
}

private fun Preferences.toSettings(): Settings {
    val defaults = Settings()
    return Settings(
        showUnairedAnime = this[UnairedAnime] ?: defaults.showUnairedAnime,
        showUpcomingEpisodes = this[UpcomingEpisodes] ?: defaults.showUpcomingEpisodes,
        slowTv = this[SlowTv] ?: defaults.slowTv,
        subOrDub = saved(SubOrDub.entries, SubOrDubKey) ?: defaults.subOrDub,
        subtitleStyle =
            defaults.subtitleStyle.let {
                SubtitleStyle(
                    size = saved(SubtitleSize.entries, SubtitleSizeKey) ?: it.size,
                    colour = saved(SubtitleColour.entries, SubtitleColourKey) ?: it.colour,
                    edge = saved(SubtitleEdge.entries, SubtitleEdgeKey) ?: it.edge,
                    position = saved(SubtitlePosition.entries, SubtitlePositionKey) ?: it.position,
                )
            },
    )
}

// A name from an older or newer version that isn't a choice here reads as the default.
private fun <T : Enum<T>> Preferences.saved(
    choices: List<T>,
    key: Preferences.Key<String>,
): T? = choices.firstOrNull { it.name == this[key] }
