package ani.saikou.neo.tv.home

import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.catalogue.AnimeSeason
import ani.saikou.neo.shared.catalogue.AnimeTitle
import ani.saikou.neo.shared.home.HomeRow
import ani.saikou.neo.shared.home.HomeState
import ani.saikou.neo.shared.home.Logo
import ani.saikou.neo.shared.home.RowState
import ani.saikou.neo.shared.home.Spotlight

private val rows = HomeRow.entries.associateWith { row -> RowState.Loaded(List(12) { card(row.ordinal * 100 + it) }) }

/**
 * Every row loaded with 12 cards. A card's id is its row's ordinal times 100, plus its index.
 * The spotlight features the first six trending shows, none with a logo.
 */
internal val loaded =
    HomeState(
        rows = rows,
        spotlight =
            rows.getValue(HomeRow.TRENDING).items.take(6).let { shows ->
                Spotlight(
                    shows,
                    logos =
                        shows.associate {
                            it.id to
                                Logo.None
                        },
                )
            },
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
