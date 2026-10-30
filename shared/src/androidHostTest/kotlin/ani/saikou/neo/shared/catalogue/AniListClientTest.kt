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

    // A show is left out until its first episode is out, and a film until it's released.
    @Test
    fun rowsAndSearchLeaveOutAnimeNotYetReleased() =
        runTest {
            val engine = RecordedEngine("browse-trending")
            val client = clientFor(engine)

            client.trending().success()
            client.popular().success()
            client.season(AnimeSeason.FALL, 2026).success()
            client.search("frieren").success()

            assertEquals(4, engine.requestBodies.size)
            assertTrue(engine.requestBodies.all { "\"statusNot\":\"NOT_YET_RELEASED\"" in it })
        }

    @Test
    fun animeNotYetReleasedShowWhenSwitchedOn() =
        runTest {
            val engine = RecordedEngine("browse-trending")

            clientFor(engine, showUnreleased = { true }).trending().success()

            assertFalse("\"statusNot\":" in engine.requestBodies.single())
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
    fun spotlightFactsAskForTheIdsAndMapEachShow() =
        runTest {
            val engine = RecordedEngine("synopses")

            val facts = clientFor(engine).spotlightFacts(listOf(195516, 195539)).success()

            assertEquals(setOf(195516, 195539), facts.keys)
            val first = facts.getValue(195516)
            assertTrue(first.description!!.startsWith("In the aftermath of the Shi Clan"))
            assertEquals(NextEpisode(5, 1793368800), first.nextEpisode)
            // Its all-time rank, not this season's.
            assertEquals(79, first.ratedRank)
            assertEquals(null, facts.getValue(195539).nextEpisode)
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
            assertEquals(61987, details.malId)
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

    // Steel Ball Run has no AniZip record, so its episodes' dates come from here.
    @Test
    fun detailsHaveTheStartDateAndWhenEachEpisodeAirs() =
        runTest {
            val details = clientFor(RecordedEngine("details-steel-ball-run")).details(210482).success()

            assertEquals(AirDate(2026, 9, 25), details.startDate)
            assertEquals((1..11).toList(), details.airingAt.keys.toList())
            assertEquals(1790323200L, details.airingAt[1])
        }

    // The Apothecary Diaries season 3: its manga, music videos and other versions aren't anime to watch next.
    @Test
    fun detailsListsTheShowsItComesFromFollowsAndLeadsToThenSideStories() =
        runTest {
            val related = clientFor(RecordedEngine("details")).details(195516).success().related

            assertEquals(
                listOf(
                    Relation.PREQUEL to 176301,
                    Relation.SEQUEL to 200927,
                    Relation.SIDE_STORY to 200929,
                    Relation.SIDE_STORY to 218532,
                ),
                related.map { it.relation to it.card.id },
            )
            assertEquals("The Apothecary Diaries Season 2", related[0].card.title.english)
            assertEquals("MOVIE", related[2].card.format)
        }

    @Test
    fun adultShowsAreLeftOutOfRelated() =
        runTest {
            val engine = RecordedEngine("details", change = { it.replace("\"isAdult\": false", "\"isAdult\": true") })

            val details = clientFor(engine).details(195516).success()
            assertEquals(emptyList(), details.related)
            assertEquals(emptyList(), details.recommendations)
        }

    @Test
    fun detailsHasTheTenBestRatedRecommendations() =
        runTest {
            val details = clientFor(RecordedEngine("details")).details(195516).success()

            assertEquals(
                listOf(188139, 161645, 190569, 142701, 154587, 20770, 170503, 957, 1914, 99935),
                details.recommendations.map { it.id },
            )
            assertTrue(details.moreRecommendations)
        }

    @Test
    fun recommendationsComeAPageAtATimeWithTheShowsTitle() =
        runTest {
            val engine = RecordedEngine("recommendations")

            val page = clientFor(engine).recommendations(1535, page = 2).success()

            assertEquals("Death Note", page.show.english)
            assertEquals(20, page.page.items.size)
            assertTrue(page.page.hasNextPage)
            assertTrue(engine.requestBodies.single().let { "\"id\":1535" in it && "\"page\":2" in it })
        }
}

internal fun <T> AniListResult<T>.success(): T = assertIs<AniListResult.Success<T>>(this).value
