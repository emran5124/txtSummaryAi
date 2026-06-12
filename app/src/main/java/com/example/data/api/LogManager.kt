package com.example.data.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogManager {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun log(message: String) {
        val timestamp = timeFormat.format(Date())
        val formattedLog = "[$timestamp] $message"
        val currentList = _logs.value.toMutableList()
        currentList.add(0, formattedLog) // prepend latest log
        if (currentList.size > 200) {
            currentList.removeAt(currentList.lastIndex) // trim excessively old logs
        }
        _logs.value = currentList
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
