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

/** The value mapped by [transform], or the same failure. */
internal inline fun <T, R> AniListResult<T>.map(transform: (T) -> R): AniListResult<R> =
    when (this) {
        is AniListResult.Success -> AniListResult.Success(transform(value))
        is AniListResult.Failure -> this
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

/** What went wrong, as the viewer is told it. Each has its own message. */
public enum class LoadProblem {
    /** The TV isn't online. */
    NO_NETWORK,

    /** AniList asked for fewer requests for a while. */
    RATE_LIMITED,

    /** Anything else, such as AniList being down. */
    OTHER,
}

internal val AniListError.problem: LoadProblem
    get() =
        when (this) {
            is AniListError.Network -> LoadProblem.NO_NETWORK
            is AniListError.RateLimited -> LoadProblem.RATE_LIMITED
            is AniListError.Http, is AniListError.Unexpected -> LoadProblem.OTHER
        }
