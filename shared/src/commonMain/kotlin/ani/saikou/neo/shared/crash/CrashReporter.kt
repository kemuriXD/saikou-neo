package ani.saikou.neo.shared.crash

/** Where crash reports go. The app has no reporter unless a build adds one. */
public interface CrashReporter {
    /** Turns sending reports on or off. */
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
}
