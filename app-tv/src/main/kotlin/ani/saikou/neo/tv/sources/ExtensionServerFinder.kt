package ani.saikou.neo.tv.sources

import ani.saikou.neo.extension.api.Anime
import ani.saikou.neo.extension.api.AnimeIds
import ani.saikou.neo.extension.api.AnimeTitles
import ani.saikou.neo.extension.api.Edition
import ani.saikou.neo.extension.api.Episode
import ani.saikou.neo.extension.api.Season
import ani.saikou.neo.extension.api.Server
import ani.saikou.neo.extension.api.SourceException
import ani.saikou.neo.extension.api.SourceExtension
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.catalogue.AnimeSeason
import ani.saikou.neo.shared.playback.FailReason
import ani.saikou.neo.shared.playback.FoundServer
import ani.saikou.neo.shared.playback.ServerEdition
import ani.saikou.neo.shared.playback.ServerFinder
import ani.saikou.neo.shared.playback.SourceFailure
import ani.saikou.neo.shared.sources.InstalledSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration

// The most servers a source can list, and the longest name shown, so a source can't flood the page.
private const val MAX_SERVERS = 100
private const val MAX_NAME = 60

/**
 * Asks the source [load] gives for an installed source, off the main thread, for the episode the catalogue picked. A
 * source's code can throw anything. Its typed errors keep their reason, and any other exception means the site is down.
 * One that won't load, or throws an error such as a linkage error when it doesn't fit the app after all, won't start.
 * A server id the source repeats is dropped, since the list tells servers apart by id.
 */
class ExtensionServerFinder(
    private val load: suspend (InstalledSource) -> SourceExtension?,
) : ServerFinder {
    override suspend fun servers(
        source: InstalledSource,
        show: AnimeDetails,
        episode: Int,
    ): List<FoundServer> =
        withContext(Dispatchers.IO) {
            try {
                val extension = load(source) ?: throw SourceFailure(FailReason.WontStart)
                extension
                    .servers(Episode(show.toAnime(), episode))
                    .distinctBy { it.id }
                    .take(MAX_SERVERS)
                    .map { it.toFound() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SourceFailure) {
                throw e
            } catch (e: Throwable) {
                throw SourceFailure(e.reason(), e)
            }
        }
}

private fun Throwable.reason(): FailReason =
    when (this) {
        is SourceException.NotFound -> FailReason.NotFound

        is SourceException.Blocked -> FailReason.Blocked

        is SourceException.RateLimited -> FailReason.RateLimited(retryAfter?.let { (it.inWholeMilliseconds + 999) / 1000 })

        // An expired link only comes up while playing, so here it's a site that isn't working.
        is Exception -> FailReason.SourceDown

        else -> FailReason.WontStart
    }

private fun AnimeDetails.toAnime(): Anime {
    val title = card.title
    return Anime(
        ids = AnimeIds(anilist = card.id, mal = malId, anidb = null, kitsu = null),
        titles =
            AnimeTitles(
                romaji = title.romaji ?: title.english ?: title.native.orEmpty(),
                english = title.english,
                native = title.native,
                synonyms = emptyList(),
            ),
        season = card.season?.toSeason(),
        year = card.seasonYear,
    )
}

private fun AnimeSeason.toSeason() =
    when (this) {
        AnimeSeason.WINTER -> Season.Winter
        AnimeSeason.SPRING -> Season.Spring
        AnimeSeason.SUMMER -> Season.Summer
        AnimeSeason.FALL -> Season.Fall
    }

private fun Server.toFound() =
    FoundServer(
        id = id,
        name = name.take(MAX_NAME),
        edition =
            when (edition) {
                Edition.Sub -> ServerEdition.SUB
                Edition.Dub -> ServerEdition.DUB
                Edition.HardSub -> ServerEdition.HARD_SUB
            },
        runtimeMinutes = runtime?.toMinutes(),
    )

// To the nearest minute, as the server list shows it.
private fun Duration.toMinutes() = ((inWholeSeconds + 30) / 60).toInt()
