package com.fitnnes.gym.stats

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.fitnnes.gym.R
import com.fitnnes.gym.util.TimeFormat

data class ExerciseStatRow(
    val exerciseName: String,
    val count: Int,
    val totalSeconds: Int,
    val calories: Int,
    val colorHex: String
)

class StatExerciseAdapter(
    private var items: List<ExerciseStatRow> = emptyList()
) : RecyclerView.Adapter<StatExerciseAdapter.RowViewHolder>() {

    fun submitList(newItems: List<ExerciseStatRow>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_stat_exercise, parent, false)
        return RowViewHolder(view)
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class RowViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dot = itemView.findViewById<View>(R.id.dotColor)
        private val tvName = itemView.findViewById<TextView>(R.id.tvStatExerciseName)
        private val tvDetail = itemView.findViewById<TextView>(R.id.tvStatExerciseDetail)

        fun bind(row: ExerciseStatRow) {
            tvName.text = row.exerciseName
            tvDetail.text = "🗒 ${row.count}   ⏱ ${TimeFormat.readable(row.totalSeconds)}   🔥 ${row.calories}kcal"
            runCatching {
                dot.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.parseColor(row.colorHex))
            }
        }
    }
}
