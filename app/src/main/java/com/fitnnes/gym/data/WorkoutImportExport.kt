package com.fitnnes.gym.data

import com.google.gson.Gson
import com.fitnnes.gym.workoutdomain.CustomInterval
import com.fitnnes.gym.workoutdomain.Exercise
import com.fitnnes.gym.workoutdomain.ExerciseType
import com.fitnnes.gym.workoutdomain.MediaType
import com.fitnnes.gym.workoutdomain.PhaseType

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

data class ImportMediaDto(
    val id: String = "",
    val encodedFile: String? = null,
    val mimeType: String? = null,
    val sourceUri: String? = null,
    val fileName: String? = null
)

data class ImportRootDto(
    val exercises: List<ImportExerciseDto>? = null,
    val media: List<ImportMediaDto>? = null
)

object WorkoutImportExport {
    private val gson = Gson()

    fun parseImport(json: String): List<Exercise> {
        val root = gson.fromJson(json, ImportRootDto::class.java)
        val mediaMap = (root?.media ?: emptyList()).associateBy { it.id }
        val list = root?.exercises ?: emptyList()
        return list.map { dto -> dto.toExercise(mediaMap) }
    }

    fun exportToJson(exercises: List<Exercise>): String {
        val dtoList = exercises.map { it.toDto() }
        return gson.toJson(ImportRootDto(exercises = dtoList))
    }

    private fun resolveMedia(mediaId: String?, mediaMap: Map<String, ImportMediaDto>): Pair<String?, MediaType> {
        val media = mediaId?.let { mediaMap[it] } ?: return null to MediaType.NONE
        return when {
            !media.sourceUri.isNullOrBlank() && media.sourceUri.contains("youtu") ->
                media.sourceUri to MediaType.YOUTUBE
            !media.sourceUri.isNullOrBlank() ->
                media.sourceUri to MediaType.VIDEO_FILE
            !media.encodedFile.isNullOrBlank() -> {
                val mime = media.mimeType ?: "image/jpeg"
                "data:$mime;base64,${media.encodedFile}" to MediaType.IMAGE_BASE64
            }
            else -> null to MediaType.NONE
        }
    }

    private fun ImportExerciseDto.toExercise(mediaMap: Map<String, ImportMediaDto>): Exercise {
        val sequence = customSequence?.map { interval ->
            val (uri, type) = resolveMedia(interval.mediaId, mediaMap)
            CustomInterval(
                name = interval.name,
                time = interval.time,
                phaseType = PhaseType.values().getOrElse(interval.phaseType) { PhaseType.WORK },
                repetitions = interval.manualRepetitions,
                mediaUri = uri,
                mediaType = type
            )
        } ?: emptyList()

        val (exerciseUri, exerciseType) = resolveMedia(mediaId, mediaMap)

        return Exercise(
            name = name,
            prepareTime = prepareTime,
            workTime = workTime,
            restTime = restTime,
            iterations = iterations,
            favourite = favourite,
            exerciseType = ExerciseType.CUSTOM,
            useCustomIntervals = sequence.isNotEmpty(),
            customSequence = sequence,
            mediaUri = exerciseUri,
            mediaType = exerciseType
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
