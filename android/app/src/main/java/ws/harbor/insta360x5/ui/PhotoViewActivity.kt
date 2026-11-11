package ws.harbor.insta360x5.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import ws.harbor.insta360x5.databinding.ActivityPhotoViewBinding
import ws.harbor.insta360x5.databinding.ItemPhotoPageBinding
import ws.harbor.insta360x5.util.GalleryPhoto
import ws.harbor.insta360x5.util.PhotoStorage

class PhotoViewActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPhotoViewBinding
    private lateinit var photos: List<GalleryPhoto>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoViewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Просмотр"

        photos = PhotoStorage.listGalleryPhotos(this)
        if (photos.isEmpty()) {
            finish()
            return
        }

        val uriText = intent.getStringExtra(EXTRA_URI)
        val indexFromIntent = intent.getIntExtra(EXTRA_INDEX, 0)
        val startIndex = when {
            !uriText.isNullOrBlank() -> {
                photos.indexOfFirst { it.uri.toString() == uriText }.takeIf { it >= 0 } ?: indexFromIntent
            }
            else -> indexFromIntent
        }.coerceIn(0, photos.lastIndex)

        binding.photoPager.adapter = PhotoPagerAdapter(photos)
        binding.photoPager.setCurrentItem(startIndex, false)
        updateTitle(startIndex)
        binding.photoPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateTitle(position)
            }
        })
    }

    private fun updateTitle(position: Int) {
        val photo = photos[position]
        binding.photoTitle.text = "${position + 1}/${photos.size}  ${photo.name}"
        title = photo.name
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        const val EXTRA_INDEX = "index"
        const val EXTRA_URI = "uri"
    }
}

private class PhotoPagerAdapter(
    private val photos: List<GalleryPhoto>,
) : RecyclerView.Adapter<PhotoPagerAdapter.VH>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemPhotoPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount() = photos.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val photo = photos[position]
        holder.binding.fullImage.setImageBitmap(
            PhotoStorage.decodeUriBounds(holder.itemView.context, photo.uri, 4096),
        )
    }

    class VH(val binding: ItemPhotoPageBinding) : RecyclerView.ViewHolder(binding.root)
}
