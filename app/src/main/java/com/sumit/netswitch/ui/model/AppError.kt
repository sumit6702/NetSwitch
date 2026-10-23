package com.sumit.netswitch.ui.model

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.sumit.netswitch.data.CommandLogRepository
import com.sumit.netswitch.shizuku.ShizukuManager

data class AppError(
    val title: String,
    val cause: String,
    val steps: List<String> = emptyList(),
    val note: String? = null,
    val primaryButtonText: String? = null,
    val primaryAction: (Context) -> Unit = {},
    val secondaryButtonText: String? = null,
    val secondaryAction: (Context) -> Unit = {},
    val showCopyLog: Boolean = false,
    val commandTextToCopy: String? = null
) {
    companion object {
        fun shizukuNotInstalled(): AppError {
            return AppError(
                title = "Shizuku is not installed",
                cause = "NetSwitch needs Shizuku to run privileged shell commands without rooting your phone.",
                steps = listOf(
                    "Install Shizuku from the Google Play Store (recommended) or from GitHub Releases.",
                    "Open Shizuku and follow its on-screen guide to start the service."
                ),
                primaryButtonText = "Google Play",
                primaryAction = { context ->
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${ShizukuManager.SHIZUKU_PACKAGE_NAME}"))
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${ShizukuManager.SHIZUKU_PACKAGE_NAME}"))
                        webIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(webIntent)
                    }
                },
                secondaryButtonText = "GitHub Releases",
                secondaryAction = { context ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku/releases"))
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                }
            )
        }

        fun shizukuNotRunning(): AppError {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                return androidBelow11()
            }
            return AppError(
                title = "Shizuku is not running",
                cause = "The Shizuku service must be running for NetSwitch to communicate with the phone's modem.",
                steps = listOf(
                    "Open Settings > About phone > tap 'Build number' 7 times to enable Developer options.",
                    "Open Settings > System > Developer options > turn on 'Wireless debugging' (phone must be connected to Wi-Fi).",
                    "Open Shizuku > 'Start via Wireless debugging' > 'Pair device with pairing code' > enter the code from the notification.",
                    "Return to Shizuku and press 'Start'."
                ),
                note = "Shizuku stops after a reboot. Repeat step 4 (and pairing if prompted) after each restart.",
                primaryButtonText = "Developer Options",
                primaryAction = { context ->
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        val intent = Intent(Settings.ACTION_SETTINGS)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                    }
                },
                secondaryButtonText = "Open Shizuku",
                secondaryAction = { context ->
                    openShizukuApp(context)
                }
            )
        }

        fun permissionNotGranted(): AppError {
            return AppError(
                title = "Permission required",
                cause = "NetSwitch needs your permission to communicate with Shizuku.",
                steps = listOf(
                    "Tap 'Grant Permission' below to approve Shizuku access.",
                    "If you previously chose 'Deny and don't ask again', open Shizuku > Authorized applications and switch NetSwitch ON."
                ),
                primaryButtonText = "Grant Permission",
                primaryAction = { _ ->
                    ShizukuManager.requestPermission()
                },
                secondaryButtonText = "Open Shizuku",
                secondaryAction = { context ->
                    openShizukuApp(context)
                }
            )
        }

        fun shizukuDisconnected(): AppError {
            return AppError(
                title = "Shizuku disconnected",
                cause = "The Shizuku service process was terminated or restarted.",
                steps = listOf(
                    "Open the Shizuku app and check if the service is running.",
                    "If it stopped, tap 'Start' in Shizuku.",
                    "Return to NetSwitch; it will automatically reconnect."
                ),
                primaryButtonText = "Open Shizuku",
                primaryAction = { context ->
                    openShizukuApp(context)
                },
                secondaryButtonText = "Check Again",
                secondaryAction = { context ->
                    ShizukuManager.updateStatus(context)
                }
            )
        }

        fun commandRefused(reason: String, onRetry: () -> Unit): AppError {
            return AppError(
                title = "Your phone refused the change",
                cause = "The telephony subsystem rejected the request: ${reason.ifBlank { "Unknown refusal" }}",
                steps = listOf(
                    "Toggle Airplane mode ON for 5 seconds, then turn it OFF.",
                    "Try the switch again.",
                    "If the issue persists, check the log."
                ),
                primaryButtonText = "Retry",
                primaryAction = { _ -> onRetry() },
                secondaryButtonText = "Airplane Settings",
                secondaryAction = { context ->
                    val intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                },
                showCopyLog = true
            )
        }

        fun stateDidNotUpdateAfter5Seconds(currentMode: String, onRetry: () -> Unit): AppError {
            return AppError(
                title = "Mode did not update",
                cause = "The phone accepted the command but is still on $currentMode mode after 5 seconds.",
                steps = listOf(
                    "Your modem may take a few moments to re-register with the cell tower.",
                    "Turn Airplane mode ON, wait 5 seconds, and turn Airplane mode OFF to force the modem to refresh.",
                    "Tap 'Retry' to check again."
                ),
                primaryButtonText = "Airplane Settings",
                primaryAction = { context ->
                    val intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                },
                secondaryButtonText = "Retry",
                secondaryAction = { _ -> onRetry() },
                showCopyLog = true
            )
        }

        fun outputCannotBeParsed(rawOutput: String): AppError {
            return AppError(
                title = "Unsupported phone response",
                cause = "Your phone's telephony service returned an unrecognized network format.",
                steps = listOf(
                    "Verify that the chosen SIM slot is active in your phone's Settings.",
                    "If you have a dual-SIM phone, try switching between Slot 0 and Slot 1 in NetSwitch.",
                    "Copy the log below to review or share the exact modem response."
                ),
                note = if (rawOutput.isNotBlank()) "Modem response: $rawOutput" else null,
                primaryButtonText = "Open Mobile Network",
                primaryAction = { context ->
                    val intent = Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                },
                showCopyLog = true
            )
        }

        fun wrongOrEmptySimSlot(slot: Int, onSwitchSlot: (Int) -> Unit): AppError {
            val otherSlot = if (slot == 0) 1 else 0
            return AppError(
                title = "No active SIM in Slot $slot",
                cause = "The phone reported that Slot $slot has no active SIM card or is unavailable.",
                steps = listOf(
                    "Most single-SIM phones use Slot 0 by default.",
                    "If you have only one SIM card inserted, ensure Slot 0 is selected.",
                    "Tap below to switch to Slot $otherSlot."
                ),
                primaryButtonText = "Switch to Slot $otherSlot",
                primaryAction = { _ -> onSwitchSlot(otherSlot) },
                secondaryButtonText = "SIM Settings",
                secondaryAction = { context ->
                    val intent = Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                }
            )
        }

        fun androidBelow11(): AppError {
            return AppError(
                title = "Android 11+ required for Wireless Start",
                cause = "Wireless debugging was introduced in Android 11. Devices running Android 10 or below require a PC with ADB to start Shizuku.",
                steps = listOf(
                    "Connect your phone to a PC using a USB cable with USB Debugging enabled.",
                    "Run the following ADB command on your computer:",
                    "adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh",
                    "Keep Shizuku open on your phone to verify it connects."
                ),
                note = "Note: The start script path can change across Shizuku releases. Always check inside the Shizuku app for the exact ADB command.",
                commandTextToCopy = "adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh",
                primaryButtonText = "Open Shizuku",
                primaryAction = { context ->
                    openShizukuApp(context)
                }
            )
        }

        fun unexpectedError(errorMsg: String, onRetry: () -> Unit): AppError {
            return AppError(
                title = "Something went wrong",
                cause = errorMsg.ifBlank { "An unexpected error occurred while communicating with the phone." },
                steps = listOf(
                    "Ensure Shizuku is running and NetSwitch has permission.",
                    "Try the operation again.",
                    "If the problem continues, copy the diagnostic log."
                ),
                primaryButtonText = "Try Again",
                primaryAction = { _ -> onRetry() },
                showCopyLog = true
            )
        }

        private fun openShizukuApp(context: Context) {
            val pm = context.packageManager
            val packages = listOf(
                ShizukuManager.SHIZUKU_PACKAGE_NAME,
                "moe.shizuku.privileged.api.debug",
                "rikka.sui"
            )
            for (pkg in packages) {
                val intent = pm.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                    return
                }
            }
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${ShizukuManager.SHIZUKU_PACKAGE_NAME}"))
            marketIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(marketIntent)
        }
    }
}
