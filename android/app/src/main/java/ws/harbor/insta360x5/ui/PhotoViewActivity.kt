package ws.harbor.insta360x5.ui

import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import ws.harbor.insta360x5.R
import ws.harbor.insta360x5.data.AppSettings
import ws.harbor.insta360x5.databinding.ActivityPhotoViewBinding
import ws.harbor.insta360x5.databinding.ItemPhotoPageBinding
import ws.harbor.insta360x5.util.GalleryPhoto
import ws.harbor.insta360x5.util.PhotoOverlay
import ws.harbor.insta360x5.util.PhotoStorage

class PhotoViewActivity : BaseActivity() {
    private lateinit var binding: ActivityPhotoViewBinding
    private lateinit var photos: List<GalleryPhoto>
    private var softOverlayEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoViewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.downloaded_photos)

        // Вариант 3: при выключенной печати — только мягкий оверлей в просмотре
        softOverlayEnabled = !AppSettings.load(this).printOverlay
        binding.softOverlay.visibility = if (softOverlayEnabled) View.VISIBLE else View.GONE

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
        updateLabels(startIndex)
        binding.photoPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateLabels(position)
            }
        })
        binding.btnShare.setOnClickListener { shareCurrent() }
    }

    private fun updateLabels(position: Int) {
        val photo = photos[position]
        val well = PhotoStorage.wellNumberFromFileName(photo.name)
        binding.photoTitle.text = getString(R.string.well_number_label, well)
        title = getString(R.string.well_number_label, well)
        if (softOverlayEnabled) {
            binding.overlayWell.text = well
            binding.overlayDate.text = PhotoOverlay.formatDateTime(photo.dateAdded * 1000L)
        }
    }

    private fun shareCurrent() {
        val position = binding.photoPager.currentItem
        if (position !in photos.indices) return
        val photo = photos[position]
        val mime = contentResolver.getType(photo.uri) ?: "image/jpeg"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, photo.uri)
            putExtra(Intent.EXTRA_SUBJECT, PhotoStorage.wellNumberFromFileName(photo.name))
            clipData = ClipData.newUri(contentResolver, photo.name, photo.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, getString(R.string.share)))
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
