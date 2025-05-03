package com.paulosd.alarmealternado

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.paulosd.alarmealternado.adapter.AlarmAdapter
import com.paulosd.alarmealternado.databinding.ActivityMainBinding
import com.paulosd.alarmealternado.model.Alarm
import com.paulosd.alarmealternado.receiver.AlarmReceiver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.media.RingtoneManager
import com.paulosd.alarmealternado.receiver.StopAlarmReceiver

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var alarmList = mutableListOf<Alarm>()
    private lateinit var alarmAdapter: AlarmAdapter
    private lateinit var sharedPreferences: SharedPreferences
    private val gson = Gson()
    private val alarmListKey = "alarmList"

    private val resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val updatedAlarm = result.data?.getSerializableExtra("alarm") as? Alarm
            updatedAlarm?.let { alarm ->
                val existingAlarmIndex = alarmList.indexOfFirst { it.id == alarm.id }

                if (existingAlarmIndex != -1) {
                    // Alarme existente encontrado, atualizar
                    Log.d("MainActivity", "Alarme com ID ${alarm.id} atualizado.")
                    alarmList[existingAlarmIndex] = alarm
                    Toast.makeText(this, "Alarme atualizado para ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(alarm.timeInMillis))}", Toast.LENGTH_SHORT).show()
                } else {
                    // Nenhum alarme existente encontrado com esse ID (novo alarme)
                    Log.d("MainActivity", "Novo alarme adicionado com ID: ${alarm.id}")
                    alarmList.add(alarm)
                    Toast.makeText(this, "Alarme agendado para ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(alarm.timeInMillis))}", Toast.LENGTH_SHORT).show()
                }

                saveAlarms()
                alarmAdapter.updateAlarmList(alarmList)
                scheduleAlarm(this, alarm)
                updateEmptyViewVisibility()
            }
        }
    }

    private val alarmTriggeredReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("MainActivity", "Broadcast ALARM_TRIGGERED recebido!")
            if (intent.action == "com.paulosd.alarmealternado.ALARM_TRIGGERED") {
                loadAlarms() // Recarregar a lista de alarmes
                alarmAdapter.updateAlarmList(alarmList)
                updateEmptyViewVisibility()
                Log.d("MainActivity", "Lista de alarmes atualizada e adaptador notificado.")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar: Toolbar = binding.toolbar
        setSupportActionBar(toolbar)

        sharedPreferences = getSharedPreferences("alarms_pref", Context.MODE_PRIVATE)

        checkAndRequestPermissions()

        // Inicialize o adapter ANTES de carregar os alarmes
        alarmAdapter = AlarmAdapter(
            alarmList,
            onEditClick = { alarm ->
                Log.d("MainActivity", "Botão Editar clicado para o alarme com ID: ${alarm.id}")
                cancelAlarm(alarm) // Cancelar o alarme antigo antes de editar
                val intent = Intent(this, AlarmConfigActivity::class.java).apply {
                    putExtra("alarm_id", alarm.id)
                    putExtra("alarm_time_in_millis", alarm.timeInMillis)
                    putExtra("alarm_ringtone_uri", alarm.ringtoneUri)
                    putExtra("alarm_vibration", alarm.vibration)
                    putExtra("alarm_is_recurring", alarm.isRecurring48Hours)
                    putExtra("is_edit_mode", true)
                }
                resultLauncher.launch(intent)
            },
            onDeleteClick = { alarm -> removeAlarm(alarm) }
        )

        binding.recyclerAlarms.layoutManager = LinearLayoutManager(this)
        binding.recyclerAlarms.adapter = alarmAdapter

        // carrega os alarmes
        loadAlarms()

        binding.fabAdd.setOnClickListener {
            val intent = Intent(this, AlarmConfigActivity::class.java)
            resultLauncher.launch(intent)
        }

        val filter = IntentFilter("com.paulosd.alarmealternado.ALARM_TRIGGERED")
        registerReceiver(alarmTriggeredReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(alarmTriggeredReceiver)
    }

    private fun removeAlarm(alarm: Alarm) {
        val index = alarmList.indexOf(alarm)
        if (index != -1) {
            alarmList.removeAt(index)
            saveAlarms() // Salvar a lista após remover um alarme
            alarmAdapter.updateAlarmList(alarmList)
            cancelAlarm(alarm)
            val stopIntent = Intent(this, StopAlarmReceiver::class.java).apply {
                //  action = AlarmService.ACTION_STOP_ALARM // Ação incorreta!
                putExtra("alarmId", alarm.id)
            }
            sendBroadcast(stopIntent)
            Toast.makeText(this, "Alarme cancelado.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cancelAlarm(alarm: Alarm) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            alarm.id,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let {
            alarmManager.cancel(it)
            Log.d("MainActivity", "Alarme com ID ${alarm.id} cancelado.")
        }
        updateEmptyViewVisibility()
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_CODE_PERMISSION_NOTIFICATIONS
                )
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager // Obtenha a instância aqui
            if (!alarmManager.canScheduleExactAlarms()) {
                openExactAlarmPermissionSettings()
            } else {
                Toast.makeText(this, "Permissão de alarmes exatos concedida.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openExactAlarmPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    private fun scheduleAlarm(context: Context, alarm: Alarm) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            openExactAlarmPermissionSettings()
            Toast.makeText(this, "Por favor, permita alarmes exatos para o funcionamento correto.", Toast.LENGTH_LONG).show()
            return
        }

        Log.d("MainActivity", "Agendando alarme para ${alarm.timeInMillis} (ID: ${alarm.id}), Recorrência: ${alarm.isRecurring48Hours}")

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("alarmId", alarm.id)
            putExtra("timeInMillis", alarm.timeInMillis)
            putExtra("ringtoneUri", alarm.ringtoneUri)
            putExtra("vibration", alarm.vibration)
            putExtra("isRecurring48Hours", alarm.isRecurring48Hours)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            alarm.timeInMillis,
            pendingIntent
        )

        Log.d("MainActivity", "Alarme agendado para ${java.util.Date(alarm.timeInMillis)}")
    }

    private fun saveAlarms() {
        val editor = sharedPreferences.edit()
        val json = gson.toJson(alarmList)
        editor.putString(alarmListKey, json)
        editor.apply()
        Log.d("MainActivity", "Alarmes salvos: $json")
        updateEmptyViewVisibility()
    }

    private fun loadAlarms() {
        val sharedPreferences = getSharedPreferences("alarms_pref", Context.MODE_PRIVATE)
        val gson = Gson()
        val json = sharedPreferences.getString("alarmList", null)
        val type = object : TypeToken<MutableList<Alarm>>() {}.type
        alarmList = gson.fromJson(json, type) ?: mutableListOf()

        alarmList = alarmList.map { alarm ->
            Log.d("MainActivity", "Carregando alarme com URI: ${alarm.ringtoneUri}")
            if (alarm.ringtoneName.isNullOrEmpty() && !alarm.ringtoneUri.isNullOrEmpty()) {
                try {
                    val uri = Uri.parse(alarm.ringtoneUri)
                    val ringtone = RingtoneManager.getRingtone(this, uri)
                    val name = ringtone?.getTitle(this)
                    alarm.copy(ringtoneName = name) // Cria uma cópia com o nome atualizado
                } catch (e: Exception) {
                    Log.e("MainActivity", "Erro ao obter nome do toque para URI: ${alarm.ringtoneUri}", e)
                    alarm // Retorna o alarme original em caso de erro
                }
            } else {
                alarm // Retorna o alarme original se o nome já estiver presente ou a URI for nula
            }
        }.toMutableList()

        alarmAdapter.updateAlarmList(alarmList)
        updateEmptyViewVisibility()
    }

    private fun updateEmptyViewVisibility() {
        if (alarmList.isEmpty()) {
            binding.recyclerAlarms.visibility = View.GONE
            binding.emptyAlarmsTextView.visibility = View.VISIBLE
        } else {
            binding.recyclerAlarms.visibility = View.VISIBLE
            binding.emptyAlarmsTextView.visibility = View.GONE
        }
    }

    companion object {
        private const val REQUEST_CODE_PERMISSION_NOTIFICATIONS = 2001
    }
}