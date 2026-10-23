package ani.saikou.neo.shared.catalogue

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.http.HttpHeader
import com.apollographql.apollo.api.http.HttpRequest
import com.apollographql.apollo.api.http.HttpResponse
import com.apollographql.apollo.exception.ApolloNetworkException
import com.apollographql.apollo.network.http.HttpEngine
import okio.Buffer

/** Answers every request with a recorded AniList response from `resources/anilist`. */
internal class RecordedEngine(
    private val fixture: String,
    private val status: Int = 200,
    private val headers: List<HttpHeader> = emptyList(),
) : HttpEngine {
    val requestBodies = mutableListOf<String>()

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

internal fun clientFor(engine: HttpEngine): AniListClient =
    AniListClient(
        ApolloClient
            .Builder()
            .serverUrl("https://anilist.test/graphql")
            .httpEngine(engine)
            .build(),
    )

/** Fails every request the way Apollo reports a dropped connection. */
internal object OfflineEngine : HttpEngine {
    override suspend fun execute(request: HttpRequest): HttpResponse = throw ApolloNetworkException("Unable to resolve host")

    override fun close() {}
}
