package com.fitnnes.gym.settings

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.fitnnes.gym.R
import com.fitnnes.gym.data.AppPrefs

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val switchSound = view.findViewById<SwitchCompat>(R.id.switchSound)
        val switchVibration = view.findViewById<SwitchCompat>(R.id.switchVibration)
        val switchVoice = view.findViewById<SwitchCompat>(R.id.switchVoice)
        val switchKeepScreenOn = view.findViewById<SwitchCompat>(R.id.switchKeepScreenOn)
        val etCaloriesPerMinute = view.findViewById<EditText>(R.id.etCaloriesPerMinute)
        val btnSave = view.findViewById<TextView>(R.id.btnSaveSettings)

        switchSound.isChecked = AppPrefs.soundEnabled
        switchVibration.isChecked = AppPrefs.vibrationEnabled
        switchVoice.isChecked = AppPrefs.voiceAssistantEnabled
        switchKeepScreenOn.isChecked = AppPrefs.keepScreenOn
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
