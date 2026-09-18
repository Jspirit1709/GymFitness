package com.fitnnes.gym.workoutdomain

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class MediaType {
    NONE, IMAGE_BASE64, VIDEO_FILE, YOUTUBE
}

@Parcelize
data class MediaItem(
    var uri: String = "",
    var type: MediaType = MediaType.NONE
) : Parcelable
