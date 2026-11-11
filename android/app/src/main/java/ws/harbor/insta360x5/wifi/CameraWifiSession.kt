package ws.harbor.insta360x5.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class CameraWifiStatus {
    Disconnected,
    Connecting,
    Connected,
    Lost,
}

/**
 * Сессия Wi‑Fi камеры через WifiNetworkSpecifier (API 29+).
 * OSC ходит только через [network]; остальной трафик телефона — LTE/обычный Wi‑Fi.
 */
object CameraWifiSession {
    private val networkRef = AtomicReference<Network?>(null)
    private val callbackRef = AtomicReference<ConnectivityManager.NetworkCallback?>(null)
    private val cmRef = AtomicReference<ConnectivityManager?>(null)

    @Volatile
    var status: CameraWifiStatus = CameraWifiStatus.Disconnected
        private set

    val network: Network?
        get() = networkRef.get()

    val isConnected: Boolean
        get() = network != null && status == CameraWifiStatus.Connected

    fun requireNetwork(): Network =
        network ?: throw IllegalStateException(
            "Нет Wi‑Fi сессии камеры. Нажмите «Камера коннект».",
        )

    suspend fun connect(context: Context, ssid: String, password: String, timeoutMs: Long = 60_000): Network {
        val cleanSsid = ssid.trim()
        require(cleanSsid.isNotEmpty()) { "Укажите Wi‑Fi SSID камеры" }

        return withContext(Dispatchers.Main) {
            disconnect(context)
            withTimeout(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    val appCtx = context.applicationContext
                    val cm = appCtx.getSystemService(ConnectivityManager::class.java)
                        ?: run {
                            cont.resumeWithException(IllegalStateException("ConnectivityManager недоступен"))
                            return@suspendCancellableCoroutine
                        }
                    cmRef.set(cm)

                    val specifierBuilder = WifiNetworkSpecifier.Builder().setSsid(cleanSsid)
                    if (password.isNotEmpty()) {
                        specifierBuilder.setWpa2Passphrase(password)
                    }
                    val specifier = specifierBuilder.build()

                    val request = NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .setNetworkSpecifier(specifier)
                        .build()

                    status = CameraWifiStatus.Connecting
                    val callback = object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            networkRef.set(network)
                            status = CameraWifiStatus.Connected
                            if (cont.isActive) cont.resume(network)
                        }

                        override fun onUnavailable() {
                            status = CameraWifiStatus.Disconnected
                            networkRef.set(null)
                            if (cont.isActive) {
                                cont.resumeWithException(
                                    IllegalStateException("Wi‑Fi камеры недоступен (отклонено или таймаут)"),
                                )
                            }
                        }

                        override fun onLost(network: Network) {
                            if (networkRef.get() == network) {
                                networkRef.set(null)
                                status = CameraWifiStatus.Lost
                            }
                        }
                    }
                    callbackRef.set(callback)
                    try {
                        cm.requestNetwork(request, callback)
                    } catch (e: Exception) {
                        status = CameraWifiStatus.Disconnected
                        if (cont.isActive) cont.resumeWithException(e)
                        return@suspendCancellableCoroutine
                    }

                    cont.invokeOnCancellation {
                        runCatching { cm.unregisterNetworkCallback(callback) }
                        if (callbackRef.get() == callback) callbackRef.set(null)
                        if (status == CameraWifiStatus.Connecting) {
                            networkRef.set(null)
                            status = CameraWifiStatus.Disconnected
                        }
                    }
                }
            }
        }
    }

    fun disconnect(context: Context) {
        val cm = cmRef.get() ?: context.applicationContext.getSystemService(ConnectivityManager::class.java)
        val callback = callbackRef.getAndSet(null)
        if (cm != null && callback != null) {
            runCatching { cm.unregisterNetworkCallback(callback) }
        }
        networkRef.set(null)
        status = CameraWifiStatus.Disconnected
    }

    fun statusLabel(): String = when (status) {
        CameraWifiStatus.Disconnected -> "Камера Wi‑Fi: нет"
        CameraWifiStatus.Connecting -> "Камера Wi‑Fi: подключение…"
        CameraWifiStatus.Connected -> "Камера Wi‑Fi: подключено"
        CameraWifiStatus.Lost -> "Камера Wi‑Fi: потеряно"
    }
}
