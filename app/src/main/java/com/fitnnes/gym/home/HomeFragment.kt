package com.fitnnes.gym.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fitnnes.gym.R
import com.fitnnes.gym.data.Repository
import com.fitnnes.gym.editor.ExerciseEditorActivity
import com.fitnnes.gym.library.ExerciseAdapter
import com.fitnnes.gym.timer.TimerActivity
import com.fitnnes.gym.util.TimeFormat
import com.fitnnes.gym.workoutdomain.Exercise

class HomeFragment : Fragment(R.layout.fragment_home) {

    private lateinit var adapter: ExerciseAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvOverviewCount = view.findViewById<TextView>(R.id.tvOverviewCount)
        val tvOverviewDetail = view.findViewById<TextView>(R.id.tvOverviewDetail)
        val rvQuickStart = view.findViewById<RecyclerView>(R.id.rvQuickStart)
        val tvEmptyQuickStart = view.findViewById<TextView>(R.id.tvEmptyQuickStart)
        val btnNewWorkout = view.findViewById<TextView>(R.id.btnNewWorkoutHome)

        adapter = ExerciseAdapter(
            onClick = { exercise -> startActivity(TimerActivity.startWithExercise(requireContext(), exercise)) },
            onFavouriteClick = { exercise ->
                Repository.toggleFavourite(exercise.id)
                refresh(tvOverviewCount, tvOverviewDetail, rvQuickStart, tvEmptyQuickStart)
            },
            onEditClick = { exercise ->
                startActivity(ExerciseEditorActivity.editIntent(requireContext(), exercise.id))
            }
        )
        rvQuickStart.layoutManager = LinearLayoutManager(requireContext())
        rvQuickStart.adapter = adapter

        btnNewWorkout.setOnClickListener {
            startActivity(ExerciseEditorActivity.createIntent(requireContext()))
        }

        refresh(tvOverviewCount, tvOverviewDetail, rvQuickStart, tvEmptyQuickStart)
    }

    override fun onResume() {
        super.onResume()
        view?.let {
            refresh(
                it.findViewById(R.id.tvOverviewCount),
                it.findViewById(R.id.tvOverviewDetail),
                it.findViewById(R.id.rvQuickStart),
                it.findViewById(R.id.tvEmptyQuickStart)
            )
        }
    }

    private fun refresh(
        tvOverviewCount: TextView,
        tvOverviewDetail: TextView,
        rvQuickStart: RecyclerView,
        tvEmptyQuickStart: TextView
    ) {
        val sessions = Repository.getSessionsLastNDays(7)
        val totalDuration = sessions.sumOf { it.durationSeconds }
        val totalCalories = sessions.sumOf { it.calories }

        tvOverviewCount.text = getString(R.string.overview_count_format, sessions.size)
        tvOverviewDetail.text = "⏱ ${TimeFormat.readable(totalDuration)}   🔥 ${totalCalories}kcal"

        val favourites: List<Exercise> = Repository.getExercises().filter { it.favourite }
        adapter.submitList(favourites)
        tvEmptyQuickStart.visibility = if (favourites.isEmpty()) View.VISIBLE else View.GONE
        rvQuickStart.visibility = if (favourites.isEmpty()) View.GONE else View.VISIBLE
    }
}
