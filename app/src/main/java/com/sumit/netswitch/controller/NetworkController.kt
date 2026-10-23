package com.sumit.netswitch.controller

import android.content.Context
import com.sumit.netswitch.data.PreferencesRepository
import com.sumit.netswitch.model.CommandResult
import com.sumit.netswitch.model.NetworkMode
import com.sumit.netswitch.shizuku.ShizukuManager
import kotlinx.coroutines.delay

data class NetworkStateResult(
    val mode: NetworkMode,
    val rawOutput: String,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

data class ToggleResult(
    val success: Boolean,
    val previousMode: NetworkMode,
    val newMode: NetworkMode,
    val message: String,
    val stateDidNotUpdateWarning: Boolean = false,
    val rawOutput: String = ""
)

object NetworkController {

    const val DEFAULT_5G_MASK = "11001101001110000111"
    const val DEFAULT_4G_MASK = "01001101001110000111"
    const val SUCCESS_OUTPUT = "set-allowed-network-types-for-users completed"

    private val KNOWN_TELEPHONY_TYPES = setOf(
        "GPRS", "EDGE", "UMTS", "HSDPA", "HSUPA", "HSPA",
        "LTE", "HSPA+", "GSM", "LTE_CA", "NR", "CDMA",
        "EVDO_0", "EVDO_A", "1xRTT", "EHRPD", "TD_SCDMA", "IWLAN"
    )

    fun parseNetworkMode(output: String?): NetworkMode {
        if (output.isNullOrBlank()) return NetworkMode.UNKNOWN
        val clean = output.trim()

        if (clean.contains("Exception", ignoreCase = true) ||
            clean.contains("Error", ignoreCase = true) ||
            clean.contains("not found", ignoreCase = true) ||
            clean.contains("invalid", ignoreCase = true) ||
            clean.contains("failed", ignoreCase = true)
        ) {
            return NetworkMode.UNKNOWN
        }

        val tokens = clean.split("|", " ", "\n", "\r", "\t")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val hasKnownType = tokens.any { it.uppercase() in KNOWN_TELEPHONY_TYPES }
        if (!hasKnownType) {
            return NetworkMode.UNKNOWN
        }

        return if (tokens.any { it.equals("NR", ignoreCase = true) }) {
            NetworkMode.FIVE_G
        } else {
            NetworkMode.FOUR_G
        }
    }

    suspend fun getState(context: Context, slot: Int): NetworkStateResult {
        val command = "cmd phone get-allowed-network-types-for-users -s $slot"
        val result = ShizukuManager.executeCommand(context, command)

        if (!result.isSuccess) {
            val errorMsg = if (result.stderr.isNotBlank()) result.stderr else "Command failed with exit code ${result.exitCode}"
            return NetworkStateResult(
                mode = NetworkMode.UNKNOWN,
                rawOutput = result.stdout.ifBlank { result.stderr },
                isSuccess = false,
                errorMessage = errorMsg
            )
        }

        val parsedMode = parseNetworkMode(result.stdout)
        return NetworkStateResult(
            mode = parsedMode,
            rawOutput = result.stdout,
            isSuccess = true,
            errorMessage = if (parsedMode == NetworkMode.UNKNOWN) "Unrecognized network types output" else null
        )
    }

    suspend fun setMode(
        context: Context,
        slot: Int,
        targetMode: NetworkMode,
        custom5gMask: String? = null,
        custom4gMask: String? = null
    ): CommandResult {
        val mask = when (targetMode) {
            NetworkMode.FIVE_G -> custom5gMask ?: DEFAULT_5G_MASK
            NetworkMode.FOUR_G -> custom4gMask ?: DEFAULT_4G_MASK
            NetworkMode.UNKNOWN -> return CommandResult.error("Cannot set mode to UNKNOWN")
        }

        val command = "cmd phone set-allowed-network-types-for-users -s $slot $mask"
        return ShizukuManager.executeCommand(context, command)
    }

    suspend fun restoreMask(context: Context, slot: Int, maskToRestore: String): CommandResult {
        val command = "cmd phone set-allowed-network-types-for-users -s $slot $maskToRestore"
        return ShizukuManager.executeCommand(context, command)
    }

    suspend fun toggle(
        context: Context,
        slot: Int,
        custom5gMask: String? = null,
        custom4gMask: String? = null
    ): ToggleResult {
        // Step 1: Read current state
        val currentStateResult = getState(context, slot)
        if (!currentStateResult.isSuccess || currentStateResult.mode == NetworkMode.UNKNOWN) {
            return ToggleResult(
                success = false,
                previousMode = currentStateResult.mode,
                newMode = currentStateResult.mode,
                message = currentStateResult.errorMessage ?: "Failed to read current network state. Ensure Shizuku is running and permission is granted.",
                rawOutput = currentStateResult.rawOutput
            )
        }

        val currentMode = currentStateResult.mode
        val targetMode = currentMode.getOpposite()

        // Step 2: Save original mask if not already saved
        val prefs = PreferencesRepository(context)
        if (prefs.savedOriginalMask == null) {
            val originalMask = if (currentMode == NetworkMode.FIVE_G) {
                custom5gMask ?: DEFAULT_5G_MASK
            } else {
                custom4gMask ?: DEFAULT_4G_MASK
            }
            prefs.savedOriginalMask = originalMask
        }

        // Step 3: Execute set command
        val setResult = setMode(
            context = context,
            slot = slot,
            targetMode = targetMode,
            custom5gMask = custom5gMask,
            custom4gMask = custom4gMask
        )

        if (!setResult.isSuccess) {
            return ToggleResult(
                success = false,
                previousMode = currentMode,
                newMode = currentMode,
                message = "Your phone refused the change. ${setResult.stderr.ifBlank { "Exit code ${setResult.exitCode}" }}",
                rawOutput = setResult.stdout.ifBlank { setResult.stderr }
            )
        }

        // Step 4: Verify state change (poll up to 5 seconds)
        var finalMode = currentMode
        var verified = false

        for (attempt in 1..5) {
            delay(1000L)
            val verifyState = getState(context, slot)
            if (verifyState.isSuccess && verifyState.mode == targetMode) {
                finalMode = verifyState.mode
                verified = true
                break
            }
        }

        if (!verified) {
            val latestState = getState(context, slot)
            finalMode = latestState.mode
            return ToggleResult(
                success = false,
                previousMode = currentMode,
                newMode = finalMode,
                message = "The phone accepted the command but is still on $currentMode mode. Try toggling airplane mode.",
                stateDidNotUpdateWarning = true,
                rawOutput = latestState.rawOutput
            )
        }

        return ToggleResult(
            success = true,
            previousMode = currentMode,
            newMode = finalMode,
            message = "Successfully switched to ${finalMode.displayName}",
            rawOutput = setResult.stdout
        )
    }
}
