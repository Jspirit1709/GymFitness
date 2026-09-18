package com.fitnnes.gym.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.fitnnes.gym.workoutdomain.CustomInterval
import com.fitnnes.gym.workoutdomain.Exercise
import com.fitnnes.gym.workoutdomain.ExercisePlan
import com.fitnnes.gym.workoutdomain.ExerciseType
import com.fitnnes.gym.workoutdomain.PhaseType

/**
 * Repositorio en memoria + persistencia en SharedPreferences (JSON vía Gson).
 * Simple pero centraliza ejercicios, planes y el historial de sesiones completadas.
 */
object Repository {

    private const val PREFS = "gymfitness_data"
    private const val KEY_EXERCISES = "exercises"
    private const val KEY_PLANS = "plans"
    private const val KEY_SESSIONS = "sessions"

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    private val exercises = mutableListOf<Exercise>()
    private val plans = mutableListOf<ExercisePlan>()
    private val sessions = mutableListOf<WorkoutSession>()

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        loadExercises()
        loadPlans()
        loadSessions()
        if (exercises.isEmpty()) {
            seedDefaults()
            saveExercises()
            savePlans()
        }
        initialized = true
    }

    // ---------- Ejercicios ----------

    fun getExercises(): List<Exercise> = exercises.sortedByDescending { it.createdAt }

    fun getExercise(id: String): Exercise? = exercises.find { it.id == id }

    fun upsertExercise(exercise: Exercise) {
        val idx = exercises.indexOfFirst { it.id == exercise.id }
        if (idx >= 0) exercises[idx] = exercise else exercises.add(0, exercise)
        saveExercises()
    }

    fun deleteExercise(id: String) {
        exercises.removeAll { it.id == id }
        plans.forEach { plan -> plan.exerciseIds = plan.exerciseIds.filterNot { it == id } }
        saveExercises()
        savePlans()
    }

    fun toggleFavourite(id: String) {
        exercises.find { it.id == id }?.let {
            it.favourite = !it.favourite
            saveExercises()
        }
    }

    fun allTags(): List<String> {
        return exercises.flatMap { it.tags }.distinct().sorted()
    }

    // ---------- Planes ----------

    fun getPlans(): List<ExercisePlan> = plans.sortedByDescending { it.createdAt }

    fun getPlan(id: String): ExercisePlan? = plans.find { it.id == id }

    fun upsertPlan(plan: ExercisePlan) {
        val idx = plans.indexOfFirst { it.id == plan.id }
        if (idx >= 0) plans[idx] = plan else plans.add(0, plan)
        savePlans()
    }

    fun deletePlan(id: String) {
        plans.removeAll { it.id == id }
        savePlans()
    }

    fun getExercisesForPlan(plan: ExercisePlan): List<Exercise> {
        return plan.exerciseIds.mapNotNull { id -> exercises.find { it.id == id } }
    }

    // ---------- Sesiones / historial ----------

    fun addSession(session: WorkoutSession) {
        sessions.add(0, session)
        saveSessions()
    }

    fun getSessions(): List<WorkoutSession> = sessions.sortedByDescending { it.dateMillis }

    fun getSessionsLastNDays(days: Int): List<WorkoutSession> {
        val cutoff = System.currentTimeMillis() - days.toLong() * 24 * 60 * 60 * 1000
        return sessions.filter { it.dateMillis >= cutoff }
    }

    // ---------- Importación / exportación ----------

    /**
     * Importa entrenamientos desde un JSON externo. Devuelve la cantidad de
     * ejercicios importados. Lanza excepción si el JSON es inválido.
     */
    fun importWorkoutsJson(json: String): Int {
        val imported = WorkoutImportExport.parseImport(json)
        if (imported.isEmpty()) return 0
        exercises.addAll(0, imported)
        saveExercises()
        groupImportedIntoPlans(imported)
        return imported.size
    }

    private fun groupImportedIntoPlans(imported: List<Exercise>) {
        // Agrupa por el texto antes de ":" (ej. "Lunes"), ignorando emojis y numeros iniciales.
        val groups = LinkedHashMap<String, MutableList<Exercise>>()
        for (ex in imported) {
            val idx = ex.name.indexOf(':')
            if (idx <= 0) continue
            val key = ex.name.substring(0, idx)
                .filter { it.isLetterOrDigit() || it == ' ' }
                .replace(Regex("^[0-9\\s]+"), "")
                .trim()
            if (key.isEmpty() || key.length > 30) continue
            groups.getOrPut(key) { mutableListOf() }.add(ex)
        }
        val base = System.currentTimeMillis()
        var created = 0
        for ((key, list) in groups) {
            if (list.size < 2) continue
            var name = "Plan - $key"
            var n = 2
            while (plans.any { it.name == name }) { name = "Plan - $key ($n)"; n++ }
            plans.add(
                ExercisePlan(
                    name = name,
                    exerciseIds = list.map { it.id },
                    createdAt = base - created
                )
            )
            created++
        }
        if (created > 0) savePlans()
    }

    /** Exporta todos los ejercicios actuales a un JSON descargable. */
    fun exportWorkoutsJson(): String {
        return WorkoutImportExport.exportToJson(exercises)
    }

    // ---------- Persistencia ----------

    private fun loadExercises() {
        val json = prefs.getString(KEY_EXERCISES, null) ?: return
        val type = object : TypeToken<List<Exercise>>() {}.type
        runCatching { gson.fromJson<List<Exercise>>(json, type) }.getOrNull()?.let {
            exercises.clear(); exercises.addAll(it)
        }
    }

    private fun saveExercises() {
        prefs.edit().putString(KEY_EXERCISES, gson.toJson(exercises)).apply()
    }

    private fun loadPlans() {
        val json = prefs.getString(KEY_PLANS, null) ?: return
        val type = object : TypeToken<List<ExercisePlan>>() {}.type
        runCatching { gson.fromJson<List<ExercisePlan>>(json, type) }.getOrNull()?.let {
            plans.clear(); plans.addAll(it)
        }
    }

    private fun savePlans() {
        prefs.edit().putString(KEY_PLANS, gson.toJson(plans)).apply()
    }

    private fun loadSessions() {
        val json = prefs.getString(KEY_SESSIONS, null) ?: return
        val type = object : TypeToken<List<WorkoutSession>>() {}.type
        runCatching { gson.fromJson<List<WorkoutSession>>(json, type) }.getOrNull()?.let {
            sessions.clear(); sessions.addAll(it)
        }
    }

    private fun saveSessions() {
        // conservamos como máximo 500 sesiones para no crecer indefinidamente
        while (sessions.size > 500) sessions.removeAt(sessions.size - 1)
        prefs.edit().putString(KEY_SESSIONS, gson.toJson(sessions)).apply()
    }

    private fun seedDefaults() {
        val burpees = Exercise(
            name = "Burpees", prepareTime = 15, workTime = 50, restTime = 15, iterations = 3,
            tags = listOf("Cardio", "Boxeo"), exerciseType = ExerciseType.HIIT, favourite = true
        )
        val jumpingJacks = Exercise(
            name = "Jumping Jacks", prepareTime = 10, workTime = 45, restTime = 15, iterations = 4,
            tags = listOf("Cardio"), exerciseType = ExerciseType.HIIT, favourite = true
        )
        val plank = Exercise(
            name = "Plank", prepareTime = 10, workTime = 150, restTime = 15, iterations = 1,
            tags = listOf("Core"), exerciseType = ExerciseType.CUSTOM
        )
        val pushUps = Exercise(
            name = "Push Ups", prepareTime = 10, workTime = 30, restTime = 15, iterations = 5,
            tags = listOf("Fuerza", "Upper-Body"), exerciseType = ExerciseType.HIIT,
            useCustomIntervals = true,
            rounds = 3,
            customSequence = listOf(
                CustomInterval(name = "Calentar", time = 20, phaseType = PhaseType.PREPARE),
                CustomInterval(name = "Ejercitar", time = 30, phaseType = PhaseType.WORK),
                CustomInterval(name = "Descanso", time = 10, phaseType = PhaseType.REST),
                CustomInterval(name = "Cooldown", time = 15, phaseType = PhaseType.COOL_DOWN, repetitions = 20)
            )
        )
        val ropes = Exercise(
            name = "Ropes", prepareTime = 30, workTime = 30, restTime = 15, iterations = 3,
            tags = listOf("Boxeo", "Cardio"), exerciseType = ExerciseType.HIIT
        )
        val yoga = Exercise(
            name = "Yoga", prepareTime = 15, workTime = 450, restTime = 15, iterations = 2,
            tags = listOf("Flexibilidad"), exerciseType = ExerciseType.CUSTOM
        )
        exercises.addAll(listOf(burpees, jumpingJacks, plank, pushUps, ropes, yoga))

        plans.add(
            ExercisePlan(
                name = "Full Body",
                exerciseIds = listOf(jumpingJacks.id, pushUps.id, burpees.id)
            )
        )
    }
}
