package ws.harbor.insta360x5.util

import android.content.Context
import java.io.File

object PhotoStorage {
    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "heic")

    fun photosDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "photos")
        dir.mkdirs()
        return dir
    }

    fun listPhotos(context: Context): List<File> {
        val dir = photosDir(context)
        return dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in IMAGE_EXT }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
    }
}
