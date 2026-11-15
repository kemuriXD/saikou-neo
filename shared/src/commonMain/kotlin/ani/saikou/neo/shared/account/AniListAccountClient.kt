package ani.saikou.neo.shared.account

import ani.saikou.neo.shared.catalogue.AniListError
import ani.saikou.neo.shared.catalogue.AniListResult
import ani.saikou.neo.shared.catalogue.anilist.ViewerQuery
import ani.saikou.neo.shared.catalogue.toAniListError
import com.apollographql.apollo.ApolloClient

/** Asks AniList who a token belongs to. It's the only call that sends the token, and only to AniList. */
public class AniListAccountClient internal constructor(
    private val apollo: ApolloClient,
) {
    public constructor() : this(ApolloClient.Builder().serverUrl(ANILIST_URL).build())

    public suspend fun viewer(token: AniListToken): AniListResult<AniListProfile> {
        val response =
            apollo
                .query(ViewerQuery())
                .addHttpHeader("Authorization", "Bearer ${token.accessToken}")
                .execute()
        response.exception?.let { return AniListResult.Failure(it.toAniListError()) }
        val viewer = response.data?.Viewer
        return if (viewer != null) {
            AniListResult.Success(AniListProfile(viewer.id, viewer.name, viewer.siteUrl))
        } else {
            AniListResult.Failure(AniListError.Unexpected(response.errors?.firstOrNull()?.message))
        }
    }

    private companion object {
        const val ANILIST_URL = "https://graphql.anilist.co"
    }
}
