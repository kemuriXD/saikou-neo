package ani.saikou.neo.shared.sources

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** What a check for updates found for an installed source (ADR 0046). */
public sealed interface UpdateStatus {
    /** It has no saved list, as it came from a single APK or was installed before lists were kept, so it isn't checked. */
    public data object NotChecked : UpdateStatus

    /** Its list hasn't been read yet, couldn't be read, or doesn't name it any more. */
    public data object Unknown : UpdateStatus

    public data object UpToDate : UpdateStatus

    /** Its list has the newer [version], built for this app. */
    public data class Available(
        val version: String,
    ) : UpdateStatus

    /** Its list has the newer [version], but built for a newer app, so it isn't offered. */
    public data class NeedsNewerApp(
        val version: String,
    ) : UpdateStatus
}

/** Reads the lists sources were installed from. */
internal class SourceUpdates(
    private val downloads: SourceDownloads,
    // Where a list is read, off the main thread.
    private val work: CoroutineDispatcher,
) {
    /**
     * The sources each list at [links] names, by link. Each list is fetched once. One that can't be fetched or read is
     * left out, without an error, so its sources stay unknown until the next check.
     */
    suspend fun lists(links: Set<String>): Map<String, List<ListedSource>> =
        links
            .mapNotNull { link ->
                val file = downloads.fetch(link)?.takeIf(::isSourceList) ?: return@mapNotNull null
                withContext(work) { readSourceList(file) }?.let { link to it }
            }.toMap()
}

/**
 * What [lists] say about [source]. Only a newer version than the installed one counts, and only one built for this app
 * is offered. [problemFor] says why a version built for an API can't be used, as [SourceStore.problemFor] does.
 */
internal fun updateStatus(
    source: InstalledSource,
    lists: Map<String, List<ListedSource>>,
    problemFor: (Int) -> SourceProblem?,
): UpdateStatus {
    val link = source.list ?: return UpdateStatus.NotChecked
    val listed = lists[link]?.firstOrNull { it.id == source.id } ?: return UpdateStatus.Unknown
    if (compareVersions(listed.version, source.version) <= 0) return UpdateStatus.UpToDate
    return when (problemFor(listed.api)) {
        null -> UpdateStatus.Available(listed.version)

        SourceProblem.BUILT_FOR_NEWER_APP -> UpdateStatus.NeedsNewerApp(listed.version)

        // A newer version built for an older app is no use either, so there's nothing to offer.
        else -> UpdateStatus.UpToDate
    }
}
