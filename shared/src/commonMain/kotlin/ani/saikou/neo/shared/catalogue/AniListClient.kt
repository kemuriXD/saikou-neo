package ani.saikou.neo.shared.catalogue

import ani.saikou.neo.shared.catalogue.anilist.AnimeDetailsQuery
import ani.saikou.neo.shared.catalogue.anilist.BrowseAnimeQuery
import ani.saikou.neo.shared.catalogue.anilist.SearchAnimeQuery
import ani.saikou.neo.shared.catalogue.anilist.fragment.AnimeCardFields
import ani.saikou.neo.shared.catalogue.anilist.type.MediaSeason
import ani.saikou.neo.shared.catalogue.anilist.type.MediaSort
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.api.Query
import com.apollographql.apollo.api.http.HttpHeader
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.exception.ApolloHttpException
import com.apollographql.apollo.exception.ApolloNetworkException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Reads the anime catalogue from AniList. */
public class AniListClient internal constructor(
    private val apollo: ApolloClient,
) {
    public constructor() : this(ApolloClient.Builder().serverUrl(ANILIST_URL).build())

    public suspend fun trending(
        page: Int = 1,
        perPage: Int = DEFAULT_PER_PAGE,
    ): AniListResult<AnimePage> = browse(page, perPage, MediaSort.TRENDING_DESC)

    public suspend fun popular(
        page: Int = 1,
        perPage: Int = DEFAULT_PER_PAGE,
    ): AniListResult<AnimePage> = browse(page, perPage, MediaSort.POPULARITY_DESC)

    /** The most popular anime of one season. Use [AnimeSeason.forMonth] for this season. */
    public suspend fun season(
        season: AnimeSeason,
        year: Int,
        page: Int = 1,
        perPage: Int = DEFAULT_PER_PAGE,
    ): AniListResult<AnimePage> = browse(page, perPage, MediaSort.POPULARITY_DESC, MediaSeason.safeValueOf(season.name), year)

    public suspend fun search(
        text: String,
        page: Int = 1,
        perPage: Int = DEFAULT_PER_PAGE,
    ): AniListResult<AnimePage> =
        fetch(SearchAnimeQuery(page = page, perPage = perPage, search = text)) { data ->
            data.Page?.let { result ->
                AnimePage(
                    items = result.media.orEmpty().mapNotNull { it?.animeCardFields?.toCard() },
                    hasNextPage = result.pageInfo?.hasNextPage == true,
                )
            }
        }

    public suspend fun details(id: Int): AniListResult<AnimeDetails> =
        fetch(AnimeDetailsQuery(id = id)) { data ->
            data.Media?.let { media ->
                AnimeDetails(
                    card = media.animeCardFields.toCard(),
                    description = media.description,
                    bannerImageUrl = media.bannerImage,
                    status = media.status?.rawValue,
                    episodeMinutes = media.duration,
                    genres = media.genres.orEmpty().filterNotNull(),
                    studios =
                        media.studios
                            ?.nodes
                            .orEmpty()
                            .mapNotNull { it?.name },
                    nextEpisode = media.nextAiringEpisode?.let { NextEpisode(it.episode, it.airingAt.toLong()) },
                )
            }
        }

    private suspend fun browse(
        page: Int,
        perPage: Int,
        sort: MediaSort,
        season: MediaSeason? = null,
        seasonYear: Int? = null,
    ): AniListResult<AnimePage> {
        val query =
            BrowseAnimeQuery(
                page = page,
                perPage = perPage,
                sort = Optional.present(listOf(sort)),
                season = Optional.presentIfNotNull(season),
                seasonYear = Optional.presentIfNotNull(seasonYear),
            )
        return fetch(query) { data ->
            data.Page?.let { result ->
                AnimePage(
                    items = result.media.orEmpty().mapNotNull { it?.animeCardFields?.toCard() },
                    hasNextPage = result.pageInfo?.hasNextPage == true,
                )
            }
        }
    }

    /** Runs [query] and maps its data, or explains why there's nothing to map. */
    private suspend fun <D : Query.Data, T : Any> fetch(
        query: Query<D>,
        map: (D) -> T?,
    ): AniListResult<T> {
        val response = apollo.query(query).execute()
        response.exception?.let { return AniListResult.Failure(it.toAniListError()) }
        val value = response.data?.let(map)
        return if (value != null) {
            AniListResult.Success(value)
        } else {
            AniListResult.Failure(AniListError.Unexpected(response.errors?.firstOrNull()?.message))
        }
    }

    private companion object {
        const val ANILIST_URL = "https://graphql.anilist.co"
        const val DEFAULT_PER_PAGE = 20
    }
}

private fun ApolloException.toAniListError(): AniListError =
    when (this) {
        is ApolloHttpException -> {
            if (statusCode == 429) AniListError.RateLimited(retryAfter(headers)) else AniListError.Http(statusCode)
        }

        is ApolloNetworkException -> {
            AniListError.Network(message)
        }

        else -> {
            AniListError.Unexpected(message)
        }
    }

/** AniList sends Retry-After in seconds. Its docs say a rate-limited client waits a minute. */
private fun retryAfter(headers: List<HttpHeader>): Duration =
    headers
        .firstOrNull { it.name.equals("Retry-After", ignoreCase = true) }
        ?.value
        ?.trim()
        ?.toLongOrNull()
        ?.seconds ?: 1.minutes

private fun AnimeCardFields.toCard(): AnimeCard =
    AnimeCard(
        id = id,
        title = AnimeTitle(romaji = title?.romaji, english = title?.english, native = title?.native),
        coverImageUrl = coverImage?.extraLarge,
        coverColor = coverImage?.color,
        format = format?.rawValue,
        episodes = episodes,
        averageScore = averageScore,
        season = AnimeSeason.entries.firstOrNull { it.name == season?.rawValue },
        seasonYear = seasonYear,
    )
