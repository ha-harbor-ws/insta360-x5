package ws.harbor.insta360x5.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

object PhotoOverlay {
    private val TEXT_BLUE = Color.parseColor("#1565C0")
    private val STROKE_WHITE = Color.WHITE

    fun formatDateTime(millis: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(millis))

    /**
     * Вшивает номер колодца (слева сверху) и дату/время (справа сверху) синим шрифтом.
     * Возвращает тот же или новый JPEG-файл.
     */
    fun burnIntoFile(
        src: File,
        wellNumber: String,
        dateTime: String = formatDateTime(),
        jpegQuality: Int = 90,
    ): File {
        val original = BitmapFactory.decodeFile(src.absolutePath)
            ?: throw IllegalStateException("Не удалось декодировать для оверлея: ${src.name}")
        val mutable = if (original.isMutable) {
            original
        } else {
            original.copy(Bitmap.Config.ARGB_8888, true).also { original.recycle() }
        }

        drawOnBitmap(mutable, wellNumber, dateTime)

        val dest = if (src.extension.lowercase() in listOf("jpg", "jpeg")) {
            src
        } else {
            File(src.parentFile, "${src.nameWithoutExtension}.jpg")
        }
        val tmp = File(dest.parentFile, "${dest.name}.overlay.tmp")
        FileOutputStream(tmp).use { out ->
            if (!mutable.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(50, 100), out)) {
                throw IllegalStateException("Ошибка записи оверлея JPEG")
            }
        }
        mutable.recycle()
        if (dest.exists() && dest != tmp) dest.delete()
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
        if (dest.absolutePath != src.absolutePath && src.exists()) {
            src.delete()
        }
        return dest
    }

    fun drawOnBitmap(bitmap: Bitmap, wellNumber: String, dateTime: String) {
        val canvas = Canvas(bitmap)
        val minSide = max(bitmap.width, bitmap.height).toFloat()
        val textSize = max(28f, minSide * 0.035f)
        val pad = max(16f, minSide * 0.02f)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_BLUE
            this.textSize = textSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            style = Paint.Style.FILL
        }
        val stroke = Paint(fill).apply {
            color = STROKE_WHITE
            style = Paint.Style.STROKE
            strokeWidth = max(2f, textSize * 0.08f)
        }

        val left = wellNumber.ifBlank { "—" }
        val right = dateTime.ifBlank { formatDateTime() }

        val baseline = pad - fill.ascent()
        canvas.drawText(left, pad, baseline, stroke)
        canvas.drawText(left, pad, baseline, fill)

        val rightWidth = fill.measureText(right)
        val rightX = bitmap.width - pad - rightWidth
        canvas.drawText(right, rightX, baseline, stroke)
        canvas.drawText(right, rightX, baseline, fill)
    }
}
