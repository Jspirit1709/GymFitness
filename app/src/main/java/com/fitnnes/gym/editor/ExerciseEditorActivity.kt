package com.fitnnes.gym.editor

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fitnnes.gym.R
import com.fitnnes.gym.data.Repository
import com.fitnnes.gym.workoutdomain.CustomInterval
import com.fitnnes.gym.workoutdomain.Exercise
import com.fitnnes.gym.workoutdomain.ExerciseType
import com.fitnnes.gym.workoutdomain.PhaseType

class ExerciseEditorActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_EXERCISE_ID = "EXTRA_EXERCISE_ID"

        fun createIntent(context: Context): Intent =
            Intent(context, ExerciseEditorActivity::class.java)

        fun editIntent(context: Context, exerciseId: String): Intent =
            Intent(context, ExerciseEditorActivity::class.java).apply {
                putExtra(EXTRA_EXERCISE_ID, exerciseId)
            }
    }

    private var isNew = true
    private lateinit var exercise: Exercise
    private var tags: MutableList<String> = mutableListOf()

    private lateinit var intervalAdapter: IntervalAdapter

    // Views
    private lateinit var tvEditorTitle: TextView
    private lateinit var btnFavourite: ImageButton
    private lateinit var chipNotes: TextView
    private lateinit var chipMedia: TextView
    private lateinit var etName: EditText
    private lateinit var etNotes: EditText
    private lateinit var mediaSection: LinearLayout
    private lateinit var ivMediaPreview: ImageView
    private lateinit var btnPickMedia: TextView
    private lateinit var btnRemoveMedia: TextView
    private lateinit var tagsContainer: LinearLayout
    private lateinit var switchCustomIntervals: SwitchCompat
    private lateinit var simpleModeSection: LinearLayout
    private lateinit var customModeSection: LinearLayout
    private lateinit var etPrepare: EditText
    private lateinit var etWork: EditText
    private lateinit var etRest: EditText
    private lateinit var etIterations: EditText
    private lateinit var etCooldown: EditText
    private lateinit var tvRoundsValue: TextView
    private lateinit var rvIntervals: RecyclerView
    private lateinit var btnDeleteExercise: TextView

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            exercise.mediaUri = uri.toString()
            updateMediaPreview()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exercise_editor)

        val id = intent.getStringExtra(EXTRA_EXERCISE_ID)
        exercise = if (id != null) Repository.getExercise(id) ?: Exercise() else Exercise()
        isNew = id == null || Repository.getExercise(id) == null
        tags = exercise.tags.toMutableList()

        bindViews()
        populateFromExercise()
        setupListeners()
    }

    private fun bindViews() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        tvEditorTitle = findViewById(R.id.tvEditorTitle)
        btnFavourite = findViewById(R.id.btnFavourite)
        chipNotes = findViewById(R.id.chipNotes)
        chipMedia = findViewById(R.id.chipMedia)
        etName = findViewById(R.id.etName)
        etNotes = findViewById(R.id.etNotes)
        mediaSection = findViewById(R.id.mediaSection)
        ivMediaPreview = findViewById(R.id.ivMediaPreview)
        btnPickMedia = findViewById(R.id.btnPickMedia)
        btnRemoveMedia = findViewById(R.id.btnRemoveMedia)
        tagsContainer = findViewById(R.id.tagsContainer)
        switchCustomIntervals = findViewById(R.id.switchCustomIntervals)
        simpleModeSection = findViewById(R.id.simpleModeSection)
        customModeSection = findViewById(R.id.customModeSection)
        rvIntervals = findViewById(R.id.rvIntervals)
        tvRoundsValue = findViewById(R.id.tvRoundsValue)
        btnDeleteExercise = findViewById(R.id.btnDeleteExercise)

        etPrepare = findViewById<LinearLayout>(R.id.fieldPrepare).findViewById(R.id.etFieldValue)
        etWork = findViewById<LinearLayout>(R.id.fieldWork).findViewById(R.id.etFieldValue)
        etRest = findViewById<LinearLayout>(R.id.fieldRest).findViewById(R.id.etFieldValue)
        etIterations = findViewById<LinearLayout>(R.id.fieldIterations).findViewById(R.id.etFieldValue)
        etCooldown = findViewById<LinearLayout>(R.id.fieldCooldown).findViewById(R.id.etFieldValue)

        findViewById<LinearLayout>(R.id.fieldPrepare).findViewById<TextView>(R.id.tvFieldLabel).text =
            getString(R.string.prepare_time)
        findViewById<LinearLayout>(R.id.fieldWork).findViewById<TextView>(R.id.tvFieldLabel).text =
            getString(R.string.work_time)
        findViewById<LinearLayout>(R.id.fieldRest).findViewById<TextView>(R.id.tvFieldLabel).text =
            getString(R.string.rest_time)
        findViewById<LinearLayout>(R.id.fieldIterations).findViewById<TextView>(R.id.tvFieldLabel).text =
            getString(R.string.iterations)
        findViewById<LinearLayout>(R.id.fieldCooldown).findViewById<TextView>(R.id.tvFieldLabel).text =
            getString(R.string.cooldown_time)

        rvIntervals.layoutManager = LinearLayoutManager(this)
        intervalAdapter = IntervalAdapter(
            onEdit = { position, interval -> showIntervalDialog(position, interval) },
            onDelete = { position ->
                val list = intervalAdapter.currentList().toMutableList()
                list.removeAt(position)
                intervalAdapter.submitList(list)
            }
        )
        rvIntervals.adapter = intervalAdapter
    }

    private fun populateFromExercise() {
        tvEditorTitle.text = if (isNew) getString(R.string.new_exercise) else exercise.name
        btnFavourite.setImageResource(if (exercise.favourite) R.drawable.ic_star else R.drawable.ic_star_outline)
        etName.setText(exercise.name.takeIf { !isNew } ?: "")
        etNotes.setText(exercise.notes ?: "")
        if (!exercise.notes.isNullOrBlank()) {
            etNotes.visibility = View.VISIBLE
            applyChipStyle(chipNotes, true)
        }
        if (!exercise.mediaUri.isNullOrBlank()) {
            mediaSection.visibility = View.VISIBLE
            applyChipStyle(chipMedia, true)
            updateMediaPreview()
        }

        etPrepare.setText(exercise.prepareTime.toString())
        etWork.setText(exercise.workTime.toString())
        etRest.setText(exercise.restTime.toString())
        etIterations.setText(exercise.iterations.toString())
        etCooldown.setText(exercise.coolDownTime.toString())

        switchCustomIntervals.isChecked = exercise.useCustomIntervals
        simpleModeSection.visibility = if (exercise.useCustomIntervals) View.GONE else View.VISIBLE
        customModeSection.visibility = if (exercise.useCustomIntervals) View.VISIBLE else View.GONE
        tvRoundsValue.text = exercise.rounds.coerceAtLeast(1).toString()
        intervalAdapter.submitList(exercise.customSequence)

        btnDeleteExercise.visibility = if (isNew) View.GONE else View.VISIBLE

        rebuildTagChips()
    }

    private fun setupListeners() {
        btnFavourite.setOnClickListener {
            exercise.favourite = !exercise.favourite
            btnFavourite.setImageResource(if (exercise.favourite) R.drawable.ic_star else R.drawable.ic_star_outline)
        }

        findViewById<ImageButton>(R.id.btnSave).setOnClickListener { saveExercise() }

        findViewById<ImageButton>(R.id.btnMore).setOnClickListener { anchor ->
            val popup = PopupMenu(this, anchor)
            popup.menu.add(0, 1, 0, getString(R.string.delete))
            popup.setOnMenuItemClickListener {
                confirmDelete()
                true
            }
            popup.show()
        }

        chipNotes.setOnClickListener {
            val show = etNotes.visibility != View.VISIBLE
            etNotes.visibility = if (show) View.VISIBLE else View.GONE
            applyChipStyle(chipNotes, show)
        }

        chipMedia.setOnClickListener {
            val show = mediaSection.visibility != View.VISIBLE
            mediaSection.visibility = if (show) View.VISIBLE else View.GONE
            applyChipStyle(chipMedia, show)
        }

        btnPickMedia.setOnClickListener { pickImageLauncher.launch(arrayOf("image/*")) }
        btnRemoveMedia.setOnClickListener {
            exercise.mediaUri = null
            updateMediaPreview()
        }

        switchCustomIntervals.setOnCheckedChangeListener { _, isChecked ->
            simpleModeSection.visibility = if (isChecked) View.GONE else View.VISIBLE
            customModeSection.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        findViewById<ImageButton>(R.id.btnRoundsMinus).setOnClickListener {
            val value = (tvRoundsValue.text.toString().toIntOrNull() ?: 1) - 1
            tvRoundsValue.text = value.coerceAtLeast(1).toString()
        }
        findViewById<ImageButton>(R.id.btnRoundsPlus).setOnClickListener {
            val value = (tvRoundsValue.text.toString().toIntOrNull() ?: 1) + 1
            tvRoundsValue.text = value.coerceAtMost(99).toString()
        }

        findViewById<TextView>(R.id.btnAddInterval).setOnClickListener { showIntervalDialog(-1, null) }

        btnDeleteExercise.setOnClickListener { confirmDelete() }
    }

    private fun updateMediaPreview() {
        if (exercise.mediaUri.isNullOrBlank()) {
            ivMediaPreview.visibility = View.GONE
        } else {
            ivMediaPreview.visibility = View.VISIBLE
            runCatching { ivMediaPreview.setImageURI(android.net.Uri.parse(exercise.mediaUri)) }
        }
    }

    private fun applyChipStyle(chip: TextView, selected: Boolean) {
        chip.setBackgroundResource(if (selected) R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected)
        chip.setTextColor(ContextCompat.getColor(this, if (selected) R.color.white else R.color.text_secondary))
    }

    private fun rebuildTagChips() {
        tagsContainer.removeAllViews()
        for (tag in tags) {
            val chip = LayoutInflater.from(this).inflate(R.layout.item_tag_chip, tagsContainer, false) as TextView
            chip.text = tag
            chip.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle(tag)
                    .setMessage(getString(R.string.delete) + "?")
                    .setPositiveButton(getString(R.string.delete)) { _, _ ->
                        tags.remove(tag)
                        rebuildTagChips()
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            }
            tagsContainer.addView(chip)
        }
        val addChip = LayoutInflater.from(this).inflate(R.layout.item_tag_chip, tagsContainer, false) as TextView
        addChip.text = "+ " + getString(R.string.new_tag)
        addChip.setOnClickListener { showAddTagDialog() }
        tagsContainer.addView(addChip)
    }

    private fun showAddTagDialog() {
        val input = EditText(this)
        input.hint = getString(R.string.add_tag_hint)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.new_tag))
            .setView(input)
            .setPositiveButton(getString(R.string.add)) { _, _ ->
                val value = input.text.toString().trim()
                if (value.isNotEmpty() && !tags.contains(value)) {
                    tags.add(value)
                    rebuildTagChips()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showIntervalDialog(position: Int, existing: CustomInterval?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_interval, null)
        val etIntervalName = view.findViewById<EditText>(R.id.etIntervalName)
        val spinner = view.findViewById<Spinner>(R.id.spinnerPhaseType)
        val etDuration = view.findViewById<EditText>(R.id.etIntervalDuration)
        val etReps = view.findViewById<EditText>(R.id.etIntervalRepetitions)

        val phaseLabels = listOf(
            getString(R.string.prepare), getString(R.string.work),
            getString(R.string.rest), getString(R.string.cooldown), getString(R.string.transition)
        )
        val phaseValues = listOf(
            PhaseType.PREPARE, PhaseType.WORK, PhaseType.REST, PhaseType.COOL_DOWN, PhaseType.TRANSITION
        )
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, phaseLabels)

        etIntervalName.setText(existing?.name ?: "")
        spinner.setSelection(phaseValues.indexOf(existing?.phaseType ?: PhaseType.WORK).coerceAtLeast(0))
        etDuration.setText((existing?.time ?: 30).toString())
        etReps.setText(existing?.repetitions?.toString() ?: "")

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) getString(R.string.add_interval) else getString(R.string.edit_exercise))
            .setView(view)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val name = etIntervalName.text.toString().trim()
                val duration = etDuration.text.toString().toIntOrNull() ?: 30
                val reps = etReps.text.toString().toIntOrNull()
                val phase = phaseValues[spinner.selectedItemPosition]
                val interval = CustomInterval(name = name, time = duration, phaseType = phase, repetitions = reps)

                val list = intervalAdapter.currentList().toMutableList()
                if (position >= 0) list[position] = interval else list.add(interval)
                intervalAdapter.submitList(list)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.delete_exercise_confirm))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                Repository.deleteExercise(exercise.id)
                finish()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun saveExercise() {
        val name = etName.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, getString(R.string.exercise_name_hint), Toast.LENGTH_SHORT).show()
            return
        }

        exercise.name = name
        exercise.notes = etNotes.text.toString().trim().ifEmpty { null }
        exercise.tags = tags.toList()
        exercise.useCustomIntervals = switchCustomIntervals.isChecked

        if (switchCustomIntervals.isChecked) {
            exercise.customSequence = intervalAdapter.currentList()
            exercise.rounds = tvRoundsValue.text.toString().toIntOrNull() ?: 1
            exercise.exerciseType = ExerciseType.CUSTOM
        } else {
            exercise.prepareTime = etPrepare.text.toString().toIntOrNull() ?: 0
            exercise.workTime = etWork.text.toString().toIntOrNull() ?: 30
            exercise.restTime = etRest.text.toString().toIntOrNull() ?: 0
            exercise.iterations = (etIterations.text.toString().toIntOrNull() ?: 1).coerceAtLeast(1)
            exercise.coolDownTime = etCooldown.text.toString().toIntOrNull() ?: 0
            exercise.exerciseType = ExerciseType.HIIT
        }

        Repository.upsertExercise(exercise)
        finish()
    }
}
