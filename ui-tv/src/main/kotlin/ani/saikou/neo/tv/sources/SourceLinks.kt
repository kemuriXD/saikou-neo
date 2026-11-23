package ani.saikou.neo.tv.sources

import java.io.File
import java.io.IOException

/** The links of the lists sources came from, each kept in a small file next to the source's APK in [folder] (ADR 0046). */
internal class SourceLinks(
    private val folder: File,
) {
    /** The link of the list the source with [id] came from, or null when it has none or it can't be read. */
    fun read(id: String): String? =
        try {
            file(id).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }
        } catch (_: IOException) {
            null
        }

    /**
     * Keeps [list] as the link the source with [id] came from, or removes the one it had when null. Returns the link
     * kept now, which is still the old one when [list] couldn't be written.
     */
    fun keep(
        id: String,
        list: String?,
    ): String? {
        val file = file(id)
        try {
            if (list == null) file.delete() else file.writeText(list)
        } catch (_: IOException) {
            // Read back below, so the source is shown with the link that's really saved.
        }
        return read(id)
    }

    /** Removes the link of the source with [id]. */
    fun forget(id: String) {
        file(id).delete()
    }

    private fun file(id: String) = File(folder, id + LIST)

    private companion object {
        const val LIST = ".list"
    }
}

/**
 * Puts a read-only copy of [incoming] in [kept]'s place. The copy is made beside it first and then renamed over it, so a
 * copy that fails leaves the old file as it was. Throws [IOException] when it can't.
 */
internal fun swapIn(
    incoming: File,
    kept: File,
) {
    val fresh = File(kept.parentFile, kept.name + ".new")
    fresh.delete()
    try {
        incoming.copyTo(fresh)
        fresh.setReadOnly()
        if (!fresh.renameTo(kept)) {
            // Where a rename can't replace a file, the old one goes only now the new one is whole.
            kept.delete()
            if (!fresh.renameTo(kept)) throw IOException("Couldn't put ${kept.name} in place")
        }
    } finally {
        fresh.delete()
    }
}
