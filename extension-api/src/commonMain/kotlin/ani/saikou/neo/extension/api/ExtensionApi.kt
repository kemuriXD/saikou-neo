package ani.saikou.neo.extension.api

/** The version of this API. Breaking changes bump it (ADR 0003). */
public object ExtensionApi {
    /**
     * The version a source is built against. It's a constant, so its value is
     * copied into the source when it's compiled and the app can read it back.
     */
    public const val VERSION: Int = 1

    /** Whether a source built for [builtFor] can run in this app. */
    public fun compatibility(builtFor: Int): Compatibility =
        when {
            builtFor < VERSION -> Compatibility.BuiltForOlderApp
            builtFor > VERSION -> Compatibility.BuiltForNewerApp
            else -> Compatibility.Compatible
        }
}

/** The result of [ExtensionApi.compatibility]. Each refusal has its own message in the app. */
public enum class Compatibility {
    Compatible,

    /** The source needs a newer version of itself. */
    BuiltForOlderApp,

    /** The app needs updating. */
    BuiltForNewerApp,
}
