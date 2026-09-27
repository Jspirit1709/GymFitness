package com.fitnnes.gym.data

import android.content.Context
import android.content.SharedPreferences

object ManualWorkoutDays {
    private const val PREFS = "gymfitness_manual_days"
    private const val KEY_DAYS = "marked_days"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    fun isMarked(dateKey: String): Boolean =
        prefs.getStringSet(KEY_DAYS, emptySet())?.contains(dateKey) == true

    fun toggle(dateKey: String) {
        val set = HashSet(prefs.getStringSet(KEY_DAYS, emptySet()) ?: emptySet())
        if (!set.add(dateKey)) set.remove(dateKey)
        prefs.edit().putStringSet(KEY_DAYS, set).apply()
    }
}
