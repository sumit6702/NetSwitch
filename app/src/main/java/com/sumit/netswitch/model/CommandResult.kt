package com.sumit.netswitch.model

import org.json.JSONObject

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val isSuccess: Boolean
        get() = exitCode == 0

    fun toJson(): String {
        val json = JSONObject()
        json.put("exitCode", exitCode)
        json.put("stdout", stdout)
        json.put("stderr", stderr)
        return json.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): CommandResult {
            return try {
                val json = JSONObject(jsonStr)
                CommandResult(
                    exitCode = json.optInt("exitCode", -1),
                    stdout = json.optString("stdout", ""),
                    stderr = json.optString("stderr", "")
                )
            } catch (e: Exception) {
                CommandResult(
                    exitCode = -1,
                    stdout = "",
                    stderr = "Failed to parse command result: ${e.message}"
                )
            }
        }

        fun error(message: String): CommandResult {
            return CommandResult(
                exitCode = -1,
                stdout = "",
                stderr = message
            )
        }
    }
}
