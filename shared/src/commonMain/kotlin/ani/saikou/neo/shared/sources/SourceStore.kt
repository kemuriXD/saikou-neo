package ani.saikou.neo.shared.sources

import kotlinx.coroutines.flow.StateFlow

/**
 * A source as Settings > Sources lists it. A source with a [problem] doesn't load. [icon] is the path of its logo, a
 * PNG the app saved from its APK, or null when it has none (ADR 0028).
 */
public data class InstalledSource(
    val id: String,
    val name: String,
    val version: String,
    val problem: SourceProblem? = null,
    val icon: String? = null,
)

/** Why a source can't be used. */
public enum class SourceProblem {
    /** It needs a newer version of the app. */
    BUILT_FOR_NEWER_APP,

    /** It needs a newer version of itself. */
    BUILT_FOR_OLDER_APP,

    /** Its code doesn't fit the app, though it says it's built for this version. */
    BROKEN,
}

/** Where installed sources are kept and loaded. The TV app reads and loads APKs; tests keep them in memory. */
public interface SourceStore {
    /** The installed sources, or null until they've been read. */
    public val installed: StateFlow<List<InstalledSource>?>

    /** Why a source built for API version [builtFor] can't be used, or null when it can. */
    public fun problemFor(builtFor: Int): SourceProblem?

    /**
     * Installs the source in [apk] and returns it. A source with a problem is returned with it and not installed. Null
     * when [apk] isn't a source at all, or isn't the source with [id] when one is given.
     */
    public suspend fun install(
        apk: ByteArray,
        id: String? = null,
    ): InstalledSource?

    /** Uninstalls the source with [id], so it isn't used again. */
    public suspend fun remove(id: String)
}
