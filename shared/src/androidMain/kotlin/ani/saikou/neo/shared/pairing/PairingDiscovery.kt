package ani.saikou.neo.shared.pairing

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.Executors

/**
 * Advertises [invite] under [code] with Android's network service discovery, in the shape [PAIRING_SERVICE_TYPE]
 * describes. Closing it stops the advert. If the advert can't start, the QR code still works, so that's not an error.
 */
internal fun advertisePairing(
    context: Context,
    code: String,
    invite: PairingInvite,
): AutoCloseable {
    val nsd = context.getSystemService(NsdManager::class.java) ?: return AutoCloseable {}
    val service =
        NsdServiceInfo().apply {
            serviceName = code
            serviceType = PAIRING_SERVICE_TYPE
            port = invite.port
            invite.advertAttributes().forEach(::setAttribute)
        }
    val listener =
        object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit

            override fun onRegistrationFailed(
                info: NsdServiceInfo,
                errorCode: Int,
            ) = Unit

            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit

            override fun onUnregistrationFailed(
                info: NsdServiceInfo,
                errorCode: Int,
            ) = Unit
        }
    try {
        nsd.registerService(service, NsdManager.PROTOCOL_DNS_SD, listener)
    } catch (_: RuntimeException) {
        // NSD refused the advert. Scanning still works.
        return AutoCloseable {}
    }
    return AutoCloseable {
        try {
            nsd.unregisterService(listener)
        } catch (_: IllegalArgumentException) {
            // Already gone.
        }
    }
}

/**
 * The phone's lookup for a typed code (#381): browses the local network for a TV advertising [code] and gives the
 * invite it carries, or null if none answers within [TV_LOOKUP_MILLIS].
 */
public suspend fun lookupTvByCode(
    context: Context,
    code: String,
): PairingInvite? {
    val nsd = context.getSystemService(NsdManager::class.java) ?: return null
    return findAdvertisedTv(code, advertsNamed(nsd, code))
}

/** Every IPv4 address services named [code] resolve to. Other services on the network aren't resolved. */
private fun advertsNamed(
    nsd: NsdManager,
    code: String,
): Flow<AdvertisedTv> =
    callbackFlow {
        val stops = mutableListOf<() -> Unit>()
        var stopped = false
        val discovery =
            object : NsdManager.DiscoveryListener {
                override fun onServiceFound(service: NsdServiceInfo) {
                    if (!service.serviceName.equals(code, ignoreCase = true)) return
                    // Discovery stops asynchronously, so a service can still turn up after the search ended.
                    synchronized(stops) {
                        if (!stopped) stops += resolve(nsd, service) { advert -> trySend(advert) }
                    }
                }

                override fun onStartDiscoveryFailed(
                    serviceType: String,
                    errorCode: Int,
                ) {
                    close(PairingException("Couldn't look for TVs on this network ($errorCode)"))
                }

                override fun onDiscoveryStarted(serviceType: String) = Unit

                override fun onServiceLost(service: NsdServiceInfo) = Unit

                override fun onDiscoveryStopped(serviceType: String) = Unit

                override fun onStopDiscoveryFailed(
                    serviceType: String,
                    errorCode: Int,
                ) = Unit
            }
        nsd.discoverServices(PAIRING_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery)
        awaitClose {
            try {
                nsd.stopServiceDiscovery(discovery)
            } catch (_: IllegalArgumentException) {
                // Discovery never started.
            }
            synchronized(stops) {
                stopped = true
                stops.forEach { it() }
            }
        }
    }

/** Resolves [service] and passes on each IPv4 address it has. Returns how to stop. */
private fun resolve(
    nsd: NsdManager,
    service: NsdServiceInfo,
    found: (AdvertisedTv) -> Unit,
): () -> Unit =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        resolveWithCallback(nsd, service, found)
    } else {
        resolveOnce(nsd, service, found)
        ({})
    }

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private fun resolveWithCallback(
    nsd: NsdManager,
    service: NsdServiceInfo,
    found: (AdvertisedTv) -> Unit,
): () -> Unit {
    val executor = Executors.newSingleThreadExecutor()
    val callback =
        object : NsdManager.ServiceInfoCallback {
            override fun onServiceUpdated(info: NsdServiceInfo) = info.hostAddresses.forEach { found(advert(info, it)) }

            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) = Unit

            override fun onServiceLost() = Unit

            override fun onServiceInfoCallbackUnregistered() = Unit
        }
    nsd.registerServiceInfoCallback(service, executor, callback)
    return {
        try {
            nsd.unregisterServiceInfoCallback(callback)
        } catch (_: IllegalArgumentException) {
            // Registration failed, so there's nothing to stop.
        }
        executor.shutdown()
    }
}

@Suppress("DEPRECATION") // The only way to resolve before Android 14.
private fun resolveOnce(
    nsd: NsdManager,
    service: NsdServiceInfo,
    found: (AdvertisedTv) -> Unit,
) {
    nsd.resolveService(
        service,
        object : NsdManager.ResolveListener {
            override fun onServiceResolved(info: NsdServiceInfo) {
                info.host?.let { found(advert(info, it)) }
            }

            override fun onResolveFailed(
                info: NsdServiceInfo,
                errorCode: Int,
            ) = Unit
        },
    )
}

/** What [info] says at [address]. Anything but IPv4 gets an empty host, which [AdvertisedTv.toInvite] refuses. */
private fun advert(
    info: NsdServiceInfo,
    address: InetAddress,
) = AdvertisedTv(
    name = info.serviceName,
    host = (address as? Inet4Address)?.hostAddress.orEmpty(),
    port = info.port,
    attributes = info.attributes.mapValues { (_, value) -> value?.decodeToString().orEmpty() },
)
