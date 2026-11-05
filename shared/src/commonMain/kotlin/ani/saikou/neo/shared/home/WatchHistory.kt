package ani.saikou.neo.shared.home

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okio.Path.Companion.toPath
import kotlin.time.Duration.Companion.milliseconds

private const val PREFIX = "progress_"
private const val SEPARATOR = ";"
private const val FIELDS = 5

/** How far this TV got in each show, keyed by anime id. */
public interface WatchHistory {
    public val shows: Flow<Map<Int, WatchProgress>>

    /** Keeps [progress] for [animeId], in place of what was kept before. It's saved even once the caller has gone. */
    public fun save(
        animeId: Int,
        progress: WatchProgress,
    )
}

/**
 * Keeps [WatchHistory] in a file, so it's there after the app restarts. Saves are written in [scope] in the order
 * they're made. Only one store may have a file open at a time, so the app makes one and shares it.
 */
public class WatchHistoryStore private constructor(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) : WatchHistory {
    private val saves = Channel<Pair<Int, WatchProgress>>(Channel.UNLIMITED)

    init {
        scope.launch {
            for ((animeId, progress) in saves) {
                dataStore.edit { it[stringPreferencesKey("$PREFIX$animeId")] = progress.encode() }
            }
        }
    }

    override val shows: Flow<Map<Int, WatchProgress>> =
        dataStore.data.map { saved ->
            saved
                .asMap()
                .entries
                .mapNotNull { (key, value) ->
                    val animeId = key.name.removePrefix(PREFIX).toIntOrNull() ?: return@mapNotNull null
                    (value as? String)?.decode()?.let { animeId to it }
                }.toMap()
        }

    override fun save(
        animeId: Int,
        progress: WatchProgress,
    ) {
        saves.trySend(animeId to progress)
    }

    public companion object {
        /** A store kept in the file at [path], which reads and writes in [scope] until that ends. */
        public fun at(
            path: String,
            scope: CoroutineScope,
        ): WatchHistoryStore = WatchHistoryStore(PreferenceDataStoreFactory.createWithPath(scope = scope) { path.toPath() }, scope)
    }
}

// The still goes last, as its address may hold the separator.
private fun WatchProgress.encode() =
    listOf(episode, watched.inWholeMilliseconds, length.inWholeMilliseconds, savedAt ?: "", still ?: "").joinToString(SEPARATOR)

private fun String.decode(): WatchProgress? {
    val fields = split(SEPARATOR, limit = FIELDS)
    if (fields.size != FIELDS) return null
    return WatchProgress(
        episode = fields[0].toIntOrNull() ?: return null,
        watched = (fields[1].toLongOrNull() ?: return null).milliseconds,
        length = (fields[2].toLongOrNull() ?: return null).milliseconds,
        still = fields[4].ifEmpty { null },
        savedAt = fields[3].toLongOrNull(),
    )
}
