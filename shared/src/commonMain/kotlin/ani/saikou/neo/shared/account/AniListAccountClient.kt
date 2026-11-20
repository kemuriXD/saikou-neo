package ani.saikou.neo.shared.account

import ani.saikou.neo.shared.catalogue.AniListError
import ani.saikou.neo.shared.catalogue.AniListResult
import ani.saikou.neo.shared.catalogue.AnimeTitle
import ani.saikou.neo.shared.catalogue.anilist.ContinueListQuery
import ani.saikou.neo.shared.catalogue.anilist.ListEntryQuery
import ani.saikou.neo.shared.catalogue.anilist.SaveProgressMutation
import ani.saikou.neo.shared.catalogue.anilist.ViewerQuery
import ani.saikou.neo.shared.catalogue.anilist.WatchListQuery
import ani.saikou.neo.shared.catalogue.anilist.type.FuzzyDateInput
import ani.saikou.neo.shared.catalogue.anilist.type.MediaListStatus
import ani.saikou.neo.shared.catalogue.anilist.type.MediaStatus
import ani.saikou.neo.shared.catalogue.toAniListError
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.exception.ApolloHttpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

/** Which of the user's lists a show is on. */
public enum class WatchListStatus {
    WATCHING,
    PLANNING,
}

/** A show on the signed-in user's Watching or Planning list. */
public data class WatchListShow(
    /** Its AniList ID. */
    val id: Int,
    val title: AnimeTitle,
    val coverImageUrl: String?,
    val status: WatchListStatus,
    /** Whether new episodes are coming out: it's airing now, or it has a first episode date. */
    val airing: Boolean,
)

/** Where the signed-in user is in a show on their Watching or Rewatching list. */
public data class ListedProgress(
    val animeId: Int,
    /** The last episode they watched, or 0 before the first. */
    val progress: Int,
    /** When the entry last changed, in seconds since 1970, where AniList says. */
    val updatedAt: Long?,
    /** How long an episode is, where AniList knows. */
    val episodeMinutes: Int?,
    /** The AniList user whose list this came from. */
    val userId: Int? = null,
)

/** The AniList call Continue watching makes with the signed-in user's token. */
public interface ContinueListClient {
    /** The progress on the Watching and Rewatching lists of [userId], the user [token] belongs to, without private entries. */
    public suspend fun continueList(
        token: AniListToken,
        userId: Int,
    ): AniListResult<List<ListedProgress>>
}

/** Calls AniList with a signed-in user's token. These are the only calls that send the token, and only to AniList. */
public class AniListAccountClient internal constructor(
    private val apollo: ApolloClient,
    /** Where the viewer timeout is timed. Tests pass their own clock. */
    private val clock: CoroutineContext = Dispatchers.Default,
) : ProgressClient,
    ContinueListClient {
    // AniList's error body tells a refused token apart from a bad query, as both are 400s.
    public constructor() : this(
        ApolloClient
            .Builder()
            .serverUrl(ANILIST_URL)
            .httpExposeErrorBody(true)
            .build(),
    )

    /**
     * Who [token] belongs to. A token AniList refuses gives [AniListError.SignedOut]. With no answer within 10 s it
     * gives up as a network problem, so a phone on a dead connection says so instead of checking for a minute.
     */
    public suspend fun viewer(token: AniListToken): AniListResult<AniListProfile> {
        val response =
            withContext(clock) {
                withTimeoutOrNull(VIEWER_TIMEOUT) {
                    apollo
                        .query(ViewerQuery())
                        .addHttpHeader("Authorization", "Bearer ${token.accessToken}")
                        .execute()
                }
            } ?: return AniListResult.Failure(AniListError.Network("No answer from AniList"))
        response.exception?.let { return AniListResult.Failure(it.toAccountError()) }
        val viewer = response.data?.Viewer
        return if (viewer != null) {
            AniListResult.Success(AniListProfile(viewer.id, viewer.name, viewer.siteUrl, viewer.avatar?.large))
        } else {
            AniListResult.Failure(AniListError.Unexpected(response.errors?.firstOrNull()?.message))
        }
    }

    /**
     * The shows on the Watching and Planning lists of [userId], the user [token] belongs to, Watching first. A token
     * AniList refuses gives [AniListError.SignedOut].
     */
    public suspend fun watchList(
        token: AniListToken,
        userId: Int,
    ): AniListResult<List<WatchListShow>> {
        val response =
            apollo
                .query(WatchListQuery(userId))
                .addHttpHeader("Authorization", "Bearer ${token.accessToken}")
                .execute()
        response.exception?.let { return AniListResult.Failure(it.toAccountError()) }
        val collection =
            response.data?.MediaListCollection
                ?: return AniListResult.Failure(AniListError.Unexpected(response.errors?.firstOrNull()?.message))
        // A show on a custom list is also on its status list, so custom lists only repeat shows.
        val shows =
            collection.lists
                .orEmpty()
                .filterNotNull()
                .filter { it.isCustomList != true }
                .flatMap { it.entries.orEmpty().filterNotNull() }
                .mapNotNull { it.toShow() }
                .distinctBy { it.id }
                .sortedBy { it.status }
        return AniListResult.Success(shows)
    }

    override suspend fun continueList(
        token: AniListToken,
        userId: Int,
    ): AniListResult<List<ListedProgress>> {
        val response =
            apollo
                .query(ContinueListQuery(userId))
                .addHttpHeader("Authorization", "Bearer ${token.accessToken}")
                .execute()
        response.exception?.let { return AniListResult.Failure(it.toAccountError()) }
        val collection =
            response.data?.MediaListCollection
                ?: return AniListResult.Failure(AniListError.Unexpected(response.errors?.firstOrNull()?.message))
        // A show on a custom list is also on its status list, so custom lists only repeat shows.
        val shows =
            collection.lists
                .orEmpty()
                .filterNotNull()
                .filter { it.isCustomList != true }
                .flatMap { it.entries.orEmpty().filterNotNull() }
                .filter { it.private != true }
                .mapNotNull { entry ->
                    val media = entry.media ?: return@mapNotNull null
                    ListedProgress(media.id, entry.progress ?: 0, entry.updatedAt?.toLong(), media.duration, userId)
                }.distinctBy { it.animeId }
        return AniListResult.Success(shows)
    }

    override suspend fun showEntry(
        token: AniListToken,
        mediaId: Int,
    ): AniListResult<ShowEntry> {
        val response =
            apollo
                .query(ListEntryQuery(mediaId))
                .addHttpHeader("Authorization", "Bearer ${token.accessToken}")
                .execute()
        response.exception?.let { return AniListResult.Failure(it.toAccountError()) }
        val media =
            response.data?.Media
                ?: return AniListResult.Failure(AniListError.Unexpected(response.errors?.firstOrNull()?.message))
        val entry =
            media.mediaListEntry?.let { entry ->
                val status = entry.status?.toListStatus() ?: return@let null
                ListEntry(status, entry.progress ?: 0, hasStartDate = entry.startedAt?.year != null, repeat = entry.repeat ?: 0)
            }
        return AniListResult.Success(ShowEntry(media.episodes, entry, finished = media.status == MediaStatus.FINISHED))
    }

    override suspend fun saveProgress(
        token: AniListToken,
        mediaId: Int,
        update: ProgressUpdate,
    ): AniListResult<Unit> {
        val mutation =
            SaveProgressMutation(
                mediaId = mediaId,
                progress = update.progress,
                status = Optional.presentIfNotNull(update.status?.toMediaListStatus()),
                startedAt = Optional.presentIfNotNull(update.startedAt?.toFuzzyDate()),
                completedAt = Optional.presentIfNotNull(update.completedAt?.toFuzzyDate()),
                repeat = Optional.presentIfNotNull(update.repeat),
            )
        val response =
            apollo
                .mutation(mutation)
                .addHttpHeader("Authorization", "Bearer ${token.accessToken}")
                .execute()
        response.exception?.let { return AniListResult.Failure(it.toAccountError()) }
        return if (response.data?.SaveMediaListEntry != null) {
            AniListResult.Success(Unit)
        } else {
            AniListResult.Failure(AniListError.Unexpected(response.errors?.firstOrNull()?.message))
        }
    }

    private companion object {
        const val ANILIST_URL = "https://graphql.anilist.co"
        val VIEWER_TIMEOUT = 10.seconds
    }
}

