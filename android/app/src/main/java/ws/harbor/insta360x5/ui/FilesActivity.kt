package ws.harbor.insta360x5.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ws.harbor.insta360x5.R
import ws.harbor.insta360x5.data.AppSettings
import ws.harbor.insta360x5.databinding.ActivityFilesBinding
import ws.harbor.insta360x5.databinding.ItemFileBinding
import ws.harbor.insta360x5.osc.CameraFile
import ws.harbor.insta360x5.osc.OscClient
import ws.harbor.insta360x5.util.ImageCompressor
import ws.harbor.insta360x5.util.PhotoStorage
import java.io.File

class FilesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFilesBinding
    private lateinit var settings: AppSettings
    private val adapter = FilesAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFilesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "Файлы на камере"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        settings = AppSettings.load(this)
        binding.filesList.layoutManager = LinearLayoutManager(this)
        binding.filesList.adapter = adapter

        binding.btnRefresh.setOnClickListener { refresh() }
        binding.btnDownload.setOnClickListener { downloadSelected() }
        binding.btnDelete.setOnClickListener { confirmDelete() }
        binding.btnOpenGallery.setOnClickListener {
            startActivity(Intent(this, PhotosActivity::class.java))
        }
        refresh()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun client() = OscClient(settings.host, settings.port)

    private fun setBusy(busy: Boolean) {
        binding.progress.visibility = if (busy) View.VISIBLE else View.GONE
        binding.btnRefresh.isEnabled = !busy
        binding.btnDownload.isEnabled = !busy
        binding.btnDelete.isEnabled = !busy
        binding.btnOpenGallery.isEnabled = !busy
    }

    private fun refresh() {
        setBusy(true)
        lifecycleScope.launch {
            try {
                val (files, total) = withContext(Dispatchers.IO) {
                    client().listFiles(40)
                }
                adapter.submit(files)
                binding.filesStatus.text = "Всего: $total. Показано: ${files.size}"
            } catch (e: Exception) {
                Toast.makeText(this@FilesActivity, e.message, Toast.LENGTH_LONG).show()
            } finally {
                setBusy(false)
            }
        }
    }

    private fun downloadSelected() {
        val selected = adapter.selected()
        if (selected.isEmpty()) {
            Toast.makeText(this, R.string.no_selection, Toast.LENGTH_SHORT).show()
            return
        }
        setBusy(true)
        lifecycleScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) {
                    val cam = client()
                    val out = mutableListOf<File>()
                    val urls = selected.map { it.fileUrl }
                    for (file in selected) {
                        val dest = File(PhotoStorage.photosDir(this@FilesActivity), file.name)
                        cam.downloadToFile(file.fileUrl, dest)
                        out += ImageCompressor.maybeCompress(dest, settings)
                    }
                    if (settings.deleteAfterDownload) {
                        cam.deleteFiles(urls)
                    }
                    out
                }
                Toast.makeText(this@FilesActivity, "Скачано: ${saved.size}", Toast.LENGTH_SHORT).show()
                if (settings.deleteAfterDownload) refresh()
                if (saved.isNotEmpty()) {
                    AlertDialog.Builder(this@FilesActivity)
                        .setMessage("Открыть скачанные фото?")
                        .setPositiveButton(R.string.open_gallery) { _, _ ->
                            startActivity(
                                Intent(this@FilesActivity, PhotosActivity::class.java)
                                    .putExtra(PhotosActivity.EXTRA_HIGHLIGHT_PATH, saved.first().absolutePath),
                            )
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@FilesActivity, e.message, Toast.LENGTH_LONG).show()
            } finally {
                setBusy(false)
            }
        }
    }

    private fun confirmDelete() {
        val selected = adapter.selected()
        if (selected.isEmpty()) {
            Toast.makeText(this, R.string.no_selection, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.confirm_delete))
            .setPositiveButton(R.string.delete) { _, _ -> deleteSelected(selected) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteSelected(selected: List<CameraFile>) {
        setBusy(true)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    client().deleteFiles(selected.map { it.fileUrl })
                }
                Toast.makeText(this@FilesActivity, "Удалено", Toast.LENGTH_SHORT).show()
                refresh()
            } catch (e: Exception) {
                Toast.makeText(this@FilesActivity, e.message, Toast.LENGTH_LONG).show()
                setBusy(false)
            }
        }
    }
}

private class FilesAdapter : RecyclerView.Adapter<FilesAdapter.VH>() {
    private val items = mutableListOf<CameraFile>()
    private val checked = mutableSetOf<String>()

    fun submit(files: List<CameraFile>) {
        items.clear()
        items.addAll(files)
        checked.clear()
        notifyDataSetChanged()
    }

    fun selected(): List<CameraFile> = items.filter { it.fileUrl in checked }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.binding.fileName.text = item.name
        holder.binding.fileMeta.text =
            "${item.width}x${item.height} | ${item.size} байт | ${item.dateTime}"
        holder.binding.checkSelect.setOnCheckedChangeListener(null)
        holder.binding.checkSelect.isChecked = item.fileUrl in checked
        holder.binding.checkSelect.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) checked += item.fileUrl else checked -= item.fileUrl
        }
        holder.itemView.setOnClickListener {
            holder.binding.checkSelect.isChecked = !holder.binding.checkSelect.isChecked
        }
    }

    class VH(val binding: ItemFileBinding) : RecyclerView.ViewHolder(binding.root)
}
