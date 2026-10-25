package ani.saikou.neo.shared.catalogue

import ani.saikou.neo.shared.home.Logo

/** A show's backdrop, null without any, and its title logo. */
internal data class ShowArt(
    val fanArt: String?,
    val logo: Logo,
)

/**
 * [animeId]'s art from AniZip and fanart.tv. A show without fan art of its own, such as a movie or a series too new
 * for AniZip, gets its background from fanart.tv, or else borrows the fan art of the series AniList says it belongs
 * to, follows or comes from. Its logo stays its own: AniZip's, with fanart.tv's to fall back on.
 */
internal suspend fun showArt(
    aniList: AniListClient,
    aniZip: AniZipClient,
    fanartTv: FanartTvClient,
    animeId: Int,
): ShowArt {
    val art = aniZip.art(animeId)
    // fanart.tv is asked even when AniZip has a logo, since some of AniZip's turn out to be pictures of a character.
    // Its one answer holds both the background and the logo.
    val fanart = art?.let { fanartTv.art(it) }
    val (aniZipLogo, fanartTvLogo) = art?.logo to fanart?.logo
    val logo = if (aniZipLogo == null && fanartTvLogo == null) Logo.None else Logo.Image(aniZipLogo, fanartTvLogo)
    return ShowArt(fanArt = art?.fanArt ?: fanart?.background ?: seriesFanArt(aniList, aniZip, animeId), logo = logo)
}

private suspend fun seriesFanArt(
    aniList: AniListClient,
    aniZip: AniZipClient,
    animeId: Int,
): String? {
    val series = (aniList.relatedSeries(animeId) as? AniListResult.Success)?.value?.firstOrNull() ?: return null
    return aniZip.art(series)?.fanArt
}
