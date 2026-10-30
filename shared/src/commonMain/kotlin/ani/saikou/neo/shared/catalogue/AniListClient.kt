package ani.saikou.neo.shared.catalogue

import ani.saikou.neo.shared.catalogue.anilist.AnimeDetailsQuery
import ani.saikou.neo.shared.catalogue.anilist.AnimeRecommendationsQuery
import ani.saikou.neo.shared.catalogue.anilist.AnimeRelationsQuery
import ani.saikou.neo.shared.catalogue.anilist.AnimeSynopsesQuery
import ani.saikou.neo.shared.catalogue.anilist.BrowseAnimeQuery
import ani.saikou.neo.shared.catalogue.anilist.SearchAnimeQuery
import ani.saikou.neo.shared.catalogue.anilist.fragment.AnimeCardFields
import ani.saikou.neo.shared.catalogue.anilist.type.MediaFormat
import ani.saikou.neo.shared.catalogue.anilist.type.MediaRankType
import ani.saikou.neo.shared.catalogue.anilist.type.MediaRelation
import ani.saikou.neo.shared.catalogue.anilist.type.MediaSeason
import ani.saikou.neo.shared.catalogue.anilist.type.MediaSort
import ani.saikou.neo.shared.catalogue.anilist.type.MediaStatus
import ani.saikou.neo.shared.catalogue.anilist.type.MediaType
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

/**
 * Reads the anime catalogue from AniList. Lists and search leave out anime not released yet, such as a show before its
 * first episode or a film before it opens, unless [showUnreleased] says to show them.
 */
public class AniListClient internal constructor(
    private val apollo: ApolloClient,
    private val showUnreleased: () -> Boolean = { false },
) {
    public constructor(showUnreleased: () -> Boolean = { false }) :
        this(ApolloClient.Builder().serverUrl(ANILIST_URL).build(), showUnreleased)

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
        fetch(SearchAnimeQuery(page = page, perPage = perPage, search = text, statusNot = statusNot())) { data ->
            data.Page?.let { result ->
                AnimePage(
                    items = result.media.orEmpty().mapNotNull { it?.animeCardFields?.toCard() },
                    hasNextPage = result.pageInfo?.hasNextPage == true,
                )
            }
        }

    /** What Home's spotlight shows of each anime, by id. */
    public suspend fun spotlightFacts(ids: List<Int>): AniListResult<Map<Int, SpotlightFacts>> =
        fetch(AnimeSynopsesQuery(ids = ids)) { data ->
            data.Page
                ?.media
                ?.filterNotNull()
                ?.associate { media ->
                    media.id to
                        SpotlightFacts(
                            description = media.description,
                            nextEpisode = media.nextAiringEpisode?.let { NextEpisode(it.episode, it.airingAt.toLong()) },
                            ratedRank =
                                media.rankings
                                    ?.filterNotNull()
                                    ?.firstOrNull { it.allTime == true && it.type == MediaRankType.RATED }
                                    ?.rank,
                        )
                }
        }

    /**
     * The ids of the anime series [id] belongs to, such as a movie's TV series, then the ones it follows, such as a
     * sequel's earlier season, then the ones that follow it and other versions of it. TV series come first in each.
     */
    public suspend fun relatedSeries(id: Int): AniListResult<List<Int>> =
        fetch(AnimeRelationsQuery(id = id)) { data ->
            data.Media
                ?.relations
                ?.edges
                .orEmpty()
                .filterNotNull()
                .filter { it.relationType in seriesRelations && it.node?.type == MediaType.ANIME }
                .sortedWith(compareBy({ seriesRelations.indexOf(it.relationType) }, { it.node?.format != MediaFormat.TV }))
                .mapNotNull { it.node?.id }
        }

    /** One page of the anime AniList's users recommend for [id], best rated first. */
    public suspend fun recommendations(
        id: Int,
        page: Int = 1,
        perPage: Int = DEFAULT_PER_PAGE,
    ): AniListResult<RecommendationsPage> =
        fetch(AnimeRecommendationsQuery(id = id, page = page, perPage = perPage)) { data ->
            data.Media?.let { media ->
                RecommendationsPage(
                    show = AnimeTitle(romaji = media.title?.romaji, english = media.title?.english, native = media.title?.native),
                    page =
                        AnimePage(
                            items =
                                media.recommendations
                                    ?.nodes
                                    .orEmpty()
                                    .mapNotNull { node ->
                                        node
                                            ?.mediaRecommendation
                                            ?.takeIf { it.isAdult != true }
                                            ?.animeCardFields
                                            ?.toCard()
                                    },
                            hasNextPage = media.recommendations?.pageInfo?.hasNextPage == true,
                        ),
                )
            }
        }

    public suspend fun details(id: Int): AniListResult<AnimeDetails> =
        fetch(AnimeDetailsQuery(id = id)) { data ->
            data.Media?.let { media ->
                AnimeDetails(
                    card = media.animeCardFields.toCard(),
                    description = media.description,
                    status = media.status?.rawValue,
                    episodeMinutes = media.duration,
                    genres = media.genres.orEmpty().filterNotNull(),
                    studios =
                        media.studios
                            ?.nodes
                            .orEmpty()
                            .mapNotNull { it?.name },
                    nextEpisode = media.nextAiringEpisode?.let { NextEpisode(it.episode, it.airingAt.toLong()) },
                    related =
                        media.relations
                            ?.edges
                            .orEmpty()
                            .mapNotNull { it?.toRelated() }
                            .sortedBy { it.relation },
                    recommendations =
                        media.recommendations
                            ?.nodes
                            .orEmpty()
                            .mapNotNull { node ->
                                node
                                    ?.mediaRecommendation
                                    ?.takeIf { it.isAdult != true }
                                    ?.animeCardFields
                                    ?.toCard()
                            },
                    moreRecommendations = media.recommendations?.pageInfo?.hasNextPage == true,
                    malId = media.idMal,
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
                statusNot = statusNot(),
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

    private fun statusNot(): Optional<MediaStatus?> =
        if (showUnreleased()) Optional.absent() else Optional.present(MediaStatus.NOT_YET_RELEASED)

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
        val seriesRelations = listOf(MediaRelation.PARENT, MediaRelation.PREQUEL, MediaRelation.SEQUEL, MediaRelation.ALTERNATIVE)
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

/** An anime in the franchise, or null for one Details doesn't list, such as the manga or a music video. */
private fun AnimeDetailsQuery.Edge.toRelated(): RelatedAnime? {
    val node = node ?: return null
    if (node.type != MediaType.ANIME || node.isAdult == true) return null
    val relation = Relation.entries.firstOrNull { it.name == relationType?.rawValue } ?: return null
    return RelatedAnime(node.animeCardFields.toCard(), relation)
}

private fun AnimeCardFields.toCard(): AnimeCard =
    AnimeCard(
        id = id,
        title = AnimeTitle(romaji = title?.romaji, english = title?.english, native = title?.native),
        coverImageUrl = coverImage?.extraLarge,
        coverColor = coverImage?.color,
        bannerImageUrl = bannerImage,
        format = format?.rawValue,
        episodes = episodes,
        averageScore = averageScore,
        season = AnimeSeason.entries.firstOrNull { it.name == season?.rawValue },
        seasonYear = seasonYear,
        // AniList's CountryCode scalar arrives as a plain string.
        country = countryOfOrigin as? String,
    )
