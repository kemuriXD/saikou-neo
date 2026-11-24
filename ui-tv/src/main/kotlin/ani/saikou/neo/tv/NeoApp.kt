package ani.saikou.neo.tv

import android.app.Application
import android.os.Build
import ani.saikou.neo.shared.account.AniListAccountClient
import ani.saikou.neo.shared.account.AniListSync
import ani.saikou.neo.shared.account.KeystoreAccountStore
import ani.saikou.neo.shared.account.ListDate
import ani.saikou.neo.shared.account.ListNotices
import ani.saikou.neo.shared.account.SyncQueueStore
import ani.saikou.neo.shared.account.accountChanges
import ani.saikou.neo.shared.catalogue.AnimeScheduleClient
import ani.saikou.neo.shared.catalogue.PrefsShowMatchStore
import ani.saikou.neo.shared.catalogue.ShowMatcher
import ani.saikou.neo.shared.crash.CrashReporter
import ani.saikou.neo.shared.crash.CrashReports
import ani.saikou.neo.shared.crash.NoCrashReporter
import ani.saikou.neo.shared.crash.deviceFacts
import ani.saikou.neo.shared.crash.networkTypes
import ani.saikou.neo.shared.home.AniListYourList
import ani.saikou.neo.shared.home.SyncedSides
import ani.saikou.neo.shared.home.WatchHistoryStore
import ani.saikou.neo.shared.log.installAppLog
import ani.saikou.neo.shared.playback.WatchingRun
import ani.saikou.neo.shared.report.BugReportSender
import ani.saikou.neo.shared.report.BugReports
import ani.saikou.neo.shared.report.NoBugReportSender
import ani.saikou.neo.shared.report.ReportAccount
import ani.saikou.neo.shared.schedule.AnimeScheduleSource
import ani.saikou.neo.shared.search.RecentSearchesStore
import ani.saikou.neo.shared.settings.Settings
import ani.saikou.neo.shared.settings.SettingsStore
import ani.saikou.neo.shared.settings.SettingsStoreOwner
import ani.saikou.neo.shared.sources.SourceHost
import ani.saikou.neo.shared.sources.SourceWarmUp
import ani.saikou.neo.tv.sources.ApkSourceStore
import ani.saikou.neo.tv.sources.ExtensionServerFinder
import ani.saikou.neo.tv.sources.ExtensionStreamFinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

/**
 * Holds what lives as long as the app: the settings, recent searches, sources and last servers, each of which only one
 * store may have open, as can the watch history. Schedule's week is kept here too, so it outlasts the screen.
 */
