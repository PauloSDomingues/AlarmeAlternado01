package com.paulosd.alarmealternado

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.media.RingtoneManager
import com.paulosd.alarmealternado.util.RingtonePlayer

class AlarmService : Service() {
    private var currentAlarmId: Int = -1
    private var currentRingtoneUri: Uri? = null

    companion object {
        const val ACTION_STOP_ALARM = "com.paulosd.alarmealternado.ACTION_STOP_ALARM"
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        when (intent.action) {
            ACTION_STOP_ALARM -> {
                val alarmId = intent.getIntExtra("alarmId", -1)
                if (alarmId == currentAlarmId) {
                    stopAlarm()
                    stopSelf() // Parar o serviço se não estiver fazendo mais nada
                }
            }
            else -> {
                val ringtoneUriString = intent.getStringExtra("ringtoneUri")
                val alarmId = intent.getIntExtra("alarmId", -1)

                ringtoneUriString?.let {
                    val ringtoneUri = Uri.parse(it)
                    if (alarmId != -1 && alarmId != currentAlarmId) {
                        currentAlarmId = alarmId
                        currentRingtoneUri = ringtoneUri
                        RingtonePlayer.play(this, ringtoneUri)
                        Log.d("AlarmService", "Iniciando som do alarme $alarmId com URI: $ringtoneUri")
                    }
                }

                // Criar notificação em primeiro plano
                val notification = NotificationCompat.Builder(this, "alarm_channel")
                    .setContentTitle("Alarme em execução")
                    .setContentText("Toque do alarme está ativo!")
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setOngoing(true)
                    .build()

                startForeground(1, notification)
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        RingtonePlayer.stop()
        currentAlarmId = -1
        currentRingtoneUri = null
        Log.d("AlarmService", "Serviço de alarme destruído.")
    }

    private fun stopAlarm() {
        RingtonePlayer.stop()
        // Remover a notificação em primeiro plano
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(currentAlarmId)
        currentAlarmId = -1
        Log.d("AlarmService", "Alarme parado.")
    }
}