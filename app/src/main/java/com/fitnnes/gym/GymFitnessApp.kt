package com.fitnnes.gym

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.fitnnes.gym.data.AppPrefs
import com.fitnnes.gym.data.Repository

class GymFitnessApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppPrefs.init(this)
        Repository.init(this)

        AppCompatDelegate.setDefaultNightMode(
            if (AppPrefs.darkModeEnabled) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }
        )
    }
}
