package ani.saikou.neo.extension.api

/** The version of this API. Breaking changes bump it (ADR 0003). */
public object ExtensionApi {
    /**
     * The version a source is built against. A source declares it in its
     * manifest, which the app reads before loading any of its code.
     */
    public const val VERSION: Int = 1

    /** The manifest `meta-data` that holds the [VERSION] a source was built for, as an integer. */
    public const val VERSION_KEY: String = "ani.saikou.neo.extension.api"

    /**
     * The manifest `meta-data` that names the source's class. It implements
     * [SourceExtension] and has a constructor with no arguments.
     */
    public const val CLASS_KEY: String = "ani.saikou.neo.extension.class"

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
