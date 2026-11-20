package ani.saikou.neo.shared.home

import ani.saikou.neo.shared.catalogue.AiredCard
import ani.saikou.neo.shared.catalogue.AniListClient
import ani.saikou.neo.shared.catalogue.AniListResult
import ani.saikou.neo.shared.catalogue.AnimeCard

// AniList sends at most this many cards at once.
private const val CARDS_PER_REQUEST = 50

/**
 * AniList's cards for the shows in a watch history. Each show is asked for once, so watching again doesn't ask anew. A
 * show AniList didn't send, such as an adult one, is kept as null so it isn't asked for again either.
 */
internal class WatchedCards(
    private val client: AniListClient,
) {
    private val cards = mutableMapOf<Int, AiredCard?>()

    /**
     * The cards of the [limit] shows in [shows] watched most recently, newest first, each with where to carry on. A show
     * with nothing left that's out is left out. If AniList can't send the cards it hasn't yet, the cards it has; it fails
     * only without any.
     */
    suspend fun unfinished(
        shows: Map<Int, WatchProgress>,
        limit: Int = Int.MAX_VALUE,
    ): AniListResult<List<Pair<AnimeCard, WatchProgress>>> {
        val recent =
            shows.entries
                .sortedByDescending { it.value.savedAt ?: Long.MIN_VALUE }
                .take(limit)
                .map { it.key }
        var failure: AniListResult.Failure? = null
        recent.filterNot { it in cards }.chunked(CARDS_PER_REQUEST).forEach { ids ->
            when (val sent = client.cards(ids)) {
                is AniListResult.Success -> {
                    val byId = sent.value.associateBy { it.card.id }
                    ids.forEach { cards[it] = byId[it] }
                }

                is AniListResult.Failure -> {
                    failure = sent
                }
            }
        }
        failure?.let { if (recent.none { id -> id in cards }) return it }
        return AniListResult.Success(
            recent.mapNotNull { id ->
                val sent = cards[id] ?: return@mapNotNull null
                shows.getValue(id).upNext(sent.aired)?.let { sent.card to it }
            },
        )
    }
}
