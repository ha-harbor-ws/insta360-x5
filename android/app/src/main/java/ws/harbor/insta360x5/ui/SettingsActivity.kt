package ws.harbor.insta360x5.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ws.harbor.insta360x5.R
import ws.harbor.insta360x5.data.AppSettings
import ws.harbor.insta360x5.databinding.ActivitySettingsBinding
import ws.harbor.insta360x5.wifi.connectAndProbeCamera

class SettingsActivity : BaseActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settings: AppSettings
    private lateinit var originalSsid: String
    private lateinit var originalPassword: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "Настройки"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        settings = AppSettings.load(this)
        originalSsid = settings.wifiSsid
        originalPassword = settings.wifiPassword

        binding.inputWifiSsid.setText(settings.wifiSsid)
        binding.inputWifiPassword.setText(settings.wifiPassword)
        binding.inputHost.setText(settings.host)
        binding.inputPort.setText(settings.port.toString())

        binding.spinnerStitch.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            AppSettings.STITCH_OPTIONS,
        )
        binding.spinnerStitch.setSelection(AppSettings.STITCH_OPTIONS.indexOf(settings.stitch).coerceAtLeast(0))

        binding.spinnerHdr.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            AppSettings.HDR_OPTIONS,
        )
        binding.spinnerHdr.setSelection(AppSettings.HDR_OPTIONS.indexOf(settings.hdr).coerceAtLeast(0))

        val resolutions = AppSettings.RESOLUTION_PRESETS.keys.toList()
        binding.spinnerResolution.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            resolutions,
        )
        binding.spinnerResolution.setSelection(resolutions.indexOf(settings.compressResolution).coerceAtLeast(0))

        val qualities = AppSettings.QUALITY_PRESETS.map { it.toString() }
        binding.spinnerQuality.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            qualities,
        )
        val qIndex = AppSettings.QUALITY_PRESETS.indexOf(settings.compressQuality)
        binding.spinnerQuality.setSelection(if (qIndex >= 0) qIndex else 2)

        binding.switchDownload.isChecked = settings.download
        binding.switchDeleteAfter.isChecked = settings.deleteAfterDownload
        binding.switchCompress.isChecked = settings.compress
        binding.switchKeepOriginal.isChecked = settings.compressKeepOriginal
        binding.switchPrintOverlay.isChecked = settings.printOverlay

        binding.btnSave.setOnClickListener { save() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun save() {
        val port = binding.inputPort.text?.toString()?.toIntOrNull()
        if (port == null || port !in 1..65535) {
            Toast.makeText(this, "Неверный порт", Toast.LENGTH_SHORT).show()
            return
        }

        val newSsid = binding.inputWifiSsid.text?.toString()?.trim().orEmpty()
        val newPassword = binding.inputWifiPassword.text?.toString().orEmpty()
        val wifiChanged = newSsid != originalSsid || newPassword != originalPassword

        settings.host = binding.inputHost.text?.toString()?.trim().orEmpty().ifBlank { "192.168.42.1" }
        settings.port = port
        settings.stitch = binding.spinnerStitch.selectedItem as String
        settings.hdr = binding.spinnerHdr.selectedItem as String
        settings.download = binding.switchDownload.isChecked
        settings.deleteAfterDownload = binding.switchDeleteAfter.isChecked
        settings.compress = binding.switchCompress.isChecked
        settings.compressResolution = binding.spinnerResolution.selectedItem as String
        settings.compressQuality = (binding.spinnerQuality.selectedItem as String).toInt()
        settings.compressKeepOriginal = binding.switchKeepOriginal.isChecked
        settings.printOverlay = binding.switchPrintOverlay.isChecked

        if (!wifiChanged) {
            settings.wifiSsid = originalSsid
            settings.wifiPassword = originalPassword
            AppSettings.save(this, settings)
            Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (newSsid.isBlank() || newPassword.isBlank()) {
            Toast.makeText(this, "SSID и пароль Wi‑Fi обязательны", Toast.LENGTH_LONG).show()
            return
        }

        binding.btnSave.isEnabled = false
        Toast.makeText(this, R.string.wifi_status_connecting, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    connectAndProbeCamera(
                        this@SettingsActivity,
                        newSsid,
                        newPassword,
                        settings.host,
                        settings.port,
                    )
                }
                settings.wifiSsid = newSsid
                settings.wifiPassword = newPassword
                AppSettings.save(this@SettingsActivity, settings)
                Toast.makeText(this@SettingsActivity, "Сохранено (Wi‑Fi проверен)", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                // Остальные настройки сохраняем, SSID/пароль — нет
                AppSettings.saveWithoutWifi(this@SettingsActivity, settings)
                Toast.makeText(
                    this@SettingsActivity,
                    getString(R.string.wifi_credentials_verify_fail) + "\n${e.message}",
                    Toast.LENGTH_LONG,
                ).show()
                binding.btnSave.isEnabled = true
            }
        }
    }
}
