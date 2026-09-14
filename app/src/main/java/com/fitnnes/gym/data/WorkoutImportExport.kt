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

    /**
     * Devuelve true solo si el string es una URI/URL realmente reproducible por el
     * dispositivo (tiene esquema conocido). Un simple nombre de archivo suelto como
     * "press_banca_plano_barra.jpg" (sobrante del exportador original, sin ruta real)
     * NO cuenta como reproducible: antes se estaba tratando como VIDEO_FILE válido
     * y rompía la reproducción de lo que en realidad eran imágenes.
     */
    private fun isPlayableUri(uri: String?): Boolean {
        if (uri.isNullOrBlank()) return false
        val lower = uri.trim().lowercase()
        return lower.startsWith("http://") ||
                lower.startsWith("https://") ||
                lower.startsWith("content://") ||
                lower.startsWith("file://") ||
                lower.startsWith("android.resource://")
    }

    /**
     * FIX: antes, cualquier sourceUri no vacío (incluyendo un simple nombre de archivo
     * sin esquema, como "foto.jpg") se clasificaba como VIDEO_FILE, aunque el media
     * fuera en realidad una imagen con su base64 embebido en encodedFile. Ahora:
     *   1) Un link de YouTube siempre gana (aunque el mimeType no lo indique).
     *   2) Si el mimeType es de imagen y hay encodedFile, se prioriza la imagen real
     *      por sobre cualquier sourceUri sobrante/inválido.
     *   3) Un sourceUri solo cuenta como video si es una URI/URL genuina reproducible
     *      (con esquema http/https/content/file), nunca un nombre de archivo suelto.
     *   4) Si nada de lo anterior aplica pero hay encodedFile, se usa como imagen
     *      (fallback seguro).
     */
    private fun resolveMedia(mediaId: String?, mediaMap: Map<String, ImportMediaDto>): Pair<String?, MediaType> {
        val media = mediaId?.let { mediaMap[it] } ?: return null to MediaType.NONE

        if (!media.sourceUri.isNullOrBlank() && media.sourceUri.contains("youtu", ignoreCase = true)) {
            return media.sourceUri to MediaType.YOUTUBE
        }

        val isImageMime = media.mimeType?.startsWith("image/", ignoreCase = true) == true
        if (isImageMime && !media.encodedFile.isNullOrBlank()) {
            val mime = media.mimeType ?: "image/jpeg"
            return "data:$mime;base64,${media.encodedFile}" to MediaType.IMAGE_BASE64
        }

        if (isPlayableUri(media.sourceUri)) {
            return media.sourceUri to MediaType.VIDEO_FILE
        }

        if (!media.encodedFile.isNullOrBlank()) {
            val mime = media.mimeType ?: "image/jpeg"
            return "data:$mime;base64,${media.encodedFile}" to MediaType.IMAGE_BASE64
        }

        return null to MediaType.NONE
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
