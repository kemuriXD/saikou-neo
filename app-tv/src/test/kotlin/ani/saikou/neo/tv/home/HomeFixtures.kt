package ani.saikou.neo.tv.home

import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.catalogue.AnimeSeason
import ani.saikou.neo.shared.catalogue.AnimeTitle
import ani.saikou.neo.shared.home.HomeRow
import ani.saikou.neo.shared.home.HomeState
import ani.saikou.neo.shared.home.Logo
import ani.saikou.neo.shared.home.RowState
import ani.saikou.neo.shared.home.Spotlight
import ani.saikou.neo.shared.home.WatchProgress
import kotlin.time.Duration.Companion.minutes

private val aniListRows = HomeRow.entries - HomeRow.CONTINUE_WATCHING
private val rows =
    mapOf(HomeRow.CONTINUE_WATCHING to RowState.Loaded(emptyList())) +
        aniListRows.associateWith { row -> RowState.Loaded(List(12) { card(aniListRows.indexOf(row) * 100 + it) }) }

/**
 * Nothing to continue, and every AniList row loaded with 12 cards. A card's id is its row's place among them times 100,
 * plus its index.
 * The spotlight features the first six trending shows, none with a logo, and AniList has answered for their details.
 */
internal val loaded =
    HomeState(
        rows = rows,
        spotlight =
            rows.getValue(HomeRow.TRENDING).items.take(6).let { shows ->
                Spotlight(
                    shows,
                    HomeRow.TRENDING,
                    detailsLoaded = true,
                    logos =
                        shows.associate {
                            it.id to
                                Logo.None
                        },
                )
            },
    )

/** [loaded], with three shows to continue: ids 900 to 902, on episodes 7 to 9, each 10 of 24 minutes in. */
internal val continuing =
    loaded.copy(
        rows = loaded.rows + (HomeRow.CONTINUE_WATCHING to RowState.Loaded(List(3) { card(900 + it) })),
        progress = List(3) { 900 + it to WatchProgress(7 + it, 10.minutes, 24.minutes, still = null) }.toMap(),
    )

internal fun card(id: Int) =
    AnimeCard(
        id = id,
        title = AnimeTitle(romaji = null, english = "Show $id", native = null),
        coverImageUrl = null,
        coverColor = "#e4a128",
        bannerImageUrl = null,
        format = "TV",
        episodes = 12,
        averageScore = null,
        season = AnimeSeason.FALL,
        seasonYear = 2026,
    )
