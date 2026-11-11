package ws.harbor.insta360x5.wifi

import android.content.Context
import android.net.Network
import ws.harbor.insta360x5.data.AppSettings
import ws.harbor.insta360x5.osc.OscClient
import ws.harbor.insta360x5.osc.OscException

data class CameraConnectResult(
    val network: Network,
    val infoText: String,
)

/**
 * Подключение к AP камеры + проверка OSC /osc/info.
 */
suspend fun connectAndProbeCamera(
    context: Context,
    ssid: String,
    password: String,
    host: String = "192.168.42.1",
    port: Int = 80,
): CameraConnectResult {
    val network = CameraWifiSession.connect(context, ssid, password)
    return try {
        val info = OscClient(host, port, network).info()
        val text = buildString {
            appendLine("Wi‑Fi OK: $ssid")
            appendLine("Модель: ${info.optString("model")}")
            appendLine("Прошивка: ${info.optString("firmwareVersion")}")
            appendLine("S/N: ${info.optString("serialNumber")}")
        }.trim()
        CameraConnectResult(network, text)
    } catch (e: Exception) {
        throw OscException(
            "Wi‑Fi подключён, но OSC не ответил ($host:$port): ${e.message}",
        )
    }
}

fun AppSettings.createOscClient(): OscClient =
    OscClient(host, port, CameraWifiSession.requireNetwork())
