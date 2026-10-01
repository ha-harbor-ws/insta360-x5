package ws.harbor.insta360x5.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import ws.harbor.insta360x5.data.AppSettings
import ws.harbor.insta360x5.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "Настройки"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        settings = AppSettings.load(this)
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
        AppSettings.save(this, settings)
        Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show()
        finish()
    }
}
