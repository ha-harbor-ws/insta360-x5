package ws.harbor.insta360x5.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ws.harbor.insta360x5.databinding.ActivityPhotosBinding
import ws.harbor.insta360x5.databinding.ItemPhotoBinding
import ws.harbor.insta360x5.util.PhotoStorage
import java.io.File

class PhotosActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPhotosBinding
    private val adapter = PhotosAdapter { file, index ->
        startActivity(
            Intent(this, PhotoViewActivity::class.java)
                .putExtra(PhotoViewActivity.EXTRA_INDEX, index)
                .putExtra(PhotoViewActivity.EXTRA_PATH, file.absolutePath),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotosBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "Скачанные фото"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.photosGrid.layoutManager = GridLayoutManager(this, 2)
        binding.photosGrid.adapter = adapter

        val highlight = intent.getStringExtra(EXTRA_HIGHLIGHT_PATH)
        if (!highlight.isNullOrBlank()) {
            // open viewer for just-downloaded file after first load
            openHighlight = highlight
        }
    }

    private var openHighlight: String? = null

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun reload() {
        val files = PhotoStorage.listPhotos(this)
        adapter.submit(files)
        binding.photosStatus.text = if (files.isEmpty()) {
            "Нет скачанных фото. Сделайте снимок или скачайте файлы с камеры."
        } else {
            "Фото: ${files.size}"
        }

        val path = openHighlight
        if (path != null) {
            openHighlight = null
            val index = files.indexOfFirst { it.absolutePath == path }.coerceAtLeast(0)
            if (files.isNotEmpty()) {
                startActivity(
                    Intent(this, PhotoViewActivity::class.java)
                        .putExtra(PhotoViewActivity.EXTRA_INDEX, index)
                        .putExtra(PhotoViewActivity.EXTRA_PATH, files[index].absolutePath),
                )
            } else {
                Toast.makeText(this, "Файл не найден", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val EXTRA_HIGHLIGHT_PATH = "highlight_path"
    }
}

private class PhotosAdapter(
    private val onClick: (File, Int) -> Unit,
) : RecyclerView.Adapter<PhotosAdapter.VH>() {
    private val items = mutableListOf<File>()

    fun submit(files: List<File>) {
        items.clear()
        items.addAll(files)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val file = items[position]
        holder.binding.thumbName.text = file.name
        val opts = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        var sample = 1
        val maxSide = 512
        var w = opts.outWidth
        var h = opts.outHeight
        while (w / (sample * 2) > maxSide || h / (sample * 2) > maxSide) {
            sample *= 2
        }
        val decode = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeFile(file.absolutePath, decode)
        holder.binding.thumbImage.setImageBitmap(bmp)
        holder.itemView.setOnClickListener { onClick(file, position) }
    }

    class VH(val binding: ItemPhotoBinding) : RecyclerView.ViewHolder(binding.root)
}
