package ani.saikou.neo.shared.crash

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.StatFs
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

private const val MB = 1024L * 1024L

/**
 * What a crash report says about the device, beyond the model, Android version, memory and disk Crashlytics adds
 * itself. [channel] is the build's channel and [tv] whether the TV screens are open. Blocking: read it off the main
 * thread.
 */
public fun deviceFacts(
    context: Context,
    channel: String,
    tv: Boolean,
): Map<String, String> {
    val memory = ActivityManager.MemoryInfo()
    context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(memory)
    val free = runCatching { StatFs(context.filesDir.path).availableBytes / MB }.getOrDefault(-1)
    return mapOf(
        "channel" to channel,
        "ui" to if (tv) "tv" else "phone",
        "network" to networkType(context),
        "memory_total_mb" to (memory.totalMem / MB).toString(),
        "memory_low" to memory.lowMemory.toString(),
        "storage_free_mb" to free.toString(),
    )
}

/** The kind of network the device is on now: wifi, ethernet, cellular, none or other. */
private fun networkType(context: Context): String {
    val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return "other"
    return try {
        kind(connectivity.activeNetwork?.let(connectivity::getNetworkCapabilities))
    } catch (_: SecurityException) {
        "other"
    }
}

/** The kind of network each time the default network changes. Empty where it can't be watched. */
public fun networkTypes(context: Context): Flow<String> =
    callbackFlow {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities,
                ) {
                    trySend(kind(capabilities))
                }

                override fun onLost(network: Network) {
                    trySend("none")
                }
            }
        val watching =
            try {
                connectivity?.registerDefaultNetworkCallback(callback)
                connectivity != null
            } catch (_: RuntimeException) {
                // A missing permission, or too many callbacks in this app.
                false
            }
        awaitClose { if (watching) runCatching { connectivity?.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()

private fun kind(capabilities: NetworkCapabilities?): String =
    when {
        capabilities == null -> "none"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
        else -> "other"
    }
