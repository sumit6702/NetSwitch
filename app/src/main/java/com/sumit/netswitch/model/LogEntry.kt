package com.sumit.netswitch.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val id: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val command: String,
    val result: CommandResult
) {
    fun getFormattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun toFormattedString(): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
        val sb = StringBuilder()
        sb.append("[$time]\n")
        sb.append("Command: ").append(command).append("\n")
        sb.append("Exit Code: ").append(result.exitCode).append("\n")
        if (result.stdout.isNotBlank()) {
            sb.append("STDOUT:\n").append(result.stdout.trim()).append("\n")
        }
        if (result.stderr.isNotBlank()) {
            sb.append("STDERR:\n").append(result.stderr.trim()).append("\n")
        }
        sb.append("--------------------------------------------------")
        return sb.toString()
    }
}
