package com.fitnnes.gym

import android.app.Application
import com.fitnnes.gym.data.AppPrefs
import com.fitnnes.gym.data.Repository

class GymFitnessApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppPrefs.init(this)
        Repository.init(this)
    }
}
