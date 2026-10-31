package ani.saikou.neo.tv.sources

import ani.saikou.neo.extension.api.Edition
import ani.saikou.neo.extension.api.Server
import ani.saikou.neo.extension.api.SourceExtension
import ani.saikou.neo.extension.api.Stream
import ani.saikou.neo.shared.playback.FailReason
import ani.saikou.neo.shared.playback.FoundServer
import ani.saikou.neo.shared.playback.FoundStream
import ani.saikou.neo.shared.playback.ServerEdition
import ani.saikou.neo.shared.playback.SourceFailure
import ani.saikou.neo.shared.playback.StreamFinder
import ani.saikou.neo.shared.sources.InstalledSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/**
 * Asks the source [load] gives for a server's stream, off the main thread, on the threads sources share. Errors read as
 * they do for [ExtensionServerFinder], with an expired link kept apart.
 */
class ExtensionStreamFinder(
    private val load: suspend (InstalledSource) -> SourceExtension?,
) : StreamFinder<Stream> {
    override suspend fun stream(
        source: InstalledSource,
        server: FoundServer,
    ): FoundStream<Stream> = ask(source) { it.stream(server.toServer()) }

    override suspend fun refresh(
        source: InstalledSource,
        server: FoundServer,
        expired: Stream,
    ): FoundStream<Stream> = ask(source) { it.refresh(server.toServer(), expired) }

    private suspend fun ask(
        source: InstalledSource,
        call: suspend (SourceExtension) -> Stream,
    ): FoundStream<Stream> =
        withContext(sourceThreads) {
            try {
                val extension = load(source) ?: throw SourceFailure(FailReason.WontStart)
                val stream = call(extension)
                FoundStream(stream, stream.qualities.maxOf { it.height })
            } catch (e: CancellationException) {
                throw e
            } catch (e: SourceFailure) {
                throw e
            } catch (e: Throwable) {
                throw SourceFailure(e.reason(), e)
            }
        }
}

// The source only reads the id. The runtime it gave was rounded for the list, so it's left out.
private fun FoundServer.toServer() =
    Server(
        id = id,
        name = name,
        edition =
            when (edition) {
                ServerEdition.SUB -> Edition.Sub
                ServerEdition.DUB -> Edition.Dub
                ServerEdition.HARD_SUB -> Edition.HardSub
            },
        runtime = null,
    )