open class NeoApp :
    Application(),
    SettingsStoreOwner {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Where crash reports go, set once the app is created. */
    lateinit var crashReporter: CrashReporter
        private set

    /** The app module gives builds with Firebase a real reporter. */
    protected open fun crashReporter(): CrashReporter = NoCrashReporter

    /** The app module gives builds with Firebase somewhere to send bug reports. */
    protected open fun bugReportSender(): BugReportSender = NoBugReportSender

    /** The build's channel, which the app module knows. */
    protected open val channel: String = "unknown"

    /** Whether the TV screens open on this device, which the app module decides. Called off the main thread. */
    protected open fun opensTvScreens(): Boolean = true

    // What goes with a crash report. Not lazy: it starts with the app, on the phone too.
    private lateinit var crashReports: CrashReports

    override fun onCreate() {
        super.onCreate()
        crashReporter = crashReporter()
        // Before the log starts, so its first line is in the trail too.
        crashReports =
            CrashReports(crashReporter, scope).apply {
                start(
                    facts = { deviceFacts(this@NeoApp, channel, opensTvScreens()) },
                    settings = settingsStore.settings,
                    account = accountChanges.map { KeystoreAccountStore(this@NeoApp).readAccount() },
                    network = networkTypes(this@NeoApp),
                )
            }
        installAppLog(this, packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown")
    }

    /** Sends the bug reports the user asks to send, with the same details and cleaned log a crash report has. */
    val bugReports by lazy {
        BugReports(
            sender = bugReportSender(),
            scrubber = crashReports.scrubber,
            device = {
                deviceFacts(this, channel, opensTvScreens()) +
                    mapOf(
                        "version" to (packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"),
                        "model" to Build.MODEL,
                        "manufacturer" to Build.MANUFACTURER,
                        "android" to "${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})",
                    )
            },
            account = { KeystoreAccountStore(this).readAccount()?.profile?.let { ReportAccount(it.id.toString(), it.name) } },
        )
    }

    override val settingsStore by lazy { SettingsStore.at(File(filesDir, "settings.preferences_pb").absolutePath, scope) }

    /** The settings now, or null until they've been read. */
    val settings: StateFlow<Settings?> by lazy { settingsStore.settings.stateIn(scope, SharingStarted.Eagerly, null) }

    val recentSearches by lazy { RecentSearchesStore.at(File(filesDir, "recent_searches.preferences_pb").absolutePath, scope) }

    /** The shared HTTP client, Cloudflare clearance and WebView extraction every source and the player use. */
    val sourceHost by lazy { SourceHost(this) }

    /**
     * The sources added on this TV, read from its files at start. Their names and IDs are kept out of crash reports;
     * the store's lines are held back until the first list is read.
     */
    val sources by lazy {
        ApkSourceStore(this, scope, sourceHost.services).also { store ->
            scope.launch {
                store.installed.filterNotNull().collect { list ->
                    crashReports.addSourceTerms(list.flatMapTo(mutableSetOf()) { listOf(it.id, it.name) })
                }
            }
        }
    }

    /** Gets the sources' sites, and any browser check they have, ready before an episode is played. */
    val sourceWarmUp by lazy { SourceWarmUp(sources.installed, sources::extension, sourceHost.services, scope) }

    /** Asks the installed sources for an episode's servers. */
    val serverFinder by lazy { ExtensionServerFinder(sources::extension) }

    /** Asks the installed sources for a server's stream. */
    val streamFinder by lazy { ExtensionStreamFinder(sources::extension) }

    /** How far this TV got in each show. */
    val history by lazy { WatchHistoryStore.at(File(filesDir, "watch_history.preferences_pb").absolutePath, scope) }

    /** How many episodes Up next has played on with no button pressed, which decides when it asks (ADR 0055). */
    val watchingRun by lazy { WatchingRun { settingsStore.settings.first().stillWatching } }

    /** Moves the signed-in user's AniList progress as episodes are watched, and sends what's waiting (ADR 0047). */
    val sync by lazy {
        AniListSync(
            scope,
            account = { KeystoreAccountStore(this).readAccount() },
            client = AniListAccountClient(),
            queue = SyncQueueStore.at(File(filesDir, "anilist_queue.preferences_pb").absolutePath, scope),
            // Calendar rather than java.time, which older Fire TVs don't have.
            today = { Calendar.getInstance().run { ListDate(get(Calendar.YEAR), get(Calendar.MONTH) + 1, get(Calendar.DAY_OF_MONTH)) } },
            // Update AniList and private mode gate it (ADR 0053).
            settings = { settingsStore.settings.first() },
            answers = settingsStore,
            // Every change drops the list Home and Details keep, so they follow it.
            yourList = yourList,
        )
    }

    /** The notice with Undo after each change the TV makes to the AniList list (ADR 0056). */
    val notices by lazy { ListNotices(scope, sync.notices) { sync.undo(it) } }

    /** The signed-in user's Watching and Rewatching progress, which Continue watching adds to the history (ADR 0049). */
    val yourList by lazy {
        AniListYourList(
            account = { withContext(Dispatchers.IO) { KeystoreAccountStore(this@NeoApp).readAccount() } },
            client = AniListAccountClient(),
        )
    }

    /** What answering Details' "where to pick up" changes on this TV and AniList (ADR 0050). */
    val pickUpSides by lazy { SyncedSides(history, sync, yourList) }

    /** This week's timetable and show matches for Schedule (ADR 0041). The week it fetched lasts 15 minutes. */
    val schedule by lazy {
        val client = AnimeScheduleClient(BuildConfig.ANIMESCHEDULE_TOKEN.ifBlank { null })
        AnimeScheduleSource(client, ShowMatcher(client, PrefsShowMatchStore(this)), AniListAccountClient())
    }
}
