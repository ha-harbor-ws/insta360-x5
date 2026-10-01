package ws.harbor.insta360x5.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import ws.harbor.insta360x5.databinding.ActivityPhotoViewBinding
import ws.harbor.insta360x5.databinding.ItemPhotoPageBinding
import ws.harbor.insta360x5.util.PhotoStorage
import java.io.File

class PhotoViewActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPhotoViewBinding
    private lateinit var files: List<File>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoViewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Просмотр"

        files = PhotoStorage.listPhotos(this)
        if (files.isEmpty()) {
            finish()
            return
        }

        val path = intent.getStringExtra(EXTRA_PATH)
        val indexFromIntent = intent.getIntExtra(EXTRA_INDEX, 0)
        val startIndex = when {
            !path.isNullOrBlank() -> files.indexOfFirst { it.absolutePath == path }.takeIf { it >= 0 } ?: indexFromIntent
            else -> indexFromIntent
        }.coerceIn(0, files.lastIndex)

        binding.photoPager.adapter = PhotoPagerAdapter(files)
        binding.photoPager.setCurrentItem(startIndex, false)
        updateTitle(startIndex)
        binding.photoPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateTitle(position)
            }
        })
    }

    private fun updateTitle(position: Int) {
        val file = files[position]
        binding.photoTitle.text = "${position + 1}/${files.size}  ${file.name}"
        title = file.name
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        const val EXTRA_INDEX = "index"
        const val EXTRA_PATH = "path"
    }
}

private class PhotoPagerAdapter(
    private val files: List<File>,
) : RecyclerView.Adapter<PhotoPagerAdapter.VH>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemPhotoPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount() = files.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val file = files[position]
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        var sample = 1
        val maxSide = 4096
        var w = opts.outWidth
        var h = opts.outHeight
        while (maxOf(w, h) / (sample * 2) > maxSide) {
            sample *= 2
        }
        val decode = BitmapFactory.Options().apply { inSampleSize = sample }
        holder.binding.fullImage.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath, decode))
    }

    class VH(val binding: ItemPhotoPageBinding) : RecyclerView.ViewHolder(binding.root)
}
