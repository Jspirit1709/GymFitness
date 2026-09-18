package com.fitnnes.gym.workoutdomain

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

enum class ExerciseType {
    HIIT, TABATA, CUSTOM, AMRAP, EMOM
}

enum class PhaseType {
    PREPARE, WORK, REST, COOL_DOWN, TRANSITION
}

@Parcelize
data class CustomInterval(
    var name: String = "",
    var time: Int = 30,
    var phaseType: PhaseType = PhaseType.WORK,
    var repetitions: Int? = null,
    var mediaUri: String? = null,
    var mediaType: MediaType = MediaType.NONE,
    var extraMedia: List<MediaItem> = emptyList()
) : Parcelable

@Parcelize
data class Exercise(
    var id: String = UUID.randomUUID().toString(),
    var name: String = "New Exercise",
    var prepareTime: Int = 10,
    var workTime: Int = 30,
    var restTime: Int = 10,
    var iterations: Int = 8,
    var favourite: Boolean = false,
    var notes: String? = null,
    var mediaUri: String? = null,
    var mediaType: MediaType = MediaType.NONE,
    var tags: List<String> = emptyList(),
    var exerciseType: ExerciseType = ExerciseType.HIIT,
    var useCustomIntervals: Boolean = false,
    var customSequence: List<CustomInterval> = emptyList(),
    var rounds: Int = 1,
    var coolDownTime: Int = 0,
    var colorHex: String? = null,
    var createdAt: Long = System.currentTimeMillis(),
    var extraMedia: List<MediaItem> = emptyList()
) : Parcelable {

    /** Media principal (mediaUri) + las extra, en orden. */
    @Suppress("USELESS_ELVIS")
    fun allMedia(): List<MediaItem> {
        val first = if (!mediaUri.isNullOrBlank() && mediaType != MediaType.NONE)
            listOf(MediaItem(mediaUri.orEmpty(), mediaType)) else emptyList()
        return first + (extraMedia ?: emptyList())
    }


    fun getRoundDuration(): Int {
        return if (useCustomIntervals && customSequence.isNotEmpty()) {
            customSequence.sumOf { it.time }
        } else {
            prepareTime + (workTime + restTime) * iterations + coolDownTime
        }
    }

    fun getTotalTime(): Int {
        val perRound = if (useCustomIntervals && customSequence.isNotEmpty()) {
            customSequence.sumOf { it.time }
        } else {
            (workTime + restTime) * iterations + coolDownTime
        }
        val prep = if (useCustomIntervals) 0 else prepareTime
        return prep + perRound * rounds
    }

    fun estimatedCalories(kcalPerMinute: Double = 6.5): Int {
        return ((getTotalTime() / 60.0) * kcalPerMinute).toInt()
    }
}

@Parcelize
data class ExercisePlan(
    var id: String = UUID.randomUUID().toString(),
    var name: String = "My Plan",
    var exerciseIds: List<String> = emptyList(),
    var createdAt: Long = System.currentTimeMillis()
) : Parcelable
