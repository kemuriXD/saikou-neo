package ani.saikou.neo.tv

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Choreographer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Surface
import ani.saikou.neo.shared.catalogue.AirDate
import ani.saikou.neo.shared.catalogue.AniListClient
import ani.saikou.neo.shared.catalogue.AniZipClient
import ani.saikou.neo.shared.catalogue.AnimeSeason
import ani.saikou.neo.shared.catalogue.ArtLibrary
import ani.saikou.neo.shared.catalogue.FanartTvClient
import ani.saikou.neo.shared.catalogue.MalClient
import ani.saikou.neo.shared.catalogue.OmdbClient
import ani.saikou.neo.shared.catalogue.TmdbClient
import ani.saikou.neo.shared.details.DetailsViewModel
import ani.saikou.neo.shared.details.Raters
import ani.saikou.neo.shared.home.HomeViewModel
import ani.saikou.neo.shared.home.SeeMoreViewModel
import ani.saikou.neo.shared.settings.SettingsViewModel
import ani.saikou.neo.tv.details.DetailsScreen
import ani.saikou.neo.tv.home.HomeScreen
import ani.saikou.neo.tv.home.SeeMoreScreen
import ani.saikou.neo.tv.home.displayTitle
import ani.saikou.neo.tv.settings.SettingsScreen
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.Section
import ani.saikou.neo.tv.ui.SectionPlaceholder
import java.util.Calendar
import java.util.TimeZone

class MainActivity : ComponentActivity() {
    private val app get() = application as NeoApp

    // Asked on every request, so lists follow the setting from the next one on.
    private val client by lazy { AniListClient { app.settings.value?.showUnairedAnime ?: false } }
    private val aniZip by lazy { AniZipClient() }
    private val library by lazy {
        ArtLibrary(client, aniZip, FanartTvClient(BuildConfig.FANART_TV_API_KEY), TmdbClient(BuildConfig.TMDB_API_KEY))
    }
    private val raters by lazy { Raters(MalClient(), OmdbClient(BuildConfig.OMDB_API_KEY)) }
    private val heldKeys =
        HeldKeys(
            systemScale = { Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) },
            afterNextFrame = { drawn -> Choreographer.getInstance().postFrameCallback { drawn() } },
        )

    private val absence = LongAbsence()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Opened again after a long time away, the app starts on Home rather than where it was left.
        super.onCreate(absence.restore(savedInstanceState))
        // The window's animations run at the speed HeldKeys sets.
        setContent(parent = window.decorView.createLifecycleAwareWindowRecomposer(heldKeys, lifecycle)) {
            // The settings take a moment to read at start, and nothing shows until they're in.
            val settings = app.settings.collectAsStateWithLifecycle().value ?: return@setContent
            NeoTheme(light = settings.slowTv) {
                // Every key passes through HeldKeys before it can move focus. Test tags double as resource IDs, so
                // :benchmark-tv can find the cards.
                Surface(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .semantics { testTagsAsResourceId = true }
                            .onPreviewKeyEvent { heldKeys.drop(it.nativeKeyEvent) },
                ) {
                    NeoNavigation(
                        home = { openDetails, openSeeMore ->
                            // Showing unaired anime or not changes the rows, so Home loads them again.
                            val home =
                                viewModel(key = "home-${settings.showUnairedAnime}") {
                                    // Only alpha builds show sample progress, until the player saves real progress.
                                    HomeViewModel(
                                        client,
                                        library,
                                        season(),
                                        year(),
                                        sampleProgress = BuildConfig.FLAVOR == "alpha",
                                        history = app.history,
                                    )
                                }
                            val state by home.state.collectAsStateWithLifecycle()
                            HomeScreen(
                                state = state,
                                onOpen = openDetails,
                                onPick = home::pick,
                                onSpotlightInView = home::watchSpotlight,
                                onSeeMore = openSeeMore,
                                onRest = home::rest,
                                onRetry = home::retry,
                                onNetworkSettings = ::openNetworkSettings,
                                onOfflineInView = home::watchOffline,
                            )
                        },
                        seeMore = { row, openDetails ->
                            val seeMore = viewModel { SeeMoreViewModel(client, library, row, season(), year()) }
                            val state by seeMore.state.collectAsStateWithLifecycle()
                            SeeMoreScreen(
                                row,
                                state,
                                onOpen = openDetails,
                                onLoadMore = seeMore::loadMore,
                                onRetry = seeMore::retry,
                                onRest = seeMore::rest,
                            )
                        },
                        details = { animeId, openDetails, openRecommendations ->
                            val details =
                                viewModel {
                                    DetailsViewModel(
                                        client,
                                        library,
                                        aniZip,
                                        raters,
                                        animeId,
                                        today(),
                                        settings.showUpcomingEpisodes,
                                        progress = app.history.shows.value[animeId],
                                        zoneOffsetSeconds = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000,
                                    )
                                }
                            val state by details.state.collectAsStateWithLifecycle()
                            DetailsScreen(state, onOpen = openDetails, onRecommendations = { openRecommendations(animeId) })
                        },
                        recommendations = { animeId, openDetails ->
                            val recommendations = viewModel { SeeMoreViewModel.recommendations(client, library, animeId) }
                            val state by recommendations.state.collectAsStateWithLifecycle()
                            SeeMoreScreen(
                                title = stringResource(R.string.details_recommendations),
                                state = state,
                                onOpen = openDetails,
                                onLoadMore = recommendations::loadMore,
                                onRetry = recommendations::retry,
                                onRest = recommendations::rest,
                                eyebrow = state.show?.displayTitle(),
                            )
                        },
                        section = { section ->
                            if (section == Section.SETTINGS) {
                                val settingsScreen = viewModel { SettingsViewModel(app.settingsStore) }
                                val state by settingsScreen.state.collectAsStateWithLifecycle()
                                state?.let { SettingsScreen(it, onChoose = settingsScreen::choose) }
                            } else {
                                SectionPlaceholder(section)
                            }
                        },
                    )
                }
            }
        }
    }

    /** The TV's network settings, or its main settings where there's no network screen to open. */
    private fun openNetworkSettings() {
        try {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    override fun onStart() {
        super.onStart()
        // Still open after a long time away, the app starts over too, in a new screen with nothing kept.
        if (absence.startedAgain()) {
            startActivity(Intent.makeRestartActivityTask(componentName))
            finish()
        }
    }

    override fun onStop() {
        super.onStop()
        absence.stopped()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        absence.save(outState)
    }

    // This season, for the This season row and its page.
    private fun season() = AnimeSeason.forMonth(Calendar.getInstance().get(Calendar.MONTH) + 1)

    private fun year() = Calendar.getInstance().get(Calendar.YEAR)

    // Today on this TV, to tell the episodes out from those still to air.
    private fun today() =
        Calendar.getInstance().let { AirDate(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH)) }
}
