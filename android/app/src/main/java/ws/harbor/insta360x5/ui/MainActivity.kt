package ws.harbor.insta360x5.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ws.harbor.insta360x5.R
import ws.harbor.insta360x5.data.AppSettings
import ws.harbor.insta360x5.databinding.ActivityMainBinding
import ws.harbor.insta360x5.databinding.DialogWellNumberBinding
import ws.harbor.insta360x5.databinding.DialogWifiConnectBinding
import ws.harbor.insta360x5.osc.OscException
import ws.harbor.insta360x5.util.ImageCompressor
import ws.harbor.insta360x5.util.PhotoOverlay
import ws.harbor.insta360x5.util.PhotoStorage
import ws.harbor.insta360x5.wifi.CameraWifiSession
import ws.harbor.insta360x5.wifi.CameraWifiStatus
import ws.harbor.insta360x5.wifi.connectAndProbeCamera
import ws.harbor.insta360x5.wifi.createOscClient
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : BaseActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: AppSettings
    private var wifiDialogShown = false
    private var autoConnectAttempted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "Insta360 X5"

        binding.btnCameraConnect.setOnClickListener { connectCameraFromSettings() }
        binding.btnTakePhoto.setOnClickListener { askWellNumberAndCapture() }
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
        refreshWifiStatus()

        if (!settings.hasWifiCredentials()) {
            if (!wifiDialogShown) {
                wifiDialogShown = true
                showWifiSetupDialog()
            }
        } else if (!CameraWifiSession.isConnected && !autoConnectAttempted) {
            // Уже есть SSID/пароль — тихо подключаемся в фоне, диалог не показываем
            autoConnectAttempted = true
            connectCameraFromSettings(silentFail = true)
        }
    }

    private fun refreshWifiStatus() {
        binding.wifiStatusLine.text = when (CameraWifiSession.status) {
            CameraWifiStatus.Disconnected -> getString(R.string.wifi_status_disconnected)
            CameraWifiStatus.Connecting -> getString(R.string.wifi_status_connecting)
            CameraWifiStatus.Connected -> getString(R.string.wifi_status_connected)
            CameraWifiStatus.Lost -> getString(R.string.wifi_status_disconnected)
        }
    }

    private fun setBusy(busy: Boolean) {
        binding.progress.visibility = if (busy) View.VISIBLE else View.GONE
        binding.btnCameraConnect.isEnabled = !busy
        binding.btnTakePhoto.isEnabled = !busy
        binding.btnCameraInfo.isEnabled = !busy
        binding.btnFiles.isEnabled = !busy
        binding.btnPhotos.isEnabled = !busy
        binding.btnSettings.isEnabled = !busy
    }

    private fun setLog(text: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        binding.logView.text = "[$stamp] $text"
    }

    private fun client() = settings.createOscClient()

    private fun showWifiSetupDialog() {
        val dialogBinding = DialogWifiConnectBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()

        dialogBinding.btnCameraConnect.setOnClickListener {
            val ssid = dialogBinding.inputWifiSsid.text?.toString()?.trim().orEmpty()
            val password = dialogBinding.inputWifiPassword.text?.toString().orEmpty()
            if (ssid.isBlank()) {
                dialogBinding.inputWifiSsid.error = getString(R.string.wifi_ssid_required)
                return@setOnClickListener
            }
            if (password.isBlank()) {
                dialogBinding.inputWifiPassword.error = getString(R.string.wifi_password_required)
                return@setOnClickListener
            }

            dialogBinding.btnCameraConnect.isEnabled = false
            setBusy(true)
            refreshWifiStatus()
            setLog("Подключение к Wi‑Fi «$ssid»…")
            lifecycleScope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        connectAndProbeCamera(
                            this@MainActivity,
                            ssid,
                            password,
                            settings.host,
                            settings.port,
                        )
                    }
                    settings.wifiSsid = ssid
                    settings.wifiPassword = password
                    AppSettings.save(this@MainActivity, settings)
                    binding.modeLine.text = settings.summary()
                    refreshWifiStatus()
                    setLog(result.infoText)
                    Toast.makeText(this@MainActivity, R.string.wifi_status_connected, Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } catch (e: Exception) {
                    refreshWifiStatus()
                    setLog("Ошибка Wi‑Fi: ${e.message}")
                    Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_LONG).show()
                    dialogBinding.btnCameraConnect.isEnabled = true
                } finally {
                    setBusy(false)
                }
            }
        }

        dialog.setOnShowListener {
            (dialogBinding.root.parent as? View)?.setPadding(0, 0, 0, 0)
        }
        dialog.setOnDismissListener { wifiDialogShown = false }
        dialog.show()
    }

    private fun connectCameraFromSettings(silentFail: Boolean = false) {
        settings = AppSettings.load(this)
        if (!settings.hasWifiCredentials()) {
            showWifiSetupDialog()
            return
        }
        setBusy(true)
        refreshWifiStatus()
        setLog("Подключение к Wi‑Fi «${settings.wifiSsid}»…")
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    connectAndProbeCamera(
                        this@MainActivity,
                        settings.wifiSsid,
                        settings.wifiPassword,
                        settings.host,
                        settings.port,
                    )
                }
                refreshWifiStatus()
                setLog(result.infoText)
                if (!silentFail) {
                    Toast.makeText(this@MainActivity, R.string.wifi_status_connected, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                refreshWifiStatus()
                setLog("Ошибка Wi‑Fi: ${e.message}")
                if (!silentFail) {
                    Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_LONG).show()
                }
            } finally {
                setBusy(false)
            }
        }
    }

    private fun askWellNumberAndCapture() {
        if (!ensureCameraWifi()) return
        val dialogBinding = DialogWellNumberBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        fun confirmAndCapture() {
            val raw = dialogBinding.inputWellNumber.text?.toString().orEmpty()
            val base = PhotoStorage.sanitizeFileBase(raw)
            if (raw.isBlank()) {
                dialogBinding.inputWellNumber.error = getString(R.string.well_number_required)
                return
            }
            dialog.dismiss()
            takePhoto(base)
        }

        dialogBinding.btnDialogTakePhoto.setOnClickListener { confirmAndCapture() }
        dialogBinding.inputWellNumber.setOnEditorActionListener { _, _, _ ->
            confirmAndCapture()
            true
        }

        dialog.setOnShowListener {
            (dialogBinding.root.parent as? View)?.setPadding(0, 0, 0, 0)
            dialogBinding.inputWellNumber.requestFocus()
        }
        dialog.show()
    }

    private fun ensureCameraWifi(): Boolean {
        if (CameraWifiSession.isConnected) return true
        Toast.makeText(this, R.string.wifi_status_disconnected, Toast.LENGTH_LONG).show()
        if (!settings.hasWifiCredentials()) {
            showWifiSetupDialog()
        } else {
            connectCameraFromSettings()
        }
        return false
    }

    private fun takePhoto(wellName: String) {
        if (!ensureCameraWifi()) return
        setBusy(true)
        setLog("Снимок… колодец=$wellName")
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
                        setLog(
                            "Модель: ${info.optString("model")} | карта: $card | " +
                                "батарея: ${state?.optDouble("batteryLevel", -1.0)}\nСнимок…",
                        )
                    }
                    val urls = cam.takePicture(settings.stitch, settings.hdr)
                    val out = mutableListOf<Uri>()
                    if (settings.download) {
                        urls.forEachIndexed { index, url ->
                            val cacheName = if (urls.size == 1) {
                                "$wellName.jpg"
                            } else {
                                "${wellName}_${index + 1}.jpg"
                            }
                            val temp = File(PhotoStorage.cacheDir(this@MainActivity), cacheName)
                            cam.downloadToFile(url, temp)
                            var processed = ImageCompressor.maybeCompress(temp, settings)
                            if (settings.printOverlay) {
                                processed = PhotoOverlay.burnIntoFile(
                                    processed,
                                    wellName,
                                    PhotoOverlay.formatDateTime(),
                                    settings.compressQuality,
                                )
                            }
                            val galleryName = if (processed.extension.isBlank()) {
                                "$wellName.jpg"
                            } else if (urls.size == 1) {
                                "$wellName.${processed.extension}"
                            } else {
                                "${wellName}_${index + 1}.${processed.extension}"
                            }
                            val uri = PhotoStorage.saveFileToGallery(
                                this@MainActivity,
                                processed,
                                galleryName,
                            )
                            out += uri
                            if (temp.exists()) temp.delete()
                            if (processed.exists() && processed.absolutePath != temp.absolutePath) {
                                processed.delete()
                            }
                        }
                        if (settings.deleteAfterDownload && out.isNotEmpty()) {
                            cam.deleteFiles(urls)
                        }
                    }
                    out to urls
                }
                val (uris, urls) = saved
                if (uris.isEmpty()) {
                    setLog("Снимок готов на камере:\n" + urls.joinToString("\n"))
                    Toast.makeText(this@MainActivity, "Снимок на камере", Toast.LENGTH_SHORT).show()
                } else {
                    setLog("Сохранено в галерею:\n" + uris.joinToString("\n") { it.toString() })
                    Toast.makeText(this@MainActivity, "Сохранено в галерею: ${uris.size}", Toast.LENGTH_SHORT).show()
                    offerOpenGallery()
                }
            } catch (e: Exception) {
                setLog("Ошибка: ${e.message}")
                Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_LONG).show()
            } finally {
                setBusy(false)
            }
        }
    }

    private fun offerOpenGallery() {
        AlertDialog.Builder(this)
            .setMessage("Открыть скачанные фото?")
            .setPositiveButton(R.string.open_gallery) { _, _ ->
                startActivity(Intent(this, PhotosActivity::class.java))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showInfo() {
        if (!ensureCameraWifi()) return
        setBusy(true)
        lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    val cam = client()
                    val info = cam.info()
                    val state = cam.state().optJSONObject("state")
                    buildString {
                        appendLine("URL: http://${settings.host}:${settings.port}")
                        appendLine("Wi‑Fi: ${settings.wifiSsid}")
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
                setLog(text.trim())
            } catch (e: Exception) {
                setLog("Ошибка: ${e.message}")
                Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_LONG).show()
            } finally {
                setBusy(false)
            }
        }
    }
}
