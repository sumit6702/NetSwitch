package com.sumit.netswitch.data

import com.sumit.netswitch.model.CommandResult
import com.sumit.netswitch.model.LogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

object CommandLogRepository {
    private const val MAX_LOGS = 50
    private val idCounter = AtomicLong(1)
    private val logsList = mutableListOf<LogEntry>()

    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logsFlow.asStateFlow()

    @Synchronized
    fun addLog(command: String, result: CommandResult) {
        val entry = LogEntry(
            id = idCounter.getAndIncrement(),
            command = command,
            result = result
        )
        logsList.add(0, entry)
        if (logsList.size > MAX_LOGS) {
            logsList.removeAt(logsList.size - 1)
        }
        _logsFlow.value = logsList.toList()
    }

    @Synchronized
    fun getLogs(): List<LogEntry> {
        return logsList.toList()
    }

    @Synchronized
    fun clearLogs() {
        logsList.clear()
        _logsFlow.value = emptyList()
    }

    @Synchronized
    fun getFormattedLogsString(): String {
        if (logsList.isEmpty()) {
            return "No commands executed yet."
        }
        val sb = StringBuilder()
        sb.append("=== NetSwitch Command Logs (Total: ").append(logsList.size).append(") ===\n\n")
        // Print in chronological order (oldest first)
        logsList.reversed().forEach { entry ->
            sb.append(entry.toFormattedString()).append("\n\n")
        }
        return sb.toString()
    }
}
