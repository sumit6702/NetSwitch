package com.sumit.netswitch.model

data class ShizukuStatus(
    val isInstalled: Boolean,
    val isRunning: Boolean,
    val isPermissionGranted: Boolean,
    val isServiceConnected: Boolean
) {
    val isReady: Boolean
        get() = isInstalled && isRunning && isPermissionGranted
}
