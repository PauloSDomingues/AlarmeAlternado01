package com.paulosd.alarmealternado.util

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri

object RingtonePlayer {
    private var ringtone: Ringtone? = null

    fun play(context: Context, uri: Uri) {
        stop() // Garante que não tenha nenhum tocando antes
        ringtone = RingtoneManager.getRingtone(context, uri)
        ringtone?.apply {
            isLooping = true
            play()
        }
    }

    fun stop() {
        ringtone?.stop()
        ringtone = null
    }
}