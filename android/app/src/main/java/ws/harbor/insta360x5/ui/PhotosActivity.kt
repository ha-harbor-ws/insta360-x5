package ws.harbor.insta360x5.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ws.harbor.insta360x5.databinding.ActivityPhotosBinding
import ws.harbor.insta360x5.databinding.ItemPhotoBinding
import ws.harbor.insta360x5.util.GalleryPhoto
import ws.harbor.insta360x5.util.MediaPermissions
import ws.harbor.insta360x5.util.PhotoStorage

class PhotosActivity : BaseActivity() {
    private lateinit var binding: ActivityPhotosBinding
    private val adapter = PhotosAdapter { photo, index ->
        startActivity(
            Intent(this, PhotoViewActivity::class.java)
                .putExtra(PhotoViewActivity.EXTRA_INDEX, index)
                .putExtra(PhotoViewActivity.EXTRA_URI, photo.uri.toString()),
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
        MediaPermissions.ensure(this)
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MediaPermissions.REQUEST_CODE) {
            reload()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun reload() {
        val photos = PhotoStorage.listGalleryPhotos(this)
        adapter.submit(photos)
        binding.photosStatus.text = if (photos.isEmpty()) {
            "Нет фото в галерее. Сделайте снимок или скачайте файлы с камеры."
        } else {
            "Фото в галерее: ${photos.size}"
        }
    }
}

private class PhotosAdapter(
    private val onClick: (GalleryPhoto, Int) -> Unit,
) : RecyclerView.Adapter<PhotosAdapter.VH>() {
    private val items = mutableListOf<GalleryPhoto>()

    fun submit(photos: List<GalleryPhoto>) {
        items.clear()
        items.addAll(photos)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val photo = items[position]
        holder.binding.thumbName.text = photo.name
        holder.binding.thumbImage.setImageBitmap(
            PhotoStorage.decodeUriBounds(holder.itemView.context, photo.uri, 512),
        )
        holder.itemView.setOnClickListener { onClick(photo, position) }
    }

    class VH(val binding: ItemPhotoBinding) : RecyclerView.ViewHolder(binding.root)
}
