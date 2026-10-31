package ani.saikou.neo.tv.sources

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import ani.saikou.neo.extension.api.Compatibility
import ani.saikou.neo.extension.api.ExtensionApi
import ani.saikou.neo.extension.api.SourceExtension
import ani.saikou.neo.shared.sources.InstalledSource
import ani.saikou.neo.shared.sources.SourceProblem
import ani.saikou.neo.shared.sources.SourceStore
import ani.saikou.neo.shared.sources.sourceIdFormat
import dalvik.system.DexClassLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Why a source built for [builtFor] can't be used in this app, or null when it can. */
internal fun problemFor(builtFor: Int): SourceProblem? =
    when (ExtensionApi.compatibility(builtFor)) {
        Compatibility.Compatible -> null
        Compatibility.BuiltForOlderApp -> SourceProblem.BUILT_FOR_OLDER_APP
        Compatibility.BuiltForNewerApp -> SourceProblem.BUILT_FOR_NEWER_APP
    }

/**
 * Keeps sources as APKs in the app's files and loads them in-process (ADR 0003). Each APK's manifest is read before
 * any of its code: the API version it was built for and its class. Its code loads parent-first, so it gets the app's
 * own `:extension-api` and Kotlin classes even when it bundles copies of them. [scope] reads the list at start, on a
 * background thread.
 */
class ApkSourceStore(
    private val context: Context,
    scope: CoroutineScope,
) : SourceStore {
    private val folder = File(context.filesDir, "sources")
    private val lock = Mutex()
    private val sources = MutableStateFlow<List<InstalledSource>?>(null)

    // Each source's code is loaded once, when it's first asked for servers, and again after it's replaced.
    private val loaded = mutableMapOf<String, SourceExtension>()
    override val installed: StateFlow<List<InstalledSource>?> = sources

    init {
        scope.launch {
            lock.withLock {
                sources.value =
                    folder
                        .listFiles { file -> file.name.endsWith(APK) }
                        .orEmpty()
                        .mapNotNull { manifest(it)?.source }
                        .sorted()
            }
        }
    }

    override fun problemFor(builtFor: Int): SourceProblem? =
        ani.saikou.neo.tv.sources
            .problemFor(builtFor)

    override suspend fun install(
        apk: ByteArray,
        id: String?,
    ): InstalledSource? = withContext(Dispatchers.IO) { lock.withLock { installLocked(apk, id) } }

    private fun installLocked(
        apk: ByteArray,
        id: String?,
    ): InstalledSource? {
        val incoming = File.createTempFile("source", APK, context.cacheDir)
        return try {
            incoming.writeBytes(apk)
            // Android 14 and later only load code from files that can't be written.
            incoming.setReadOnly()
            val manifest = manifest(incoming) ?: return null
            val source = manifest.source
            if (id != null && source.id != id) return null
            if (source.problem != null) return source
            // Loaded once here so a source whose code doesn't fit is never kept.
            if (load(incoming, manifest.className) == null) return source.copy(problem = SourceProblem.BROKEN)
            folder.mkdirs()
            val kept = File(folder, source.id + APK)
            kept.delete()
            incoming.copyTo(kept)
            kept.setReadOnly()
            loaded.remove(source.id)
            sources.value = (sources.value.orEmpty().filter { it.id != source.id } + source).sorted()
            source
        } catch (_: IOException) {
            null
        } finally {
            incoming.delete()
        }
    }

    override suspend fun remove(id: String): Unit =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val listed = sources.value.orEmpty()
                // Only a listed id names a file here.
                if (listed.any { it.id == id }) {
                    File(folder, id + APK).delete()
                    loaded.remove(id)
                    sources.value = listed.filter { it.id != id }
                }
            }
        }

    /**
     * The code of [source], loaded in-process, or null when it isn't installed or doesn't load. The source's own code
     * first runs outside the lock, so one that hangs as it starts doesn't hold up the other sources, installs or removes.
     */
    suspend fun extension(source: InstalledSource): SourceExtension? =
        withContext(Dispatchers.IO) {
            val (listed, type) =
                lock.withLock {
                    val listed =
                        sources.value.orEmpty().firstOrNull { it.id == source.id && it.problem == null }
                            ?: return@withContext null
                    loaded[source.id]?.let { return@withContext it }
                    val apk = File(folder, source.id + APK)
                    val manifest = manifest(apk)?.takeIf { it.source.problem == null } ?: return@withContext null
                    listed to (loadClass(apk, manifest.className) ?: return@withContext null)
                }
            val extension = instance(type) ?: return@withContext null
            // Kept only if the same copy is still installed, so one replaced or removed meanwhile isn't used again.
            lock.withLock { if (sources.value.orEmpty().any { it === listed }) loaded.getOrPut(source.id) { extension } else null }
        }

    private class Manifest(
        val source: InstalledSource,
        val className: String,
    )

    /** What [apk]'s manifest says about the source, or null when it isn't a source. */
    private fun manifest(apk: File): Manifest? {
        val packages = context.packageManager
        val info = archiveInfo(packages, apk.path) ?: return null
        val app = info.applicationInfo ?: return null
        val meta = app.metaData ?: return null
        val id = info.packageName.takeIf { it.matches(sourceIdFormat) } ?: return null
        if (!meta.containsKey(ExtensionApi.VERSION_KEY)) return null
        val builtFor = meta.getInt(ExtensionApi.VERSION_KEY, -1)
        val className = meta.getString(ExtensionApi.CLASS_KEY) ?: return null
        // The label is read from the APK itself.
        app.sourceDir = apk.path
        app.publicSourceDir = apk.path
        val name = app.loadLabel(packages).toString().take(MAX_TEXT)
        val version = info.versionName.orEmpty().take(MAX_TEXT)
        return Manifest(InstalledSource(id, name, version, problemFor(builtFor)), className)
    }

    /** An instance of [className] from [apk], or null when its code doesn't fit the app. */
    private fun load(
        apk: File,
        className: String,
    ): SourceExtension? = loadClass(apk, className)?.let(::instance)

    /** The class [className] from [apk], not yet started, so none of the source's code has run. */
    private fun loadClass(
        apk: File,
        className: String,
    ): Class<*>? =
        loading {
            DexClassLoader(apk.path, context.codeCacheDir.path, null, javaClass.classLoader).loadClass(className)
        }

    /** A new [type], which runs the source's static initialiser and constructor. */
    private fun instance(type: Class<*>): SourceExtension? = loading { type.getDeclaredConstructor().newInstance() as? SourceExtension }

    private fun <T> loading(block: () -> T): T? =
        try {
            block()
        } catch (_: ReflectiveOperationException) {
            null
        } catch (_: LinkageError) {
            // Includes NoSuchMethodError, AbstractMethodError and a failing static initialiser.
            null
        } catch (_: SecurityException) {
            null
        }

    private companion object {
        const val APK = ".apk"
        const val MAX_TEXT = 60
    }
}

private fun List<InstalledSource>.sorted() = sortedBy { it.name.lowercase() }

private fun archiveInfo(
    packages: PackageManager,
    path: String,
): PackageInfo? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packages.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
    } else {
        @Suppress("DEPRECATION")
        packages.getPackageArchiveInfo(path, PackageManager.GET_META_DATA)
    }
