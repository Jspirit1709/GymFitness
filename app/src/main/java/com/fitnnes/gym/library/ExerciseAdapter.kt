package com.fitnnes.gym.library

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.fitnnes.gym.R
import com.fitnnes.gym.util.TimeFormat
import com.fitnnes.gym.workoutdomain.Exercise

class ExerciseAdapter(
    private var items: List<Exercise> = emptyList(),
    private val onClick: (Exercise) -> Unit,
    private val onFavouriteClick: (Exercise) -> Unit,
    private val onEditClick: (Exercise) -> Unit
) : RecyclerView.Adapter<ExerciseAdapter.ExerciseViewHolder>() {

    fun submitList(newItems: List<Exercise>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExerciseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_exercise_card, parent, false)
        return ExerciseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ExerciseViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val tvName = itemView.findViewById<android.widget.TextView>(R.id.tvExerciseName)
        private val tvSummary = itemView.findViewById<android.widget.TextView>(R.id.tvExerciseSummary)
        private val tvTotal = itemView.findViewById<android.widget.TextView>(R.id.tvExerciseTotal)
        private val btnFavourite = itemView.findViewById<android.widget.ImageButton>(R.id.btnFavourite)
        private val btnEdit = itemView.findViewById<android.widget.ImageButton>(R.id.btnEdit)

        fun bind(exercise: Exercise) {
            tvName.text = exercise.name
            tvSummary.text = "⟲ ${TimeFormat.mmss(exercise.prepareTime)}  ↗ ${TimeFormat.mmss(exercise.workTime)}  " +
                "↑ ${TimeFormat.mmss(exercise.restTime)}  ↻ ${exercise.iterations}"
            tvTotal.text = "➤ ${TimeFormat.mmss(exercise.getTotalTime())}"

            btnFavourite.setImageResource(
                if (exercise.favourite) R.drawable.ic_star else R.drawable.ic_star_outline
            )

            itemView.setOnClickListener { onClick(exercise) }
            btnFavourite.setOnClickListener { onFavouriteClick(exercise) }
            btnEdit.setOnClickListener { onEditClick(exercise) }
        }
    }
}
