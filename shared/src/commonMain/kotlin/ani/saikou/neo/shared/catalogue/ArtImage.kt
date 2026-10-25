package ani.saikou.neo.shared.catalogue

/**
 * One logo or backdrop as an artwork API describes it, so the app can choose before downloading anything. Lists of
 * these come best liked first.
 */
public data class ArtImage(
    val url: String,
    val width: Int,
    val height: Int,
    /** The language of any words in it, such as `en`. Null, `00` or `xx` when it has none in one language. */
    val language: String?,
    /** Drawn as shapes rather than pixels, so it's sharp at any size. */
    val vector: Boolean = false,
)
