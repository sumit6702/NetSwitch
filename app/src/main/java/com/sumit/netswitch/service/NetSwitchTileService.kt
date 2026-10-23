package com.sumit.netswitch.service

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.SystemClock
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import com.sumit.netswitch.R
import com.sumit.netswitch.controller.NetworkController
import com.sumit.netswitch.data.PreferencesRepository
import com.sumit.netswitch.model.NetworkMode
import com.sumit.netswitch.shizuku.ShizukuManager
import com.sumit.netswitch.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NetSwitchTileService : TileService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastClickTime = 0L

    override fun onCreate() {
        super.onCreate()
        ShizukuManager.init(applicationContext)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()

        val now = SystemClock.elapsedRealtime()
        if (now - lastClickTime < DEBOUNCE_INTERVAL_MS) {
            Log.d(TAG, "Tile click debounced")
            return
        }
        lastClickTime = now

        val context = applicationContext
        val isShizukuReady = ShizukuManager.isShizukuInstalled(context) &&
                ShizukuManager.isShizukuRunning() &&
                ShizukuManager.isPermissionGranted()

        if (!isShizukuReady) {
            openAppSetupScreen()
            return
        }

        val tile = qsTile ?: return
        tile.subtitle = "Switching..."
        tile.updateTile()

        serviceScope.launch {
            val prefs = PreferencesRepository(context)
            val slot = prefs.selectedSimSlot
            val result = NetworkController.toggle(
                context = context,
                slot = slot,
                custom5gMask = prefs.custom5gMask,
                custom4gMask = prefs.custom4gMask
            )

            if (!result.success) {
                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            }

            refreshTile()
        }
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val context = applicationContext

        val isInstalled = ShizukuManager.isShizukuInstalled(context)
        val isRunning = ShizukuManager.isShizukuRunning()
        val isPermitted = ShizukuManager.isPermissionGranted()

        if (!isInstalled || !isRunning || !isPermitted) {
            tile.label = getString(R.string.tile_label)
            tile.subtitle = "Setup needed"
            tile.state = Tile.STATE_UNAVAILABLE
            tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_network)
            tile.updateTile()
            return
        }

        serviceScope.launch {
            val prefs = PreferencesRepository(context)
            val slot = prefs.selectedSimSlot
            val stateResult = NetworkController.getState(context, slot)

            val currentTile = qsTile ?: return@launch
            currentTile.label = getString(R.string.tile_label)
            currentTile.icon = Icon.createWithResource(this@NetSwitchTileService, R.drawable.ic_qs_network)

            when (stateResult.mode) {
                NetworkMode.FIVE_G -> {
                    currentTile.subtitle = "5G"
                    currentTile.state = Tile.STATE_ACTIVE
                }
                NetworkMode.FOUR_G -> {
                    currentTile.subtitle = "4G"
                    currentTile.state = Tile.STATE_INACTIVE
                }
                NetworkMode.UNKNOWN -> {
                    currentTile.subtitle = "Unknown"
                    currentTile.state = Tile.STATE_INACTIVE
                }
            }
            currentTile.updateTile()
        }
    }

    private fun openAppSetupScreen() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        private const val TAG = "NetSwitchTileService"
        private const val DEBOUNCE_INTERVAL_MS = 2000L
    }
}
