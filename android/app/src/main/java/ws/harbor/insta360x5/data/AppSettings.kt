package ws.harbor.insta360x5.data

import android.content.Context

data class AppSettings(
    var wifiSsid: String = "",
    var wifiPassword: String = "",
    var host: String = "192.168.42.1",
    var port: Int = 80,
    var stitch: String = "ondevice",
    var hdr: String = "off",
    var download: Boolean = true,
    var deleteAfterDownload: Boolean = false,
    var compress: Boolean = true,
    var compressResolution: String = "1080",
    var compressQuality: Int = 85,
    var compressKeepOriginal: Boolean = false,
    /** Печатать номер колодца и дату на фото (вшить в файл). По умолчанию включено. */
    var printOverlay: Boolean = true,
) {
    fun hasWifiCredentials(): Boolean = wifiSsid.isNotBlank() && wifiPassword.isNotBlank()

    fun summary(): String {
        val compressLabel = if (compress) {
            "$compressResolution/q$compressQuality"
        } else {
            "off"
        }
        val wifi = if (wifiSsid.isBlank()) "ssid=—" else "ssid=$wifiSsid"
        return "$wifi | stitch=$stitch | hdr=$hdr | download=${onOff(download)} | " +
            "del-after=${onOff(deleteAfterDownload)} | compress=$compressLabel | " +
            "print=${onOff(printOverlay)}"
    }

    private fun onOff(value: Boolean) = if (value) "on" else "off"

    companion object {
        val RESOLUTION_PRESETS = linkedMapOf(
            "original" to null,
            "8k" to 7680,
            "6k" to 6144,
            "4k" to 3840,
            "2k" to 2048,
            "1080" to 1920,
            "720" to 1280,
        )
        val QUALITY_PRESETS = listOf(95, 90, 85, 80, 75, 60, 50)
        val STITCH_OPTIONS = listOf("ondevice", "none")
        val HDR_OPTIONS = listOf("off", "hdr")

        private const val PREFS = "insta360_settings"

        fun load(context: Context): AppSettings {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return AppSettings(
                wifiSsid = p.getString("wifiSsid", "") ?: "",
                wifiPassword = p.getString("wifiPassword", "") ?: "",
                host = p.getString("host", "192.168.42.1") ?: "192.168.42.1",
                port = p.getInt("port", 80),
                stitch = p.getString("stitch", "ondevice") ?: "ondevice",
                hdr = p.getString("hdr", "off") ?: "off",
                download = p.getBoolean("download", true),
                deleteAfterDownload = p.getBoolean("deleteAfterDownload", false),
                compress = p.getBoolean("compress", true),
                compressResolution = p.getString("compressResolution", "1080") ?: "1080",
                compressQuality = p.getInt("compressQuality", 85),
                compressKeepOriginal = p.getBoolean("compressKeepOriginal", false),
                printOverlay = p.getBoolean("printOverlay", true),
            )
        }

        fun save(context: Context, settings: AppSettings) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("wifiSsid", settings.wifiSsid)
                .putString("wifiPassword", settings.wifiPassword)
                .putString("host", settings.host)
                .putInt("port", settings.port)
                .putString("stitch", settings.stitch)
                .putString("hdr", settings.hdr)
                .putBoolean("download", settings.download)
                .putBoolean("deleteAfterDownload", settings.deleteAfterDownload)
                .putBoolean("compress", settings.compress)
                .putString("compressResolution", settings.compressResolution)
                .putInt("compressQuality", settings.compressQuality)
                .putBoolean("compressKeepOriginal", settings.compressKeepOriginal)
                .putBoolean("printOverlay", settings.printOverlay)
                .apply()
        }

        /** Сохранить всё, кроме Wi‑Fi (SSID/пароль остаются прежними в prefs). */
        fun saveWithoutWifi(context: Context, settings: AppSettings) {
            val current = load(context)
            settings.wifiSsid = current.wifiSsid
            settings.wifiPassword = current.wifiPassword
            save(context, settings)
        }
    }
}
