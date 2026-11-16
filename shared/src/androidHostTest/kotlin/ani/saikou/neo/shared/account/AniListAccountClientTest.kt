package ani.saikou.neo.shared.account

import ani.saikou.neo.shared.catalogue.AniListError
import ani.saikou.neo.shared.catalogue.AniListResult
import ani.saikou.neo.shared.catalogue.OfflineEngine
import ani.saikou.neo.shared.catalogue.RecordedEngine
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.http.HttpRequest
import com.apollographql.apollo.api.http.HttpResponse
import com.apollographql.apollo.network.http.HttpEngine
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AniListAccountClientTest {
    private val token = AniListToken("the-token", 2_000_000_000)

    private fun clientFor(engine: HttpEngine) =
        AniListAccountClient(
            ApolloClient
                .Builder()
                .serverUrl("https://anilist.test/graphql")
                .httpEngine(engine)
                .build(),
        )

    @Test
    fun viewerReadsTheSignedInUser() =
        runTest {
            val result = clientFor(RecordedEngine("viewer")).viewer(token)

            assertEquals(
                AniListResult.Success(
                    AniListProfile(
                        5412345,
                        "Kemuri",
                        "https://anilist.co/user/5412345",
                        "https://s4.anilist.co/file/anilistcdn/user/avatar/large/b5412345.png",
                    ),
                ),
                result,
            )
        }

    @Test
    fun viewerSendsTheTokenAsABearerHeader() =
        runTest {
            val engine = HeaderEngine(RecordedEngine("viewer"))

            clientFor(engine).viewer(token)

            assertEquals(listOf("Bearer the-token"), engine.authorizations)
        }

    @Test
    fun aTokenAniListRefusesIsAnHttpError() =
        runTest {
            val result = clientFor(RecordedEngine("viewer-invalid-token", status = 400)).viewer(token)

            assertEquals(AniListResult.Failure(AniListError.Http(400)), result)
        }

    @Test
    fun noConnectionIsANetworkError() =
        runTest {
            val result = clientFor(OfflineEngine).viewer(token)

            assertIs<AniListError.Network>(assertIs<AniListResult.Failure>(result).error)
        }
}

/** Notes each request's Authorization header, then answers it with [engine]. */
private class HeaderEngine(
    private val engine: HttpEngine,
) : HttpEngine {
    val authorizations = mutableListOf<String>()

    override suspend fun execute(request: HttpRequest): HttpResponse {
        request.headers.filter { it.name.equals("Authorization", ignoreCase = true) }.forEach { authorizations += it.value }
        return engine.execute(request)
    }

    override fun close() {}
}
