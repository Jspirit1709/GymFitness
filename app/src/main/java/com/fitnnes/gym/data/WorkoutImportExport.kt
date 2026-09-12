package com.fitnnes.gym.data

import com.google.gson.Gson
import com.fitnnes.gym.workoutdomain.CustomInterval
import com.fitnnes.gym.workoutdomain.Exercise
import com.fitnnes.gym.workoutdomain.ExerciseType
import com.fitnnes.gym.workoutdomain.PhaseType

/**
 * DTOs del formato de importación/exportación de entrenamientos.
 * Compatible con exportaciones tipo "customSequence" (fases con nombre,
 * tiempo, tipo de fase numérico y repeticiones manuales opcionales).
 */
data class ImportIntervalDto(
    val mediaId: String? = null,
    val name: String = "",
    val phaseType: Int = 1,
    val time: Int = 30,
    val manualRepetitions: Int? = null
)

data class ImportExerciseDto(
    val customSequence: List<ImportIntervalDto>? = null,
    val favourite: Boolean = false,
    val iterations: Int = 1,
    val mediaId: String? = null,
    val name: String = "Ejercicio importado",
    val prepareTime: Int = 10,
    val restTime: Int = 10,
    val workTime: Int = 30
)

data class ImportRootDto(
    val exercises: List<ImportExerciseDto>? = null
)

object WorkoutImportExport {
    private val gson = Gson()

    /**
     * Convierte un JSON externo a una lista de [Exercise] utilizables por la app.
     * Lanza excepción si el JSON no tiene el formato esperado (se recomienda
     * envolver la llamada en un try/catch al usarla).
     */
    fun parseImport(json: String): List<Exercise> {
        val root = gson.fromJson(json, ImportRootDto::class.java)
        val list = root?.exercises ?: emptyList()
        return list.map { dto -> dto.toExercise() }
    }

    /** Exporta una lista de ejercicios de la app al mismo formato JSON. */
    fun exportToJson(exercises: List<Exercise>): String {
        val dtoList = exercises.map { it.toDto() }
        return gson.toJson(ImportRootDto(exercises = dtoList))
    }

    private fun ImportExerciseDto.toExercise(): Exercise {
        val sequence = customSequence?.map { interval ->
            CustomInterval(
                name = interval.name,
                time = interval.time,
                phaseType = PhaseType.values().getOrElse(interval.phaseType) { PhaseType.WORK },
                repetitions = interval.manualRepetitions
            )
        } ?: emptyList()

        return Exercise(
            name = name,
            prepareTime = prepareTime,
            workTime = workTime,
            restTime = restTime,
            iterations = iterations,
            favourite = favourite,
            exerciseType = ExerciseType.CUSTOM,
            useCustomIntervals = sequence.isNotEmpty(),
            customSequence = sequence
        )
    }

    private fun Exercise.toDto(): ImportExerciseDto {
        val sequenceDto = customSequence.map { interval ->
            ImportIntervalDto(
                name = interval.name,
                phaseType = interval.phaseType.ordinal,
                time = interval.time,
                manualRepetitions = interval.repetitions
            )
        }
        return ImportExerciseDto(
            customSequence = sequenceDto.ifEmpty { null },
            favourite = favourite,
            iterations = iterations,
            name = name,
            prepareTime = prepareTime,
            restTime = restTime,
            workTime = workTime
        )
    }
}
