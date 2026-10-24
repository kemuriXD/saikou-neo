package ani.saikou.neo.shared.catalogue

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.http.HttpHeader
import com.apollographql.apollo.api.http.HttpRequest
import com.apollographql.apollo.api.http.HttpResponse
import com.apollographql.apollo.exception.ApolloNetworkException
import com.apollographql.apollo.network.http.HttpEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import okio.Buffer
import java.util.Collections

/** Answers every request with a recorded AniList response from `resources/anilist`. */
internal class RecordedEngine(
    private val fixture: String,
    private val status: Int = 200,
    private val headers: List<HttpHeader> = emptyList(),
) : HttpEngine {
    // Apollo sends requests from several threads at once.
    val requestBodies: MutableList<String> = Collections.synchronizedList(mutableListOf())

    override suspend fun execute(request: HttpRequest): HttpResponse {
        requestBodies += Buffer().also { request.body!!.writeTo(it) }.readUtf8()
        val body = checkNotNull(javaClass.getResource("/anilist/$fixture.json")) { "No fixture $fixture" }.readText()
        return HttpResponse
            .Builder(status)
            .addHeaders(headers)
            .body(Buffer().writeUtf8(body))
            .build()
    }

    override fun close() {}
}

/** Answers each request with the recording named for its query, or [default] when none is. */
internal class QueryEngine(
    default: String,
    private val byQuery: Map<String, String>,
) : HttpEngine {
    private val default = RecordedEngine(default)
    private val engines = byQuery.mapValues { RecordedEngine(it.value) }
    val requestBodies: MutableList<String> = Collections.synchronizedList(mutableListOf())

    override suspend fun execute(request: HttpRequest): HttpResponse {
        val body = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
        requestBodies += body
        val engine = engines.entries.firstOrNull { it.key in body }?.value ?: default
        return engine.execute(request)
    }

    override fun close() {}
}

/**
 * An AniList client that asks [engine]. ViewModel tests pass their test dispatcher as [dispatcher], so no request is
 * still on its way once a test ends.
 */
internal fun clientFor(
    engine: HttpEngine,
    dispatcher: CoroutineDispatcher? = null,
): AniListClient =
    AniListClient(
        ApolloClient
            .Builder()
            .serverUrl("https://anilist.test/graphql")
            .httpEngine(engine)
            .dispatcher(dispatcher)
            .build(),
    )

/** Fails every request the way Apollo reports a dropped connection. */
internal object OfflineEngine : HttpEngine {
    override suspend fun execute(request: HttpRequest): HttpResponse = throw ApolloNetworkException("Unable to resolve host")

    override fun close() {}
}

/** Never answers, as if the request were still on its way. */
internal object SilentEngine : HttpEngine {
    override suspend fun execute(request: HttpRequest): HttpResponse = awaitCancellation()

    override fun close() {}
}
