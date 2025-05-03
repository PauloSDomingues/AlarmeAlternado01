package com.paulosd.alarmealternado

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.datepicker.MaterialDatePicker
import com.paulosd.alarmealternado.databinding.ActivityAlarmConfigBinding
import com.paulosd.alarmealternado.model.Alarm
import java.text.SimpleDateFormat
import java.util.*
import java.util.Calendar
import java.util.TimeZone
import java.util.Locale



class AlarmConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmConfigBinding
    private var alarmId: Int = -1
    private var selectedTimeInMillis: Long = Calendar.getInstance().timeInMillis
    private var selectedRingtoneUri: Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    private lateinit var txtSelectedDate: TextView
    private lateinit var btnSelectDate: Button
    private var currentCalendar = Calendar.getInstance()

    companion object {
        const val RINGTONE_PICKER_REQUEST = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlarmConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        txtSelectedDate = binding.txtSelectedDate
        btnSelectDate = binding.btnSelectDate

        // Inicializa a data exibida com a data atual
        updateSelectedDateTextView()

        btnSelectDate.setOnClickListener {
            showDatePicker()
        }

        binding.btnSelectRingtone.setOnClickListener {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, selectedRingtoneUri)
            }
            startActivityForResult(intent, RINGTONE_PICKER_REQUEST)
        }

        binding.timePicker.setOnTimeChangedListener { _, hourOfDay, minute ->
            currentCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
            currentCalendar.set(Calendar.MINUTE, minute)
            updateSelectedTimeInMillis()
            updateTimeTextView()
        }

        binding.btnSaveAlarm.setOnClickListener {
            val hour = binding.timePicker.hour
            val minute = binding.timePicker.minute

            currentCalendar.set(Calendar.HOUR_OF_DAY, hour)
            currentCalendar.set(Calendar.MINUTE, minute)
            currentCalendar.set(Calendar.SECOND, 0)

            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            Log.d("SaveAlarm", "Data/Hora ao salvar: ${sdf.format(currentCalendar.time)} (millis: ${currentCalendar.timeInMillis})")


            val ringtoneName = selectedRingtoneUri?.let {
                RingtoneManager.getRingtone(this, it)?.getTitle(this)
            }

            val alarm = Alarm(
                id = if (alarmId != -1) alarmId else System.currentTimeMillis().toInt(),
                timeInMillis = currentCalendar.timeInMillis,
                ringtoneUri = selectedRingtoneUri?.toString() ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM).toString(),
                vibration = binding.switchVibration.isChecked,
                isRecurring48Hours = binding.switchRecurring48Hours.isChecked,
                ringtoneName = ringtoneName
            )

            val resultIntent = Intent().apply {
                putExtra("alarm", alarm)
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }

        // Carregar dados para edição, se houver
        loadAlarmDataForEdit()
        updateTimeTextView()
        updateRingtoneTextView()
    }

    private fun updateSelectedTimeInMillis() {
        selectedTimeInMillis = currentCalendar.timeInMillis
    }

    private fun updateTimeTextView() {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        binding.txtTime.text = sdf.format(currentCalendar.time)
    }

    private fun updateSelectedDateTextView() {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        txtSelectedDate.text = "Data: ${sdf.format(currentCalendar.time)}"
    }

    private fun showDatePicker() {
        Log.d("DatePicker", "processo iniciado")
        val builder = MaterialDatePicker.Builder.datePicker()
        builder.setTitleText("Selecionar Data")
        val picker = builder.build()

        picker.addOnPositiveButtonClickListener { selection ->
            Log.d("DatePicker", "Timestamp selecionado (UTC): $selection")

            // Obter a data selecionada (em UTC)
            val selectedDateUtc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
            selectedDateUtc.timeInMillis = selection

            // Obter a hora atual do TimePicker (no fuso horário local)
            val hour = binding.timePicker.hour
            val minute = binding.timePicker.minute

            // Criar um Calendar no fuso horário local e definir a data selecionada e a hora do TimePicker
            currentCalendar = Calendar.getInstance()
            currentCalendar.set(selectedDateUtc.get(Calendar.YEAR),
                selectedDateUtc.get(Calendar.MONTH),
                selectedDateUtc.get(Calendar.DAY_OF_MONTH),
                hour,
                minute,
                0)
            currentCalendar.set(Calendar.MILLISECOND, 0)

            val sdfLocal = SimpleDateFormat("dd/MM/yyyy HH:mm:ss 'Local'", Locale.getDefault())
            Log.d("DatePicker", "Data/Hora combinada (Local): ${sdfLocal.format(currentCalendar.time)}")

            updateSelectedDateTextView()
            updateSelectedTimeInMillis()
        }

        picker.show(supportFragmentManager, picker.toString())
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RINGTONE_PICKER_REQUEST && resultCode == RESULT_OK) {
            val uri: Uri? = data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            selectedRingtoneUri = uri
            updateRingtoneTextView()
        }
    }

    private fun updateRingtoneTextView() {
        val ringtone = selectedRingtoneUri?.let { RingtoneManager.getRingtone(this, it) }
        binding.txtSelectedRingtone.text = ringtone?.getTitle(this) ?: "Padrão"
    }

    private fun loadAlarmDataForEdit() {
        alarmId = intent.getIntExtra("alarm_id", -1)
        Log.d("EditData", "Alarm ID para edição: $alarmId")
        if (alarmId != -1) {
            val timeInMillis = intent.getLongExtra("alarm_time_in_millis", Calendar.getInstance().timeInMillis)
            Log.d("EditData", "timeInMillis recebido para edição: $timeInMillis")
            currentCalendar.timeInMillis = timeInMillis
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            Log.d("EditData", "Data/Hora após definir currentCalendar: ${sdf.format(currentCalendar.time)}")
            // ... restante do código ...
            updateSelectedDateTextView()
            updateTimeTextView()
            updateRingtoneTextView()
        }
    }
}