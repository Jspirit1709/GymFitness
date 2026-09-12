package com.fitnnes.gym.stats

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fitnnes.gym.R
import com.fitnnes.gym.data.Repository
import com.fitnnes.gym.data.WorkoutSession
import com.fitnnes.gym.util.TimeFormat
import com.fitnnes.gym.workoutdomain.Exercise
import com.fitnnes.gym.workoutdomain.PhaseType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class StatsFragment : Fragment(R.layout.fragment_stats) {

    private var weekOffset = 0
    private lateinit var statAdapter: StatExerciseAdapter
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val dayLabels = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")
    private val palette = listOf("#7CA982", "#3B7DB8", "#D4A373", "#B85C5C")

    private lateinit var tvStatQuantity: TextView
    private lateinit var tvStatTotal: TextView
    private lateinit var tvStatActive: TextView
    private lateinit var tvStatCalories: TextView
    private lateinit var tvWeekRange: TextView
    private lateinit var tvStatsEmpty: TextView
    private lateinit var chartContainer: LinearLayout
    private lateinit var rvStatExercises: RecyclerView
    private lateinit var btnWeekPrev: ImageButton
    private lateinit var btnWeekNext: ImageButton

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvStatQuantity = view.findViewById(R.id.tvStatQuantity)
        tvStatTotal = view.findViewById(R.id.tvStatTotal)
        tvStatActive = view.findViewById(R.id.tvStatActive)
        tvStatCalories = view.findViewById(R.id.tvStatCalories)
        tvWeekRange = view.findViewById(R.id.tvWeekRange)
        tvStatsEmpty = view.findViewById(R.id.tvStatsEmpty)
        chartContainer = view.findViewById(R.id.chartContainer)
        rvStatExercises = view.findViewById(R.id.rvStatExercises)
        btnWeekPrev = view.findViewById(R.id.btnWeekPrev)
        btnWeekNext = view.findViewById(R.id.btnWeekNext)

        statAdapter = StatExerciseAdapter()
        rvStatExercises.layoutManager = LinearLayoutManager(requireContext())
        rvStatExercises.adapter = statAdapter

        btnWeekPrev.setOnClickListener {
            weekOffset -= 1
            refresh()
        }
        btnWeekNext.setOnClickListener {
            if (weekOffset < 0) {
                weekOffset += 1
                refresh()
            }
        }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun weekBounds(offset: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.WEEK_OF_YEAR, offset)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 7)
        val end = cal.timeInMillis
        return start to end
    }

    private fun activeSecondsFor(exercise: Exercise): Int {
        return if (exercise.useCustomIntervals && exercise.customSequence.isNotEmpty()) {
            exercise.customSequence.filter { it.phaseType == PhaseType.WORK }.sumOf { it.time } * exercise.rounds
        } else {
            exercise.workTime * exercise.iterations * exercise.rounds
        }
    }

    private fun refresh() {
        val (start, end) = weekBounds(weekOffset)
        val (prevStart, prevEnd) = weekBounds(weekOffset - 1)

        val allSessions = Repository.getSessions()
        val sessions = allSessions.filter { it.dateMillis in start until end }
        val prevSessions = allSessions.filter { it.dateMillis in prevStart until prevEnd }

        btnWeekNext.isEnabled = weekOffset < 0
        btnWeekNext.alpha = if (weekOffset < 0) 1f else 0.35f

        val delta = sessions.size - prevSessions.size
        tvStatQuantity.text = when {
            delta > 0 -> "${sessions.size} (+$delta)"
            delta < 0 -> "${sessions.size} ($delta)"
            else -> "${sessions.size}"
        }

        val totalSeconds = sessions.sumOf { it.durationSeconds }
        tvStatTotal.text = TimeFormat.readable(totalSeconds)

        val activeSeconds = sessions.sumOf { session ->
            Repository.getExercise(session.exerciseId)?.let { activeSecondsFor(it) } ?: session.durationSeconds
        }
        tvStatActive.text = TimeFormat.readable(activeSeconds)

        val totalCalories = sessions.sumOf { it.calories }
        tvStatCalories.text = "🔥 ${getString(R.string.calories)}: ${totalCalories}kcal"

        tvWeekRange.text = getString(
            R.string.week_range,
            dateFormat.format(Date(start)),
            dateFormat.format(Date(end - 1))
        )

        tvStatsEmpty.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE

        buildChart(start, sessions)
        buildExerciseList(sessions)
    }

    private fun buildChart(weekStart: Long, sessions: List<WorkoutSession>) {
        chartContainer.removeAllViews()
        val cal = Calendar.getInstance()
        cal.timeInMillis = weekStart
        val counts = IntArray(7)
        for (i in 0 until 7) {
            val dayStart = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
            val dayEnd = cal.timeInMillis
            counts[i] = sessions.count { it.dateMillis in dayStart until dayEnd }
        }
        val maxCount = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
        val density = resources.displayMetrics.density
        val maxBarHeightDp = 110

        for (i in 0 until 7) {
            val column = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            }
            val barHeightDp = if (counts[i] == 0) 4 else (maxBarHeightDp * counts[i] / maxCount).coerceAtLeast(8)
            val bar = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams((24 * density).toInt(), (barHeightDp * density).toInt())
                setBackgroundResource(R.drawable.bg_bar_top)
            }
            val label = TextView(requireContext()).apply {
                text = dayLabels[i]
                textSize = 11f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (4 * density).toInt() }
            }
            column.addView(bar)
            column.addView(label)
            chartContainer.addView(column)
        }
    }

    private fun buildExerciseList(sessions: List<WorkoutSession>) {
        val grouped = sessions.groupBy { it.exerciseName }
        val rows = grouped.entries.mapIndexed { index, entry ->
            val list = entry.value
            ExerciseStatRow(
                exerciseName = entry.key,
                count = list.size,
                totalSeconds = list.sumOf { it.durationSeconds },
                calories = list.sumOf { it.calories },
                colorHex = palette[index % palette.size]
            )
        }.sortedByDescending { it.count }
        statAdapter.submitList(rows)
    }
}
