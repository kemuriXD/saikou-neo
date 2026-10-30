package ani.saikou.neo.shared.catalogue

/** What a search can be narrowed to. TV takes in TV shorts; Airing is anything with episodes still coming out. */
public enum class SearchFilter {
    ALL,
    TV,
    MOVIES,
    AIRING,
}
