package ani.saikou.neo.tv

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Choreographer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Surface
import ani.saikou.neo.shared.account.AniListAccountClient
import ani.saikou.neo.shared.account.AniListProfile
import ani.saikou.neo.shared.account.KeystoreAccountStore
import ani.saikou.neo.shared.account.ListButtonState
import ani.saikou.neo.shared.account.ListButtonViewModel
import ani.saikou.neo.shared.catalogue.AirDate
import ani.saikou.neo.shared.catalogue.AniListClient
import ani.saikou.neo.shared.catalogue.AniZipClient
import ani.saikou.neo.shared.catalogue.AnimeSeason
import ani.saikou.neo.shared.catalogue.ArtLibrary
import ani.saikou.neo.shared.catalogue.FanartTvClient
import ani.saikou.neo.shared.catalogue.KitsuClient
import ani.saikou.neo.shared.catalogue.MalClient
import ani.saikou.neo.shared.catalogue.OmdbClient
import ani.saikou.neo.shared.catalogue.TmdbClient
import ani.saikou.neo.shared.details.DetailsState
import ani.saikou.neo.shared.details.DetailsViewModel
import ani.saikou.neo.shared.details.Raters
import ani.saikou.neo.shared.discover.DiscoverViewModel
import ani.saikou.neo.shared.home.ContinueWatchingViewModel
import ani.saikou.neo.shared.home.HomeRow
import ani.saikou.neo.shared.home.HomeViewModel
import ani.saikou.neo.shared.home.SeeMoreViewModel
import ani.saikou.neo.shared.pairing.TvAccountViewModel
import ani.saikou.neo.shared.pairing.TvSignInState
import ani.saikou.neo.shared.pairing.TvSignInViewModel
import ani.saikou.neo.shared.pairing.openTvPairing
import ani.saikou.neo.shared.pairing.thisTv
import ani.saikou.neo.shared.playback.ControlsViewModel
import ani.saikou.neo.shared.playback.NextPlay
import ani.saikou.neo.shared.playback.PlayerViewModel
import ani.saikou.neo.shared.playback.ServersViewModel
import ani.saikou.neo.shared.playback.playRequest
import ani.saikou.neo.shared.schedule.DeviceScheduleZone
import ani.saikou.neo.shared.schedule.PrefsScheduleFilterStore
import ani.saikou.neo.shared.schedule.ScheduleEvent
import ani.saikou.neo.shared.schedule.ScheduleViewModel
import ani.saikou.neo.shared.search.SearchViewModel
import ani.saikou.neo.shared.settings.PlayerSubtitlesViewModel
import ani.saikou.neo.shared.settings.SettingsViewModel
import ani.saikou.neo.shared.settings.SubtitleSettings
import ani.saikou.neo.shared.settings.SubtitleSettingsViewModel
import ani.saikou.neo.shared.sources.ShortCodes
import ani.saikou.neo.shared.sources.SourceChecker
import ani.saikou.neo.shared.sources.SourceChecksViewModel
import ani.saikou.neo.shared.sources.SourceDownloads
import ani.saikou.neo.shared.sources.SourcesViewModel
import ani.saikou.neo.tv.details.DetailsScreen
import ani.saikou.neo.tv.details.StreamsActions
import ani.saikou.neo.tv.details.StreamsPage
import ani.saikou.neo.tv.discover.DiscoverScreen
import ani.saikou.neo.tv.home.HomeScreen
import ani.saikou.neo.tv.home.SeeMoreScreen
import ani.saikou.neo.tv.home.displayTitle
import ani.saikou.neo.tv.player.LookActions
import ani.saikou.neo.tv.player.Looks
import ani.saikou.neo.tv.player.PlayerActions
import ani.saikou.neo.tv.player.PlayerScreen
import ani.saikou.neo.tv.player.Sample
import ani.saikou.neo.tv.player.StreamVideo
import ani.saikou.neo.tv.player.rememberExoPlayback
import ani.saikou.neo.tv.schedule.ScheduleActions
import ani.saikou.neo.tv.schedule.ScheduleScreen
import ani.saikou.neo.tv.search.SearchScreen
import ani.saikou.neo.tv.search.genreLabels
import ani.saikou.neo.tv.settings.AccountSettings
import ani.saikou.neo.tv.settings.SettingsCategory
import ani.saikou.neo.tv.settings.SettingsScreen
import ani.saikou.neo.tv.settings.SourceActions
import ani.saikou.neo.tv.settings.SubtitleActions
import ani.saikou.neo.tv.signin.ProfileSignedOutScreen
import ani.saikou.neo.tv.signin.SignInScreen
import ani.saikou.neo.tv.sources.sourceThreads
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.NoticeActions
import ani.saikou.neo.tv.ui.NoticeOverScreens
import ani.saikou.neo.tv.ui.Section
import ani.saikou.neo.tv.ui.SectionPlaceholder
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.TimeZone

