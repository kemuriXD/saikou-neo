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
    fun parentSeriesPutTvSeriesFirstAndLeaveOutOtherRelations() =
        runTest {
            val engine = RecordedEngine("parents")

            val parents = clientFor(engine).parentSeries(206814).success()

            assertEquals(listOf(21175, 14837), parents)
            assertTrue("\"id\":206814" in engine.requestBodies.single())
        }

    @Test
    fun aShowWithoutAParentHasNone() =
        runTest { assertEquals(emptyList(), clientFor(RecordedEngine("parents-none")).parentSeries(177709).success()) }

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
