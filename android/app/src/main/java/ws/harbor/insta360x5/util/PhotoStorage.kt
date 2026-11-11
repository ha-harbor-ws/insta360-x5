package ws.harbor.insta360x5.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream

data class GalleryPhoto(
    val id: Long,
    val uri: Uri,
    val name: String,
    val dateAdded: Long,
)

object PhotoStorage {
    private const val ALBUM = "Insta360X5"
    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "heic")

    fun cacheDir(context: Context): File {
        val dir = File(context.cacheDir, "photos")
        dir.mkdirs()
        return dir
    }

    fun sanitizeFileBase(raw: String): String {
        val cleaned = raw.trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), "_")
        return cleaned.ifBlank { "photo" }
    }

    /** Номер колодца из имени файла (без расширения). */
    fun wellNumberFromFileName(name: String): String {
        val base = name.substringBeforeLast('.').ifBlank { name }
        return base.ifBlank { name }
    }

    fun saveFileToGallery(context: Context, source: File, displayName: String): Uri {
        val name = if (displayName.contains('.')) displayName else "$displayName.jpg"
        val mime = when (name.substringAfterLast('.', "jpg").lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("Не удалось создать запись в галерее")

        resolver.openOutputStream(uri)?.use { out ->
            FileInputStream(source).use { input -> input.copyTo(out) }
        } ?: throw IllegalStateException("Не удалось записать файл в галерею")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }

    fun listGalleryPhotos(context: Context): List<GalleryPhoto> {
        val photos = mutableListOf<GalleryPhoto>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATA,
        )
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val selection: String?
        val args: Array<String>?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            args = arrayOf("%$ALBUM%")
        } else {
            selection = "${MediaStore.Images.Media.DATA} LIKE ?"
            args = arrayOf("%/${Environment.DIRECTORY_PICTURES}/$ALBUM/%")
        }

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            sort,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "photo.jpg"
                val date = cursor.getLong(dateCol)
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                if (name.substringAfterLast('.', "").lowercase() in IMAGE_EXT || name.contains('.')) {
                    photos += GalleryPhoto(id, uri, name, date)
                }
            }
        }
        return photos
    }

    fun decodeUriBounds(context: Context, uri: Uri, maxSide: Int): android.graphics.Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
        var sample = 1
        var w = opts.outWidth
        var h = opts.outHeight
        while (maxOf(w, h) / (sample * 2) > maxSide) {
            sample *= 2
        }
        val decode = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decode)
        }
    }
}
