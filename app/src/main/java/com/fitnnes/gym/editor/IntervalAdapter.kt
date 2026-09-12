package com.fitnnes.gym.editor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.fitnnes.gym.R
import com.fitnnes.gym.util.TimeFormat
import com.fitnnes.gym.workoutdomain.CustomInterval
import com.fitnnes.gym.workoutdomain.PhaseType

class IntervalAdapter(
    private var items: MutableList<CustomInterval> = mutableListOf(),
    private val onEdit: (Int, CustomInterval) -> Unit,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<IntervalAdapter.IntervalViewHolder>() {

    fun submitList(newItems: List<CustomInterval>) {
        items = newItems.toMutableList()
        notifyDataSetChanged()
    }

    fun currentList(): List<CustomInterval> = items

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IntervalViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_interval, parent, false)
        return IntervalViewHolder(view)
    }

    override fun onBindViewHolder(holder: IntervalViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    inner class IntervalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName = itemView.findViewById<TextView>(R.id.tvIntervalName)
        private val tvDetail = itemView.findViewById<TextView>(R.id.tvIntervalDetail)
        private val dot = itemView.findViewById<View>(R.id.viewPhaseDot)
        private val btnMenu = itemView.findViewById<ImageButton>(R.id.btnIntervalMenu)

        fun bind(interval: CustomInterval, position: Int) {
            tvName.text = interval.name.ifBlank { "Intervalo" }
            tvDetail.text = if (interval.repetitions != null) {
                "↑ ${interval.repetitions} Repeticiones"
            } else {
                "↗ ${TimeFormat.mmss(interval.time)}"
            }

            val colorRes = when (interval.phaseType) {
                PhaseType.PREPARE -> R.color.phase_prepare
                PhaseType.WORK -> R.color.phase_work
                PhaseType.REST -> R.color.phase_rest
                PhaseType.COOL_DOWN -> R.color.phase_cooldown
                PhaseType.TRANSITION -> R.color.phase_transition
            }
            dot.backgroundTintList = ContextCompat.getColorStateList(itemView.context, colorRes)

            itemView.setOnClickListener { onEdit(position, interval) }
            btnMenu.setOnClickListener { anchor ->
                val popup = PopupMenu(anchor.context, anchor)
                popup.menu.add(0, 1, 0, "Editar")
                popup.menu.add(0, 2, 1, anchor.context.getString(R.string.delete))
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> onEdit(position, interval)
                        2 -> onDelete(position)
                    }
                    true
                }
                popup.show()
            }
        }
    }
}
