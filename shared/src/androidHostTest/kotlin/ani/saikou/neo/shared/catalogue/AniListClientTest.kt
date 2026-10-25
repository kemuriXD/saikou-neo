package ani.saikou.neo.shared.catalogue

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AniListClientTest {
    @Test
    fun trendingMapsTheRecordedPage() =
        runTest {
            val engine = RecordedEngine("browse-trending")

            val page = clientFor(engine).trending(page = 1, perPage = 2).success()

            assertTrue(page.hasNextPage)
            assertEquals(2, page.items.size)
            assertEquals(
                AnimeCard(
                    id = 195516,
                    title =
                        AnimeTitle(
                            romaji = "Kusuriya no Hitorigoto 3rd Season",
                            english = "The Apothecary Diaries Season 3",
                            native = "薬屋のひとりごと 第3期",
                        ),
                    coverImageUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx195516-MJpUZlOberqH.jpg",
                    coverColor = "#e4a128",
                    bannerImageUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/banner/195516-B5M0cPaQbQXI.jpg",
                    format = "TV",
                    episodes = 12,
                    averageScore = 85,
                    season = AnimeSeason.FALL,
                    seasonYear = 2026,
                ),
                page.items.first(),
            )
            assertTrue("\"sort\":[\"TRENDING_DESC\"]" in engine.requestBodies.single())
        }

    @Test
    fun popularSortsByPopularityAcrossAllSeasons() =
        runTest {
            val engine = RecordedEngine("browse-trending")

            clientFor(engine).popular().success()

            val body = engine.requestBodies.single()
            assertTrue("\"sort\":[\"POPULARITY_DESC\"]" in body)
            assertFalse("\"season\"" in body)
        }

    @Test
    fun seasonAsksForThatSeasonAndYear() =
        runTest {
            val engine = RecordedEngine("browse-trending")

            clientFor(engine).season(AnimeSeason.FALL, 2026).success()

            val body = engine.requestBodies.single()
            assertTrue("\"sort\":[\"POPULARITY_DESC\"]" in body)
            assertTrue("\"season\":\"FALL\"" in body)
            assertTrue("\"seasonYear\":2026" in body)
        }

    @Test
    fun searchSendsTheTextAndMapsMatches() =
        runTest {
            val engine = RecordedEngine("search")

            val page = clientFor(engine).search("frieren", perPage = 2).success()

            assertEquals(listOf(154587, 209939), page.items.map { it.id })
            assertTrue("\"search\":\"frieren\"" in engine.requestBodies.single())
        }

    // The art picks a logo in the show's own language when there's no English one.
    @Test
    fun aCardKnowsWhereTheShowIsFrom() =
        runTest {
            val page = clientFor(RecordedEngine("search-country")).search("Psychic Princess", perPage = 1).success()

            assertEquals("CN", page.items.single().country)
        }

    @Test
    fun synopsesAskForTheIdsAndMapEachDescription() =
        runTest {
            val engine = RecordedEngine("synopses")

            val synopses = clientFor(engine).synopses(listOf(195516, 195539)).success()

            assertEquals(setOf(195516, 195539), synopses.keys)
            assertTrue(synopses.getValue(195516)!!.startsWith("In the aftermath of the Shi Clan"))
            assertTrue("\"ids\":[195516,195539]" in engine.requestBodies.single())
        }

    // Beerus lists the Battle of Gods movie first, but the TV series makes the better backdrop.
    @Test
    fun relatedSeriesPutParentsThenPrequelsWithTvSeriesFirst() =
        runTest {
            val engine = RecordedEngine("relations")

            val series = clientFor(engine).relatedSeries(206814).success()

            assertEquals(listOf(21175, 14837, 813, 20635, 170083), series)
            assertTrue("\"id\":206814" in engine.requestBodies.single())
        }

    // The Ramparts of Ice Season 2 follows season 1, with no parent.
    @Test
    fun aSequelFollowsItsEarlierSeason() =
        runTest { assertEquals(listOf(186497), clientFor(RecordedEngine("relations-prequel")).relatedSeries(213805).success()) }

    // Attack on Titan Season 2 follows season 1 and is followed by season 3. The earlier season comes first.
    @Test
    fun anEarlierSeasonComesBeforeALaterOne() =
        runTest { assertEquals(listOf(16498, 99147), clientFor(RecordedEngine("relations-sequel")).relatedSeries(20958).success()) }

    // Sakamoto Days has no parent or earlier season, so its second season is next.
    @Test
    fun aFirstSeasonFallsBackToItsSequel() =
        runTest { assertEquals(listOf(184237), clientFor(RecordedEngine("relations-first-season")).relatedSeries(177709).success()) }

    // THE ONE PIECE is a new version of ONE PIECE, with no parent or earlier season.
    @Test
    fun aNewVersionFallsBackToTheOriginal() =
        runTest { assertEquals(listOf(21), clientFor(RecordedEngine("relations-alternative")).relatedSeries(171630).success()) }

    @Test
    fun aShowWithoutRelatedSeriesHasNone() =
        runTest { assertEquals(emptyList(), clientFor(RecordedEngine("relations-none")).relatedSeries(21519).success()) }

    @Test
    fun detailsMapsTheRecordedAnime() =
        runTest {
            val details = clientFor(RecordedEngine("details")).details(195516).success()

            assertEquals(195516, details.card.id)
            assertEquals("RELEASING", details.status)
            assertEquals(24, details.episodeMinutes)
            assertEquals(listOf("Drama", "Mystery"), details.genres)
            assertEquals(listOf("OLM"), details.studios)
            assertEquals(NextEpisode(number = 5, airingAtEpochSeconds = 1793368800), details.nextEpisode)
            assertEquals(
                "https://s4.anilist.co/file/anilistcdn/media/anime/banner/195516-B5M0cPaQbQXI.jpg",
                details.card.bannerImageUrl,
            )
            assertTrue(details.description.orEmpty().isNotBlank())
        }
}

internal fun <T> AniListResult<T>.success(): T = assertIs<AniListResult.Success<T>>(this).value
