package ani.saikou.neo.shared.crash

/** Keeps the keys and recovered errors a crash report would get. */
class RecordingReporter : CrashReporter {
    val keys = mutableMapOf<String, String>()
    val recovered = mutableListOf<Pair<String, Throwable?>>()

    override fun setEnabled(enabled: Boolean) = Unit

    override fun setUser(
        id: String?,
        name: String?,
    ) = Unit

    override fun setKey(
        key: String,
        value: String,
    ) {
        keys[key] = value
    }

    override fun log(line: String) = Unit

    override fun recordNonFatal(
        message: String,
        error: Throwable?,
    ) {
        recovered += message to error
    }
}
