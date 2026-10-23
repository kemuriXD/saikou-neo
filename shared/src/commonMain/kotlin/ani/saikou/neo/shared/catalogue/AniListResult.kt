package ani.saikou.neo.shared.catalogue

import kotlin.time.Duration

/** The outcome of an AniList call. */
public sealed interface AniListResult<out T> {
    public data class Success<T>(
        val value: T,
    ) : AniListResult<T>

    public data class Failure(
        val error: AniListError,
    ) : AniListResult<Nothing>
}

/** Why an AniList call failed. */
public sealed interface AniListError {
    /** AniList answered 429. Wait [retryAfter] before calling again. */
    public data class RateLimited(
        val retryAfter: Duration,
    ) : AniListError

    /** AniList answered with another error status, such as 404 for an unknown anime. */
    public data class Http(
        val statusCode: Int,
    ) : AniListError

    /** AniList couldn't be reached. */
    public data class Network(
        val message: String?,
    ) : AniListError

    /** AniList answered, but not with what the query asked for. */
    public data class Unexpected(
        val message: String?,
    ) : AniListError
}
