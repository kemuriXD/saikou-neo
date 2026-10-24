package ani.saikou.neo.shared.catalogue

/**
 * [animeId]'s art from AniZip. A show without fan art of its own, such as a movie or a series too new for AniZip,
 * borrows the fan art of the series AniList says it belongs to. Its logo stays its own.
 */
internal suspend fun showArt(
    aniList: AniListClient,
    aniZip: AniZipClient,
    animeId: Int,
): AniZipArt? {
    val art = aniZip.art(animeId)
    if (art?.fanArt != null) return art
    val series = (aniList.parentSeries(animeId) as? AniListResult.Success)?.value?.firstOrNull() ?: return art
    val fanArt = aniZip.art(series)?.fanArt ?: return art
    return AniZipArt(logo = art?.logo, fanArt = fanArt)
}
