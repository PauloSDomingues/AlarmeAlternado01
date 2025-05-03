package com.paulosd.alarmealternado.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.paulosd.alarmealternado.AlarmService

class StopAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra("alarmId", -1)

        if (alarmId != -1) {
            Log.d("StopAlarmReceiver", "Solicitando parada do alarme $alarmId")
            // Enviar um Intent para o AlarmService com uma ação para parar
            val serviceIntent = Intent(context, AlarmService::class.java).apply {
                action = AlarmService.ACTION_STOP_ALARM
                putExtra("alarmId", alarmId)
            }
            context.startService(serviceIntent)
        } else {
            Log.e("StopAlarmReceiver", "ID do alarme inválido")
        }
    }
}
