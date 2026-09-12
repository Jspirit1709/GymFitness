package com.fitnnes.gym.data

import android.content.Context
import android.content.SharedPreferences

/** Ajustes globales de la app (pantalla Ajustes). */
object AppPrefs {
    private const val PREFS = "gymfitness_settings"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    var soundEnabled: Boolean
        get() = prefs.getBoolean("sound_enabled", true)
        set(value) = prefs.edit().putBoolean("sound_enabled", value).apply()

    var vibrationEnabled: Boolean
        get() = prefs.getBoolean("vibration_enabled", true)
        set(value) = prefs.edit().putBoolean("vibration_enabled", value).apply()

    var voiceAssistantEnabled: Boolean
        get() = prefs.getBoolean("voice_enabled", false)
        set(value) = prefs.edit().putBoolean("voice_enabled", value).apply()

    var keepScreenOn: Boolean
        get() = prefs.getBoolean("keep_screen_on", true)
        set(value) = prefs.edit().putBoolean("keep_screen_on", value).apply()

    var caloriesPerMinute: Float
        get() = prefs.getFloat("kcal_per_min", 6.5f)
        set(value) = prefs.edit().putFloat("kcal_per_min", value).apply()

    /** Modo oscuro de la app (independiente del tema del sistema). */
    var darkModeEnabled: Boolean
        get() = prefs.getBoolean("dark_mode_enabled", false)
        set(value) = prefs.edit().putBoolean("dark_mode_enabled", value).apply()
}
