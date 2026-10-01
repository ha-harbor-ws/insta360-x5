package ws.harbor.insta360x5.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ws.harbor.insta360x5.R
import ws.harbor.insta360x5.data.AppSettings
import ws.harbor.insta360x5.databinding.ActivityMainBinding
import ws.harbor.insta360x5.osc.OscClient
import ws.harbor.insta360x5.osc.OscException
import ws.harbor.insta360x5.util.ImageCompressor
import ws.harbor.insta360x5.util.PhotoStorage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "Insta360 X5"

        binding.btnTakePhoto.setOnClickListener { takePhoto() }
        binding.btnCameraInfo.setOnClickListener { showInfo() }
        binding.btnFiles.setOnClickListener {
            startActivity(Intent(this, FilesActivity::class.java))
        }
        binding.btnPhotos.setOnClickListener {
            startActivity(Intent(this, PhotosActivity::class.java))
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        settings = AppSettings.load(this)
        binding.modeLine.text = settings.summary()
    }

    private fun setBusy(busy: Boolean) {
        binding.progress.visibility = if (busy) View.VISIBLE else View.GONE
        binding.btnTakePhoto.isEnabled = !busy
        binding.btnCameraInfo.isEnabled = !busy
        binding.btnFiles.isEnabled = !busy
        binding.btnPhotos.isEnabled = !busy
        binding.btnSettings.isEnabled = !busy
    }

    private fun appendLog(line: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val current = binding.logView.text?.toString().orEmpty()
        binding.logView.text = if (current.isBlank()) {
            "[$stamp] $line"
        } else {
            "$current\n[$stamp] $line"
        }
    }

    private fun client() = OscClient(settings.host, settings.port)

    private fun takePhoto() {
        setBusy(true)
        appendLog("Снимок…")
        lifecycleScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) {
                    val cam = client()
                    val info = cam.info()
                    val state = cam.state().optJSONObject("state")
                    val card = state?.optString("_cardState", "?")
                    if (card in listOf("noCard", "noSpace", "invalidFormat", "writeProtect", "otherError")) {
                        throw OscException("Проблема с картой памяти: $card")
                    }
                    withContext(Dispatchers.Main) {
                        appendLog(
                            "Модель: ${info.optString("model")} | карта: $card | " +
                                "батарея: ${state?.optDouble("batteryLevel", -1.0)}",
                        )
                    }
                    val urls = cam.takePicture(settings.stitch, settings.hdr)
                    val out = mutableListOf<File>()
                    if (settings.download) {
                        for (url in urls) {
                            val name = url.trimEnd('/').substringAfterLast('/')
                            val dest = File(PhotoStorage.photosDir(this@MainActivity), name)
                            cam.downloadToFile(url, dest)
                            out += ImageCompressor.maybeCompress(dest, settings)
                        }
                        if (settings.deleteAfterDownload && out.isNotEmpty()) {
                            cam.deleteFiles(urls)
                        }
                    }
                    out to urls
                }
                val (files, urls) = saved
                if (files.isEmpty()) {
                    appendLog("Снимок готов на камере:\n" + urls.joinToString("\n"))
                    Toast.makeText(this@MainActivity, "Снимок на камере", Toast.LENGTH_SHORT).show()
                } else {
                    appendLog("Сохранено:\n" + files.joinToString("\n") { it.absolutePath })
                    Toast.makeText(this@MainActivity, "Готово: ${files.size} файл(ов)", Toast.LENGTH_SHORT).show()
                    offerOpenGallery(files.first())
                }
            } catch (e: Exception) {
                appendLog("Ошибка: ${e.message}")
                Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_LONG).show()
            } finally {
                setBusy(false)
            }
        }
    }

    private fun offerOpenGallery(firstFile: File) {
        AlertDialog.Builder(this)
            .setMessage("Открыть скачанные фото?")
            .setPositiveButton(R.string.open_gallery) { _, _ ->
                startActivity(
                    Intent(this, PhotosActivity::class.java)
                        .putExtra(PhotosActivity.EXTRA_HIGHLIGHT_PATH, firstFile.absolutePath),
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showInfo() {
        setBusy(true)
        lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    val cam = client()
                    val info = cam.info()
                    val state = cam.state().optJSONObject("state")
                    buildString {
                        appendLine("URL: http://${settings.host}:${settings.port}")
                        appendLine("Модель: ${info.optString("model")}")
                        appendLine("Прошивка: ${info.optString("firmwareVersion")}")
                        appendLine("S/N: ${info.optString("serialNumber")}")
                        appendLine("Карта: ${state?.optString("_cardState")}")
                        val battery = state?.optDouble("batteryLevel", Double.NaN)
                        if (battery != null && !battery.isNaN()) {
                            appendLine("Батарея: ${(battery * 100).toInt()}%")
                        }
                        appendLine("Storage: ${state?.optString("storageUri")}")
                    }
                }
                appendLog(text.trim())
            } catch (e: Exception) {
                appendLog("Ошибка: ${e.message}")
                Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_LONG).show()
            } finally {
                setBusy(false)
            }
        }
    }
}
