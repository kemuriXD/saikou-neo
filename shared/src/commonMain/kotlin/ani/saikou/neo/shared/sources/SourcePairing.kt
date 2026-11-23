package ani.saikou.neo.shared.sources

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A source on the TV, as it tells the phone: its id, version, and whether a list is saved with it (ADR 0046). */
public data class TvSource(
    val id: String,
    val version: String,
    val hasList: Boolean,
)

/** What the TV tells a phone first (ADR 0042): its sources and the extension API versions it runs (ADR 0023). */
public data class TvSources(
    val installed: List<TvSource>,
    val api: IntRange,
)

/** What the phone sends the TV. Only these entries, never files. */
public sealed interface PickedSources {
    /** The ticked entries of the list at [list]. A short code is sent as the link it led to. */
    public data class FromList(
        val list: String,
        val sources: List<ListedSource>,
    ) : PickedSources

    /** A link that isn't a list, taken as one APK. It's installed with no list saved. */
    public data class OneApk(
        val apk: String,
    ) : PickedSources
}

/** How many sources [this] has. */
public val PickedSources.size: Int
    get() =
        when (this) {
            is PickedSources.FromList -> sources.size
            is PickedSources.OneApk -> 1
        }

/** What a link or code typed on the phone leads to, for a TV that sent [TvSources]. */
public sealed interface LinkForTv {
    public data class Failed(
        val error: AddError,
    ) : LinkForTv

    /**
     * A list at [list], each source with what it is on the TV, as the TV would show it. Nothing is ticked. It may hold
     * nothing that can be picked, so the phone can show why (4.9).
     */
    public data class Listed(
        val list: String,
        val choices: List<Choice>,
    ) : LinkForTv

    /** A link that isn't a list, shown as one row named after [apk]. The phone doesn't download it. */
    public data class OneApk(
        val apk: String,
    ) : LinkForTv
}

/**
 * Reads [text], a link or short code, for the TV that sent [tv], with the TV's rules (ADR 0025, ADR 0042). A list's
 * sources are marked from what the TV has and the API versions it runs.
 */
public suspend fun readLinkForTv(
    text: String,
    tv: TvSources,
    codes: ShortCodes,
    downloads: SourceDownloads,
    work: CoroutineDispatcher = Dispatchers.Default,
): LinkForTv {
    val url = linkFor(text, codes) ?: return LinkForTv.Failed(AddError.NO_SUCH_CODE)
    if (!isAllowedSourceLink(url)) return LinkForTv.Failed(AddError.NOT_SECURE)
    val start = downloads.fetchStart(url) ?: return LinkForTv.Failed(AddError.NOT_A_SOURCE)
    if (!isSourceList(start)) return LinkForTv.OneApk(url)
    val listed = withContext(work) { readSourceList(start) } ?: return LinkForTv.Failed(AddError.BROKEN_LIST)
    val installed = tv.installed.associateBy { it.id }
    return LinkForTv.Listed(
        url,
        listed.choices {
            choiceStatus(it, installed[it.id]?.version, installed[it.id]?.hasList == true, tv.api::problemFor)
        },
    )
}

/** What to send for the ticked sources that can be picked, without their logos. Null when none are ticked. */
public fun LinkForTv.Listed.picked(): PickedSources.FromList? =
    choices
        .filter { it.ticked && it.status.canPick }
        .map { it.source.copy(icon = null) }
        .takeIf { it.isNotEmpty() }
        ?.let { PickedSources.FromList(list, it) }

/** What to send for this APK link. */
public fun LinkForTv.OneApk.picked(): PickedSources.OneApk = PickedSources.OneApk(apk)

/** What [this] is to a TV: its sources, with [api] the extension API versions the TV runs. */
public fun List<InstalledSource>.toTvSources(api: IntRange): TvSources =
    TvSources(map { TvSource(it.id, it.version, it.list != null) }, api)

/** Why a source built for API version [builtFor] can't be used by an app that runs this range, or null when it can. */
internal fun IntRange.problemFor(builtFor: Int): SourceProblem? =
    when {
        builtFor > last -> SourceProblem.BUILT_FOR_NEWER_APP
        builtFor < first -> SourceProblem.BUILT_FOR_OLDER_APP
        else -> null
    }
