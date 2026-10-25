package ani.saikou.neo.shared.catalogue

// Narrower than Tensei Kizoku's 0.8:1 or wider than any real logo, and a logo can't be read at the logo's size.
private const val MIN_LOGO_ASPECT = 0.75f
private const val MAX_LOGO_ASPECT = 8f

// fanart.tv's HD logos are 800 wide and its old ones 400. TMDB's are often bigger, but some are small and blurry.
private const val MIN_LOGO_WIDTH = 600

private val noLanguage = setOf(null, "00", "xx")

/** The best sharp title logo in [logos]: English first, then one with no words in any one language, then the most liked. */
internal fun pickLogo(logos: List<ArtImage>): ArtImage? {
    val usable =
        logos.filter {
            it.height > 0 && it.width.toFloat() / it.height in MIN_LOGO_ASPECT..MAX_LOGO_ASPECT && (it.vector || it.width >= MIN_LOGO_WIDTH)
        }
    return usable.firstOrNull { it.language == "en" } ?: usable.firstOrNull { it.language in noLanguage } ?: usable.firstOrNull()
}
