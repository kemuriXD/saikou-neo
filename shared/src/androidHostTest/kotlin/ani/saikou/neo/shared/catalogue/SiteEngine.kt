package ani.saikou.neo.shared.catalogue

import com.apollographql.apollo.api.http.HttpRequest
import com.apollographql.apollo.api.http.HttpResponse
import com.apollographql.apollo.network.http.HttpEngine
import okio.Buffer

/** Answers each request with the recording [fixture] picks from `resources/[folder]`, and keeps the urls asked for. */
internal class SiteEngine(
    private val folder: String,
    private val status: Int = 200,
    private val fixture: (url: String) -> String,
) : HttpEngine {
    val urls = mutableListOf<String>()

    override suspend fun execute(request: HttpRequest): HttpResponse {
        urls += request.url
        val name = fixture(request.url)
        val body = checkNotNull(javaClass.getResource("/$folder/$name.json")) { "No fixture $name" }.readText()
        return HttpResponse
            .Builder(status)
            .body(Buffer().writeUtf8(body))
            .build()
    }

    override fun close() {}
}
