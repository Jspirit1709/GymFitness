package com.fitnnes.gym.library

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fitnnes.gym.R
import com.fitnnes.gym.data.Repository
import com.fitnnes.gym.editor.ExerciseEditorActivity
import com.fitnnes.gym.menu.CreatePlanActivity
import com.fitnnes.gym.timer.TimerActivity
import com.fitnnes.gym.workoutdomain.Exercise
import com.fitnnes.gym.workoutdomain.ExercisePlan
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout

private const val FILTER_ALL = "__ALL__"
private const val FILTER_FAVOURITES = "__FAV__"

class LibraryFragment : Fragment(R.layout.fragment_library) {

    private lateinit var exerciseAdapter: ExerciseAdapter
    private lateinit var planAdapter: PlanAdapter
    private var currentTab = 0
    private var selectedFilter = FILTER_ALL
    private var searchQuery = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tabLayout = view.findViewById<TabLayout>(R.id.tabLayout)
        val scrollTags = view.findViewById<HorizontalScrollView>(R.id.scrollTags)
        val tagContainer = view.findViewById<LinearLayout>(R.id.tagContainer)
        val rvExercises = view.findViewById<RecyclerView>(R.id.rvExercises)
        val rvPlans = view.findViewById<RecyclerView>(R.id.rvPlans)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmptyLibrary)
        val fab = view.findViewById<FloatingActionButton>(R.id.fabAdd)
        val btnSearch = view.findViewById<ImageButton>(R.id.btnSearch)
        val etSearch = view.findViewById<EditText>(R.id.etSearch)

        exerciseAdapter = ExerciseAdapter(
            onClick = { exercise -> startActivity(TimerActivity.startWithExercise(requireContext(), exercise.id)) },
            onFavouriteClick = { exercise ->
                Repository.toggleFavourite(exercise.id)
                refreshExercises(rvExercises, tvEmpty)
            },
            onEditClick = { exercise ->
                startActivity(ExerciseEditorActivity.editIntent(requireContext(), exercise.id))
            }
        )
        rvExercises.layoutManager = LinearLayoutManager(requireContext())
        rvExercises.adapter = exerciseAdapter

        planAdapter = PlanAdapter(
            onClick = { plan -> startActivity(TimerActivity.startWithPlan(requireContext(), plan.id)) },
            onEditClick = { plan ->
                startActivity(CreatePlanActivity.editIntent(requireContext(), plan.id))
            }
        )
        rvPlans.layoutManager = LinearLayoutManager(requireContext())
        rvPlans.adapter = planAdapter

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTab = tab.position
                scrollTags.visibility = if (currentTab == 0) View.VISIBLE else View.GONE
                rvExercises.visibility = if (currentTab == 0) View.VISIBLE else View.GONE
                rvPlans.visibility = if (currentTab == 0) View.GONE else View.VISIBLE
                refreshAll(rvExercises, rvPlans, tvEmpty)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        btnSearch.setOnClickListener {
            etSearch.visibility = if (etSearch.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            if (etSearch.visibility == View.GONE) {
                etSearch.setText("")
            }
        }
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString().orEmpty()
                refreshExercises(rvExercises, tvEmpty)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        fab.setOnClickListener {
            if (currentTab == 0) {
                startActivity(ExerciseEditorActivity.createIntent(requireContext()))
            } else {
                startActivity(CreatePlanActivity.createIntent(requireContext()))
            }
        }

        buildTagChips(tagContainer, rvExercises, tvEmpty)
        refreshAll(rvExercises, rvPlans, tvEmpty)
    }

    override fun onResume() {
        super.onResume()
        view?.let {
            val tagContainer = it.findViewById<LinearLayout>(R.id.tagContainer)
            val rvExercises = it.findViewById<RecyclerView>(R.id.rvExercises)
            val rvPlans = it.findViewById<RecyclerView>(R.id.rvPlans)
            val tvEmpty = it.findViewById<TextView>(R.id.tvEmptyLibrary)
            buildTagChips(tagContainer, rvExercises, tvEmpty)
            refreshAll(rvExercises, rvPlans, tvEmpty)
        }
    }

    private fun buildTagChips(container: LinearLayout, rvExercises: RecyclerView, tvEmpty: TextView) {
        container.removeAllViews()
        val tags = mutableListOf(FILTER_ALL, FILTER_FAVOURITES)
        tags.addAll(Repository.allTags())
        if (!tags.contains(selectedFilter)) selectedFilter = FILTER_ALL

        for (tag in tags) {
            val chip = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_tag_chip, container, false) as TextView
            chip.text = when (tag) {
                FILTER_ALL -> getString(R.string.all_tag)
                FILTER_FAVOURITES -> getString(R.string.favourite_tag)
                else -> tag
            }
            applyChipStyle(chip, tag == selectedFilter)
            chip.setOnClickListener {
                selectedFilter = tag
                for (i in 0 until container.childCount) {
                    val child = container.getChildAt(i) as TextView
                    applyChipStyle(child, child == chip)
                }
                refreshExercises(rvExercises, tvEmpty)
            }
            container.addView(chip)
        }
    }

    private fun applyChipStyle(chip: TextView, selected: Boolean) {
        chip.setBackgroundResource(if (selected) R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected)
        chip.setTextColor(
            ContextCompat.getColor(requireContext(), if (selected) R.color.white else R.color.text_secondary)
        )
    }

    private fun refreshAll(rvExercises: RecyclerView, rvPlans: RecyclerView, tvEmpty: TextView) {
        refreshExercises(rvExercises, tvEmpty)
        refreshPlans(rvPlans, tvEmpty)
    }

    private fun refreshExercises(rvExercises: RecyclerView, tvEmpty: TextView) {
        if (currentTab != 0) return
        var list: List<Exercise> = Repository.getExercises()
        list = when (selectedFilter) {
            FILTER_ALL -> list
            FILTER_FAVOURITES -> list.filter { it.favourite }
            else -> list.filter { it.tags.contains(selectedFilter) }
        }
        if (searchQuery.isNotBlank()) {
            list = list.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
        exerciseAdapter.submitList(list)
        tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        tvEmpty.text = getString(R.string.empty_exercises)
        rvExercises.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun refreshPlans(rvPlans: RecyclerView, tvEmpty: TextView) {
        if (currentTab != 1) return
        val list: List<ExercisePlan> = Repository.getPlans()
        planAdapter.submitList(list)
        tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        tvEmpty.text = getString(R.string.empty_plans)
        rvPlans.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
    }
}
