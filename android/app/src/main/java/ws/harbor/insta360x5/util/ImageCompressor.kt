package ws.harbor.insta360x5.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import ws.harbor.insta360x5.data.AppSettings
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object ImageCompressor {
    fun maybeCompress(src: File, settings: AppSettings): File {
        if (!settings.compress) return src
        return compress(src, settings)
    }

    fun compress(src: File, settings: AppSettings): File {
        val maxSide = AppSettings.RESOLUTION_PRESETS[settings.compressResolution]
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.absolutePath, options)
        val width = options.outWidth
        val height = options.outHeight
        if (width <= 0 || height <= 0) {
            throw IllegalStateException("Не удалось прочитать изображение: ${src.name}")
        }

        var sample = 1
        if (maxSide != null) {
            var w = width
            var h = height
            while (max(w, h) / (sample * 2) > maxSide) {
                sample *= 2
            }
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bitmap = BitmapFactory.decodeFile(src.absolutePath, decodeOpts)
            ?: throw IllegalStateException("Не удалось декодировать: ${src.name}")

        if (maxSide != null) {
            val bw = bitmap.width
            val bh = bitmap.height
            if (max(bw, bh) > maxSide) {
                val (nw, nh) = if (bw >= bh) {
                    maxSide to max(1, (bh.toDouble() * maxSide / bw).roundToInt())
                } else {
                    max(1, (bw.toDouble() * maxSide / bh).roundToInt()) to maxSide
                }
                val scaled = Bitmap.createScaledBitmap(bitmap, nw, nh, true)
                if (scaled !== bitmap) bitmap.recycle()
                bitmap = scaled
            }
        }

        val dest = if (settings.compressKeepOriginal) {
            File(src.parentFile, "${src.nameWithoutExtension}_${settings.compressResolution}_q${settings.compressQuality}.jpg")
        } else if (src.extension.lowercase() in listOf("jpg", "jpeg")) {
            src
        } else {
            File(src.parentFile, "${src.nameWithoutExtension}.jpg")
        }

        val tmp = File(dest.parentFile, "${dest.name}.tmp")
        FileOutputStream(tmp).use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, settings.compressQuality, out)) {
                throw IllegalStateException("Ошибка JPEG compress")
            }
        }
        bitmap.recycle()
        if (dest.exists() && dest != tmp) dest.delete()
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }

        if (!settings.compressKeepOriginal && dest.absolutePath != src.absolutePath && src.exists()) {
            src.delete()
        }
        return dest
    }
}
