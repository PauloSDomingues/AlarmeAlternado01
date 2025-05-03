package com.paulosd.alarmealternado.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.paulosd.alarmealternado.databinding.ItemAlarmBinding
import com.paulosd.alarmealternado.model.Alarm
import java.text.SimpleDateFormat
import java.util.*

class AlarmAdapter(
    private var alarmList: List<Alarm>,
    private val onEditClick: (Alarm) -> Unit,
    private val onDeleteClick: (Alarm) -> Unit
) : RecyclerView.Adapter<AlarmAdapter.AlarmViewHolder>() {

    inner class AlarmViewHolder(val binding: ItemAlarmBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val binding = ItemAlarmBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AlarmViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        val alarm = alarmList[position]
        val dateTime = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            .format(Date(alarm.timeInMillis))

        val ringtoneText = if (!alarm.ringtoneName.isNullOrEmpty()) {
            "Toque: ${alarm.ringtoneName}"
        } else {
            "Toque: Padrão" // Ou outra mensagem padrão, se preferir
        }

        holder.binding.apply {
            textDateTime.text = dateTime
            textVibration.text = "Vibração: ${if (alarm.vibration) "Sim" else "Não"}"
            textRingtoneName.text = ringtoneText // Definindo o texto do nome do toque
            val recurringText = if (alarm.isRecurring48Hours) "Repete a cada 48 horas" else "Não repete"
            textRecurring.text = recurringText
            btnEdit.setOnClickListener { onEditClick(alarm) }
            btnDelete.setOnClickListener { onDeleteClick(alarm) }
        }
    }

    override fun getItemCount(): Int = alarmList.size

    fun updateAlarmList(newAlarmList: List<Alarm>) {
        this.alarmList = newAlarmList
        notifyDataSetChanged()
    }
}