/** 401 and 403 mean AniList no longer accepts the token. */
private val REFUSED = setOf(401, 403)

/** AniList also answers 400 with this message for a token it refuses. */
private const val INVALID_TOKEN = "Invalid token"

/**
 * The error for a failed call. A token AniList refuses is [AniListError.SignedOut]; any other 400, such as a query
 * AniList no longer accepts, stays an HTTP error. The error body is always closed.
 */
private fun ApolloException.toAccountError(): AniListError {
    val body = (this as? ApolloHttpException)?.body?.use { runCatching { it.readUtf8() }.getOrNull() }
    val error = toAniListError()
    if (error !is AniListError.Http) return error
    val refused =
        error.statusCode in REFUSED ||
            (error.statusCode == 400 && body?.contains(INVALID_TOKEN) == true)
    return if (refused) AniListError.SignedOut else error
}

private fun WatchListQuery.Entry.toShow(): WatchListShow? {
    val media = media ?: return null
    val listStatus =
        when (status) {
            MediaListStatus.CURRENT -> WatchListStatus.WATCHING
            MediaListStatus.PLANNING -> WatchListStatus.PLANNING
            else -> return null
        }
    val airing =
        media.status == MediaStatus.RELEASING ||
            (media.status == MediaStatus.NOT_YET_RELEASED && media.nextAiringEpisode != null)
    return WatchListShow(
        id = media.id,
        title = AnimeTitle(romaji = media.title?.romaji, english = media.title?.english, native = media.title?.native),
        coverImageUrl = media.coverImage?.extraLarge,
        status = listStatus,
        airing = airing,
    )
}

private fun MediaListStatus.toListStatus(): ListStatus? =
    when (this) {
        MediaListStatus.CURRENT -> ListStatus.WATCHING
        MediaListStatus.PLANNING -> ListStatus.PLANNING
        MediaListStatus.COMPLETED -> ListStatus.COMPLETED
        MediaListStatus.DROPPED -> ListStatus.DROPPED
        MediaListStatus.PAUSED -> ListStatus.PAUSED
        MediaListStatus.REPEATING -> ListStatus.REWATCHING
        else -> null
    }

private fun ListStatus.toMediaListStatus(): MediaListStatus =
    when (this) {
        ListStatus.WATCHING -> MediaListStatus.CURRENT
        ListStatus.PLANNING -> MediaListStatus.PLANNING
        ListStatus.COMPLETED -> MediaListStatus.COMPLETED
        ListStatus.DROPPED -> MediaListStatus.DROPPED
        ListStatus.PAUSED -> MediaListStatus.PAUSED
        ListStatus.REWATCHING -> MediaListStatus.REPEATING
    }

private fun ListDate.toFuzzyDate(): FuzzyDateInput = FuzzyDateInput(Optional.present(year), Optional.present(month), Optional.present(day))
