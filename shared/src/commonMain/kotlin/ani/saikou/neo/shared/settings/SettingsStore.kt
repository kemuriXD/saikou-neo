package ani.saikou.neo.shared.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import ani.saikou.neo.shared.account.ShowAnswers
import ani.saikou.neo.shared.catalogue.ShowFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath

private val UnairedAnime = booleanPreferencesKey("show_unaired_anime")
private val UpcomingEpisodes = booleanPreferencesKey("show_upcoming_episodes")
private val SlowTv = booleanPreferencesKey("slow_tv")
private val SubOrDubKey = stringPreferencesKey("sub_or_dub")
private val ScheduleRaw = booleanPreferencesKey("schedule_raw_episodes")
private val ScheduleDubs = booleanPreferencesKey("schedule_dubs")
private val ScheduleFormats = stringPreferencesKey("schedule_formats")
private val ScheduleChinese = booleanPreferencesKey("schedule_chinese_animation")
private val ScheduleShort = booleanPreferencesKey("schedule_short_episodes")
private val UpdateAniListKey = stringPreferencesKey("update_anilist")
private val PrivateMode = booleanPreferencesKey("private_mode")
private val StillWatchingKey = stringPreferencesKey("still_watching")

// Each "user:show=yes" or "user:show=no", one per show and user.
private val ShowAnswersKey = stringSetPreferencesKey("anilist_show_answers")

/**
 * Keeps [Settings] in a file, so they're there after the app restarts. Only one store may have a file open at a time,
 * so the app makes one and shares it.
 */
public class SettingsStore private constructor(
    private val dataStore: DataStore<Preferences>,
) : ShowAnswers {
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
            saved[ScheduleRaw] = changed.schedule.rawEpisodes
            saved[ScheduleDubs] = changed.schedule.dubs
            saved[ScheduleFormats] = changed.schedule.formats.joinToString(",") { it.name }
            saved[ScheduleChinese] = changed.schedule.chineseAnimation
            saved[ScheduleShort] = changed.schedule.shortEpisodes
            saved[UpdateAniListKey] = changed.updateAniList.name
            saved[PrivateMode] = changed.privateMode
            saved[StillWatchingKey] = changed.stillWatching.name
        }
    }

    override suspend fun answer(
        userId: Int,
        animeId: Int,
    ): Boolean? {
        val key = "$userId:$animeId="
        val kept = dataStore.data.first()[ShowAnswersKey]?.firstOrNull { it.startsWith(key) } ?: return null
        return kept.removePrefix(key) == YES
    }

    override suspend fun save(
        userId: Int,
        animeId: Int,
        update: Boolean,
    ) {
        val key = "$userId:$animeId="
        dataStore.edit { saved ->
            val others = saved[ShowAnswersKey].orEmpty().filterNot { it.startsWith(key) }
            saved[ShowAnswersKey] = others.toSet() + (key + if (update) YES else NO)
        }
    }

    /** The subtitle settings now, and again each time one changes. They're kept apart from [settings]. */
    public val subtitles: Flow<SubtitleSettings> =
        dataStore.data.map { it.toSubtitleSettings() }

    /** Saves the subtitle settings [change] makes to the ones now. Changing them to [SubtitleSettings] resets them. */
    public suspend fun updateSubtitles(change: (SubtitleSettings) -> SubtitleSettings) {
        dataStore.edit { saved -> saved.putSubtitleSettings(change(saved.toSubtitleSettings())) }
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
        subOrDub = SubOrDub.entries.firstOrNull { it.name == this[SubOrDubKey] } ?: defaults.subOrDub,
        schedule = toScheduleSettings(),
        updateAniList = UpdateAniList.entries.firstOrNull { it.name == this[UpdateAniListKey] } ?: defaults.updateAniList,
        privateMode = this[PrivateMode] ?: defaults.privateMode,
        stillWatching = StillWatching.entries.firstOrNull { it.name == this[StillWatchingKey] } ?: defaults.stillWatching,
    )
}

private const val YES = "yes"
private const val NO = "no"

private fun Preferences.toScheduleSettings(): ScheduleSettings {
    val defaults = ScheduleSettings()
    val formats = this[ScheduleFormats]?.split(",")?.mapNotNull { name -> ShowFormat.entries.firstOrNull { it.name == name } }?.toSet()
    return ScheduleSettings(
        rawEpisodes = this[ScheduleRaw] ?: defaults.rawEpisodes,
        dubs = this[ScheduleDubs] ?: defaults.dubs,
        // At least one format stays on, so none read means the default.
        formats = formats?.takeIf { it.isNotEmpty() } ?: defaults.formats,
        chineseAnimation = this[ScheduleChinese] ?: defaults.chineseAnimation,
        shortEpisodes = this[ScheduleShort] ?: defaults.shortEpisodes,
    )
}
