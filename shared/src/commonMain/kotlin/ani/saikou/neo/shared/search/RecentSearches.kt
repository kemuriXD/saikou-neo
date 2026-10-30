package ani.saikou.neo.shared.search

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath

private const val KEPT = 5

// The keyboard can't type a line break, so one keeps the searches apart.
private const val SEPARATOR = "\n"
private val Searches = stringPreferencesKey("recent_searches")

/** The last searches the viewer opened a result from, newest first. */
public interface RecentSearches {
    public val searches: Flow<List<String>>

    /** Puts [query] first. The same search typed differently replaces the old one, and only five are kept. */
    public suspend fun add(query: String)
}

/** [query] first, then the others without it, up to five. */
internal fun List<String>.withNewest(query: String): List<String> =
    (listOf(query) + filterNot { it.equals(query, ignoreCase = true) }).take(KEPT)

/**
 * Keeps [RecentSearches] in a file, so they're there after the app restarts. Only one store may have a file open at a
 * time, so the app makes one and shares it.
 */
public class RecentSearchesStore private constructor(
    private val dataStore: DataStore<Preferences>,
) : RecentSearches {
    override val searches: Flow<List<String>> =
        dataStore.data.map { it.searches() }

    override suspend fun add(query: String) {
        dataStore.edit { saved -> saved[Searches] = saved.searches().withNewest(query).joinToString(SEPARATOR) }
    }

    public companion object {
        /** A store kept in the file at [path], which reads and writes in [scope] until that ends. */
        public fun at(
            path: String,
            scope: CoroutineScope,
        ): RecentSearchesStore = RecentSearchesStore(PreferenceDataStoreFactory.createWithPath(scope = scope) { path.toPath() })
    }
}

private fun Preferences.searches(): List<String> = this[Searches]?.split(SEPARATOR).orEmpty()
