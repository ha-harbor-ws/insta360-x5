package ws.harbor.insta360x5.data

import android.content.Context
import android.content.SharedPreferences

data class AppSettings(
    var host: String = "192.168.42.1",
    var port: Int = 80,
    var stitch: String = "ondevice",
    var hdr: String = "off",
    var download: Boolean = true,
    var deleteAfterDownload: Boolean = false,
    var compress: Boolean = false,
    var compressResolution: String = "4k",
    var compressQuality: Int = 85,
    var compressKeepOriginal: Boolean = false,
) {
    fun summary(): String {
        val compressLabel = if (compress) {
            "$compressResolution/q$compressQuality"
        } else {
            "off"
        }
        return "stitch=$stitch | hdr=$hdr | download=${onOff(download)} | " +
            "del-after=${onOff(deleteAfterDownload)} | compress=$compressLabel"
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
                host = p.getString("host", "192.168.42.1") ?: "192.168.42.1",
                port = p.getInt("port", 80),
                stitch = p.getString("stitch", "ondevice") ?: "ondevice",
                hdr = p.getString("hdr", "off") ?: "off",
                download = p.getBoolean("download", true),
                deleteAfterDownload = p.getBoolean("deleteAfterDownload", false),
                compress = p.getBoolean("compress", false),
                compressResolution = p.getString("compressResolution", "4k") ?: "4k",
                compressQuality = p.getInt("compressQuality", 85),
                compressKeepOriginal = p.getBoolean("compressKeepOriginal", false),
            )
        }

        fun save(context: Context, settings: AppSettings) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
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
                .apply()
        }
    }
}
