package com.sumit.netswitch.service

import android.content.Context
import androidx.annotation.Keep
import com.sumit.netswitch.IUserService
import com.sumit.netswitch.model.CommandResult
import kotlin.system.exitProcess

@Keep
class UserService : IUserService.Stub {

    constructor() : super()

    @Suppress("UNUSED_PARAMETER")
    constructor(context: Context) : super()

    override fun destroy() {
        exitProcess(0)
    }

    override fun runCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            CommandResult(
                exitCode = exitCode,
                stdout = stdout,
                stderr = stderr
            ).toJson()
        } catch (e: Exception) {
            CommandResult(
                exitCode = -1,
                stdout = "",
                stderr = e.message ?: "Execution failed"
            ).toJson()
        }
    }
}
