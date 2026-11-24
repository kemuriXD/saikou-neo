package ani.saikou.neo.shared.crash

// The app's own packages. Extensions can use any other, the app's name included, such as the example's.
private val appPackages =
    listOf(
        "ani.saikou.neo.shared.",
        "ani.saikou.neo.tv.",
        "ani.saikou.neo.phone.",
        "ani.saikou.neo.crash.",
        "ani.saikou.neo.extension.api.",
    )

private val platformPackages =
    listOf("java.", "javax.", "kotlin.", "kotlinx.", "android.", "androidx.", "dalvik.", "com.android.", "libcore.", "okhttp3.", "okio.")

/**
 * The frames of [frames] from the app and the platform, for a recovered error's report. An extension's frames go, as
 * its package can name the source. Null when none are left.
 */
public fun appFrames(frames: Array<StackTraceElement>?): Array<StackTraceElement>? =
    frames
        ?.filter { frame ->
            val name = frame.className
            // The app module's own classes sit right in ani.saikou.neo.
            (appPackages + platformPackages).any(name::startsWith) || name.substringBeforeLast('.') == "ani.saikou.neo"
        }?.takeIf { it.isNotEmpty() }
        ?.toTypedArray()
