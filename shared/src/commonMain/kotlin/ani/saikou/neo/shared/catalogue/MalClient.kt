package ani.saikou.neo.shared.catalogue

import com.apollographql.apollo.api.json.JsonReader
import com.apollographql.apollo.network.http.DefaultHttpEngine
import com.apollographql.apollo.network.http.HttpEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.milliseconds

/** An episode MAL marks as one viewers may want to skip. */
public enum class EpisodeKind { Filler, Recap }

/**
 * Reads MyAnimeList's scores, and which episodes are filler or recaps, through Tenrai, a free mirror of MAL's catalogue
 * that answers in Jikan's shape. It needs no key, and allows 120 requests a minute and 4 a second from each TV.
 */
public class MalClient internal constructor(
    private val engine: HttpEngine,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    public constructor() : this(DefaultHttpEngine(TIMEOUT_MILLIS))

    // The last few shows' kinds, oldest first. A map keeps the order things were put in.
    private val kept = mutableMapOf<Int, Map<Int, EpisodeKind>>()
    private val keptLock = Mutex()

    /** The show's MAL score out of 100, or null when nobody has scored it or Tenrai can't answer. */
    public suspend fun score(malId: Int): Int? = engine.getJson("$TENRAI_URL/anime/$malId", io) { score() }

    /**
     * The show's filler and recap episodes by number. An episode MAL marks as both is a recap. Tenrai lists 100 episodes
     * a page, so a long show takes a page a request, a little apart. If a later page fails, the episodes before it are
     * still given, but not kept, so the next visit asks again. Null when Tenrai can't answer at all.
     */
    public suspend fun episodeKinds(malId: Int): Map<Int, EpisodeKind>? {
        // Asking for a show again moves it to the end, so the one left alone longest goes first.
        keptLock.withLock { kept.remove(malId)?.also { kept[malId] = it } }?.let { return it }
        val kinds = mutableMapOf<Int, EpisodeKind>()
        for (page in 1..MAX_PAGES) {
            if (page > 1) delay(PAGE_GAP)
            val found = engine.getJson("$TENRAI_URL/anime/$malId/episodes?page=$page", io) { episodePage() }
            if (found == null) return if (page == 1) null else kinds
            kinds += found.kinds
            if (!found.hasNext) break
        }
        keptLock.withLock {
            kept[malId] = kinds
            if (kept.size > KEPT_SHOWS) kept.remove(kept.keys.first())
        }
        return kinds
    }

    private companion object {
        const val TENRAI_URL = "https://api.tenrai.org/v1"
        const val TIMEOUT_MILLIS = 15_000L

        // Detective Conan, the longest, has 13 pages. This stops a list that never ends.
        const val MAX_PAGES = 30
        val PAGE_GAP = 300.milliseconds
        const val KEPT_SHOWS = 20
    }
}

/** Reads `data.score`, and skips everything else. */
private fun JsonReader.score(): Int? {
    var score: Int? = null
    beginObject()
    while (hasNext()) {
        if (nextName() == "data" && peek() == JsonReader.Token.BEGIN_OBJECT) {
            score = readFields()["score"]?.let(::outOfTen)
        } else {
            skipValue()
        }
    }
    endObject()
    return score
}

private class EpisodePage(
    val kinds: Map<Int, EpisodeKind>,
    val hasNext: Boolean,
)

/** Reads one page of `data`, keeping only the numbers of filler and recap episodes, and `pagination.has_next_page`. */
private fun JsonReader.episodePage(): EpisodePage {
    val kinds = mutableMapOf<Int, EpisodeKind>()
    var hasNext = false
    beginObject()
    while (hasNext()) {
        val name = nextName()
        when {
            name == "data" && peek() == JsonReader.Token.BEGIN_ARRAY -> {
                beginArray()
                while (hasNext()) episode()?.let { (number, kind) -> kinds[number] = kind }
                endArray()
            }

            name == "pagination" && peek() == JsonReader.Token.BEGIN_OBJECT -> {
                hasNext = flags()["has_next_page"] == true
            }

            else -> {
                skipValue()
            }
        }
    }
    endObject()
    return EpisodePage(kinds, hasNext)
}

/** One episode's number and kind, or null for an ordinary one. */
private fun JsonReader.episode(): Pair<Int, EpisodeKind>? {
    if (peek() != JsonReader.Token.BEGIN_OBJECT) {
        skipValue()
        return null
    }
    var number: Int? = null
    var filler = false
    var recap = false
    beginObject()
    while (hasNext()) {
        val name = nextName()
        when {
            name == "mal_id" && (peek() == JsonReader.Token.NUMBER || peek() == JsonReader.Token.LONG) -> number = nextInt()
            name == "filler" && peek() == JsonReader.Token.BOOLEAN -> filler = nextBoolean()
            name == "recap" && peek() == JsonReader.Token.BOOLEAN -> recap = nextBoolean()
            else -> skipValue()
        }
    }
    endObject()
    val kind =
        when {
            recap -> EpisodeKind.Recap
            filler -> EpisodeKind.Filler
            else -> null
        }
    return if (number != null && kind != null) number to kind else null
}

/** An object's true and false fields. */
private fun JsonReader.flags(): Map<String, Boolean> {
    val flags = mutableMapOf<String, Boolean>()
    beginObject()
    while (hasNext()) {
        val name = nextName()
        if (peek() == JsonReader.Token.BOOLEAN) flags[name] = nextBoolean() else skipValue()
    }
    endObject()
    return flags
}
