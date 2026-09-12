package com.fitnnes.gym.data

import java.util.UUID

data class WorkoutSession(
    val id: String = UUID.randomUUID().toString(),
    val exerciseId: String,
    val exerciseName: String,
    val tags: List<String> = emptyList(),
    val dateMillis: Long = System.currentTimeMillis(),
    val durationSeconds: Int,
    val calories: Int
)
