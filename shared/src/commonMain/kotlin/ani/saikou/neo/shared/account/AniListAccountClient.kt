package ani.saikou.neo.shared.account

import ani.saikou.neo.shared.catalogue.AniListError
import ani.saikou.neo.shared.catalogue.AniListResult
import ani.saikou.neo.shared.catalogue.AnimeTitle
import ani.saikou.neo.shared.catalogue.anilist.ViewerQuery
import ani.saikou.neo.shared.catalogue.anilist.WatchListQuery
import ani.saikou.neo.shared.catalogue.anilist.type.MediaListStatus
import ani.saikou.neo.shared.catalogue.anilist.type.MediaStatus
import ani.saikou.neo.shared.catalogue.toAniListError
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.exception.ApolloHttpException

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

/** Calls AniList with a signed-in user's token. These are the only calls that send the token, and only to AniList. */
public class AniListAccountClient internal constructor(
    private val apollo: ApolloClient,
) {
    // AniList's error body tells a refused token apart from a bad query, as both are 400s.
    public constructor() : this(
        ApolloClient
            .Builder()
            .serverUrl(ANILIST_URL)
            .httpExposeErrorBody(true)
            .build(),
    )

    /** Who [token] belongs to. */
    public suspend fun viewer(token: AniListToken): AniListResult<AniListProfile> {
        val response =
            apollo
                .query(ViewerQuery())
                .addHttpHeader("Authorization", "Bearer ${token.accessToken}")
                .execute()
        response.exception?.let { return AniListResult.Failure(it.toAccountError(refusedIsSignedOut = false)) }
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
        response.exception?.let { return AniListResult.Failure(it.toAccountError(refusedIsSignedOut = true)) }
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

    private companion object {
        const val ANILIST_URL = "https://graphql.anilist.co"
    }
}

/** 401 and 403 mean AniList no longer accepts the token. */
private val REFUSED = setOf(401, 403)

/** AniList also answers 400 with this message for a token it refuses. */
private const val INVALID_TOKEN = "Invalid token"

/**
 * The error for a failed call. With [refusedIsSignedOut], a token AniList refuses is [AniListError.SignedOut]; any other
 * 400, such as a query AniList no longer accepts, stays an HTTP error. The error body is always closed.
 */
private fun ApolloException.toAccountError(refusedIsSignedOut: Boolean): AniListError {
    val body = (this as? ApolloHttpException)?.body?.use { runCatching { it.readUtf8() }.getOrNull() }
    val error = toAniListError()
    if (!refusedIsSignedOut || error !is AniListError.Http) return error
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
