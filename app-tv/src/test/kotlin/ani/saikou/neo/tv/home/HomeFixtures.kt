package ani.saikou.neo.tv.home

import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.catalogue.AnimeSeason
import ani.saikou.neo.shared.catalogue.AnimeTitle
import ani.saikou.neo.shared.home.HomeRow
import ani.saikou.neo.shared.home.HomeState
import ani.saikou.neo.shared.home.RowState

/** Every row loaded with 12 cards. A card's id is its row's ordinal times 100, plus its index. */
internal val loaded =
    HomeState(HomeRow.entries.associateWith { row -> RowState.Loaded(List(12) { card(row.ordinal * 100 + it) }) })

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
