package ani.saikou.neo.shared.catalogue

// Narrower than Tensei Kizoku's 0.8:1 or wider than any real logo, and a logo can't be read at the logo's size.
private const val MIN_LOGO_ASPECT = 0.75f
private const val MAX_LOGO_ASPECT = 8f

// fanart.tv's HD logos are 800 wide and its old ones 400. TMDB's are often bigger, but some are small and blurry.
private const val MIN_LOGO_WIDTH = 600

private val noLanguage = setOf(null, "00", "xx")

/**
 * The best sharp title logo in [logos]: English first, then one with no words in any one language, then one in the
 * show's [ownLanguage]. A logo in any other language, such as a Russian one for a Korean show, is left out, so the title
 * shows in words.
 */
internal fun pickLogo(
    logos: List<ArtImage>,
    ownLanguage: String? = null,
): ArtImage? {
    val usable =
        logos.filter {
            it.height > 0 && it.width.toFloat() / it.height in MIN_LOGO_ASPECT..MAX_LOGO_ASPECT && (it.vector || it.width >= MIN_LOGO_WIDTH)
        }
    return usable.firstOrNull { it.language == "en" }
        ?: usable.firstOrNull { it.language in noLanguage }
        ?: ownLanguage?.let { language -> usable.firstOrNull { it.language == language } }
}

/** The language a show made in [AnimeCard.country] is written in, as TMDB and fanart.tv name it. */
internal val AnimeCard.language: String?
    get() =
        when (country) {
            "JP" -> "ja"
            "KR" -> "ko"
            "CN", "TW" -> "zh"
            else -> null
        }

private const val FULL_HD_WIDTH = 1920
private const val FULL_HD_HEIGHT = 1080

/** The most liked backdrop in [backdrops] that's full HD or more, with no words on it. */
internal fun pickBackdrop(backdrops: List<ArtImage>): ArtImage? =
    backdrops.firstOrNull { it.width >= FULL_HD_WIDTH && it.height >= FULL_HD_HEIGHT && it.language in noLanguage }

/**
 * AniZip's [fanArt] when it's one of TheTVDB's newer backgrounds, which were all 1920 × 1080 in a survey of 209 shows.
 * AniZip doesn't say how big its art is, and TheTVDB's old fan art is often 1280 × 720. Only https art is used, as
 * Android 7 and 8 still allow plain http.
 */
internal fun fullHdFanArt(fanArt: String?): String? = fanArt?.takeIf { it.startsWith("https://") && "/backgrounds/" in it }
