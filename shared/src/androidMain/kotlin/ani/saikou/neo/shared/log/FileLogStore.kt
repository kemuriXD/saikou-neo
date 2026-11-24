package ani.saikou.neo.shared.log

import android.content.Context
import android.os.Build
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Keeps this run's lines in `current.log` and the run before in `previous.log`. Writes happen on one background
 * thread. When `current.log` grows past [capBytes] the older half is dropped. IO errors are ignored: the log must
 * never crash the app.
 */
public class FileLogStore internal constructor(
    private val dir: File,
    private val capBytes: Long,
    private val executor: ExecutorService,
) : LogStore {
    public constructor(dir: File, capBytes: Long = 256 * 1024) : this(dir, capBytes, logThread())

    internal constructor(dir: File, executor: ExecutorService) : this(dir, 256 * 1024, executor)

    private val current = File(dir, "current.log")
    private val previous = File(dir, "previous.log")
    private var size = 0L

    init {
        // The first task on the log thread, so it runs before any line and off the caller's thread.
        append { rotate() }
    }

    override fun append(line: String) {
        append { write(line) }
    }

    override fun read(session: LogSession): String {
        flush()
        return try {
            val file = if (session == LogSession.Current) current else previous
            if (file.exists()) file.readText() else ""
        } catch (_: Exception) {
            ""
        }
    }

    override fun flush() {
        try {
            executor.submit {}.get()
        } catch (_: Exception) {
            // Nothing pending that we can wait for.
        }
    }

    private fun append(task: Runnable) {
        try {
            executor.execute(task)
        } catch (_: Exception) {
            // The executor is gone.
        }
    }

    /** Moves the last run's lines to `previous.log`. If that fails, they stay at the top of `current.log`. */
    private fun rotate() {
        try {
            dir.mkdirs()
            previous.delete()
            if (current.exists() && !current.renameTo(previous)) {
                try {
                    current.copyTo(previous, overwrite = true)
                } catch (_: Exception) {
                    size = current.length()
                    return
                }
            }
            current.writeText("")
        } catch (_: Exception) {
            // Logging goes on without a file.
        }
    }

    private fun write(line: String) {
        try {
            val bytes = (line + "\n").toByteArray()
            current.appendBytes(bytes)
            size += bytes.size
            if (size > capBytes) trim()
        } catch (_: Exception) {
            // Dropped line.
        }
    }

    /** Keeps the newest half, starting at a line. */
    private fun trim() {
        val all = current.readBytes()
        var start = all.size / 2
        while (start < all.size && all[start - 1] != '\n'.code.toByte()) start++
        val kept = all.copyOfRange(start, all.size)
        current.writeBytes(kept)
        size = kept.size.toLong()
    }
}

private fun logThread(): ExecutorService =
    Executors.newSingleThreadExecutor { task ->
        Thread(task, "app-log").apply { isDaemon = true }
    }

/** Starts the log in the app's private files, which aren't backed up, and notes which version is running. */
public fun installAppLog(
    context: Context,
    version: String,
) {
    AppLog.install(FileLogStore(File(context.noBackupFilesDir, "logs")))
    AppLog.i("App", "Started version $version on Android ${Build.VERSION.SDK_INT}")
}
