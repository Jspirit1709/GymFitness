package com.fitnnes.gym.data

import com.google.gson.Gson
import com.fitnnes.gym.workoutdomain.CustomInterval
import com.fitnnes.gym.workoutdomain.Exercise
import com.fitnnes.gym.workoutdomain.ExerciseType
import com.fitnnes.gym.workoutdomain.MediaType
import com.fitnnes.gym.workoutdomain.PhaseType
import java.util.UUID

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
        val mediaList = mutableListOf<ImportMediaDto>()
        val dtoList = exercises.map { it.toDto(mediaList) }
        return gson.toJson(ImportRootDto(exercises = dtoList, media = mediaList.ifEmpty { null }))
    }

    private fun isPlayableUri(uri: String?): Boolean {
        if (uri.isNullOrBlank()) return false
        val lower = uri.trim().lowercase()
        return lower.startsWith("http://") ||
                lower.startsWith("https://") ||
                lower.startsWith("content://") ||
                lower.startsWith("file://") ||
                lower.startsWith("android.resource://")
    }

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

    /**
     * Inverso de resolveMedia: a partir de (mediaUri, mediaType) tal como quedan guardados
     * en Exercise/CustomInterval, arma el ImportMediaDto y lo agrega a la lista compartida
     * de export, devolviendo el mediaId para referenciarlo desde el ejercicio o intervalo.
     * Devuelve null si no hay media real que exportar.
     */
    private fun addMediaAndGetId(
        uri: String?,
        type: MediaType,
        mediaList: MutableList<ImportMediaDto>
    ): String? {
        if (uri.isNullOrBlank() || type == MediaType.NONE) return null

        val id = UUID.randomUUID().toString()
        val dto = when (type) {
            MediaType.YOUTUBE -> ImportMediaDto(id = id, sourceUri = uri)
            MediaType.VIDEO_FILE -> ImportMediaDto(id = id, sourceUri = uri)
            MediaType.IMAGE_BASE64 -> {
                if (uri.startsWith("data:") && uri.contains("base64,")) {
                    val mime = uri.substringAfter("data:").substringBefore(";base64,")
                    val b64 = uri.substringAfter("base64,")
                    ImportMediaDto(id = id, encodedFile = b64, mimeType = mime.ifBlank { "image/jpeg" })
                } else {
                    // Ya viene como URI reproducible (content://, file://, http...), no como base64.
                    ImportMediaDto(id = id, sourceUri = uri)
                }
            }
            MediaType.NONE -> return null
        }
        mediaList.add(dto)
        return id
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

    private fun Exercise.toDto(mediaList: MutableList<ImportMediaDto>): ImportExerciseDto {
        val sequenceDto = customSequence.map { interval ->
            val intervalMediaId = addMediaAndGetId(interval.mediaUri, interval.mediaType, mediaList)
            ImportIntervalDto(
                mediaId = intervalMediaId,
                name = interval.name,
                phaseType = interval.phaseType.ordinal,
                time = interval.time,
                manualRepetitions = interval.repetitions
            )
        }
        val exerciseMediaId = addMediaAndGetId(mediaUri, mediaType, mediaList)
        return ImportExerciseDto(
            customSequence = sequenceDto.ifEmpty { null },
            favourite = favourite,
            iterations = iterations,
            mediaId = exerciseMediaId,
            name = name,
            prepareTime = prepareTime,
            restTime = restTime,
            workTime = workTime
        )
    }
}