class MainActivity : ComponentActivity() {
    private val app get() = application as NeoApp

    // Asked on every request, so lists follow the setting from the next one on.
    private val client by lazy {
        AniListClient(showUnreleased = { app.settings.value?.showUnairedAnime ?: false }, isOnline = ::isOnline)
    }
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
        // AniList updates that couldn't be sent before go now. Only the TV does this, so the phone never opens the queue.
        // The notice listens from the start, so no change the TV makes goes unsaid (ADR 0056).
        app.notices
        if (savedInstanceState == null) app.sync.flush()
        // Where the system splash shows, it goes with a plain cut to the same lockup in the window background (ADR 0018).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { it.remove() }
        }
        // The window's animations run at the speed HeldKeys sets.
        setContent(parent = window.decorView.createLifecycleAwareWindowRecomposer(heldKeys, lifecycle)) {
            // The settings take a moment to read at start, and nothing shows until they're in.
            val settings = app.settings.collectAsStateWithLifecycle().value ?: return@setContent
            // An episode picked in the player, for the Details under it to open as the player goes.
            var episodeFromPlayer by remember { mutableStateOf<Int?>(null) }
            // The AniList account on this TV, for Profile and Settings > Account. Read again once Sign in saves one.
            val account = viewModel { TvAccountViewModel(KeystoreAccountStore(applicationContext)) }
            val profile by account.profile.collectAsStateWithLifecycle()
            // The AniList notice (ADR 0056): Up next shows it in the player, and it shows over every other screen.
            val notice by app.notices.shown.collectAsStateWithLifecycle()
            val noticeActions = remember { NoticeActions(app.notices::undo, app.notices::undoFocused) }
            var inPlayer by remember { mutableStateOf(false) }
            val screen = remember { FocusRequester() }
            NeoTheme(light = settings.slowTv) {
                // Every key passes through HeldKeys before it can move focus, and tells the run of episodes someone's
                // there (ADR 0055). Test tags double as resource IDs, so :benchmark-tv can find the cards.
                Surface(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .semantics { testTagsAsResourceId = true }
                            // Where focus goes back to from the notice's Undo.
                            .focusRequester(screen)
                            .focusGroup()
                            .onPreviewKeyEvent {
                                if (it.type == KeyEventType.KeyDown) app.watchingRun.pressed()
                                heldKeys.drop(it.nativeKeyEvent)
                            },
                ) {
                    NeoNavigation(
                        home = { openDetails, openSeeMore ->
                            // Showing unaired anime or not changes the rows, so Home loads them again.
                            val home =
                                viewModel(key = "home-${settings.showUnairedAnime}") {
                                    HomeViewModel(client, library, season(), year(), app.history, app.yourList)
                                }
                            // Continue watching adds your AniList list, so signing in or out reads it again.
                            LaunchedEffect(profile?.id) { home.signedInAs(profile?.id) }
                            val state by home.state.collectAsStateWithLifecycle()
                            // A list button for each featured show, read once, so it's ready as the spotlight moves on.
                            val lists =
                                state.spotlight?.shows.orEmpty().associate { card ->
                                    card.id to listButton(card.id, profile?.id, settings.privateMode)
                                }
                            val listStates = lists.mapValues { (_, list) -> list.state.collectAsStateWithLifecycle().value }
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
                                list = { listStates[it] ?: ListButtonState.Hidden },
                                onListChange = { animeId, status -> lists[animeId]?.change(status) },
                                privateMode = settings.privateMode && profile != null,
                            )
                        },
                        seeMore = { row, openDetails ->
                            // Continue watching comes from this TV's history and your AniList list, the other rows from AniList.
                            if (row == HomeRow.CONTINUE_WATCHING) {
                                val watching = viewModel { ContinueWatchingViewModel(client, library, app.history, app.yourList) }
                                val state by watching.state.collectAsStateWithLifecycle()
                                SeeMoreScreen(
                                    row,
                                    state,
                                    onOpen = openDetails,
                                    onLoadMore = {},
                                    onRetry = watching::retry,
                                    onRest = watching::rest,
                                )
                            } else {
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
                            }
                        },
                        details = { animeId, openDetails, openRecommendations, openSources, openPlayer ->
                            // Private mode changes what Details may send, so turning it on or off reads the show again.
                            val details =
                                viewModel(key = "details-$animeId-${settings.privateMode}") {
                                    DetailsViewModel(
                                        client,
                                        library,
                                        aniZip,
                                        raters,
                                        animeId,
                                        today(),
                                        settings.showUpcomingEpisodes,
                                        progress = app.history.shows.map { it[animeId] },
                                        listed = { app.yourList.shows().firstOrNull { it.animeId == animeId } },
                                        sides = app.pickUpSides,
                                        marks = app.sync,
                                        marked = { app.yourList.forget() },
                                        zoneOffsetSeconds = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000,
                                        warmSources = app.sourceWarmUp::warm,
                                        asksWhereToPickUp = !settings.privateMode,
                                    )
                                }
                            val state by details.state.collectAsStateWithLifecycle()
                            val list = listButton(animeId, profile?.id, settings.privateMode)
                            val listState by list.state.collectAsStateWithLifecycle()
                            DetailsScreen(
                                state,
                                onOpen = openDetails,
                                onRecommendations = { openRecommendations(animeId) },
                                streams = { episode, back, modifier ->
                                    val show = (state as? DetailsState.Loaded)?.details ?: return@DetailsScreen
                                    // Asks the sources installed when the episode is picked, with the setting then.
                                    val servers =
                                        viewModel(key = "servers-$animeId-$episode") {
                                            ServersViewModel(
                                                app.serverFinder,
                                                app.sources.installed,
                                                show,
                                                episode,
                                                settings.subOrDub,
                                                siteChecking = app.sourceHost.siteChecking,
                                            )
                                        }
                                    val serversState by servers.state.collectAsStateWithLifecycle()
                                    val episodeTitle =
                                        (state as? DetailsState.Loaded)
                                            ?.episodes
                                            ?.episodes
                                            ?.firstOrNull { it.number == episode }
                                            ?.title
                                    StreamsPage(
                                        serversState,
                                        episode,
                                        show.card.displayTitle(),
                                        onPick = { source, server ->
                                            openPlayer(
                                                serversState
                                                    .playRequest(
                                                        source,
                                                        server,
                                                        show,
                                                        episode,
                                                        episodeTitle,
                                                        (state as? DetailsState.Loaded)?.backdrop,
                                                    ).copy(
                                                        episodes = (state as? DetailsState.Loaded)?.episodes,
                                                        progress = (state as? DetailsState.Loaded)?.place,
                                                    ),
                                            )
                                        },
                                        onEdition = servers::showEdition,
                                        onRetry = servers::retry,
                                        actions =
                                            StreamsActions(
                                                onRetry = servers::retry,
                                                onBack = back,
                                                onAddSource = openSources,
                                                // The answers outlive the page, so reopening it gives a failed source another chance.
                                                onOpen = servers::retry,
                                            ),
                                        modifier = modifier,
                                    )
                                },
                                openStreams = episodeFromPlayer,
                                onStreamsOpened = { episodeFromPlayer = null },
                                onKeepAniList = details::keepAniList,
                                onKeepThisTv = details::keepThisTv,
                                list = listState,
                                onListChange = list::change,
                                onMark = { episode, watched ->
                                    if (watched) details.markWatched(episode) else details.markUnwatched(episode)
                                },
                            )
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
                        search = { openDetails, openGenre ->
                            val search = viewModel { SearchViewModel(client, KitsuClient(), app.recentSearches) }
                            val state by search.state.collectAsStateWithLifecycle()
                            SearchScreen(
                                state,
                                onType = search::type,
                                onDelete = search::delete,
                                onClear = search::clear,
                                onRetry = search::retry,
                                onOpen = {
                                    search.openedResult()
                                    openDetails(it)
                                },
                                onGenre = openGenre,
                                onFilter = search::filter,
                                onRecent = search::searchAgain,
                            )
                        },
                        genre = { genre, openDetails ->
                            val popular = viewModel { SeeMoreViewModel.genre(client, library, genre) }
                            val state by popular.state.collectAsStateWithLifecycle()
                            SeeMoreScreen(
                                title = stringResource(genreLabels.getValue(genre)),
                                state = state,
                                onOpen = openDetails,
                                onLoadMore = popular::loadMore,
                                onRetry = popular::retry,
                                onRest = popular::rest,
                            )
                        },
                        discover = { openDetails ->
                            val discover = viewModel { DiscoverViewModel(client, season(), year()) }
                            val state by discover.state.collectAsStateWithLifecycle()
                            DiscoverScreen(
                                state,
                                onOpen = openDetails,
                                onRetry = discover::retry,
                                onNetworkSettings = ::openNetworkSettings,
                                onOfflineInView = discover::watchOffline,
                            )
                        },
                        schedule = { openDetails, goHome ->
                            val schedule =
                                viewModel {
                                    ScheduleViewModel(
                                        app.schedule,
                                        KeystoreAccountStore(applicationContext),
                                        PrefsScheduleFilterStore(applicationContext),
                                        app.settingsStore.settings,
                                        DeviceScheduleZone(),
                                    )
                                }
                            val state by schedule.state.collectAsStateWithLifecycle()
                            var cantOpen by remember { mutableStateOf<ScheduleEvent.CantOpen?>(null) }
                            LaunchedEffect(schedule) {
                                schedule.events.collect { event ->
                                    when (event) {
                                        is ScheduleEvent.OpenDetails -> openDetails(event.aniListId)
                                        is ScheduleEvent.CantOpen -> cantOpen = event
                                    }
                                }
                            }
                            ScheduleScreen(
                                state,
                                actions =
                                    ScheduleActions(
                                        onSelectDay = schedule::selectDay,
                                        onFilter = schedule::showFilter,
                                        onOpen = schedule::open,
                                        onRetry = schedule::retry,
                                        onHome = goHome,
                                        onCantOpenShown = { cantOpen = null },
                                    ),
                                cantOpen = cantOpen,
                            )
                        },
                        section = { section, openSignIn ->
                            if (section ==
                                Section.SETTINGS
                            ) {
                                SettingsSection(SettingsCategory.BROWSING, openSignIn, profile, account::signOut)
                            } else {
                                SectionPlaceholder(section)
                            }
                        },
                        sources = { openSignIn -> SettingsSection(SettingsCategory.SOURCES, openSignIn, profile, account::signOut) },
                        signedIn = { profile != null },
                        profile = profile,
                        signedOutProfile = { openSignIn, back -> ProfileSignedOutScreen(onSignIn = openSignIn, onHome = back) },
                        signIn = { leave, goHome ->
                            val signIn =
                                viewModel {
                                    TvSignInViewModel(
                                        open = { code -> openTvPairing(applicationContext, code) },
                                        viewer = AniListAccountClient()::viewer,
                                        store = KeystoreAccountStore(applicationContext),
                                        tv = thisTv(applicationContext),
                                    )
                                }
                            val state by signIn.state.collectAsStateWithLifecycle()
                            LaunchedEffect(state is TvSignInState.SignedIn) { if (state is TvSignInState.SignedIn) account.refresh() }
                            // No pairing in the background: a phone can't finish signing in while nobody's watching.
                            LifecycleEventEffect(Lifecycle.Event.ON_STOP) { signIn.cancel() }
                            // Leaving clears the ViewModel, which closes the listener and any phone without signing in.
                            SignInScreen(state, onNewCode = signIn::newCode, onLeave = leave, onHome = goHome)
                        },
                        player = { request, back, playNext ->
                            val player =
                                viewModel {
                                    PlayerViewModel(
                                        app.streamFinder,
                                        request,
                                        history = app.history,
                                        updates = app.sync,
                                        servers = app.serverFinder,
                                        run = app.watchingRun,
                                    )
                                }
                            val state by player.state.collectAsStateWithLifecycle()
                            // A notice from watching waits while the video is full screen, for Up next or the way out.
                            val fullScreen = state.upNext == null
                            LaunchedEffect(fullScreen) { app.notices.playing(fullScreen) }
                            DisposableEffect(Unit) {
                                inPlayer = true
                                onDispose {
                                    inPlayer = false
                                    app.notices.playing(fullScreen = false)
                                }
                            }
                            // On to the next episode, or its servers page when the same server or audio isn't there (ADR 0054).
                            LaunchedEffect(state.going) {
                                when (val going = state.going) {
                                    is NextPlay.Play -> {
                                        playNext(going.request)
                                    }

                                    is NextPlay.Choose -> {
                                        episodeFromPlayer = going.episode
                                        back()
                                    }

                                    null -> {}
                                }
                            }
                            // Where to go once "Update AniList as you watch?" is answered (ADR 0053).
                            var leaving by remember { mutableStateOf<(() -> Unit)?>(null) }
                            val leaveThen = { go: () -> Unit -> if (player.leave()) leaving = go else go() }
                            val playback = rememberExoPlayback()
                            val subtitles =
                                viewModel {
                                    PlayerSubtitlesViewModel(
                                        app.settingsStore.subtitles,
                                        app.settingsStore::updateSubtitles,
                                        request.show.card.id,
                                        request.show.card.displayTitle(),
                                    )
                                }
                            val looks by subtitles.state.collectAsStateWithLifecycle()
                            val controls = viewModel { ControlsViewModel() }
                            val shown by controls.state.collectAsStateWithLifecycle()
                            val track = playback.subtitles.firstOrNull { it.selected }?.let { it.label ?: "${it.group}:${it.track}" }
                            LaunchedEffect(track, playback.subtitleStyled) { subtitles.onTrack(track, playback.subtitleStyled) }
                            val sample =
                                Sample(stringResource(R.string.player_look_sample), stringResource(R.string.player_look_sample_label))
                            PlayerScreen(
                                state,
                                request,
                                PlayerActions(
                                    onResume = player::resume,
                                    onStartOver = player::startOver,
                                    onStarted = player::started,
                                    onFailed = player::failed,
                                    onTryNext = player::tryNext,
                                    onTryAgain = player::tryAgain,
                                    // The servers page for the episode playing, which after Next isn't the one Details opened.
                                    onChooseAnother = {
                                        episodeFromPlayer = request.episode
                                        back()
                                    },
                                    onQuality = player::changeQuality,
                                    onAudio = player::changeAudio,
                                    onServer = player::changeServer,
                                    onEpisode = { episode ->
                                        player.movingOn(episode)
                                        leaveThen {
                                            episodeFromPlayer = episode
                                            back()
                                        }
                                    },
                                    onLeave = { leaveThen(back) },
                                    onAnswer = { update ->
                                        if (update == null) player.noAnswer() else player.answer(update)
                                        leaving?.invoke()
                                        leaving = null
                                    },
                                    onNext = player::playNext,
                                    onUpNext = player::reachedUpNext,
                                    onWatchCredits = player::watchCredits,
                                ),
                                playback,
                                controls,
                                video = { subtitlesAbove ->
                                    StreamVideo(
                                        state,
                                        state.server.server.edition,
                                        playback,
                                        player::started,
                                        player::failed,
                                        app.sourceHost.client,
                                        looks.drawing,
                                        onPlayed = player::played,
                                        onEnded = { if (player.ended()) leaving = back },
                                        sample = sample.takeIf { shown.looks },
                                        subtitlesAbove = subtitlesAbove,
                                        online = ::isOnline,
                                    )
                                },
                                looks =
                                    Looks(
                                        looks,
                                        LookActions(
                                            subtitles::preview,
                                            subtitles::keep,
                                            subtitles::changeSize,
                                            subtitles::changePosition,
                                            subtitles::useMyLook,
                                        ),
                                    ),
                                notice = notice,
                                noticeActions = noticeActions,
                            )
                        },
                    )
                    if (!inPlayer) NoticeOverScreens(notice, noticeActions, screen)
                }
            }
        }
    }

    /** Settings with the installed sources, opened on [start]. Account opens Sign in with [openSignIn] and signs out with [onSignOut]. */
    @Composable
    private fun SettingsSection(
        start: SettingsCategory,
        openSignIn: () -> Unit,
        profile: AniListProfile?,
        onSignOut: () -> Unit,
    ) {
        val settingsScreen = viewModel { SettingsViewModel(app.settingsStore) }
        val state by settingsScreen.state.collectAsStateWithLifecycle()
        val sourcesScreen = viewModel { SourcesViewModel(app.sources, ShortCodes(), SourceDownloads()) }
        val sources by sourcesScreen.state.collectAsStateWithLifecycle()
        val checksScreen =
            viewModel { SourceChecksViewModel(SourceChecker(app.sources::extension, app.sourceHost.services, sourceThreads)) }
        val checks by checksScreen.checks.collectAsStateWithLifecycle()
        val subtitlesScreen = viewModel { SubtitleSettingsViewModel(app.settingsStore.subtitles, app.settingsStore::updateSubtitles) }
        val subtitles by subtitlesScreen.state.collectAsStateWithLifecycle()
        state?.let {
            SettingsScreen(
                it,
                onChoose = settingsScreen::choose,
                onTick = settingsScreen::tick,
                sources = sources,
                sourceActions =
                    SourceActions(
                        sourcesScreen::add,
                        sourcesScreen::dismiss,
                        sourcesScreen::remove,
                        sourcesScreen::toggle,
                        sourcesScreen::addTicked,
                        checksScreen::check,
                        { checksScreen.checkAll(sources.installed.orEmpty()) },
                        checksScreen::stop,
                    ),
                start = start,
                checks = checks,
                subtitles = subtitles ?: SubtitleSettings(),
                subtitleActions =
                    SubtitleActions(
                        subtitlesScreen::step,
                        subtitlesScreen::use,
                        subtitlesScreen::addLook,
                        subtitlesScreen::edit,
                        subtitlesScreen::reset,
                        subtitlesScreen::delete,
                        subtitlesScreen::clearShow,
                        subtitlesScreen::resetAll,
                    ),
                account = AccountSettings(profile, openSignIn, onSignOut),
            )
        }
    }

    /** True while the TV has a network that reaches the internet, so AniList calls without one fail at once. */
    private fun isOnline(): Boolean {
        val connectivity = getSystemService(ConnectivityManager::class.java)
        return connectivity
            .getNetworkCapabilities(
                connectivity.activeNetwork,
            )?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ==
            true
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

    /**
     * [animeId]'s list button, read again for whoever [userId] is signed in now (ADR 0051), and when [privateMode] turns
     * on or off, which hides it (ADR 0053).
     */
    @Composable
    private fun listButton(
        animeId: Int,
        userId: Int?,
        privateMode: Boolean,
    ): ListButtonViewModel =
        viewModel(key = "list-$animeId-$userId-$privateMode") { ListButtonViewModel(animeId, app.sync) { app.yourList.forget() } }

    private fun season() = AnimeSeason.forMonth(Calendar.getInstance().get(Calendar.MONTH) + 1)

    private fun year() = Calendar.getInstance().get(Calendar.YEAR)

    // Today on this TV, to tell the episodes out from those still to air.
    private fun today() =
        Calendar.getInstance().let { AirDate(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH)) }
}
