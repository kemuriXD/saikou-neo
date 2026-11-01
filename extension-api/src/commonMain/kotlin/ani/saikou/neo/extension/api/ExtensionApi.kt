package ani.saikou.neo.extension.api

/**
 * The version of this API. Any change bumps [VERSION]; only a change that
 * breaks sources built before it also bumps [MIN_VERSION].
 */
public object ExtensionApi {
    /**
     * The version a source is built against. A source declares it in its
     * manifest, which the app reads before loading any of its code.
     */
    public const val VERSION: Int = 2

    /**
     * The oldest version a source can be built for and still run. It only goes
     * up when a change breaks existing sources, such as a removed or renamed
     * type or a changed function. Adding things leaves it where it is.
     */
    public const val MIN_VERSION: Int = 1

    /** The manifest `meta-data` that holds the [VERSION] a source was built for, as an integer. */
    public const val VERSION_KEY: String = "ani.saikou.neo.extension.api"

    /**
     * The manifest `meta-data` that names the source's class. It implements
     * [SourceExtension] and has a constructor with no arguments.
     */
    public const val CLASS_KEY: String = "ani.saikou.neo.extension.class"

    /**
     * Whether a source built for [builtFor] can run in this app. A source built
     * for a newer version may use types this app doesn't have, so it's refused.
     */
    public fun compatibility(builtFor: Int): Compatibility = compatibility(builtFor, VERSION, MIN_VERSION)
}

internal fun compatibility(
    builtFor: Int,
    version: Int,
    minVersion: Int,
): Compatibility =
    when {
        builtFor < minVersion -> Compatibility.BuiltForOlderApp
        builtFor > version -> Compatibility.BuiltForNewerApp
        else -> Compatibility.Compatible
    }

/** The result of [ExtensionApi.compatibility]. Each refusal has its own message in the app. */
public enum class Compatibility {
    Compatible,

    /** The source was built before a breaking change, so it needs a newer version of itself. */
    BuiltForOlderApp,

    /** The app needs updating. */
    BuiltForNewerApp,
}
