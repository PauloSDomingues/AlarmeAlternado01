package com.paulosd.alarmealternado.model

import java.io.Serializable

data class Alarm(
    val id: Int,
    var timeInMillis: Long,
    val ringtoneUri: String?,
    val vibration: Boolean,
    val isRecurring48Hours: Boolean = false,
    val ringtoneName: String? = null
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

