package com.paulosd.alarmealternado.receiver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.paulosd.alarmealternado.AlarmService
import com.paulosd.alarmealternado.R
import java.util.concurrent.TimeUnit
import android.app.AlarmManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.paulosd.alarmealternado.model.Alarm
import java.util.Date
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        var currentRingtone: Ringtone? = null
        var currentAlarmId: Int = -1
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra("alarmId", -1)
        val ringtoneUriString = intent.getStringExtra("ringtoneUri")
        val shouldVibrate = intent.getBooleanExtra("vibration", false)
        val isRecurring48Hours = intent.getBooleanExtra("isRecurring48Hours", false)

        Log.d("AlarmReceiver", "Alarme recebido para ID: $alarmId, URI: $ringtoneUriString, Vibração: $shouldVibrate, Recorrência: $isRecurring48Hours")

        if (alarmId == -1) {
            Log.e("AlarmReceiver", "ID do alarme inválido.")
            return
        }

        // Iniciar o AlarmService para tocar o alarme
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra("ringtoneUri", ringtoneUriString)
            putExtra("alarmId", alarmId)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d("AlarmReceiver", "Iniciando ForegroundService para alarme $alarmId")
            context.startForegroundService(serviceIntent)
        } else {
            Log.d("AlarmReceiver", "Iniciando Service para alarme $alarmId")
            context.startService(serviceIntent)
        }

        // Exibir notificação
        showNotification(context, alarmId)

        // Agendar o próximo alarme se a recorrência de 48 horas estiver ativa
        if (isRecurring48Hours) {
            scheduleNextAlarm(context, alarmId, ringtoneUriString, shouldVibrate)
        }
    }

    private fun scheduleNextAlarm(context: Context, alarmId: Int, ringtoneUri: String?, vibration: Boolean) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val nextAlarmTime = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(2)

        // Carregar a lista de alarmes para atualizar o horário
        val sharedPreferences = context.getSharedPreferences("alarms_pref", Context.MODE_PRIVATE)
        val gson = Gson()
        val json = sharedPreferences.getString("alarmList", null)
        val type = object : TypeToken<MutableList<Alarm>>() {}.type
        val alarmList = gson.fromJson<MutableList<Alarm>>(json, type) ?: mutableListOf()

        alarmList.find { it.id == alarmId }?.let { alarmToUpdate ->
            alarmToUpdate.timeInMillis = nextAlarmTime
            // Salvar a lista atualizada
            val editor = sharedPreferences.edit()
            val updatedJson = gson.toJson(alarmList)
            editor.putString("alarmList", updatedJson)
            editor.apply()
            Log.d("AlarmReceiver", "Horário do alarme $alarmId atualizado para ${Date(nextAlarmTime)} e salvo.")

            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("alarmId", alarmId)
                putExtra("timeInMillis", nextAlarmTime)
                putExtra("ringtoneUri", ringtoneUri)
                putExtra("vibration", vibration)
                putExtra("isRecurring48Hours", true)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                alarmId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextAlarmTime,
                        pendingIntent
                    )
                    Log.d("AlarmReceiver", "Próximo alarme (exato) agendado para ${Date(nextAlarmTime)} (ID: $alarmId)")
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, nextAlarmTime, pendingIntent)
                    Log.d("AlarmReceiver", "Próximo alarme (não exato) agendado para ${Date(nextAlarmTime)} (ID: $alarmId) - permissão negada")
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextAlarmTime,
                    pendingIntent
                )
                Log.d("AlarmReceiver", "Próximo alarme (exato) agendado para ${Date(nextAlarmTime)} (ID: $alarmId)")
            }
        } ?: run {
            Log.w("AlarmReceiver", "Alarme com ID $alarmId não encontrado para reagendamento.")
        }
    }

    private fun showNotification(context: Context, alarmId: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "alarm_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Alarm Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for alarm notifications"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val stopIntent = Intent(context, AlarmService::class.java).apply {
            action = AlarmService.ACTION_STOP_ALARM
            putExtra("alarmId", alarmId)
        }

        val stopPendingIntent = PendingIntent.getService(
            context,
            alarmId,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Alarme Disparado")
            .setContentText("Alarme $alarmId disparado!")
            .setSmallIcon(R.drawable.ic_alarm)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .addAction(R.drawable.ic_stop, "Parar", stopPendingIntent)
            .setOngoing(true)
            .build()

        notificationManager.notify(alarmId, notification)
    }
}