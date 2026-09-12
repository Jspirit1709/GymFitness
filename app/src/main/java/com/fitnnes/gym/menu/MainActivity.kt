package com.fitnnes.gym.menu

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.fitnnes.gym.R
import com.fitnnes.gym.home.HomeFragment
import com.fitnnes.gym.library.LibraryFragment
import com.fitnnes.gym.settings.SettingsFragment
import com.fitnnes.gym.stats.StatsFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        if (savedInstanceState == null) {
            showFragment(HomeFragment())
        }

        bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_workouts -> LibraryFragment()
                R.id.nav_stats -> StatsFragment()
                R.id.nav_settings -> SettingsFragment()
                else -> HomeFragment()
            }
            showFragment(fragment)
            true
        }
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    fun goToTab(itemId: Int) {
        findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId = itemId
    }
}
