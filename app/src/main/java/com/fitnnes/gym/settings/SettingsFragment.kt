package com.fitnnes.gym.settings

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.fitnnes.gym.R
import com.fitnnes.gym.data.AppPrefs
import com.fitnnes.gym.data.Repository
import java.io.BufferedReader
import java.io.InputStreamReader

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            val input = requireContext().contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("No se pudo abrir el archivo")
            val json = BufferedReader(InputStreamReader(input)).use { it.readText() }
            Repository.importWorkoutsJson(json)
        }.onSuccess { count ->
            Toast.makeText(
                requireContext(),
                getString(R.string.import_success, count),
                Toast.LENGTH_LONG
            ).show()
        }.onFailure {
            Toast.makeText(requireContext(), R.string.import_error, Toast.LENGTH_LONG).show()
        }
    }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            val json = Repository.exportWorkoutsJson()
            requireContext().contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray())
            } ?: throw IllegalStateException("No se pudo escribir el archivo")
        }.onSuccess {
            Toast.makeText(requireContext(), R.string.export_success, Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(requireContext(), R.string.export_error, Toast.LENGTH_LONG).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val switchSound = view.findViewById<SwitchCompat>(R.id.switchSound)
        val switchVibration = view.findViewById<SwitchCompat>(R.id.switchVibration)
        val switchVoice = view.findViewById<SwitchCompat>(R.id.switchVoice)
        val switchKeepScreenOn = view.findViewById<SwitchCompat>(R.id.switchKeepScreenOn)
        val switchDarkMode = view.findViewById<SwitchCompat>(R.id.switchDarkMode)
        val etCaloriesPerMinute = view.findViewById<EditText>(R.id.etCaloriesPerMinute)
        val btnSave = view.findViewById<TextView>(R.id.btnSaveSettings)
        val rowImport = view.findViewById<TextView>(R.id.rowImportWorkouts)
        val rowExport = view.findViewById<TextView>(R.id.rowExportWorkouts)

        switchSound.isChecked = AppPrefs.soundEnabled
        switchVibration.isChecked = AppPrefs.vibrationEnabled
        switchVoice.isChecked = AppPrefs.voiceAssistantEnabled
        switchKeepScreenOn.isChecked = AppPrefs.keepScreenOn
        switchDarkMode.isChecked = AppPrefs.darkModeEnabled
        etCaloriesPerMinute.setText(formatCalories(AppPrefs.caloriesPerMinute))

        switchSound.setOnCheckedChangeListener { _, isChecked ->
            AppPrefs.soundEnabled = isChecked
        }
        switchVibration.setOnCheckedChangeListener { _, isChecked ->
            AppPrefs.vibrationEnabled = isChecked
        }
        switchVoice.setOnCheckedChangeListener { _, isChecked ->
            AppPrefs.voiceAssistantEnabled = isChecked
        }
        switchKeepScreenOn.setOnCheckedChangeListener { _, isChecked ->
            AppPrefs.keepScreenOn = isChecked
        }
        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            AppPrefs.darkModeEnabled = isChecked
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
            requireActivity().recreate()
        }

        rowImport.setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        }
        rowExport.setOnClickListener {
            exportLauncher.launch("entrenamientos_gymfitness.json")
        }

        btnSave.setOnClickListener {
            val kcal = etCaloriesPerMinute.text.toString().toFloatOrNull()
            if (kcal == null || kcal <= 0f) {
                etCaloriesPerMinute.error = getString(R.string.invalid_number)
                return@setOnClickListener
            }
            AppPrefs.caloriesPerMinute = kcal
            Toast.makeText(requireContext(), R.string.save, Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatCalories(value: Float): String {
        return if (value == value.toInt().toFloat()) {
            value.toInt().toString()
        } else {
            value.toString()
        }
    }
}
