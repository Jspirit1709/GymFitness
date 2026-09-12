package com.fitnnes.gym.menu

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fitnnes.gym.R
import com.fitnnes.gym.data.Repository
import com.fitnnes.gym.util.TimeFormat
import com.fitnnes.gym.workoutdomain.Exercise
import com.fitnnes.gym.workoutdomain.ExercisePlan

class CreatePlanActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_PLAN_ID = "EXTRA_PLAN_ID"

        fun createIntent(context: Context): Intent = Intent(context, CreatePlanActivity::class.java)

        fun editIntent(context: Context, planId: String): Intent =
            Intent(context, CreatePlanActivity::class.java).apply { putExtra(EXTRA_PLAN_ID, planId) }
    }

    private var isNew = true
    private lateinit var plan: ExercisePlan
    private val selectedIds = LinkedHashSet<String>()
    private lateinit var adapter: ExercisePickAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_plan)

        val id = intent.getStringExtra(EXTRA_PLAN_ID)
        plan = if (id != null) Repository.getPlan(id) ?: ExercisePlan() else ExercisePlan()
        isNew = id == null || Repository.getPlan(id) == null
        selectedIds.addAll(plan.exerciseIds)

        val etPlanName = findViewById<EditText>(R.id.etPlanName)
        val tvTitle = findViewById<TextView>(R.id.tvPlanEditorTitle)
        val btnDelete = findViewById<ImageButton>(R.id.btnDeletePlan)
        val rv = findViewById<RecyclerView>(R.id.rvPickExercises)

        etPlanName.setText(plan.name.takeIf { !isNew } ?: "")
        tvTitle.text = if (isNew) getString(R.string.new_plan_title) else getString(R.string.edit_plan_title)
        btnDelete.visibility = if (isNew) View.GONE else View.VISIBLE

        adapter = ExercisePickAdapter(Repository.getExercises(), selectedIds) { refreshOrderBadges(rv) }
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        findViewById<ImageButton>(R.id.btnBackPlan).setOnClickListener { finish() }

        findViewById<ImageButton>(R.id.btnSavePlan).setOnClickListener {
            val name = etPlanName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, getString(R.string.plan_name_hint), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedIds.isEmpty()) {
                Toast.makeText(this, getString(R.string.select_exercises), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            plan.name = name
            plan.exerciseIds = selectedIds.toList()
            Repository.upsertPlan(plan)
            finish()
        }

        btnDelete.setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.delete_plan_confirm))
                .setPositiveButton(getString(R.string.delete)) { _, _ ->
                    Repository.deletePlan(plan.id)
                    finish()
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }

    private fun refreshOrderBadges(rv: RecyclerView) {
        adapter.notifyDataSetChanged()
    }
}

private class ExercisePickAdapter(
    private val items: List<Exercise>,
    private val selectedIds: LinkedHashSet<String>,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<ExercisePickAdapter.PickViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PickViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_exercise_pick, parent, false)
        return PickViewHolder(view)
    }

    override fun onBindViewHolder(holder: PickViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class PickViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkbox = itemView.findViewById<CheckBox>(R.id.checkboxExercise)
        private val tvName = itemView.findViewById<TextView>(R.id.tvPickName)
        private val tvSummary = itemView.findViewById<TextView>(R.id.tvPickSummary)
        private val tvOrder = itemView.findViewById<TextView>(R.id.tvPickOrder)

        fun bind(exercise: Exercise) {
            tvName.text = exercise.name
            tvSummary.text = "➤ ${TimeFormat.mmss(exercise.getTotalTime())}"

            checkbox.setOnCheckedChangeListener(null)
            checkbox.isChecked = selectedIds.contains(exercise.id)
            updateOrderBadge(exercise)

            checkbox.setOnCheckedChangeListener { _, checked ->
                if (checked) selectedIds.add(exercise.id) else selectedIds.remove(exercise.id)
                onSelectionChanged()
            }
            itemView.setOnClickListener { checkbox.isChecked = !checkbox.isChecked }
        }

        private fun updateOrderBadge(exercise: Exercise) {
            val order = selectedIds.toList().indexOf(exercise.id)
            if (order >= 0) {
                tvOrder.visibility = View.VISIBLE
                tvOrder.text = (order + 1).toString()
            } else {
                tvOrder.visibility = View.GONE
            }
        }
    }
}
