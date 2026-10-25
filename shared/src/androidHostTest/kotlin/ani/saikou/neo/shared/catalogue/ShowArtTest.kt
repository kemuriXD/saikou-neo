package ani.saikou.neo.shared.catalogue

import ani.saikou.neo.shared.home.Logo
import com.apollographql.apollo.network.http.HttpEngine
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShowArtTest {
    private val dragonBallSuperFanArt = "https://artworks.thetvdb.com/banners/fanart/original/295068-1.jpg"
    private val noTmdb = TmdbEngine(status = 404) { "not-found" }
    private val noFanartTv = FanartTvEngine("not-found", status = 404)

    private fun sources(
        aniZip: HttpEngine,
        aniList: HttpEngine = RecordedEngine("relations-none"),
        fanartTv: HttpEngine = noFanartTv,
        tmdb: HttpEngine = noTmdb,
    ) = ArtSources(clientFor(aniList), AniZipClient(aniZip), FanartTvClient(KEY, fanartTv), TmdbClient(KEY, tmdb))

    @Test
    fun aLogoComesFromTmdbFirst() =
        runTest {
            val tmdb = TmdbEngine { "tv-images" }
            val fanartTv = FanartTvEngine("tv")

            val art = showArt(sources(AniZipEngine("mappings-one-piece"), fanartTv = fanartTv, tmdb = tmdb), card(21, "One Piece"))

            assertEquals(Logo.Image("https://image.tmdb.org/t/p/original/9F7daAmibx8ZHTE17CdM5FAwiHE.png"), art.logo)
            assertEquals("https://api.themoviedb.org/3/tv/37854/images?api_key=$KEY", tmdb.urls.single())
            // Both are asked at once, so a slow answer from one doesn't hold up the other.
            assertEquals("https://webservice.fanart.tv/v3.2/tv/81797", fanartTv.requests.single().url)
        }

    @Test
    fun withoutATmdbLogoTheLogoComesFromFanartTv() =
        runTest {
            val art = showArt(sources(AniZipEngine("mappings-one-piece"), fanartTv = FanartTvEngine("tv")), card(21, "One Piece"))

            assertEquals(Logo.Image("https://assets.fanart.tv/fanart/sakamoto-days-66866dc54d0e7.png"), art.logo)
        }

    // AniZip's "Clearlogo" is TheTVDB's square series icon, so it's never used.
    @Test
    fun withoutALogoFromEitherTheTitleShowsInWords() =
        runTest { assertEquals(Logo.None, showArt(sources(AniZipEngine("mappings")), card(177709, "Sakamoto Days")).logo) }

    // AniZip gives The End of Evangelion the series' TMDB id 890, so TMDB finds the film by its IMDb id instead.
    @Test
    fun aFilmIsFoundByItsImdbId() =
        runTest {
            val tmdb = TmdbEngine { if ("/find/" in it) "find-movie" else "movie-images" }
            val fanartTv = FanartTvEngine("movie")

            val art =
                showArt(
                    sources(AniZipEngine("mappings-film-series-id"), fanartTv = fanartTv, tmdb = tmdb),
                    card(32, "The End of Evangelion"),
                )

            assertEquals(
                listOf(
                    "https://api.themoviedb.org/3/find/tt0169858?external_source=imdb_id&api_key=$KEY",
                    "https://api.themoviedb.org/3/movie/18491/images?api_key=$KEY",
                ),
                tmdb.urls,
            )
            assertEquals("https://webservice.fanart.tv/v3.2/movies/18491", fanartTv.requests.single().url)
            assertEquals(Logo.Image("https://image.tmdb.org/t/p/original/hkUi1Z15Ymj9GNamWQkzUhehLuy.png"), art.logo)
        }

    // Kizumonogatari has no ids on AniZip.
    @Test
    fun aFilmWithoutIdsIsFoundByItsTitleAndYear() =
        runTest {
            val tmdb = TmdbEngine { if ("/search/" in it) "search-movie" else "movie-images" }
            val kizumonogatari = card(9260, "Kizumonogatari Part 1: Tekketsu", format = "MOVIE", year = 2016)

            showArt(sources(AniZipEngine("mappings-no-ids"), tmdb = tmdb), kizumonogatari)

            assertEquals(
                listOf(
                    "https://api.themoviedb.org/3/search/movie?query=Kizumonogatari%20Part%201%3A%20Tekketsu&year=2016&api_key=$KEY",
                    "https://api.themoviedb.org/3/movie/357786/images?api_key=$KEY",
                ),
                tmdb.urls,
            )
        }

    @Test
    fun aFilmTmdbCannotFindHasNoLogo() =
        runTest {
            val tmdb = TmdbEngine { "find-none" }
            val fanartTv = FanartTvEngine("movie")
            val kizumonogatari = card(9260, "Kizumonogatari Part 1: Tekketsu", format = "MOVIE", year = 2016)

            val art = showArt(sources(AniZipEngine("mappings-no-ids"), fanartTv = fanartTv, tmdb = tmdb), kizumonogatari)

            assertEquals(Logo.None, art.logo)
            // Without an id, fanart.tv can't be asked.
            assertTrue(fanartTv.requests.isEmpty())
        }

    // Dragon Ball Super: Beerus has no ids, but it belongs to Dragon Ball Super, which has.
    @Test
    fun aShowWithoutIdsUsesItsSeriesLogo() =
        runTest {
            val aniZip = AniZipEngine("mappings-unknown", byId = mapOf(21175 to "mappings-parent"))
            val fanartTv = FanartTvEngine("tv")

            val art = showArt(sources(aniZip, aniList = RecordedEngine("relations"), fanartTv = fanartTv), card(206814, "Beerus"))

            assertEquals(Logo.Image("https://assets.fanart.tv/fanart/sakamoto-days-66866dc54d0e7.png"), art.logo)
            assertEquals("https://webservice.fanart.tv/v3.2/tv/295068", fanartTv.requests.single().url)
            assertEquals(dragonBallSuperFanArt, art.backdrop)
        }

    @Test
    fun aShowWithFanArtNeedsNoSeries() =
        runTest {
            val aniList = QueryEngine("relations", emptyMap())

            val art = showArt(sources(AniZipEngine("mappings"), aniList = aniList), card(177709, "Sakamoto Days"))

            assertEquals("https://artworks.thetvdb.com/banners/v4/series/423732/backgrounds/6654700131bab.jpg", art.backdrop)
            assertTrue(aniList.requestBodies.isEmpty())
        }

    @Test
    fun aShowWithoutArtOrASeriesHasNone() =
        runTest {
            val art = showArt(sources(AniZipEngine("mappings-unknown")), card(21519, "Your Name."))

            assertEquals(ShowArt(backdrop = null, logo = Logo.None), art)
        }

    @Test
    fun noConnectionMeansNoArt() =
        runTest {
            val art = showArt(sources(OfflineEngine, OfflineEngine, OfflineEngine, OfflineEngine), card(206814, "Beerus"))

            assertEquals(ShowArt(backdrop = null, logo = Logo.None), art)
        }

    private companion object {
        const val KEY = "test-key"
    }
}

/** A card with just what finding a show's art needs. */
internal fun card(
    id: Int,
    title: String,
    format: String = "TV",
    year: Int? = null,
) = AnimeCard(id, AnimeTitle(romaji = title, english = title, native = null), null, null, null, format, null, null, null, year)
