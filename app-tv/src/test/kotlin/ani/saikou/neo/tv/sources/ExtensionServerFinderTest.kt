package ani.saikou.neo.tv.sources

import ani.saikou.neo.extension.api.Edition
import ani.saikou.neo.extension.api.Episode
import ani.saikou.neo.extension.api.Season
import ani.saikou.neo.extension.api.Server
import ani.saikou.neo.extension.api.SourceException
import ani.saikou.neo.extension.api.SourceExtension
import ani.saikou.neo.extension.api.Stream
import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.catalogue.AnimeSeason
import ani.saikou.neo.shared.catalogue.AnimeTitle
import ani.saikou.neo.shared.playback.FailReason
import ani.saikou.neo.shared.playback.FoundServer
import ani.saikou.neo.shared.playback.ServerEdition
import ani.saikou.neo.shared.playback.SourceFailure
import ani.saikou.neo.shared.sources.InstalledSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val demo = InstalledSource("com.example.demo", "Demo source", "1.0.0")

private val show =
    AnimeDetails(
        card =
            AnimeCard(
                1535,
                AnimeTitle(null, "Death Note", "デスノート"),
                null,
                null,
                null,
                "TV",
                37,
                null,
                AnimeSeason.FALL,
                2006,
            ),
        description = null,
        status = null,
        episodeMinutes = 23,
        genres = emptyList(),
        studios = emptyList(),
        nextEpisode = null,
        malId = 1535,
    )

/** Hands out [servers], remembering the episode it was asked for, or throws [failure]. */
private class FakeSource(
    private val servers: List<Server> = emptyList(),
    private val failure: Throwable? = null,
) : SourceExtension {
    var asked: Episode? = null

    override suspend fun servers(episode: Episode): List<Server> {
        asked = episode
        failure?.let { throw it }
        return servers
    }

    override suspend fun stream(server: Server): Stream = error("Not used")
}

class ExtensionServerFinderTest {
    private fun finder(source: SourceExtension?) = ExtensionServerFinder { if (it == demo) source else null }

    @Test
    fun theSourceIsAskedForTheCataloguesEpisode() {
        val source = FakeSource()

        runBlocking { finder(source).servers(demo, show, 8) }

        val anime = source.asked!!.anime
        assertEquals(8, source.asked!!.number)
        assertEquals(1535, anime.ids.anilist)
        assertEquals(1535, anime.ids.mal)
        assertEquals("Death Note", anime.titles.english)
        assertEquals("デスノート", anime.titles.native)
        // AniList always has a romaji title, but the catalogue can lack it, so the next title stands in.
        assertEquals("Death Note", anime.titles.romaji)
        assertEquals(Season.Fall, anime.season)
        assertEquals(2006, anime.year)
    }

    @Test
    fun serversKeepTheirNameEditionAndRuntime() {
        val source =
            FakeSource(
                listOf(
                    Server("a", "Demo, Sub", Edition.Sub, 24.minutes),
                    Server("b", "Demo, Dub", Edition.Dub, 90.seconds),
                    Server("c", "Burned in", Edition.HardSub, null),
                ),
            )

        val servers = runBlocking { finder(source).servers(demo, show, 8) }

        assertEquals(
            listOf(
                FoundServer("a", "Demo, Sub", ServerEdition.SUB, 24),
                FoundServer("b", "Demo, Dub", ServerEdition.DUB, 2),
                FoundServer("c", "Burned in", ServerEdition.HARD_SUB, null),
            ),
            servers,
        )
    }

    @Test
    fun aRepeatedServerIdIsDropped() {
        val source = FakeSource(listOf(Server("a", "First", Edition.Sub, null), Server("a", "Again", Edition.Dub, null)))

        val servers = runBlocking { finder(source).servers(demo, show, 8) }

        assertEquals(listOf("First"), servers.map { it.name })
    }

    @Test
    fun longNamesAndListsAreCut() {
        val source = FakeSource(List(150) { Server("$it", "x".repeat(500), Edition.Sub, null) })

        val servers = runBlocking { finder(source).servers(demo, show, 8) }

        assertEquals(100, servers.size)
        assertEquals(60, servers.first().name.length)
    }

    /** Why the source failed, from what it threw. */
    private fun reasonFor(failure: Throwable?) =
        assertThrows(SourceFailure::class.java) { runBlocking { finder(failure?.let { FakeSource(failure = it) }).servers(demo, show, 8) } }
            .reason

    @Test
    fun aSourceThatWontLoadWontStart() {
        assertEquals(FailReason.WontStart, reasonFor(null))
    }

    @Test
    fun anErrorOrOtherThrowableMeansItWontStart() {
        assertEquals(FailReason.WontStart, reasonFor(NoSuchMethodError("Built for another app")))
        assertEquals(FailReason.WontStart, reasonFor(object : Throwable("Neither an error nor an exception") {}))
    }

    @Test
    fun eachTypedErrorKeepsItsReason() {
        assertEquals(FailReason.NotFound, reasonFor(SourceException.NotFound()))
        assertEquals(FailReason.Blocked, reasonFor(SourceException.Blocked()))
        assertEquals(FailReason.SourceDown, reasonFor(SourceException.SourceDown()))
        assertEquals(FailReason.RateLimited(null), reasonFor(SourceException.RateLimited()))
    }

    @Test
    fun aRateLimitsWaitIsInWholeSecondsRoundedUp() {
        assertEquals(FailReason.RateLimited(31), reasonFor(SourceException.RateLimited(30.5.seconds)))
    }

    @Test
    fun anEndlessWaitIsAnUnknownOneAndAVeryLongOneIsCappedAtADay() {
        assertEquals(FailReason.RateLimited(null), reasonFor(SourceException.RateLimited(Duration.INFINITE)))
        assertEquals(FailReason.RateLimited(86_400), reasonFor(SourceException.RateLimited(1000.days)))
    }

    @Test
    fun anExpiredLinkOrAnyOtherExceptionMeansTheSiteIsDown() {
        assertEquals(FailReason.SourceDown, reasonFor(SourceException.LinkExpired()))
        assertEquals(FailReason.SourceDown, reasonFor(IllegalStateException("Broken")))
    }

    @Test
    fun cancellingStillCancels() {
        val source = FakeSource(failure = CancellationException("Left the page"))

        assertThrows(CancellationException::class.java) { runBlocking { finder(source).servers(demo, show, 8) } }
    }
}
