package com.fitnnes.gym.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.fitnnes.gym.R
import com.fitnnes.gym.data.Repository
import com.fitnnes.gym.util.TimeFormat
import com.fitnnes.gym.workoutdomain.ExercisePlan

class PlanAdapter(
    private var items: List<ExercisePlan> = emptyList(),
    private val onClick: (ExercisePlan) -> Unit,
    private val onEditClick: (ExercisePlan) -> Unit
) : RecyclerView.Adapter<PlanAdapter.PlanViewHolder>() {

    fun submitList(newItems: List<ExercisePlan>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlanViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_plan_card, parent, false)
        return PlanViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlanViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class PlanViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName = itemView.findViewById<TextView>(R.id.tvPlanName)
        private val tvSummary = itemView.findViewById<TextView>(R.id.tvPlanSummary)
        private val tvTotal = itemView.findViewById<TextView>(R.id.tvPlanTotal)
        private val btnEdit = itemView.findViewById<ImageButton>(R.id.btnEditPlan)

        fun bind(plan: ExercisePlan) {
            val exercises = Repository.getExercisesForPlan(plan)
            tvName.text = plan.name
            tvSummary.text = if (exercises.isEmpty()) {
                "Sin ejercicios"
            } else {
                exercises.joinToString(", ") { it.name }
            }
            val totalSeconds = exercises.sumOf { it.getTotalTime() }
            tvTotal.text = "➤ ${TimeFormat.mmss(totalSeconds)}"

            itemView.setOnClickListener { onClick(plan) }
            btnEdit.setOnClickListener { onEditClick(plan) }
        }
    }
}
