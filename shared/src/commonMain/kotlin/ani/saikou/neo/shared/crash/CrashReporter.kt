package ani.saikou.neo.shared.crash

/** Where crash reports go. The app has no reporter unless a build adds one. */
public interface CrashReporter {
    /** Turns sending reports on or off. Off also deletes reports waiting on the device. */
    public fun setEnabled(enabled: Boolean)

    /** Tags reports with the signed-in user. Null clears it. */
    public fun setUser(
        id: String?,
        name: String?,
    )

    public fun setKey(
        key: String,
        value: String,
    )

    /** Adds a line to the trail kept with the next report. */
    public fun log(line: String)

    /**
     * Records an error the app recovered from. [message] is already cleaned and is the only text sent. [error], when
     * there is one, gives only the app's stack frames, so reports group by where it happened. Its own message is never sent.
     */
    public fun recordNonFatal(
        message: String,
        error: Throwable?,
    )
}

/** A [CrashReporter] that does nothing. */
public object NoCrashReporter : CrashReporter {
    override fun setEnabled(enabled: Boolean): Unit = Unit

    override fun setUser(
        id: String?,
        name: String?,
    ): Unit = Unit

    override fun setKey(
        key: String,
        value: String,
    ): Unit = Unit

    override fun log(line: String): Unit = Unit

    override fun recordNonFatal(
        message: String,
        error: Throwable?,
    ): Unit = Unit
}
